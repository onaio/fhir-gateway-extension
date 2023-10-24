package org.smartregister.fhir.gateway.plugins;

import static org.mockito.Mockito.when;

import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.entity.StringEntity;
import org.mockito.Mockito;

class TestUtil {

  public static void setUpFhirResponseMock(HttpResponse fhirResponseMock, String responseJson) {
    Preconditions.checkNotNull(responseJson);
    StatusLine statusLineMock = Mockito.mock(StatusLine.class);
    StringEntity testEntity = new StringEntity(responseJson, StandardCharsets.UTF_8);
    when(fhirResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(HttpStatus.SC_OK);
    when(fhirResponseMock.getEntity()).thenReturn(testEntity);
  }
}
