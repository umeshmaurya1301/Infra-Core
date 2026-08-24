package org.infra.chaos;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 11, 2. Proves the machinery works before anything in phase 3-5 relies
 * on it - the same discipline {@link ChaosSeamsTest} applies to {@code PAUSE}
 * and {@code FAIL}.
 */
class ChaosLabTest {

    private final ChaosLab lab = new ChaosLab();

    /**
     * The whole point of the lab. Two threads taking opposite lock orders park
     * permanently - neither {@code join} returns - until {@link
     * ChaosLab#release()} interrupts them, and then both do.
     */
    @Test
    void oppositeLockOrdersDeadlockAndReleaseRecoversBoth() throws InterruptedException {
        Thread ab = new Thread(() -> lab.deadlock(ChaosLab.LOCK_ORDER_AB));
        Thread ba = new Thread(() -> lab.deadlock(ChaosLab.LOCK_ORDER_BA));
        ab.start();
        ba.start();

        // Give both threads time to take their first lock and block on the
        // second - including the deliberate widening sleep inside deadlock().
        // If either has already finished here, the two arms did not actually
        // deadlock and the rest of the test is meaningless.
        ab.join(800);
        ba.join(800);
        assertThat(ab.isAlive()).as("thread AB should still be parked").isTrue();
        assertThat(ba.isAlive()).as("thread BA should still be parked").isTrue();
        assertThat(lab.parkedOnDeadlockCount()).isEqualTo(2);
        assertThat(lab.lockAHeld()).isTrue();
        assertThat(lab.lockBHeld()).isTrue();

        lab.release();

        ab.join(2000);
        ba.join(2000);
        assertThat(ab.isAlive()).as("AB should have unwound after release").isFalse();
        assertThat(ba.isAlive()).as("BA should have unwound after release").isFalse();
        assertThat(lab.parkedOnDeadlockCount()).isZero();
        assertThat(lab.lockAHeld()).isFalse();
        assertThat(lab.lockBHeld()).isFalse();
    }

    /**
     * The single-arm case: nothing on the other side to contend with, so the
     * first and only caller takes both locks and returns normally. Deadlock
     * needs two opposite-ordered arms; one arm alone is just... two locks.
     */
    @Test
    void oneArmAloneAcquiresBothLocksAndReturns() {
        lab.deadlock(ChaosLab.LOCK_ORDER_AB);

        assertThat(lab.lockAHeld()).isFalse();
        assertThat(lab.lockBHeld()).isFalse();
        assertThat(lab.completions()).isEqualTo(2);
    }

