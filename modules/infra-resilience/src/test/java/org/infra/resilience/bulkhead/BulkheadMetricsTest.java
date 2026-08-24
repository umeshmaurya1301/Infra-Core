package org.infra.resilience.bulkhead;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11, 5. Pins down the two gauges added to close the gap the phase plan's
 * hypothesis assumed was already closed: {@code executor_active_threads} and
 * {@code executor_queued_tasks} do not exist anywhere in this project, and
 * {@code ThreadPoolBulkhead} never registered its executors with Micrometer's
 * {@code ExecutorServiceMetrics} - see {@code ThreadPoolBulkhead#queueDepth}'s
 * javadoc for how that was confirmed against a live service.
 */
class BulkheadMetricsTest {

    /**
     * The property {@code DeadlockDetector}'s javadoc argues for and this
     * section repeats: a gauge that only appears during an incident cannot be
     * alerted on. Both new series must exist, reading zero, before anything has
     * ever saturated the pool.
     */
    @Test
    void queueDepthAndActiveCountArePublishedEvenWhenZero() {
        ThreadPoolBulkhead pooled = new ThreadPoolBulkhead(2, 2, 500, 10);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new BulkheadMetrics(pooled).bindTo(registry);

        Gauge queueDepth = registry.get("payorch.bulkhead.queue.depth").gauge();
        Gauge activeCount = registry.get("payorch.bulkhead.active.count").gauge();
        assertThat(queueDepth.value()).isZero();
        assertThat(activeCount.value()).isZero();

        pooled.shutdown();
    }

    /**
     * The gauges move with the pool they are bound to - registered against the
     * live {@link ThreadPoolBulkhead}, not a snapshot taken at bind time, the
     * same way {@code payorch.bulkhead.platform.threads} already does.
     */
    @Test
    void theGaugesTrackTheLivePoolAfterSaturation() throws Exception {
        ThreadPoolBulkhead pooled = new ThreadPoolBulkhead(1, 2, 2_000, 10);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new BulkheadMetrics(pooled).bindTo(registry);

        Gauge queueDepth = registry.get("payorch.bulkhead.queue.depth").gauge();
        Gauge activeCount = registry.get("payorch.bulkhead.active.count").gauge();

        CountDownLatch hold = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(1);
        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            callers.submit(() -> pooled.call("mockpsp", () -> {
                running.countDown();
                hold.await();
                return "held";
            }));
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

            callers.submit(() -> pooled.call("mockpsp", () -> "queued"));
            Thread.sleep(300);

            assertThat(activeCount.value()).isEqualTo(1.0);
            assertThat(queueDepth.value()).isEqualTo(1.0);

            hold.countDown();
        }
        pooled.shutdown();
    }

    /**
     * A semaphore bulkhead has no queue and no pool workers - registering
     * either gauge for it would always read zero and would mean "not this
     * implementation", not "healthy". {@code BulkheadMetrics} gates both behind
     * {@code instanceof ThreadPoolBulkhead}, the same guard
     * {@code payorch.bulkhead.platform.threads} already uses.
     */
    @Test
    void theSemaphoreImplementationPublishesNeitherGauge() {
        SemaphoreBulkhead semaphore = new SemaphoreBulkhead(2, 500, 10);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new BulkheadMetrics(semaphore).bindTo(registry);

        assertThat(registry.find("payorch.bulkhead.queue.depth").gauge()).isNull();
        assertThat(registry.find("payorch.bulkhead.active.count").gauge()).isNull();
    }
}
