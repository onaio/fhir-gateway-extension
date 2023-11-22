package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.fhir.gateway.plugins.Constants.KEYCLOAK_UUID;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.smartregister.model.practitioner.PractitionerDetails;

import com.google.fhir.gateway.TokenVerifier;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

@WebServlet("/PractitionerDetail")
public class PractitionerDetailEndpoint extends BaseEndpoint {
    private final TokenVerifier tokenVerifier;
    private final FhirContext fhirR4Context = FhirContext.forR4();
    private final IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);
    private final PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;

    public PractitionerDetailEndpoint() throws IOException {
        this.tokenVerifier = TokenVerifier.createFromEnvVars();
        this.practitionerDetailsEndpointHelper =
                new PractitionerDetailsEndpointHelper(
                        fhirR4Context.newRestfulGenericClient(
                                System.getenv(Constants.PROXY_TO_ENV)));
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
            String resultContent;
            if (org.smartregister.utils.Constants.PRACTITIONER_NOT_FOUND.equals(
                    practitionerDetails.getId())) {
                resultContent =
                        fhirR4JsonParser.encodeResourceToString(
                                createEmptyBundle(
                                        request.getRequestURL() + "?" + request.getQueryString()));
            } else {
                resultContent =
                        fhirR4JsonParser.encodeResourceToString(
                                createBundle(Collections.singletonList(practitionerDetails)));
            }

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
