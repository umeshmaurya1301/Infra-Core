package org.infra.core.service;

import lombok.extern.slf4j.Slf4j;
import org.infra.commons.constants.ApplicationConstants;
import org.springframework.stereotype.Service;

/**
 * Base service providing common functionality for all core services.
 */
@Slf4j
@Service
public abstract class BaseService {
    
    /**
     * Logs service initialization.
     */
    protected void logServiceInitialization() {
        log.info("Initializing service: {}", this.getClass().getSimpleName());
    }
    
    /**
     * Validates that a required parameter is not null.
     */
    protected void validateRequired(Object parameter, String parameterName) {
        if (parameter == null) {
            throw new IllegalArgumentException(parameterName + " cannot be null");
        }
    }
    
    /**
     * Gets the default timezone for the application.
     */
    protected String getDefaultTimezone() {
        return ApplicationConstants.DEFAULT_TIMEZONE;
    }
}
