package org.infra.resilience.bulkhead;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Publishes admission-control outcomes, tagged with which implementation is in
 * use.
 *
 * <p>The {@code kind} tag is what makes 3d's comparison possible from the
 * captured metrics alone: two runs of the same experiment differ only in that
 * label, so a rejection rate can be attributed to the implementation rather than
 * to whatever else was happening that afternoon.
 *
 * <p>{@code rejected} is the number that matters and the one that looks like bad
 * news. It is not: a rejection is work shed deliberately and promptly, which is
 * the entire point. The failure mode this component prevents does not show up as
 * a rejection - it shows up as a heap dump.
 */
public class BulkheadMetrics implements MeterBinder {

    private final Bulkhead bulkhead;

    public BulkheadMetrics(Bulkhead bulkhead) {
        this.bulkhead = bulkhead;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Tags tags = Tags.of("kind", bulkhead.kind());

        // FunctionCounter, not Gauge - see RetryMetrics for what a cumulative
        // total typed as a gauge does to a rate query. Saturation is a question
        // about a WINDOW ("how many did we shed in the last minute"), and that
        // question is unanswerable unless the instrument says it is a counter.
        FunctionCounter.builder("payorch.bulkhead.permitted", bulkhead, Bulkhead::permitted)
                .description("Calls admitted")
                .tags(tags)
                .register(registry);

        FunctionCounter.builder("payorch.bulkhead.rejected", bulkhead, Bulkhead::rejected)
                .description("Calls shed because the concurrency limit was reached - work deliberately not done")
                .tags(tags)
                .register(registry);

        // Per provider, because the whole point of keying by provider is that
        // one saturating must not starve another - and a single aggregate
        // number would hide exactly that.
        //
        // Genuinely a gauge: permits are taken and returned.
        Gauge.builder("payorch.bulkhead.available.total", bulkhead,
                        b -> b.available().values().stream().mapToInt(Integer::intValue).sum())
                .description("Permits currently free across all providers")
                .tags(tags)
                .register(registry);

        if (bulkhead instanceof ThreadPoolBulkhead pooled) {
            // The cost of this implementation, in the unit that matters. A
            // semaphore bulkhead has no equivalent series because the number
            // would always be zero.
            Gauge.builder("payorch.bulkhead.platform.threads", pooled,
                            ThreadPoolBulkhead::platformThreads)
                    .description("Platform threads allocated by the thread-pool bulkhead")
                    .tags(tags)
                    .register(registry);

            // Phase 11, 5. The two metrics that closed a real instrumentation
            // gap rather than the two the phase plan predicted
            // (executor_active_threads / executor_queued_tasks - neither
            // exists anywhere in this project; see ThreadPoolBulkhead#queueDepth
            // for how that was confirmed). Registered here, under the same
            // `instanceof ThreadPoolBulkhead` guard as platform.threads above,
            // for the same reason: a semaphore bulkhead has no queue and no
            // pool workers, so the series would always read zero and would
            // not mean "healthy", it would mean "not this implementation".
            //
            // Unconditional once a thread-pool bulkhead exists, not gated
            // behind a chaos flag or an experiment - see queueDepth()'s own
            // javadoc for why a gauge born only during an incident is useless
            // to an alert rule that needs a baseline to compare against.
            Gauge.builder("payorch.bulkhead.queue.depth", pooled, ThreadPoolBulkhead::queueDepth)
                    .description("Tasks queued behind the thread-pool bulkhead's own workers, summed "
                            + "across every provider - climbing while active.count stays pinned at the "
                            + "concurrency limit is nested-submission starvation's own signature")
                    .tags(tags)
                    .register(registry);

            Gauge.builder("payorch.bulkhead.active.count", pooled, ThreadPoolBulkhead::activeCount)
                    .description("Pool workers currently executing a task, summed across every provider")
                    .tags(tags)
                    .register(registry);
        }
    }
}
