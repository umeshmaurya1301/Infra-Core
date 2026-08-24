package org.infra.chaos;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

/**
 * Runtime control of {@link ChaosLab}'s state - the half of a lab action that
 * arming alone cannot end.
 *
 * <pre>{@code
 * curl localhost:8081/actuator/chaoslab
 * curl -XPOST localhost:8081/actuator/chaoslab/release
 * curl -XPOST localhost:8081/actuator/chaoslab/drop
 * }</pre>
 *
 * <p>Separate from {@link ChaosSeamsEndpoint} on purpose. {@code /actuator/
 * chaosseams} controls whether the fault fires again; this controls state the
 * fault already created and that disarming cannot undo - a parked thread from
 * {@link ChaosSeam.Action#DEADLOCK}, or memory {@link ChaosSeam.Action#LEAK}
 * already retained. See {@link ChaosLab#release()} and {@link ChaosLab#drop()}
 * for why each is its own explicit step rather than something {@code disarm}
 * does automatically.
 */
@Endpoint(id = "chaoslab")
public class ChaosLabEndpoint {

    private final ChaosLab lab;

    public ChaosLabEndpoint(ChaosLab lab) {
        this.lab = lab;
    }

    @ReadOperation
    public Map<String, Object> status() {
        return describe();
    }

    /**
     * @param action {@code release} interrupts every thread parked inside
     *        {@link ChaosLab#deadlock}; {@code drop} clears everything {@link
     *        ChaosLab#leak} has retained. Anything else is rejected rather than
     *        silently ignored - an experiment's teardown script depends on this
     *        call actually doing what it asked for.
     */
    @WriteOperation
    public Map<String, Object> execute(@Selector String action) {
        switch (action) {
            case "release" -> lab.release();
            case "drop" -> lab.drop();
            default -> throw new IllegalArgumentException(
                    "unknown chaoslab action '" + action + "' - expected 'release' or 'drop'");
        }
        return describe();
    }

    private Map<String, Object> describe() {
        // LinkedHashMap for the same reason BeanAssaultEndpoint uses one: a
        // stable field order means a diff between two captures shows what
        // changed, not what got reordered.
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("lockAHeld", lab.lockAHeld());
        view.put("lockBHeld", lab.lockBHeld());
        view.put("parkedOnDeadlock", lab.parkedOnDeadlockCount());
        view.put("retainedChunks", lab.retainedChunks());
        view.put("retainedBytes", lab.retainedBytes());
        // Two fields, not one "progress" - phase 11, 4's defect A. spins is
        // touched on every livelock loop iteration regardless of outcome
        // (proves the threads are running); completions is touched only when
        // an action actually gets somewhere (proves they are getting
        // anywhere). See ChaosLab's class javadoc for why folding both under
        // one name made a real livelock look like contention.
        view.put("spins", lab.spins());
        view.put("completions", lab.completions());
        return view;
    }
}
