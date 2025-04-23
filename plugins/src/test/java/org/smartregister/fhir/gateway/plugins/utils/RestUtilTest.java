package org.smartregister.fhir.gateway.plugins.utils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.mockito.Mockito;
import org.smartregister.fhir.gateway.plugins.Constants;

import com.google.fhir.gateway.TokenVerifier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RestUtilTest {

    @Test(expected = RuntimeException.class)
    public void testCheckAuthenticationThrowsExceptionWhenNoAuthHeader() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        TokenVerifier tokenVerifierMock = mock(TokenVerifier.class);
        Mockito.when(requestMock.getHeader(Constants.AUTHORIZATION)).thenReturn(null);
        RestUtils.checkAuthentication(requestMock, tokenVerifierMock);
    }

    @Test
    public void testCheckAuthenticationCallsTokenVerifierWhenAuthHeaderExists() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        TokenVerifier tokenVerifierMock = mock(TokenVerifier.class);
        String authHeader = "Bearer someToken";
        Mockito.when(requestMock.getHeader(Constants.AUTHORIZATION)).thenReturn(authHeader);
        RestUtils.checkAuthentication(requestMock, tokenVerifierMock);
        verify(tokenVerifierMock).decodeAndVerifyBearerToken(authHeader);
    }

    @Test
    public void testAddCorsHeadersSetsCorsHeaders() {
        HttpServletResponse responseMock = mock(HttpServletResponse.class);
        RestUtils.addCorsHeaders(responseMock);
        verify(responseMock)
                .addHeader(Constants.CORS_ALLOW_HEADERS_KEY, Constants.CORS_ALLOW_HEADERS_VALUE);
        verify(responseMock)
                .addHeader(Constants.CORS_ALLOW_METHODS_KEY, Constants.CORS_ALLOW_METHODS_VALUE);
        verify(responseMock).addHeader(eq(Constants.CORS_ALLOW_ORIGIN_KEY), anyString());
    }
}
