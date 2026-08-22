package org.infra.audit.interceptor;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.UUID;

/**
 * Interceptor for centralized API request/response logging.
 * Logs all incoming requests and outgoing responses with correlation IDs.
 */
@Component
public class ApiLoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ApiLoggingInterceptor.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String START_TIME_ATTRIBUTE = "startTime";

    private final ObjectMapper objectMapper;

    public ApiLoggingInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);

        // Generate or extract correlation ID
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Set correlation ID in MDC and response header
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        // Log request
        logRequest(request, correlationId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        try {
            long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
            long duration = System.currentTimeMillis() - startTime;

            // Log response
            logResponse(request, response, duration, ex);

        } finally {
            // Clean up MDC
            MDC.clear();
        }
    }

    private void logRequest(HttpServletRequest request, String correlationId) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            String remoteAddr = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");

            StringBuilder logMessage = new StringBuilder();
            logMessage.append("API Request - ")
                     .append("Method: ").append(method)
                     .append(", URI: ").append(uri);

            if (queryString != null) {
                logMessage.append(", Query: ").append(queryString);
            }

            logMessage.append(", IP: ").append(remoteAddr)
                     .append(", UserAgent: ").append(userAgent)
                     .append(", CorrelationId: ").append(correlationId);

            logger.info(logMessage.toString());

            // Log request body for POST/PUT requests (if available)
            if (("POST".equals(method) || "PUT".equals(method)) && 
                request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
                byte[] content = wrapper.getContentAsByteArray();
                if (content.length > 0) {
                    String body = new String(content, wrapper.getCharacterEncoding());
                    logger.debug("Request Body: {}", sanitizeRequestBody(body));
                }
            }

        } catch (Exception e) {
            logger.warn("Error logging request: {}", e.getMessage());
        }
    }

    private void logResponse(HttpServletRequest request, HttpServletResponse response, 
                           long duration, Exception ex) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();

            StringBuilder logMessage = new StringBuilder();
            logMessage.append("API Response - ")
                     .append("Method: ").append(method)
                     .append(", URI: ").append(uri)
                     .append(", Status: ").append(status)
                     .append(", Duration: ").append(duration).append("ms");

            if (ex != null) {
                logMessage.append(", Exception: ").append(ex.getClass().getSimpleName())
                         .append(" - ").append(ex.getMessage());
                logger.error(logMessage.toString(), ex);
            } else if (status >= 400) {
                logger.warn(logMessage.toString());
            } else {
                logger.info(logMessage.toString());
            }

            // Log response body for errors (if available)
            if (status >= 400 && response instanceof ContentCachingResponseWrapper) {
                ContentCachingResponseWrapper wrapper = (ContentCachingResponseWrapper) response;
                byte[] content = wrapper.getContentAsByteArray();
                if (content.length > 0) {
                    String body = new String(content, wrapper.getCharacterEncoding());
                    logger.debug("Response Body: {}", body);
                }
            }

        } catch (Exception e) {
            logger.warn("Error logging response: {}", e.getMessage());
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private String sanitizeRequestBody(String body) {
        // Remove or mask sensitive information from request body
        // This is a simple implementation - enhance based on your needs
        if (body.toLowerCase().contains("password")) {
            body = body.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"");
        }
        if (body.toLowerCase().contains("token")) {
            body = body.replaceAll("\"token\"\\s*:\\s*\"[^\"]*\"", "\"token\":\"***\"");
        }
        return body;
    }
}
