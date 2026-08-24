package org.infra.chaos;

/**
 * What an armed seam does when execution reaches it.
 *
 * @param action      pause or fail
 * @param pauseMs     how long to sleep, for {@link Action#PAUSE}
 * @param probability chance, 0.0 to 1.0, that reaching the seam does anything
 */
public record ChaosSeam(Action action, long pauseMs, double probability) {

    /**
     * A seam armed without a stated probability fires every time. That is the
     * right default for the two faults this class was built for - a deadlock is
     * demonstrated by making it happen, not by making it likely.
     */
    public static final double ALWAYS = 1.0;

    public ChaosSeam {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException(
                    "probability must be between 0.0 and 1.0, got " + probability);
        }
    }

    public enum Action {

        /**
         * Sleep, in place, on the calling thread.
         *
         * <p>The whole value is <em>where</em> the sleep happens. A generic
         * latency injector delays a call; this delays the thread at a chosen
         * line - specifically, one inside a transaction that is already holding
         * a {@code SELECT ... FOR UPDATE} lock. That turns a deadlock from
         * something that happens occasionally under load into something that
         * happens on demand, which is the difference between a phase-7
         * experiment and a phase-7 anecdote.
         */
        PAUSE,

        /**
         * Throw at this exact point.
         *
         * <p>For the phase-6 case Chaos Monkey cannot express: failing one named
         * {@code @KafkaListener} and no other bean, after the message has been
         * polled but before the offset is committed. That is the window where
         * at-least-once delivery either works or quietly loses a message, and
         * assaulting "all listener beans" would tell you nothing about which
         * one.
         */
        FAIL,

        /**
         * Phase 11, 3. Takes both of {@link ChaosLab}'s locks, in the order
         * named by the arming seam - {@link ChaosLab#LOCK_ORDER_AB} takes A
         * then B, {@link ChaosLab#LOCK_ORDER_BA} takes B then A - and never
         * lets go on its own.
         *
         * <p>{@code PAUSE} and {@code FAIL} are point faults: they do one thing
         * on the calling thread and return control to it. This is the first
         * <em>state</em> fault in the enum - it starts, it persists, and the
         * calling thread does not come back until something outside it acts.
         * Arm one seam of each order and drive one request at each, and the two
         * threads take opposite lock orders at the same moment, which is the
         * entire mechanism a Java deadlock needs. See {@link ChaosLab}'s own
         * javadoc for why this lives in a labelled lab rather than as a second
         * lock order manufactured somewhere in real code.
         */
        DEADLOCK,

        /**
         * Phase 11, 4. {@code tryLock(A)}, then {@code tryLock(B)}; on failing
         * the second, release the first and retry immediately, with no backoff,
         * until the seam is disarmed.
         *
         * <p>This is the state fault that a deadlock detector cannot see.
         * {@link DEADLOCK} parks both threads and {@code findDeadlockedThreads()}
         * finds the cycle; this one never blocks at all - both threads stay
         * {@code RUNNABLE}, spinning, perfectly polite, and neither ever makes
         * it past the second lock. The signature that tells the two apart from
         * outside the process is the CPU, not the thread state.
         */
        LIVELOCK,

        /**
         * Phase 11, 5. Occupies the lab's own bounded pool with a task that
         * submits a second task to the <em>same</em> pool and blocks on its
         * result, until the seam is disarmed.
         *
         * <p>The classic nested-submission starvation: fill every worker thread
         * with parents waiting on children, and no child can ever be scheduled,
         * because the resource each parent is waiting for is behind those same
         * threads. Phase 5's real experiment points the identical mechanism at
         * {@code infra-resilience}'s {@code ThreadPoolBulkhead}; what is armable
         * here is the lab's own pool, proving the mechanism independently of
         * which pool it is later aimed at.
         */
        STARVE,

        /**
         * Phase 11, 5 arm B. The same nested-submission shape as {@link #STARVE},
         * pointed at a real, caller-owned pool instead of the lab's.
         *
         * <p>{@code STARVE} can be fully implemented inside {@link ChaosLab}
         * because {@link ChaosLab} owns the pool it starves - {@link
         * #STARVE}'s own javadoc says as much: it "proves the mechanism
         * independently of which pool it is later aimed at". This action is
         * that aiming. {@code infra-chaos} does not and should not depend on
         * {@code infra-resilience} - a chaos module reaching into a specific
         * resilience component would make every future pool a special case
         * here - so {@link ChaosSeams#reach} does nothing for this action
         * beyond recording that the seam fired. The actual nesting - a task
         * already running on the target pool making a second call into that
         * <em>same</em> pool, with the <em>same</em> key, and blocking on the
         * result - is written at the one real call site that needs it,
         * exactly the way every other seam in this codebase marks a specific
         * line and leaves the fault itself to whoever owns that line. See
         * {@code MockPspAdapter} in {@code psp-connector} for the only caller
         * today, and {@code docs/experiments/32-nested-starvation.md} for why
         * a lab-only proof was not enough for this section.
         *
         * <p>Armed and reached exactly like every other seam - the same
         * {@code /actuator/chaosseams} endpoint, the same {@code injections()}
         * accounting - so a driver script cannot tell, from the control
         * surface alone, that this one has no generic implementation behind
         * it. That asymmetry is deliberate: the fault lives where the
         * resource does.
         */
        NESTED_SUBMIT,

        /**
         * Phase 11, 7. Retains a fixed chunk per reach in a list {@link ChaosLab}
         * holds, until {@code POST /actuator/chaoslab/drop} clears it.
         *
         * <p>The specimen leak this phase writes up is
         * {@code mock-psp-simulator}'s already-unbounded {@code AuthorizationLedger}
         * - real, and better evidence than anything built for the purpose. This
         * seam exists only for the <em>rate</em>: pushing enough retention
         * through the lab to reach the wall inside a drill instead of a week of
         * organic traffic.
         */
        LEAK
    }

    public static ChaosSeam pause(long millis) {
        return new ChaosSeam(Action.PAUSE, millis, ALWAYS);
    }

    public static ChaosSeam fail() {
        return new ChaosSeam(Action.FAIL, 0, ALWAYS);
    }

    /**
     * Fails a fraction of the time.
     *
     * <p>Added for phase 6f, and the reason is worth stating because "make it
     * fail 30% of the time" sounds like a detail and is not. A seam that fails
     * <em>every</em> message sends every message to the DLQ, which proves the
     * DLQ works and proves nothing about the retry tiers - the interesting
     * assertion is that some messages recover at tier 1, some at tier 2, and
     * only the persistently unlucky ones exhaust the ladder. That distribution
     * only exists if each retry gets an independent roll.
     *
     * <p>It also means a run is not reproducible message-for-message, which is a
     * real cost. The experiment is written to assert on invariants that hold for
     * any seed - the books balance, no event posts twice - rather than on
     * counts, which would be a flaky test wearing an experiment's clothes.
     */
    public static ChaosSeam fail(double probability) {
        return new ChaosSeam(Action.FAIL, 0, probability);
    }

    public static ChaosSeam pause(long millis, double probability) {
        return new ChaosSeam(Action.PAUSE, millis, probability);
    }
}
