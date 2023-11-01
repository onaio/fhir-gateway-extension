package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.fhir.gateway.plugins.Constants.KEYCLOAK_UUID;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.smartregister.model.practitioner.PractitionerDetails;

import com.google.fhir.gateway.TokenVerifier;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

@WebServlet("/PractitionerDetail")
public class PractitionerDetailEndpoint extends HttpServlet {
    private final TokenVerifier tokenVerifier;
    private FhirContext fhirR4Context = FhirContext.forR4();
    private IGenericClient r4FhirClient =
            fhirR4Context.newRestfulGenericClient(System.getenv(Constants.PROXY_TO_ENV));

    private IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);

    private PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;

    public PractitionerDetailEndpoint() throws IOException {
        this.tokenVerifier = TokenVerifier.createFromEnvVars();
        this.practitionerDetailsEndpointHelper =
                new PractitionerDetailsEndpointHelper(r4FhirClient);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            RestUtils.checkAuthentication(request, tokenVerifier);
            String keycloakUuid = request.getParameter(KEYCLOAK_UUID);
            PractitionerDetails practitionerDetails =
                    practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId(
                            keycloakUuid);
            String resultContent = fhirR4JsonParser.encodeResourceToString(practitionerDetails);
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
