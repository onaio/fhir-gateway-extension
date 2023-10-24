package org.smartregister.fhir.gateway.plugins;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.instance.model.api.IIdType;

import com.google.fhir.gateway.interfaces.RequestDetailsReader;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;

// Note instances of this class are expected to be one per thread and this class is not thread-safe
// the same way the underlying `requestDetails` is not.
public class TestRequestDetailsToReader implements RequestDetailsReader {
    private final RequestDetails requestDetails;

    TestRequestDetailsToReader(RequestDetails requestDetails) {
        this.requestDetails = requestDetails;
    }

    public String getRequestId() {
        return requestDetails.getRequestId();
    }

    public Charset getCharset() {
        return requestDetails.getCharset();
    }

    public String getCompleteUrl() {
        return requestDetails.getCompleteUrl();
    }

    public FhirContext getFhirContext() {
        // TODO: There might be a race condition in the underlying `getFhirContext`; check if this
        // is
        // true. Note the `myServer` object is shared between threads.
        return requestDetails.getFhirContext();
    }

    public String getFhirServerBase() {
        return requestDetails.getFhirServerBase();
    }

    public String getHeader(String name) {
        return requestDetails.getHeader(name);
    }

    public List<String> getHeaders(String name) {
        return requestDetails.getHeaders(name);
    }

    public IIdType getId() {
        return requestDetails.getId();
    }

    public String getOperation() {
        return requestDetails.getOperation();
    }

    public Map<String, String[]> getParameters() {
        return requestDetails.getParameters();
    }

    public String getRequestPath() {
        return requestDetails.getRequestPath();
    }

    public RequestTypeEnum getRequestType() {
        return requestDetails.getRequestType();
    }

    public String getResourceName() {
        return requestDetails.getResourceName();
    }

    public RestOperationTypeEnum getRestOperationType() {
        return requestDetails.getRestOperationType();
    }

    public String getSecondaryOperation() {
        return requestDetails.getSecondaryOperation();
    }

    public boolean isRespondGzip() {
        return requestDetails.isRespondGzip();
    }

    public byte[] loadRequestContents() {
        return requestDetails.loadRequestContents();
    }
}
