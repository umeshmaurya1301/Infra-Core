package org.infra.chaos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ChaosSeamsTest {

    private final ChaosSeams seams = new ChaosSeams();

    /**
     * The property that makes it acceptable to leave seam calls in production
     * code: reaching an unarmed seam does nothing and costs nothing.
     */
    @Test
    void anUnarmedSeamIsANoOp() {
        assertThatNoException().isThrownBy(() -> seams.reach("payment-row-lock"));
        assertThat(seams.armed()).isEmpty();
    }

    @Test
    void anArmedFailSeamThrowsAtThatPoint() {
        seams.arm("ledger-consumer", ChaosSeam.fail());

        assertThatThrownBy(() -> seams.reach("ledger-consumer"))
                .isInstanceOf(ChaosSeams.ChaosInjectedException.class)
                .hasMessageContaining("ledger-consumer");
    }

    /**
     * Targeting is the whole point of this seam. Chaos Monkey can assault "all
     * beans in a package"; it cannot fail one named consumer and leave its
     * neighbour alone, and phase 6 needs exactly that.
     */
    @Test
    void armingOneSeamLeavesTheOthersAlone() {
        seams.arm("ledger-consumer", ChaosSeam.fail());

        assertThatThrownBy(() -> seams.reach("ledger-consumer"))
                .isInstanceOf(ChaosSeams.ChaosInjectedException.class);
        assertThatNoException().isThrownBy(() -> seams.reach("webhook-consumer"));
    }

    @Test
    void anArmedPauseSeamSleepsInPlace() {
        seams.arm("payment-row-lock", ChaosSeam.pause(120));

        long startedAt = System.nanoTime();
        seams.reach("payment-row-lock");
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(100);
    }

    @Test
    void disarmingRestoresTheNoOp() {
        seams.arm("payment-row-lock", ChaosSeam.fail());
        seams.disarm("payment-row-lock");

        assertThatNoException().isThrownBy(() -> seams.reach("payment-row-lock"));
    }

    /**
     * Teardown has to be one call. A seam left armed between runs is a second,
     * invisible fault, and it would be attributed to whatever the next
     * experiment was actually testing.
     */
    @Test
    void disarmAllClearsEverything() {
        seams.arm("payment-row-lock", ChaosSeam.pause(50));
        seams.arm("ledger-consumer", ChaosSeam.fail());

        seams.disarmAll();

        assertThat(seams.armed()).isEmpty();
        assertThatNoException().isThrownBy(() -> seams.reach("payment-row-lock"));
        assertThatNoException().isThrownBy(() -> seams.reach("ledger-consumer"));
    }

    @Test
    void theEndpointArmsAndDisarms() {
        ChaosSeamsEndpoint endpoint = new ChaosSeamsEndpoint(seams);

        endpoint.arm("payment-row-lock", ChaosSeam.Action.PAUSE, 250L, null);
        assertThat(seams.armed("payment-row-lock"))
                .get()
                .isEqualTo(new ChaosSeam(ChaosSeam.Action.PAUSE, 250, ChaosSeam.ALWAYS));

        endpoint.arm("ledger-consumer", ChaosSeam.Action.FAIL, null, null);
        assertThat(seams.armed("ledger-consumer")).get().isEqualTo(ChaosSeam.fail());

        assertThat(endpoint.disarmAll()).isEmpty();
    }

    /**
     * Omitting the probability must behave exactly as it did before it existed.
     * The two phase-2 seams are armed without one and depend on firing every
     * time.
     */
    @Test
    void aSeamArmedWithoutAProbabilityFiresEveryTime() {
        seams.arm("ledger-consumer", ChaosSeam.fail());

        for (int i = 0; i < 50; i++) {
            assertThatThrownBy(() -> seams.reach("ledger-consumer"))
                    .isInstanceOf(ChaosSeams.ChaosInjectedException.class);
        }
        assertThat(seams.injections()).containsEntry("ledger-consumer", 50L);
    }

    /**
     * Phase 6f arms this at 0.3. The assertion is deliberately loose - it is
     * checking that the roll happens at all and that it is per-reach, not that a
     * random process hit a number.
     */
    @Test
    void aProbabilisticSeamFiresSomeOfTheTimeAndNotAll() {
        seams.arm("ledger-consumer", ChaosSeam.fail(0.3));

        int failures = 0;
        for (int i = 0; i < 2000; i++) {
            try {
                seams.reach("ledger-consumer");
            } catch (ChaosSeams.ChaosInjectedException expected) {
                failures++;
            }
        }

        // p(all 2000 miss) and p(all 2000 hit) are both far below any number
        // worth calling flaky. Anything tighter would be asserting on a seed.
        assertThat(failures).isPositive().isLessThan(2000);
        assertThat(seams.injections()).containsEntry("ledger-consumer", (long) failures);
    }

    @Test
    void aZeroProbabilitySeamNeverFires() {
        seams.arm("ledger-consumer", ChaosSeam.fail(0.0));

        for (int i = 0; i < 200; i++) {
            assertThatNoException().isThrownBy(() -> seams.reach("ledger-consumer"));
        }
        assertThat(seams.injections()).doesNotContainKey("ledger-consumer");
    }

    @Test
    void anImpossibleProbabilityIsRejectedAtArmingTime() {
        // Rather than clamped. An operator who types 30 meaning "30 percent"
        // has made a mistake that a clamp would turn into a 100% failure rate
        // in the middle of a run, and they would read the result as a finding.
        assertThatThrownBy(() -> ChaosSeam.fail(30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0.0 and 1.0");
    }

    /**
     * The counts outlive disarming, because the experiment disarms the seam
     * before it replays and asserts - and still has to report what it injected.
     */
    @Test
    void injectionCountsSurviveDisarming() {
        seams.arm("ledger-consumer", ChaosSeam.fail());
        assertThatThrownBy(() -> seams.reach("ledger-consumer"))
                .isInstanceOf(ChaosSeams.ChaosInjectedException.class);

        seams.disarmAll();

        assertThat(seams.injections()).containsEntry("ledger-consumer", 1L);
    }

    /**
     * Phase 11, 5 arm B. {@code NESTED_SUBMIT} has no lab-side behaviour - the
     * fault lives at whichever call site checks {@code armed(name)} - so
     * {@code reach()} on a {@code ChaosSeams} with NO lab wired must still
     * succeed and still count. This is the one state action that does not
     * throw {@link IllegalStateException} without a lab; contrast with
     * {@link #aLabActionWithNoLabWiredFailsLoudly()} below.
     */
    @Test
    void nestedSubmitNeedsNoLabAndOnlyRecordsThatItFired() {
        seams.arm("psp-nested-submit", new ChaosSeam(ChaosSeam.Action.NESTED_SUBMIT, 0, ChaosSeam.ALWAYS));

        assertThatNoException().isThrownBy(() -> seams.reach("psp-nested-submit"));
        assertThat(seams.injections()).containsEntry("psp-nested-submit", 1L);
    }

    /**
     * The other half: a caller checking {@code armed(name)} directly - the
     * pattern {@code MockPspAdapter} uses, since the actual nested {@code
     * bulkhead.call} has to be written where the bulkhead is - must see the
     * seam whether or not anyone has called {@code reach()} on it yet.
     */
    @Test
    void armedIsVisibleToACallSiteThatChecksItDirectly() {
        assertThat(seams.armed("psp-nested-submit")).isEmpty();

        seams.arm("psp-nested-submit", new ChaosSeam(ChaosSeam.Action.NESTED_SUBMIT, 0, ChaosSeam.ALWAYS));

        assertThat(seams.armed("psp-nested-submit")).isPresent();
        seams.disarm("psp-nested-submit");
        assertThat(seams.armed("psp-nested-submit")).isEmpty();
    }

    /**
     * Phase 11, 2. A {@code ChaosSeams} built with the no-arg constructor - the
     * one every pre-phase-11 caller uses - has no lab to route a state action
     * to, and that must fail loudly at the point of use rather than silently
     * doing nothing.
     */
    @Test
    void aLabActionWithNoLabWiredFailsLoudly() {
        seams.arm("lab-lock-ab", new ChaosSeam(ChaosSeam.Action.DEADLOCK, 0, ChaosSeam.ALWAYS));

        assertThatThrownBy(() -> seams.reach("lab-lock-ab"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no ChaosLab");
    }

    /**
     * End to end through {@code reach()}, not just against {@link ChaosLab}
     * directly - this is the path {@code PaymentService}'s two call sites
     * actually go through in phase 11, 3. Two seams, opposite lock orders, one
     * thread each: both park, and {@code disarmAll()} plus {@link
     * ChaosLab#release()} recovers both - the same two-step recovery the phase
     * plan requires of the real experiment.
     */
    @Test
    void reachDispatchesDeadlockToTheWiredLabAndRecoversAfterReleaseAndDisarm() throws InterruptedException {
        ChaosLab lab = new ChaosLab();
        ChaosSeams labSeams = new ChaosSeams(lab);
        labSeams.arm("lab-lock-ab", new ChaosSeam(ChaosSeam.Action.DEADLOCK, 0, ChaosSeam.ALWAYS));
        labSeams.arm("lab-lock-ba", new ChaosSeam(ChaosSeam.Action.DEADLOCK, 0, ChaosSeam.ALWAYS));

        Thread ab = new Thread(() -> labSeams.reach("lab-lock-ab"));
        Thread ba = new Thread(() -> labSeams.reach("lab-lock-ba"));
        ab.start();
        ba.start();

        ab.join(800);
        ba.join(800);
        assertThat(ab.isAlive()).isTrue();
        assertThat(ba.isAlive()).isTrue();
        assertThat(labSeams.injections())
                .containsEntry("lab-lock-ab", 1L)
                .containsEntry("lab-lock-ba", 1L);

        labSeams.disarmAll();
        // disarmAll alone does not recover a DEADLOCK - the whole reason its
        // row in the phase plan names TWO steps to end it. Confirmed here so a
        // future change that "simplifies" this away breaks a test instead of
        // shipping a lab that quietly cannot be recovered.
        assertThat(ab.isAlive()).isTrue();
        assertThat(ba.isAlive()).isTrue();

        lab.release();
        ab.join(2000);
        ba.join(2000);
        assertThat(ab.isAlive()).isFalse();
        assertThat(ba.isAlive()).isFalse();
    }
}
