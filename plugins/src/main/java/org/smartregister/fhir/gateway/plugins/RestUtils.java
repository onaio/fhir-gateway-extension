package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.fhir.gateway.plugins.Constants.AUTHORIZATION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.fhir.gateway.ExceptionUtil;
import com.google.fhir.gateway.TokenVerifier;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RestUtils {

    private static final Logger logger = LoggerFactory.getLogger(RestUtils.class);

    public static void checkAuthentication(
            HttpServletRequest request, TokenVerifier tokenVerifier) {
        String authHeader = request.getHeader(AUTHORIZATION);
        if (authHeader == null) {
            ExceptionUtil.throwRuntimeExceptionAndLog(
                    logger, "No Authorization header provided!", new AuthenticationException());
        }
        tokenVerifier.decodeAndVerifyBearerToken(authHeader);
    }

    public static void addCorsHeaders(HttpServletResponse response) {
        response.addHeader(Constants.CORS_ALLOW_HEADERS_KEY, Constants.CORS_ALLOW_HEADERS_VALUE);
        response.addHeader(Constants.CORS_ALLOW_METHODS_KEY, Constants.CORS_ALLOW_METHODS_VALUE);
        String corsAllowOrigin = System.getenv(Constants.CORS_ALLOW_ORIGIN_ENV);
        response.addHeader(
                Constants.CORS_ALLOW_ORIGIN_KEY,
                corsAllowOrigin != null && !corsAllowOrigin.isEmpty()
                        ? corsAllowOrigin
                        : Constants.CORS_ALLOW_ORIGIN_VALUE);
    }
}