    @Test
    void anUnrecognisedSeamNameIsRejected() {
        assertThatThrownBy(() -> lab.deadlock("not-a-real-seam"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lab-lock-ab");
    }

    /**
     * A single, uncontended livelock arm just acquires both free locks and
     * returns - livelock needs a second arm to collide with, the same reason
     * {@link #oneArmAloneAcquiresBothLocksAndReturns} holds for deadlock.
     *
     * <p>Exactly one {@link ChaosLab#completions()} - the single real
     * acquisition of both locks - but {@link ChaosLab#spins()} is already well
     * into the hundreds from the one {@code busySpinDwell} it ran on the way
     * there. That second number is the regression check for defect B: a dwell
     * that had regressed back to {@code Thread.sleep} would still touch the
     * counter (once, per the old design) but could never produce a count this
     * large from a single 15ms dwell - a tight busy-wait executes its
     * increment far more than once per millisecond.
     */
    @Test
    void oneUncontendedLivelockArmSucceedsImmediately() {
        AtomicBoolean armed = new AtomicBoolean(true);

        lab.livelock(ChaosLab.LOCK_ORDER_AB, armed::get);

        assertThat(lab.completions()).isEqualTo(1);
        assertThat(lab.spins())
                .as("a single 15ms busy-wait dwell should already be into the hundreds of iterations")
                .isGreaterThan(100);
    }

    /**
     * Pins lock B for a guaranteed window using a real {@link
     * ChaosLab#deadlock} call in the opposite order - it takes B immediately
     * and unconditionally, then dwells for a fixed interval before even
     * attempting A, so B is certainly held for that whole window regardless of
     * scheduling. Against that deterministic hold, the livelock arm's
     * {@code tryLock} on B is guaranteed to keep failing - proving the spin is
     * real and touching {@link ChaosLab#spins()} - without relying on two
     * independently-timed spinners to collide with each other by luck, which
     * they mostly do not (see {@code LIVELOCK_DWELL_MS}'s own javadoc).
     *
     * <p><strong>This is the regression test for both phase 11, 4 defects.</strong>
     * Defect A: {@link ChaosLab#completions()} must stay at zero for the whole
     * window - the spinner never gets anywhere, and the old single-counter
     * design would have shown "progress" advancing on every failed attempt,
     * which is exactly the false signal the phase plan's own falsification
     * criterion would have misread as "contention, not livelock". Defect B:
     * {@link ChaosLab#spins()} must reach a count only a genuine busy-wait
     * could produce in this window, not a count bounded by
     * {@code windowMillis / LIVELOCK_DWELL_MS} the way a sleeping dwell would
     * cap it.
     */
    @Test
    void livelockSpinsAgainstAHeldLockAndStopsOnDisarmEvenIfStillContended() throws InterruptedException {
        Thread holder = new Thread(() -> lab.deadlock(ChaosLab.LOCK_ORDER_BA));
        holder.start();
        Thread.sleep(50); // let the holder actually take lock B before the spinner starts

        // The holder is itself a DEADLOCK call and legitimately advances
        // completions() by taking its own first lock - that is real motion for
        // ITS action, not noise. What this test asserts on is the DELTA caused
        // by the livelock spinner, not the absolute value.
        long completionsBeforeSpinning = lab.completions();

        AtomicBoolean armed = new AtomicBoolean(true);
        Thread spinner = new Thread(() -> lab.livelock(ChaosLab.LOCK_ORDER_AB, armed::get));
        spinner.start();

        Thread.sleep(150);
        assertThat(spinner.isAlive()).as("should still be spinning against the held lock").isTrue();
        assertThat(lab.completions())
                .as("defect A: a spin that never gets the second lock must NOT register as forward motion")
                .isEqualTo(completionsBeforeSpinning);
        assertThat(lab.spins())
                .as("defect B: ~150ms against a busy-wait dwell should be many thousands of iterations, "
                        + "not the low single digits a sleep-based dwell would have produced in the same window")
                .isGreaterThan(1000);

        armed.set(false);
        spinner.join(1000);
        assertThat(spinner.isAlive())
                .as("should have exited on disarm alone, with the lock still held and no release() call")
                .isFalse();
        assertThat(lab.completions())
                .as("disarming ends the loop without ever letting it complete - it never got anywhere")
                .isEqualTo(completionsBeforeSpinning);

        holder.join(2000);
        assertThat(holder.isAlive()).isFalse();
    }

    /**
     * Phase 11, 4: "livelock needs no release() since nothing blocks - verify
     * that claim" - the phase plan trusts this but does not itself prove it.
     * {@link ChaosLab#release()} only interrupts threads in {@link
     * ChaosLab#parkedOnDeadlockCount()} (DEADLOCK's set); a livelock thread is
     * never added to it, because {@code tryLock} never blocks. Calling
     * {@code release()} on two genuinely racing livelock arms must therefore
     * do nothing at all - only disarming both (flipping {@code stillArmed})
     * may end them.
     *
     * <p>Uses two opposite-ordered LIVELOCK arms racing each other - the exact
     * shape {@code tools/loadtest/jvm-livelock.sh} drives - rather than a
     * {@link ChaosLab#deadlock} call pinning one lock: a DEADLOCK-based holder
     * adds itself to {@link ChaosLab#parkedOnDeadlockCount()}, which would
     * make the "nothing for release() to act on" assertion below false for a
     * reason that has nothing to do with the livelock arm being tested.
     */
    @Test
    void releaseIsANoOpForLivelockOnlyDisarmEndsIt() throws InterruptedException {
        AtomicBoolean armedAb = new AtomicBoolean(true);
        AtomicBoolean armedBa = new AtomicBoolean(true);
        Thread ab = new Thread(() -> lab.livelock(ChaosLab.LOCK_ORDER_AB, armedAb::get));
        Thread ba = new Thread(() -> lab.livelock(ChaosLab.LOCK_ORDER_BA, armedBa::get));
        ab.start();
        ba.start();
        Thread.sleep(100);
        assertThat(ab.isAlive()).as("AB should still be racing BA").isTrue();
        assertThat(ba.isAlive()).as("BA should still be racing AB").isTrue();

        assertThat(lab.parkedOnDeadlockCount())
                .as("neither livelock arm ever parks - only DEADLOCK adds itself to this set")
                .isZero();
        lab.release();
        Thread.sleep(100);
        assertThat(ab.isAlive())
                .as("release() must be a no-op for LIVELOCK - it only ever acted on DEADLOCK's parked set")
                .isTrue();
        assertThat(ba.isAlive())
                .as("release() must be a no-op for LIVELOCK - it only ever acted on DEADLOCK's parked set")
                .isTrue();

        armedAb.set(false);
        armedBa.set(false);
        ab.join(1000);
        ba.join(1000);
        assertThat(ab.isAlive()).as("should have exited on disarm alone").isFalse();
        assertThat(ba.isAlive()).as("should have exited on disarm alone").isFalse();
    }

    /**
     * A parent occupying the (tiny, two-worker) pool submits a child to the
     * same pool and blocks on it - the child can never run while both workers
     * are parents, so disarming (not releasing - starve never blocks the way
     * deadlock does) is what ends it.
     */
    @Test
    void starveFillsThePoolAndDisarmEndsIt() throws InterruptedException {
        AtomicBoolean armed = new AtomicBoolean(true);
        Thread parentOne = new Thread(() -> lab.starve(armed::get));
        Thread parentTwo = new Thread(() -> lab.starve(armed::get));
        parentOne.start();
        parentTwo.start();

        // Both pool workers are now occupied by parents; their children are
        // queued and cannot run, so progress must stay at zero while armed.
        Thread.sleep(500);
        assertThat(parentOne.isAlive()).isTrue();
        assertThat(parentTwo.isAlive()).isTrue();
        assertThat(lab.completions()).as("no child should have run while the pool was starved").isZero();

        armed.set(false);
        parentOne.join(3000);
        parentTwo.join(3000);
        assertThat(parentOne.isAlive()).isFalse();
        assertThat(parentTwo.isAlive()).isFalse();
    }

    @Test
    void leakRetainsUntilDropped() {
        assertThat(lab.retainedChunks()).isZero();

        lab.leak();
        lab.leak();
        lab.leak();

        assertThat(lab.retainedChunks()).isEqualTo(3);
        assertThat(lab.retainedBytes()).isPositive();
        assertThat(lab.completions()).isEqualTo(3);

        lab.drop();

        assertThat(lab.retainedChunks()).isZero();
        assertThat(lab.retainedBytes()).isZero();
    }

    /** {@link ChaosLab#release()} with nothing parked is a safe no-op. */
    @Test
    void releaseWithNothingParkedDoesNothing() {
        lab.release();
        assertThat(lab.parkedOnDeadlockCount()).isZero();
    }

    @Test
    void theEndpointReportsStatusAndDrivesReleaseAndDrop() throws InterruptedException {
        ChaosLabEndpoint endpoint = new ChaosLabEndpoint(lab);

        lab.leak();
        assertThat(endpoint.status())
                .containsEntry("retainedChunks", 1)
                .containsEntry("completions", 1L)
                .containsEntry("spins", 0L);

        Thread ab = new Thread(() -> lab.deadlock(ChaosLab.LOCK_ORDER_AB));
        Thread ba = new Thread(() -> lab.deadlock(ChaosLab.LOCK_ORDER_BA));
        ab.start();
        ba.start();
        ab.join(800);
        ba.join(800);
        assertThat(endpoint.status()).containsEntry("parkedOnDeadlock", 2);

        endpoint.execute("release");
        ab.join(2000);
        ba.join(2000);
        assertThat(lab.parkedOnDeadlockCount()).isZero();

        endpoint.execute("drop");
        assertThat(endpoint.status()).containsEntry("retainedChunks", 0);

        assertThatThrownBy(() -> endpoint.execute("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("release");
    }
}
