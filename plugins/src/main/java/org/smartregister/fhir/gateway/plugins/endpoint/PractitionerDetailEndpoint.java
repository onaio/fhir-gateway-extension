package org.smartregister.fhir.gateway.plugins.endpoint;

import static org.smartregister.fhir.gateway.plugins.Constants.KEYCLOAK_UUID;

import java.io.IOException;
import java.util.Collections;

import org.apache.http.HttpStatus;
import org.smartregister.fhir.gateway.plugins.Constants;
import org.smartregister.fhir.gateway.plugins.helper.PractitionerDetailsEndpointHelper;
import org.smartregister.fhir.gateway.plugins.utils.RestUtils;
import org.smartregister.fhir.gateway.plugins.utils.Utils;
import org.smartregister.model.practitioner.PractitionerDetails;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/PractitionerDetail")
public class PractitionerDetailEndpoint extends BaseEndpoint {
    private final PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;

    public PractitionerDetailEndpoint() throws IOException {
        this.practitionerDetailsEndpointHelper =
                new PractitionerDetailsEndpointHelper(
                        fhirR4Context.newRestfulGenericClient(
                                System.getenv(Constants.PROXY_TO_ENV)));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        RestUtils.addCorsHeaders(response);
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
                                Utils.createEmptyBundle(
                                        request.getRequestURL() + "?" + request.getQueryString()));
            } else {
                resultContent =
                        fhirR4JsonParser.encodeResourceToString(
                                Utils.createBundle(Collections.singletonList(practitionerDetails)));
            }

            response.setContentType("application/json");
            writeUTF8StringToStream(response.getOutputStream(), resultContent);
            response.setStatus(HttpStatus.SC_OK);
        } catch (AuthenticationException authenticationException) {
            response.setContentType("application/json");
            writeUTF8StringToStream(
                    response.getOutputStream(), authenticationException.getMessage());
            response.setStatus(authenticationException.getStatusCode());
        } catch (Exception exception) {
            response.setContentType("application/json");
            writeUTF8StringToStream(response.getOutputStream(), exception.getMessage());
            response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
