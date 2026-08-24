package org.infra.chaos;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 11, 2. The machinery behind {@link ChaosSeam.Action#DEADLOCK},
 * {@link ChaosSeam.Action#LIVELOCK}, {@link ChaosSeam.Action#STARVE} and
 * {@link ChaosSeam.Action#LEAK}.
 *
 * <p><strong>Why a lab rather than a fault manufactured in real code, and why
 * that is not a cop-out.</strong> {@link ChaosSeams}' own javadoc is strict that
 * a seam exists for a fault that needs to happen at a specific line in the
 * middle of a method, while a lock is held - faults the other chaos layers
 * cannot reach. A Java-monitor deadlock has no such natural site in this
 * codebase: there is no path that takes two Java locks in two orders, because
 * the ledger's own locking is at the database and {@code LedgerTransfer}
 * already fixes the row order to prevent exactly this. Manufacturing a
 * two-lock path purely so it can be broken would be inventing the bug in order
 * to find it, and the ledger is the worst place to put a lock that exists only
 * for a demonstration. So the lab is labelled as a lab, in the chaos module,
 * where a reader can see exactly what it is and is not.
 *
 * <p><strong>These are state faults, not point faults.</strong> {@code PAUSE}
 * and {@code FAIL} do one thing on the calling thread and return. Every action
 * this class implements starts, persists, and needs something outside the
 * calling thread to end it - see the per-action methods below for exactly what
 * ends each one. A seam left holding lab state between experiment runs is the
 * "second, invisible fault" the phase-5 lesson warns about, and it applies here
 * more than anywhere else, because these faults are silent by construction.
 *
 * <p><strong>Two counters, not one - found by phase 11, 4, and it is worth
 * explaining why one was not enough.</strong> A single shared "progress"
 * counter that every action touched on every unit of forward motion sounds
 * like exactly what "progress stopped" needs to observe, and for {@link
 * #deadlock}, {@link #starve} and {@link #leak} it is. But {@link #livelock}
 * is a retry loop, and a retry loop has two different kinds of "motion": the
 * loop iterating (which must be counted or the JIT is legally entitled to
 * eliminate a side-effect-free spin - see {@link #livelock}'s own javadoc)
 * and the operation actually completing (which is the one thing a livelock,
 * by definition, never does while contended). Counting both under one name
 * meant the phase plan's own falsification criterion for section 4 - "the
 * lab's progress counter advances -> threads are making progress; this is
 * contention, not livelock" - fired on a <em>perfect</em> livelock, because
 * the anti-JIT touch on every failed spin was indistinguishable from real
 * completion. {@link #spins} and {@link #completions} exist to make that
 * distinction observable instead of assumed: {@code spins} is the counter
 * that proves the threads are running, and {@code completions} is the counter
 * that proves they are getting anywhere. A real livelock climbs the first and
 * never moves the second.
 */
public class ChaosLab {

    private static final Logger log = LoggerFactory.getLogger(ChaosLab.class);

    /** Arm a {@link ChaosSeam.Action#DEADLOCK} seam under this name to take A then B. */
    public static final String LOCK_ORDER_AB = "lab-lock-ab";

    /** Arm a {@link ChaosSeam.Action#DEADLOCK} seam under this name to take B then A. */
    public static final String LOCK_ORDER_BA = "lab-lock-ba";

    /**
     * Retained per {@link ChaosSeam.Action#LEAK} reach. Fixed rather than
     * configurable - this seam exists only for the rate at which the wall is
     * reached (see the class javadoc's note on the real specimen), and that
     * rate is tuned by how often the seam is reached, not by the chunk size.
     */
    private static final int LEAK_KB_PER_REACH = 64;

    /**
     * Deliberately tiny. Two workers is enough for two concurrent
     * {@link ChaosSeam.Action#STARVE} reaches to fill the pool with parents and
     * starve every child behind them - a bigger pool would only mean more
     * concurrent reaches are needed to prove the same thing.
     */
    private static final int STARVE_POOL_SIZE = 2;

    /**
     * How long {@link #deadlock} waits between taking its first lock and
     * asking for its second. The same reasoning as {@code LedgerTransfer}'s own
     * seam: a bare back-to-back {@code lock()}/{@code lock()} leaves a
     * microsecond window, and two independently scheduled HTTP requests do not
     * reliably land inside it - most runs would race each other to complete
     * cleanly instead of colliding. Widening the window is what turns "usually
     * deadlocks" into "deadlocks every time", which is the whole difference
     * between a demonstration and an anecdote.
     */
    private static final long DEADLOCK_WIDEN_MS = 250;

    private final ReentrantLock lockA = new ReentrantLock();
    private final ReentrantLock lockB = new ReentrantLock();

    /**
     * Threads currently parked inside a {@link ReentrantLock#lockInterruptibly()}
     * call taken on behalf of {@link ChaosSeam.Action#DEADLOCK}. {@link #release()}
     * interrupts exactly this set - and only this set, because it is the only
     * one of the four actions whose calling thread is genuinely blocked rather
     * than spinning or waiting with a poll, so it is the only one that cannot
     * unstick itself once the seam is disarmed.
     */
    private final Set<Thread> parkedOnDeadlock = ConcurrentHashMap.newKeySet();

    private final List<byte[]> retained = new CopyOnWriteArrayList<>();

    /**
     * Real forward motion: {@link #deadlock} taking a lock, {@link #starve}'s
     * child running, {@link #leak} retaining a chunk, or {@link #livelock}
     * actually acquiring both of its locks in one attempt. Not per-action: the
     * question every experiment asks is "did anything actually get anywhere",
     * and one counter answers it regardless of which action is armed.
     *
     * <p>Deliberately does <strong>not</strong> count a {@link #livelock} spin
     * that failed to get its second lock - see the class javadoc's note on why
     * that distinction is the entire fix for section 4's falsification
     * criterion. {@code AtomicLong} satisfies the same requirement
     * {@code volatile} would - visibility across the thread that increments it
     * and the thread asserting on it - and gives atomic increment for free
     * where two lab actions happen to race.
     */
    private final AtomicLong completions = new AtomicLong();

    /**
     * Loop iterations that made no claim of getting anywhere - currently only
     * touched by {@link #livelock}'s busy-wait dwell. Exists for one reason:
     * {@link #livelock} is the one action in this lab that is a tight retry
     * loop with an iteration that can, on any given spin, have no externally
     * visible effect at all (fail to get either lock, or get the first and
     * fail the second) - which is legal for the JIT to hoist or eliminate
     * entirely (phase plan trap 1). Touching this on every iteration of the
     * busy-wait is what makes the spin real rather than something the
     * compiler is free to delete, and keeping it separate from {@link
     * #completions} is what makes "the loop is running" and "the loop is
     * getting anywhere" two different, independently checkable claims instead
     * of one counter answering both questions badly.
     */
    private final AtomicLong spins = new AtomicLong();

    private final ExecutorService starvePool = Executors.newFixedThreadPool(
            STARVE_POOL_SIZE, ChaosLab::daemonThread);

    /**
     * Phase 11, 3. Takes both locks in the order the seam name declares, and
     * never lets go on its own.
     *
     * <p>The first lock is taken with a plain, uninterruptible {@link
     * ReentrantLock#lock()} - it must always succeed, or there is nothing for
     * the opposite-ordered arm to deadlock against. The second is taken with
     * {@link ReentrantLock#lockInterruptibly()}, which is what makes {@link
     * #release()} possible at all: an uninterruptible {@code lock()} here would
     * make a Java deadlock genuinely unrecoverable short of a process restart,
     * which is a worse lab than a permanent one that stays permanent only until
     * somebody asks it to stop.
     *
     * @throws IllegalArgumentException if {@code seamName} is not one of {@link
     *         #LOCK_ORDER_AB} or {@link #LOCK_ORDER_BA} - a typo in the arming
     *         curl is a configuration mistake, not a fault to inject
     */
    void deadlock(String seamName) {
        LockPair order = resolveOrder(seamName);
        ReentrantLock first = order.first();
        ReentrantLock second = order.second();

        first.lock();
        try {
            completions.incrementAndGet();
            Thread self = Thread.currentThread();
            // Added to the parked set before the deliberate widening sleep, not
            // after - release() must be able to interrupt this thread out of
            // EITHER wait, not only out of the second lock() call.
            parkedOnDeadlock.add(self);
            try {
                Thread.sleep(DEADLOCK_WIDEN_MS);
                second.lockInterruptibly();
                try {
                    completions.incrementAndGet();
                } finally {
                    second.unlock();
                }
            } catch (InterruptedException e) {
                // release() woke this thread up, from the widening sleep or
                // from the lock wait. Restore the flag rather than swallow it -
                // see ChaosSeams.pause() for why this project never eats an
                // interrupt.
                Thread.currentThread().interrupt();
            } finally {
                parkedOnDeadlock.remove(self);
            }
        } finally {
            first.unlock();
        }
    }

    /**
     * How long a livelock arm holds its first lock before trying for its
     * second, on every spin. The same problem {@link #DEADLOCK_WIDEN_MS}
     * solves for {@link #deadlock}, paid on every iteration instead of once:
     * two arms retrying at full speed with no synchronisation between them
     * mostly do NOT collide - lock/unlock is microseconds, so the far more
     * common outcome is one arm slipping through uncontested while the other
     * has not even reached its own first {@code tryLock} yet. A few
     * milliseconds of dwell time is what makes "both arms are mid-attempt at
     * the same moment" the common case instead of the rare one, which is what
     * a livelock demo needs to actually look like one.
     *
     * <p><strong>This used to be {@code Thread.sleep(15)} - found to be
     * defect B in phase 11, 4.</strong> A sleeping dwell widens the collision
     * window exactly as intended, but it does it by parking the thread
     * {@code TIMED_WAITING} at near-zero CPU, which is the opposite of what
     * this section exists to demonstrate: the central claim is that a
     * livelock is CPU-hot and {@code RUNNABLE} where a deadlock is silent and
     * idle, and {@code process_cpu_usage} rising is one of the plan's own
     * falsification criteria. A sleeping dwell also leaves the thread out of
     * {@code RUNNABLE} in {@code /actuator/threaddump}, which breaks {@code
     * jvm-forensics.sh}'s livelock verdict - it requires the worker threads to
     * be stably {@code RUNNABLE} across all three dumps. {@link
     * #busySpinDwell} replaces the sleep with a busy-wait on a deadline,
     * keeping the exact same wall-clock window width (collision probability
     * is a function of how long the window is open, not of how the CPU is
     * spent while it is) while making the thread genuinely {@code RUNNABLE}
     * and CPU-hot for that whole window - which is what a real livelock looks
     * like. 15ms is kept rather than shortened: burning CPU for the same
     * window that used to cost nothing is the point, not a side effect to
     * minimise, and two arms doing it back-to-back is what produces the ~100%
     * CPU signature the hypothesis predicts.
     */
    private static final long LIVELOCK_DWELL_MS = 15;

    /**
     * Phase 11, 4. {@code tryLock} the first lock the seam name declares
     * (see {@link #resolveOrder}), dwell briefly, {@code tryLock} the second;
     * on failing it, release the first and retry immediately.
     *
     * <p>Touches {@link #spins} on every spin via {@link #busySpinDwell}, not
     * only on success - the trap the phase plan names explicitly: a retry loop
     * whose only work is acquiring and releasing locks with no other
     * observable effect is legal for the JIT to eliminate, and a livelock demo
     * the compiler optimised away produces a confusing null result that looks
     * like the seam failing to arm. {@link #completions} is touched only when
     * a spin actually acquires <em>both</em> locks - see the class javadoc for
     * why the two are kept apart.
     *
     * @param stillArmed polled once per spin. {@code ChaosSeams.reach} supplies
     *        "is this name still in the armed map", which is what makes plain
     *        {@code disarm} sufficient to end this action - unlike {@link
     *        #deadlock}, nothing here ever blocks, so there is nothing for
     *        {@link #release()} to interrupt.
     */
    void livelock(String seamName, BooleanSupplier stillArmed) {
        LockPair order = resolveOrder(seamName);
        ReentrantLock first = order.first();
        ReentrantLock second = order.second();

        while (stillArmed.getAsBoolean()) {
            if (first.tryLock()) {
                try {
                    busySpinDwell(LIVELOCK_DWELL_MS);
                    if (second.tryLock()) {
                        try {
                            completions.incrementAndGet();
                            return;
                        } finally {
                            second.unlock();
                        }
                    }
                    // Could not get the second lock - release the first and go
                    // straight back around. No backoff, on purpose: backoff is
                    // what turns a livelock into ordinary contention, and the
                    // whole point here is the shape that never resolves itself.
                } finally {
                    first.unlock();
                }
            }
            spins.incrementAndGet();
        }
    }

    /**
     * Busy-waits until {@code millis} have elapsed, touching {@link #spins} on
     * every check of the clock.
     *
     * <p>Two things this must do, both load-bearing. First, it must actually
     * burn CPU rather than yield it - see {@link #LIVELOCK_DWELL_MS}'s javadoc
     * for why a sleep here defeats the entire section. Second, the loop must
     * be impossible for the JIT to hoist or eliminate: {@link System#nanoTime()}
     * cannot be constant-folded, so the deadline check cannot be proven
     * loop-invariant, and every iteration performs a visible {@code AtomicLong}
     * write that the JVM's memory model forbids coalescing away. Between the
     * two, this loop cannot be optimised into "sleep once and set the counter
     * to N" - which is exactly what a plain {@code for} loop with a
     * compile-time-predictable trip count risks becoming.
     */
    private void busySpinDwell(long millis) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < deadlineNanos) {
            spins.incrementAndGet();
        }
    }

    /**
     * Phase 11, 5. A task on the lab's own bounded pool submits a second task
     * to the same pool and blocks on its result.
     *
     * <p>The calling thread is submitted onto {@link #starvePool} as the
     * "parent" and blocks on {@link Future#get()} for it - so the starvation is
     * visible at the seam's own call site, not only inside the pool. With
     * {@link #STARVE_POOL_SIZE} small, two or more concurrent reaches fill
     * every worker with a parent, and every child queues behind a parent that
     * is waiting for it and can never be scheduled.
     *
     * @param stillArmed polled by the parent while it waits on its child - see
     *        {@link #livelock} for why this is what makes plain {@code disarm}
     *        sufficient to end this action
     */
    void starve(BooleanSupplier stillArmed) {
        Future<?> parent = starvePool.submit(() -> runStarvedParent(stillArmed));
        try {
            parent.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // The parent recorded its own outcome; nothing further to surface
            // to the caller of reach().
        }
    }

    private void runStarvedParent(BooleanSupplier stillArmed) {
        Future<?> child = starvePool.submit(() -> {
            completions.incrementAndGet();
            return null;
        });
        try {
            while (stillArmed.getAsBoolean()) {
                try {
                    child.get(200, TimeUnit.MILLISECONDS);
                    return;
                } catch (TimeoutException expected) {
                    // Still queued behind however many other parents are
                    // occupying the pool. Expected for as long as the seam is
                    // armed and the pool stays saturated.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException ignored) {
                    return;
                }
            }
        } finally {
            // Disarmed while still queued: cancel rather than abandon it, or
            // repeated arm/disarm cycles pile dead children up in the pool's
            // queue forever - the executor equivalent of the leak this same
            // class also has a seam for.
            child.cancel(true);
        }
    }

    /**
     * Phase 11, 7. Retains {@link #LEAK_KB_PER_REACH} KB per reach, held until
     * {@link #drop()}.
     */
    void leak() {
        retained.add(new byte[LEAK_KB_PER_REACH * 1024]);
        completions.incrementAndGet();
    }

    /**
     * Interrupts every thread parked inside {@link #deadlock}, so it unwinds
     * its {@code finally} blocks and releases whatever lock it holds.
     *
     * <p>This is the half of DEADLOCK's teardown that {@code disarm} cannot do
     * on its own. Disarming stops a NEW reach from taking a lock; it does
     * nothing for a thread already blocked inside {@code lockInterruptibly()},
     * which has no armed-map to poll - it is parked, not spinning. That is why
     * {@link ChaosSeam.Action#DEADLOCK}'s row in the phase plan lists its end
     * condition as "disarm <strong>and</strong> release", the only one of the
     * four actions that needs both.
     *
     * <p>Interrupting a thread that already finished, or one whose interrupt
     * races its own natural completion, is harmless - {@link Set#forEach} over
     * {@link #parkedOnDeadlock} at worst delivers a spurious interrupt to a
     * thread on its way out, which {@link #deadlock} already treats as the
     * normal wake-up path.
     */
    public void release() {
        int count = parkedOnDeadlock.size();
        if (count > 0) {
            log.info("chaos lab: releasing {} thread(s) parked on a lab lock", count);
        }
        parkedOnDeadlock.forEach(Thread::interrupt);
    }

    /**
     * Clears every retained chunk from {@link #leak}.
     *
     * <p>Separate from {@code disarm} on purpose - disarming the {@code LEAK}
     * seam stops NEW retention, exactly like disarming stops any other seam
     * from firing again, but the memory already retained is not the seam's to
     * give back. Only an explicit drop reclaims it, which mirrors the real
     * specimen this seam exists to reach faster: nothing about turning off new
     * traffic frees what {@code AuthorizationLedger} has already retained.
     */
    public void drop() {
        int count = retained.size();
        retained.clear();
        if (count > 0) {
            log.info("chaos lab: dropped {} retained chunk(s), {} KB",
                    count, count * LEAK_KB_PER_REACH);
        }
    }

    /**
     * How many units of REAL forward motion any lab action has made - a lock
     * pair actually taken, a starved child actually run, a chunk actually
     * retained, or a livelock spin that actually got both locks. See the class
     * javadoc for why this is deliberately not the same number as {@link
     * #spins()}.
     */
    public long completions() {
        return completions.get();
    }

    /**
     * How many {@link #livelock} loop iterations have happened, successful or
     * not. Climbs during a perfect livelock precisely because it is not
     * evidence of progress - it is evidence the threads are still running. See
     * {@link #busySpinDwell} for why this cannot be optimised away, and the
     * class javadoc for why it is not folded into {@link #completions()}.
     */
    public long spins() {
        return spins.get();
    }

    /** How many chunks {@link #leak} is currently holding. */
    public int retainedChunks() {
        return retained.size();
    }

    public long retainedBytes() {
        return (long) retained.size() * LEAK_KB_PER_REACH * 1024;
    }

    public boolean lockAHeld() {
        return lockA.isLocked();
    }

    public boolean lockBHeld() {
        return lockB.isLocked();
    }

    /** Threads currently blocked inside {@link #deadlock}, waiting on the other lock. */
    public int parkedOnDeadlockCount() {
        return parkedOnDeadlock.size();
    }

    /**
     * Shared by {@link #deadlock} and {@link #livelock}: both are named by
     * which lock they take first, and both fail the same way on a name that
     * is neither.
     *
     * @throws IllegalArgumentException if {@code seamName} is not one of
     *         {@link #LOCK_ORDER_AB} or {@link #LOCK_ORDER_BA}
     */
    private LockPair resolveOrder(String seamName) {
        if (LOCK_ORDER_AB.equals(seamName)) {
            return new LockPair(lockA, lockB);
        }
        if (LOCK_ORDER_BA.equals(seamName)) {
            return new LockPair(lockB, lockA);
        }
        throw new IllegalArgumentException(
                "must be armed as '" + LOCK_ORDER_AB + "' or '" + LOCK_ORDER_BA
                        + "', not '" + seamName + "'");
    }

    private record LockPair(ReentrantLock first, ReentrantLock second) {
    }

    private static Thread daemonThread(Runnable runnable) {
        // Daemon: a starved parent blocked on a cancelled-but-still-running
        // child must never be the reason the JVM fails to exit on shutdown -
        // the same reasoning as DeadlockDetector's own executor.
        Thread thread = new Thread(runnable, "chaos-lab-starve");
        thread.setDaemon(true);
        return thread;
    }
}
