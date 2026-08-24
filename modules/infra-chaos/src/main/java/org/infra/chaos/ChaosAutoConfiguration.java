package org.infra.chaos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Contributes the seam registry and, where actuator is present, its endpoint.
 *
 * <p>Unconditional on purpose. The registry is an empty map until something
 * arms it, and a disarmed seam costs one hash lookup - so there is no
 * meaningful production cost to leaving it wired, and gating it behind a
 * property would mean the one time it is needed is the one time it is not
 * enabled.
 *
 * <p>The same applies to {@link BeanAssaultAspect} and to {@link ChaosLab}:
 * installed always, doing nothing until {@code /actuator/chaosbeans},
 * {@code /actuator/chaosseams} or {@code /actuator/chaoslab} says otherwise.
 * All three control surfaces are exposed only if a service lists them in
 * {@code management.endpoints.web.exposure.include}, so nothing here becomes
 * reachable by accident.
 */
@AutoConfiguration
public class ChaosAutoConfiguration {

    /**
     * Phase 11, 2. Unconditional, like {@link ChaosSeams} itself - the two
     * {@link java.util.concurrent.locks.ReentrantLock}s cost nothing until
     * something arms {@link ChaosSeam.Action#DEADLOCK}, and gating this bean
     * behind a property would mean {@link ChaosSeams} has nowhere to route the
     * four lab actions in a service that later decides to use one.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChaosLab chaosLab() {
        return new ChaosLab();
    }

    @Bean
    @ConditionalOnMissingBean
    public ChaosSeams chaosSeams(ChaosLab lab) {
        return new ChaosSeams(lab);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(Endpoint.class)
    public ChaosSeamsEndpoint chaosSeamsEndpoint(ChaosSeams seams) {
        return new ChaosSeamsEndpoint(seams);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(Endpoint.class)
    public ChaosLabEndpoint chaosLabEndpoint(ChaosLab lab) {
        return new ChaosLabEndpoint(lab);
    }

    @Bean
    @ConditionalOnMissingBean
    public BeanAssaultAspect beanAssaultAspect(
            @Value("${infra.chaos.target-package:}") String targetPackagePrefix) {
        return new BeanAssaultAspect(targetPackagePrefix);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(Endpoint.class)
    public BeanAssaultEndpoint beanAssaultEndpoint(BeanAssaultAspect aspect) {
        return new BeanAssaultEndpoint(aspect);
    }
}
