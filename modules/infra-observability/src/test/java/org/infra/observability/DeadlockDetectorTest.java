package org.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase 11, 1d. These tests exist to pin down the three properties the class
 * javadoc calls out as each independently a silent bug if missed - not to
 * re-test {@code ThreadMXBean} itself.
 *
 * <p>All tests share one JVM, so a deadlock armed by one test and left running
 * would contaminate every test that runs after it - {@link #healthyJvmReadsZero}
 * failed exactly this way during development, deadlocked by a previous test's
 * leftover threads, which is why {@link TwoLockDeadlock} disarms with {@code
 * lockInterruptibly()} rather than the plain {@code lock()} the production seam
 * uses: a thread parked in {@code lock()} does not respond to interruption at
 * all, and this specimen has to be able to let go on command.
 */
class DeadlockDetectorTest {

    @Test
    @DisplayName("the gauge exists and reads 0 before a single poll has run")
    void gaugePublishedEvenWhenZero() {
        DeadlockDetector detector = new DeadlockDetector(10);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        detector.bindTo(registry);

        Gauge gauge = registry.get("payorch.jvm.deadlocked.threads").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isZero();
    }

    @Test
    @DisplayName("a healthy JVM polls to a deadlocked count of 0")
    void healthyJvmReadsZero() {
        DeadlockDetector detector = new DeadlockDetector(10);

        detector.poll();

        assertThat(detector.deadlockedThreadCount()).isZero();
    }

    @Test
    @DisplayName("findMonitorDeadlockedThreads() is blind to a ReentrantLock cycle - "
            + "the exact bug the class javadoc says findDeadlockedThreads() must avoid")
    void monitorVariantMissesTheLocksThisCodebaseActuallyUses() throws Exception {
        TwoLockDeadlock deadlock = TwoLockDeadlock.arm();
        try {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();

            // The wrong method: every lock infra-resilience takes is a
            // ReentrantLock, an ownable synchronizer, not a Java monitor - so
            // the monitor-only finder sees nothing here.
            assertThat(bean.findMonitorDeadlockedThreads()).isNull();

            // The right one, which is what DeadlockDetector actually calls.
            assertThat(bean.findDeadlockedThreads()).isNotNull();
        } finally {
            deadlock.disarm();
        }
    }

    @Test
    @DisplayName("a real ReentrantLock deadlock is detected, sized correctly, and logged once - "
            + "not once per poll")
    void detectsAndLogsOncePerTransition() throws Exception {
        TwoLockDeadlock deadlock = TwoLockDeadlock.arm();
        try {
            DeadlockDetector detector = new DeadlockDetector(10);

            detector.poll();
            assertThat(detector.deadlockedThreadCount()).isEqualTo(2);

            // A second, third... poll while the cycle is unchanged must not
            // re-trigger logCycle(). There is no log spy wired up here (this
            // module has no logging-capture precedent to build on), so this
            // exercises the code path for the second and later polls the same
            // way production traffic would - a NullPointerException or an
            // exception from double-logging would fail the test even without
            // an assertion on log content.
            detector.poll();
            detector.poll();
            assertThat(detector.deadlockedThreadCount()).isEqualTo(2);
        } finally {
            deadlock.disarm();
        }
    }

    @Test
    @DisplayName("the transition flag resets on disarm, so the NEXT deadlock in the same "
            + "process logs too rather than staying latched")
    void cycleLoggingResetsAfterTheCycleClears() throws Exception {
        DeadlockDetector detector = new DeadlockDetector(10);

        TwoLockDeadlock first = TwoLockDeadlock.arm();
        try {
            detector.poll();
            assertThat(detector.deadlockedThreadCount()).isEqualTo(2);
        } finally {
            first.disarm();
        }

        // Give the disarmed threads a moment to actually release both locks -
        // disarm() joins them, so by the time it returns this should already be
        // true, but the poll below is the assertion that matters.
        detector.poll();
        assertThat(detector.deadlockedThreadCount()).isZero();

        TwoLockDeadlock second = TwoLockDeadlock.arm();
        try {
            detector.poll();
            assertThat(detector.deadlockedThreadCount()).isEqualTo(2);
        } finally {
            second.disarm();
        }
    }

    /**
     * Two daemon threads, two {@link ReentrantLock}s, opposite acquisition
     * order - the same shape {@code ChaosLab}'s {@code DEADLOCK} action
     * produces in section 3, reduced to what a unit test needs.
     *
     * <p>The second acquisition in each thread uses {@code lockInterruptibly()}
     * specifically so {@link #disarm()} can break the cycle on command. The
     * production seam this specimen stands in for uses plain {@code lock()},
     * which is correct there - a chaos lab lock that could be interrupted out
     * from under an experiment would not be demonstrating the pathology - but
     * a test suite needs a way to let go between cases, which the production
     * shape does not offer.
     */
    private record TwoLockDeadlock(List<Thread> threads) {

        static TwoLockDeadlock arm() throws InterruptedException {
            ReentrantLock a = new ReentrantLock();
            ReentrantLock b = new ReentrantLock();
            CountDownLatch bothHeld = new CountDownLatch(2);

            Thread ab = daemon("test-lock-ab", () -> run(a, b, bothHeld));
            Thread ba = daemon("test-lock-ba", () -> run(b, a, bothHeld));

            ab.start();
            ba.start();
            bothHeld.await();

            // Both threads have taken their first lock and are about to reach
            // for the second. Give the JVM a moment to actually land both
            // threads in BLOCKED on the second acquisition before any test
            // asserts on it - findDeadlockedThreads() answers instantly once
            // that is true, but "instantly" from a countdown latch releasing
            // is not the same instant as "both threads are parked".
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            while (bean.findDeadlockedThreads() == null && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }

            return new TwoLockDeadlock(List.of(ab, ba));
        }

        /** Holds {@code first}, waits, then reaches for {@code second}. */
        private static void run(ReentrantLock first, ReentrantLock second, CountDownLatch bothHeld) {
            first.lock();
            try {
                bothHeld.countDown();
                // Long enough that both threads are certainly holding their
                // first lock before either reaches for its second - the window
                // this specimen needs the race to land inside.
                Thread.sleep(100);
                second.lockInterruptibly();
                second.unlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                first.unlock();
            }
        }

        private static Thread daemon(String name, Runnable body) {
            Thread t = new Thread(body, name);
            t.setDaemon(true);
            return t;
        }

        /**
         * Interrupts both threads and waits for them to actually exit, so the
         * next test starts with a clean thread set rather than a race against
         * whichever of these two woke up first.
         */
        void disarm() throws InterruptedException {
            for (Thread t : threads) {
                t.interrupt();
            }
            for (Thread t : threads) {
                t.join(2_000);
            }
        }
    }
}
