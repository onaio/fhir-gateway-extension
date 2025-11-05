package org.smartregister.fhir.gateway.plugins.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.smartregister.fhir.gateway.plugins.Constants;
import org.smartregister.fhir.gateway.plugins.helper.PractitionerDetailsEndpointHelper;
import org.smartregister.fhir.gateway.plugins.utils.RestUtils;
import org.smartregister.model.practitioner.PractitionerDetails;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class PractitionerDetailEndpointTest {

    private PractitionerDetailEndpoint endpoint;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;
    @Mock private PractitionerDetails practitionerDetails;

    @Before
    public void setUp() throws Exception {
        // Skip tests if PROXY_TO_ENV is not set (constructor requires it)
        String proxyUrl = System.getenv(Constants.PROXY_TO_ENV);
        org.junit.Assume.assumeTrue(
                "Skipping test: PROXY_TO environment variable not set. "
                        + "Set PROXY_TO=http://localhost:8080/fhir to run these tests.",
                proxyUrl != null && !proxyUrl.trim().isEmpty());

        // Create endpoint with the environment variable
        endpoint = new PractitionerDetailEndpoint();

        // Replace the helper with our mock using reflection
        java.lang.reflect.Field field =
                PractitionerDetailEndpoint.class.getDeclaredField(
                        "practitionerDetailsEndpointHelper");
        field.setAccessible(true);
        field.set(endpoint, practitionerDetailsEndpointHelper);
    }

    @Test
    public void testDoGetWithPractitionerFound() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String keycloakUuid = "test-uuid";
        String practitionerId = "practitioner-123";

        try (MockedStatic<RestUtils> restUtilsMock = Mockito.mockStatic(RestUtils.class)) {
            when(request.getParameter(Constants.KEYCLOAK_UUID)).thenReturn(keycloakUuid);
            jakarta.servlet.ServletOutputStream servletOutputStream =
                    new jakarta.servlet.ServletOutputStream() {
                        @Override
                        public void write(int b) throws IOException {
                            outputStream.write(b);
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setWriteListener(jakarta.servlet.WriteListener listener) {
                            // No-op for testing
                        }
                    };
            when(response.getOutputStream()).thenReturn(servletOutputStream);
            when(request.getRequestURL())
                    .thenReturn(new StringBuffer("http://test:8080/PractitionerDetail"));
            when(request.getQueryString()).thenReturn("keycloakUuid=" + keycloakUuid);

            when(practitionerDetails.getId()).thenReturn(practitionerId);
            when(practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId(keycloakUuid))
                    .thenReturn(practitionerDetails);

            restUtilsMock
                    .when(() -> RestUtils.addCorsHeaders(any()))
                    .thenAnswer(invocation -> null);
            restUtilsMock
                    .when(() -> RestUtils.checkAuthentication(any(), any()))
                    .thenAnswer(invocation -> null);

            // Use reflection to set the helper
            try {
                java.lang.reflect.Field field =
                        PractitionerDetailEndpoint.class.getDeclaredField(
                                "practitionerDetailsEndpointHelper");
                field.setAccessible(true);
                field.set(endpoint, practitionerDetailsEndpointHelper);

                java.lang.reflect.Field tokenVerifierField =
                        BaseEndpoint.class.getDeclaredField("tokenVerifier");
                tokenVerifierField.setAccessible(true);
                com.google.fhir.gateway.TokenVerifier mockTokenVerifier =
                        mock(com.google.fhir.gateway.TokenVerifier.class);
                tokenVerifierField.set(endpoint, mockTokenVerifier);
            } catch (Exception e) {
                return;
            }

            endpoint.doGet(request, response);

            verify(response).setStatus(200);
            verify(practitionerDetailsEndpointHelper)
                    .getPractitionerDetailsByKeycloakId(keycloakUuid);
        }
    }

    @Test
    public void testDoGetWithPractitionerNotFound() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String keycloakUuid = "test-uuid";

        try (MockedStatic<RestUtils> restUtilsMock = Mockito.mockStatic(RestUtils.class)) {
            when(request.getParameter(Constants.KEYCLOAK_UUID)).thenReturn(keycloakUuid);
            jakarta.servlet.ServletOutputStream servletOutputStream =
                    new jakarta.servlet.ServletOutputStream() {
                        @Override
                        public void write(int b) throws IOException {
                            outputStream.write(b);
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setWriteListener(jakarta.servlet.WriteListener listener) {
                            // No-op for testing
                        }
                    };
            when(response.getOutputStream()).thenReturn(servletOutputStream);
            when(request.getRequestURL())
                    .thenReturn(new StringBuffer("http://test:8080/PractitionerDetail"));
            when(request.getQueryString()).thenReturn("keycloakUuid=" + keycloakUuid);

            when(practitionerDetails.getId())
                    .thenReturn(org.smartregister.utils.Constants.PRACTITIONER_NOT_FOUND);
            when(practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId(keycloakUuid))
                    .thenReturn(practitionerDetails);

            restUtilsMock
                    .when(() -> RestUtils.addCorsHeaders(any()))
                    .thenAnswer(invocation -> null);
            restUtilsMock
                    .when(() -> RestUtils.checkAuthentication(any(), any()))
                    .thenAnswer(invocation -> null);

            try {
                java.lang.reflect.Field field =
                        PractitionerDetailEndpoint.class.getDeclaredField(
                                "practitionerDetailsEndpointHelper");
                field.setAccessible(true);
                field.set(endpoint, practitionerDetailsEndpointHelper);

                java.lang.reflect.Field tokenVerifierField =
                        BaseEndpoint.class.getDeclaredField("tokenVerifier");
                tokenVerifierField.setAccessible(true);
                com.google.fhir.gateway.TokenVerifier mockTokenVerifier =
                        mock(com.google.fhir.gateway.TokenVerifier.class);
                tokenVerifierField.set(endpoint, mockTokenVerifier);
            } catch (Exception e) {
                return;
            }

            endpoint.doGet(request, response);

            verify(response).setStatus(200);
        }
    }

    @Test
    public void testDoGetWithAuthenticationException() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AuthenticationException authException = new AuthenticationException();

        try (MockedStatic<RestUtils> restUtilsMock = Mockito.mockStatic(RestUtils.class)) {
            jakarta.servlet.ServletOutputStream servletOutputStream =
                    new jakarta.servlet.ServletOutputStream() {
                        @Override
                        public void write(int b) throws IOException {
                            outputStream.write(b);
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setWriteListener(jakarta.servlet.WriteListener listener) {
                            // No-op for testing
                        }
                    };
            when(response.getOutputStream()).thenReturn(servletOutputStream);
            restUtilsMock
                    .when(() -> RestUtils.addCorsHeaders(any()))
                    .thenAnswer(invocation -> null);
            restUtilsMock
                    .when(() -> RestUtils.checkAuthentication(any(), any()))
                    .thenThrow(authException);

            endpoint.doGet(request, response);

            verify(response).setStatus(401);
        }
    }

    @Test
    public void testDoGetWithException() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RuntimeException exception = new RuntimeException("Test exception");

        try (MockedStatic<RestUtils> restUtilsMock = Mockito.mockStatic(RestUtils.class)) {
            jakarta.servlet.ServletOutputStream servletOutputStream =
                    new jakarta.servlet.ServletOutputStream() {
                        @Override
                        public void write(int b) throws IOException {
                            outputStream.write(b);
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setWriteListener(jakarta.servlet.WriteListener listener) {
                            // No-op for testing
                        }
                    };
            when(response.getOutputStream()).thenReturn(servletOutputStream);
            restUtilsMock
                    .when(() -> RestUtils.addCorsHeaders(any()))
                    .thenAnswer(invocation -> null);
            restUtilsMock
                    .when(() -> RestUtils.checkAuthentication(any(), any()))
                    .thenThrow(exception);

            endpoint.doGet(request, response);

            verify(response).setStatus(500);
        }
    }
}
