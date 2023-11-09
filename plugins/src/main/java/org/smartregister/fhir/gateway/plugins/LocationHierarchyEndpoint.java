package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.fhir.gateway.plugins.Constants.IDENTIFIER;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.smartregister.model.location.LocationHierarchy;

import com.google.fhir.gateway.FhirClientFactory;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.TokenVerifier;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

@WebServlet("/LocationHierarchy")
public class LocationHierarchyEndpoint extends HttpServlet {
    private final TokenVerifier tokenVerifier;

    private final HttpFhirClient fhirClient;
    String PROXY_TO_ENV = "PROXY_TO";

    private FhirContext fhirR4Context = FhirContext.forR4();
    private IGenericClient r4FHIRClient =
            fhirR4Context.newRestfulGenericClient(System.getenv(PROXY_TO_ENV));

    private IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);

    private LocationHierarchyEndpointHelper locationHierarchyEndpointHelper;

    public LocationHierarchyEndpoint() throws IOException {
        this.tokenVerifier = TokenVerifier.createFromEnvVars();
        this.fhirClient = FhirClientFactory.createFhirClientFromEnvVars();
        this.locationHierarchyEndpointHelper = new LocationHierarchyEndpointHelper(r4FHIRClient);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            RestUtils.checkAuthentication(request, tokenVerifier);
            String identifier = request.getParameter(IDENTIFIER);

            LocationHierarchy locationHierarchy =
                    locationHierarchyEndpointHelper.getLocationHierarchy(identifier);
            String resultContent = fhirR4JsonParser.encodeResourceToString(locationHierarchy);
            response.setContentType("application/json");
            response.getOutputStream().print(resultContent);
            response.setStatus(HttpStatus.SC_OK);
        } catch (AuthenticationException authenticationException) {
            response.setContentType("application/json");
            response.getOutputStream().print(authenticationException.getMessage());
            response.setStatus(authenticationException.getStatusCode());
        } catch (Exception exception) {
            response.setContentType("application/json");
            response.getOutputStream().print(exception.getMessage());
            response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
