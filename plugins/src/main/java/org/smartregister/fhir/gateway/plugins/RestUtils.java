package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.fhir.gateway.plugins.Constants.AUTHORIZATION;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.fhir.gateway.ExceptionUtil;
import com.google.fhir.gateway.TokenVerifier;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

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
}
