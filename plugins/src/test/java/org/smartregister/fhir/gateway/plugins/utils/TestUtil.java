package org.smartregister.fhir.gateway.plugins.utils;

import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.entity.StringEntity;
import org.mockito.Mockito;

import com.google.common.base.Preconditions;

public class TestUtil {

    public static final String JWT_NORMAL_USER =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJUZXN0IFVzZXIiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiTk9STUFMX1VTRVIiXX19.";
    public static final String JWT_SUPERVISOR_USER =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJUZXN0IFVzZXIiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiU1VQRVJWSVNPUiJdXX0.\n";

    public static void setUpFhirResponseMock(HttpResponse fhirResponseMock, String responseJson) {
        Preconditions.checkNotNull(responseJson);
        StatusLine statusLineMock = Mockito.mock(StatusLine.class);
        StringEntity testEntity = new StringEntity(responseJson, StandardCharsets.UTF_8);
        when(fhirResponseMock.getStatusLine()).thenReturn(statusLineMock);
        when(statusLineMock.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(fhirResponseMock.getEntity()).thenReturn(testEntity);
    }
}
