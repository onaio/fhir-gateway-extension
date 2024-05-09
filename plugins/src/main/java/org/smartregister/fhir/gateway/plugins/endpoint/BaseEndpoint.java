package org.smartregister.fhir.gateway.plugins.endpoint;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.smartregister.fhir.gateway.plugins.RestUtils;

import com.google.fhir.gateway.TokenVerifier;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

public abstract class BaseEndpoint extends HttpServlet {
    protected final TokenVerifier tokenVerifier = TokenVerifier.createFromEnvVars();
    protected final FhirContext fhirR4Context = FhirContext.forR4();
    protected final IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        RestUtils.addCorsHeaders(response);
    }

    protected BaseEndpoint() throws IOException {}
}
