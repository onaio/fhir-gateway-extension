package org.smartregister.fhir.gateway.plugins;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

public abstract class BaseEndpoint extends HttpServlet {
    protected final FhirContext fhirR4Context = FhirContext.forR4();
    protected final IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        RestUtils.addCorsHeaders(response);
    }
}
