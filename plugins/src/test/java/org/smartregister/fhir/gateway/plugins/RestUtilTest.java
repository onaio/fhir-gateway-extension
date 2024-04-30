package org.smartregister.fhir.gateway.plugins;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import javax.servlet.http.HttpServletResponse;

import org.junit.Test;

public class RestUtilTest {
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
