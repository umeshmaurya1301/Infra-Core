package org.infra.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for phase 4 (and phase 11's addition), under
 * {@code payorch.observability}.
 *
 * @param rollingWindowSeconds how much recent history the per-provider
 *                             percentile covers. 60 s is a compromise with a
 *                             direction: short enough that phase 5 reacts to a
 *                             provider degrading within a minute, long enough
 *                             that a slow provider at low traffic still has
 *                             enough samples for a P99 to mean anything. At
 *                             10 rps a 60 s window holds 600 calls, so the 99th
 *                             is the 6th-slowest - defensible. At 10 s it would
 *                             be the slowest single call, which is a maximum
 *                             wearing a percentile's name.
 * @param deadlockDetector     phase 11, 1d. See {@link DeadlockDetector}.
 */
@ConfigurationProperties(prefix = "payorch.observability")
public record ObservabilityProperties(Integer rollingWindowSeconds, DeadlockDetectorSettings deadlockDetector) {

    public ObservabilityProperties {
        // Boxed and defaulted, for the reason 3a's properties record explains:
        // constructor binding gives an absent primitive its zero value, and a
        // zero-second window is a percentile over nothing.
        if (rollingWindowSeconds == null || rollingWindowSeconds <= 0) {
            rollingWindowSeconds = 60;
        }
        if (deadlockDetector == null) {
            deadlockDetector = new DeadlockDetectorSettings(null, null);
        }
    }

    /**
     * {@code payorch.observability.deadlock-detector.*}.
     *
     * @param enabled         off by default. {@code findDeadlockedThreads()}
     *                        forces a JVM safepoint on every poll, which is a
     *                        cost with no return in a service that has never
     *                        deadlocked - see {@link DeadlockDetector}'s class
     *                        javadoc. Compose turns it on for all six services.
     * @param intervalSeconds how often the check runs. 10 s matches the phase
     *                        plan: short enough that a deadlock is caught
     *                        within one tick of the alert rule's own 1 m
     *                        {@code for}, long enough that the safepoint cost
     *                        stays negligible against a service that is not
     *                        deadlocked, which is the overwhelming majority of
     *                        the time this runs.
     */
    public record DeadlockDetectorSettings(Boolean enabled, Integer intervalSeconds) {

        public DeadlockDetectorSettings {
            if (enabled == null) {
                enabled = Boolean.FALSE;
            }
            if (intervalSeconds == null || intervalSeconds <= 0) {
                intervalSeconds = 10;
            }
        }
    }
}
