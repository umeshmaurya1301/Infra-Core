package org.infra.chaos;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects latency and exceptions into this service's own beans.
 *
 * <p>Replaces Spring Boot Chaos Monkey, which is inert on Boot 4 - see the
 * comment at the top of this module's {@code build.gradle.kts} for the
 * measurement that established that.
 *
 * <p><strong>Targeting is restricted to {@code com.payorch}.</strong> A generic
 * "assault every bean" mode is easy to write and produces failures that say
 * nothing: a Chaos Monkey run that injects an exception into a framework bean
 * tells you Spring propagates exceptions, which was not in doubt. Restricting
 * the pointcut to our own stereotypes means every observed failure is one this
 * system could actually have.
 *
 * <p><strong>Off by default and free when off.</strong> The advice runs on every
 * targeted call, so the disabled path is one volatile read and one comparison.
 * That is cheap enough to leave installed, which matters because the alternative
 * - enabling it by restarting with a flag - loses the ability to turn chaos on
 * during a run without resetting the connection pools being measured.
 */
@Aspect
public class BeanAssaultAspect {

    private static final Logger log = LoggerFactory.getLogger(BeanAssaultAspect.class);

    private final AtomicReference<BeanAssault> assault = new AtomicReference<>(BeanAssault.off());
    private final LongAdder latencyInjections = new LongAdder();
    private final LongAdder exceptionInjections = new LongAdder();
    private final String targetPackagePrefix;

    public BeanAssaultAspect() {
        this("");
    }

    /**
     * @param targetPackagePrefix restricts injection to beans whose class is in
     *      this package or a sub-package of it. Blank means no restriction
     *      beyond the stereotype pointcut below - the right default for a
     *      shared library with no fixed base package of its own. A consumer
     *      that wants the original single-application defence in depth back
     *      (see {@link #assaultTarget()}) supplies its own root package here.
     */
    public BeanAssaultAspect(String targetPackagePrefix) {
        this.targetPackagePrefix = targetPackagePrefix == null ? "" : targetPackagePrefix;
    }

    /**
     * Service and component beans, and Spring Data's repository proxies.
     *
     * <p>Controllers are deliberately excluded. An assault at the controller is
     * indistinguishable from the downstream fault the simulator already injects,
     * and it would be a second way to do the same thing - which is exactly how a
     * chaos harness ends up with two overlapping layers and no attribution.
     *
     * <p>The package restriction that used to live in this expression
     * ({@code within(com.payorch..*)}) is applied at runtime instead, via
     * {@link #targetPackagePrefix} - a shared library has no fixed base package
     * of its own to hardcode here, unlike the single application this was
     * originally written for.
     */
    @Pointcut("@within(org.springframework.stereotype.Service) || "
            + "@within(org.springframework.stereotype.Component) || "
            + "@within(org.springframework.stereotype.Repository)")
    void assaultTarget() {
    }

    @Around("assaultTarget()")
    public Object assault(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!targetPackagePrefix.isEmpty()
                && !joinPoint.getTarget().getClass().getPackageName().startsWith(targetPackagePrefix)) {
            return joinPoint.proceed();
        }

        BeanAssault active = assault.get();
        if (!active.isActive()) {
            return joinPoint.proceed();
        }

        if (roll(active.exceptionRate())) {
            exceptionInjections.increment();
            log.warn("bean assault: throwing at {}", joinPoint.getSignature().toShortString());
            throw new BeanAssaultException(joinPoint.getSignature().toShortString());
        }

        if (active.latencyMs() > 0 && roll(active.latencyRate())) {
            latencyInjections.increment();
            sleep(active.latencyMs());
        }

        return joinPoint.proceed();
    }

    public BeanAssault current() {
        return assault.get();
    }

    public BeanAssault apply(BeanAssault updated) {
        BeanAssault previous = assault.getAndSet(updated);
        log.warn("bean assault reconfigured: {}", updated);
        return previous;
    }

    public BeanAssault reset() {
        return apply(BeanAssault.off());
    }

    /**
     * How many faults were actually injected.
     *
     * <p>Reported because a probabilistic assault that happened not to fire and
     * an assault that was never enabled look identical in every other
     * measurement, and mistaking one for the other invalidates a run.
     */
    public long injectedLatencies() {
        return latencyInjections.sum();
    }

    public long injectedExceptions() {
        return exceptionInjections.sum();
    }

    private static boolean roll(double probability) {
        return probability > 0 && ThreadLocalRandom.current().nextDouble() < probability;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Thrown by an active exception assault. */
    public static class BeanAssaultException extends RuntimeException {

        public BeanAssaultException(String target) {
            super("chaos injected an exception at " + target);
        }
    }
}
