package org.infra.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Phase 11, 1d. The thing that tells you to go and look.
 *
 * <p>Polls {@link ThreadMXBean#findDeadlockedThreads()} on a fixed interval and
 * publishes what it finds as the gauge {@code payorch.jvm.deadlocked.threads}.
 * Three properties of this class are each load-bearing, and doing any of them
 * the other way is a silent bug rather than a loud one:
 *
 * <ul>
 *   <li><strong>{@code findDeadlockedThreads()}, never {@code
 *       findMonitorDeadlockedThreads()}.</strong> The monitor variant only sees
 *       cycles built from Java {@code synchronized} blocks. Every lock
 *       {@code infra-resilience} takes is a
 *       {@link java.util.concurrent.locks.ReentrantLock} or a semaphore - an
 *       <em>ownable synchronizer</em>, not a monitor - and the monitor-only
 *       method is blind to all of them. Get this wrong and the detector
 *       compiles, deploys, and passes every test right up until the one
 *       incident it exists for, which it silently does not see.</li>
 *   <li><strong>The cycle is logged once per transition, not once per
 *       poll.</strong> A JVM deadlock does not resolve itself - by definition
 *       nothing inside it can make progress - so logging it on every tick
 *       until the process is killed would bury the one occurrence with useful
 *       timing under a wall of identical duplicates.</li>
 *   <li><strong>The gauge is registered - and therefore scraped - from the
 *       first tick, whether or not anything is wrong.</strong> A series that
 *       only starts existing once a deadlock has happened cannot be alerted
 *       on: there is no earlier baseline for a threshold rule to compare
 *       against, and it is indistinguishable from a series nobody ever
 *       scraped.</li>
 * </ul>
 *
 * <p><strong>What this cannot see: virtual threads.</strong> Read this before
 * trusting a zero. {@link ThreadMXBean} predates virtual threads and
 * <a href="https://openjdk.org/jeps/444">JEP 444</a> declined to extend
 * {@code java.lang.management} to them, so
 * {@link ThreadMXBean#findDeadlockedThreads()} walks a lock-ownership graph
 * built only from <em>platform</em> threads. Two virtual threads deadlocked on
 * two {@code ReentrantLock}s are not "sometimes missed" - they are structurally
 * invisible, and this gauge reads 0 for as long as the process lives.
 * {@code -Djdk.trackAllThreads=true} does not close the gap; it only lets
 * {@code jcmd}/{@code jstack} describe virtual threads, it does not extend the
 * cycle search. This was confirmed against a real, verified, permanently-held
 * deadlock rather than inferred from the spec - see phase 11's
 * {@code docs/experiments/30-jvm-deadlock.md}.
 *
 * <p>The consequence is asymmetric and worth stating in exactly one direction:
 * a non-zero reading is authoritative evidence of a deadlock, and a zero
 * reading is evidence of nothing at all on a service running virtual threads.
 * Any alert rule or runbook built on this metric inherits that asymmetry.
 * {@code jcmd <pid> Thread.dump_to_file -format=json} <em>is</em>
 * virtual-thread-aware and is the diagnosis path (it needs a JDK image, not a
 * JRE one); there is no management-API equivalent to poll, which is why this
 * class does not offer one.
 *
 * <p>Runs its own {@link ScheduledExecutorService} rather than Spring's
 * {@code @Scheduled}, deliberately. {@code @Scheduled} only fires in a service
 * that has called {@code @EnableScheduling}, and not every one of the six
 * services this detector is wired into does so for its own reasons -
 * {@code psp-router} and {@code mock-psp-simulator} have no scheduled work of
 * their own. A library bean whose interval silently depended on an
 * application-level annotation it does not control would be exactly the kind
 * of gap this phase exists to close, not one to introduce into it.
 *
 * <p>Off by default ({@code payorch.observability.deadlock-detector.enabled}),
 * on in compose. {@code findDeadlockedThreads()} forces a JVM safepoint, and
 * paying that cost every ten seconds in a service that has never deadlocked -
 * which is most services, most of the time - is a cost with no return.
 */
public class DeadlockDetector implements MeterBinder, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DeadlockDetector.class);

    private final ThreadMXBean threadMXBean;
    private final int intervalSeconds;
    private final AtomicInteger deadlockedThreads = new AtomicInteger(0);

    // Set on the transition into a cycle, cleared on the transition out of one -
    // see the class javadoc on why a poll-scoped flag (log every tick) is wrong.
    private final AtomicBoolean cycleLogged = new AtomicBoolean(false);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        // Daemon: this must never be the reason the JVM fails to exit on
        // shutdown, and destroy() below is a best-effort courtesy, not the only
        // thing standing between this thread and a hung shutdown.
        Thread thread = new Thread(runnable, "payorch-deadlock-detector");
        thread.setDaemon(true);
        return thread;
    });

    public DeadlockDetector(int intervalSeconds) {
        this(ManagementFactory.getThreadMXBean(), intervalSeconds);
    }

    // Package-private: lets the test hand in a ThreadMXBean stub for the "no
    // deadlock" and "method choice matters" cases without needing a real
    // deadlock in the test JVM for every assertion.
    DeadlockDetector(ThreadMXBean threadMXBean, int intervalSeconds) {
        this.threadMXBean = threadMXBean;
        this.intervalSeconds = intervalSeconds;
    }

    @Override
    public void afterPropertiesSet() {
        executor.scheduleWithFixedDelay(this::poll, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // Bound here, unconditionally, rather than lazily on first detection -
        // see the class javadoc on why a gauge born only during an incident
        // cannot be the thing an alert rule is compared against.
        Gauge.builder("payorch.jvm.deadlocked.threads", deadlockedThreads, AtomicInteger::get)
                .description("Platform threads currently part of a JVM deadlock cycle, from "
                        + "ThreadMXBean.findDeadlockedThreads() (ReentrantLock and monitor cycles "
                        + "both). Always present; reads 0 on a healthy JVM - and also reads 0 "
                        + "through a virtual-thread deadlock, which ThreadMXBean cannot see "
                        + "(JEP 444). Non-zero is conclusive; zero is not.")
                .register(registry);
    }

    /**
     * Package-private rather than private so the test can call it directly on
     * demand instead of racing the internal scheduler.
     */
    void poll() {
        try {
            long[] ids = threadMXBean.findDeadlockedThreads();
            int count = ids == null ? 0 : ids.length;
            deadlockedThreads.set(count);

            if (count > 0) {
                if (cycleLogged.compareAndSet(false, true)) {
                    logCycle(ids);
                }
            } else {
                // Not expected for a genuine deadlock - nothing inside one can
                // make progress on its own - but the phase-11 chaos lab's
                // release() forcibly breaks the cycle between arms, and without
                // this reset the NEXT deadlock in the same process would never
                // log because the flag would already be latched.
                cycleLogged.set(false);
            }
        } catch (RuntimeException e) {
            // ThreadMXBean calls are not documented to throw here, but a
            // detector that takes the process down is a strictly worse outcome
            // than a detector that misses one poll and tries again in
            // intervalSeconds.
            log.warn("deadlock poll failed, will retry next interval: {}", e.toString());
        }
    }

    private void logCycle(long[] ids) {
        ThreadInfo[] infos = threadMXBean.getThreadInfo(ids, true, true);
        StringBuilder report = new StringBuilder()
                .append("JVM deadlock detected - ").append(ids.length).append(" threads in a cycle\n");
        for (ThreadInfo info : infos) {
            if (info == null) {
                // Thread exited between findDeadlockedThreads() and
                // getThreadInfo() - vanishingly unlikely for a thread that is,
                // by definition, unable to make progress, but the array is not
                // guaranteed dense.
                continue;
            }
            report.append("  \"").append(info.getThreadName()).append("\" #").append(info.getThreadId())
                    .append(' ').append(info.getThreadState());
            LockInfo lock = info.getLockInfo();
            if (lock != null) {
                report.append(" waiting on ").append(lock)
                        .append(" held by \"").append(info.getLockOwnerName())
                        .append("\" #").append(info.getLockOwnerId());
            }
            report.append('\n');
            for (StackTraceElement frame : info.getStackTrace()) {
                report.append("      at ").append(frame).append('\n');
            }
        }
        log.error(report.toString());
    }

    int deadlockedThreadCount() {
        return deadlockedThreads.get();
    }
}
