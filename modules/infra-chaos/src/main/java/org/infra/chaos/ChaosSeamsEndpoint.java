package org.infra.chaos;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

/**
 * Runtime control of the seams, over actuator.
 *
 * <pre>{@code
 * curl localhost:8081/actuator/chaosseams
 * curl -XPOST localhost:8081/actuator/chaosseams/payment-row-lock \
 *      -H 'content-type: application/json' -d '{"action":"PAUSE","pauseMs":5000}'
 * curl -XDELETE localhost:8081/actuator/chaosseams
 * }</pre>
 *
 * <p>An actuator endpoint rather than a {@code @RestController} for two
 * reasons. It sits on the management surface alongside Chaos Monkey's own
 * {@code /actuator/chaosmonkey}, so there is one place to look; and it is not
 * exposed unless a service explicitly lists it in
 * {@code management.endpoints.web.exposure.include}, which makes shipping the
 * seam machinery to somewhere it should not be reachable an explicit act rather
 * than an accident.
 */
@Endpoint(id = "chaosseams")
public class ChaosSeamsEndpoint {

    private final ChaosSeams seams;

    public ChaosSeamsEndpoint(ChaosSeams seams) {
        this.seams = seams;
    }

    @ReadOperation
    public Map<String, Object> armed() {
        // Both halves in one response, because the question during a run is
        // never just "what is armed" - it is "what is armed and has it actually
        // fired". A seam that is armed and has fired zero times is the shape of
        // an experiment about to report a false pass.
        return Map.of("armed", seams.armed(), "injections", seams.injections());
    }

    /**
     * @param action  {@code PAUSE}, {@code FAIL}, or one of phase 11's state
     *        actions - {@code DEADLOCK}, {@code LIVELOCK}, {@code STARVE},
     *        {@code LEAK}, or {@code NESTED_SUBMIT}. {@code DEADLOCK} must be
     *        armed as {@code lab-lock-ab} or {@code lab-lock-ba} - see {@code
     *        ChaosLab#LOCK_ORDER_AB}. {@code NESTED_SUBMIT} has no lab-side
     *        behaviour of its own - see {@code ChaosSeam.Action#NESTED_SUBMIT}
     *        - so arming it only ever records that the seam fired; the fault
     *        happens at whichever call site checks {@code armed(name)}.
     * @param pauseMs meaningful only for {@code PAUSE}, where a missing value
     *        defaults to 1000ms - every other action ignores it, and it
     *        defaults to 0 rather than being required, because
     *        {@code @Nullable} parameters are still accepted by actuator's
     *        binder as absent JSON keys, and a state fault has no pause to name.
     *        {@code @Nullable} is not decoration: actuator treats every
     *        operation parameter as mandatory unless it is marked nullable, so
     *        without it, arming anything but {@code PAUSE} is rejected with
     *        "Missing parameters: pauseMs" for a value that has no meaning in
     *        that case.
     * @param probability 0.0 to 1.0, defaulting to 1.0. Omitting it keeps the
     *        old behaviour exactly - a seam armed without one fires every time.
     *        Applies to every action, including the four state faults: a
     *        probabilistic livelock or leak is a legitimate thing to want, even
     *        though phase 11's own experiments only ever arm these at 1.0.
     */
    @WriteOperation
    public Map<String, ChaosSeam> arm(@Selector String name,
                                      ChaosSeam.Action action,
                                      @Nullable Long pauseMs,
                                      @Nullable Double probability) {
        double p = probability == null ? ChaosSeam.ALWAYS : probability;
        long resolvedPauseMs = action == ChaosSeam.Action.PAUSE
                ? (pauseMs == null ? 1000 : pauseMs)
                : (pauseMs == null ? 0 : pauseMs);
        seams.arm(name, new ChaosSeam(action, resolvedPauseMs, p));
        return seams.armed();
    }

    @DeleteOperation
    public Map<String, ChaosSeam> disarmAll() {
        seams.disarmAll();
        return seams.armed();
    }
}
