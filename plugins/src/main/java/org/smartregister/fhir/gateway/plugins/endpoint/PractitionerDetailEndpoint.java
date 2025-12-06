package org.smartregister.fhir.gateway.plugins.endpoint;

import static org.smartregister.fhir.gateway.plugins.Constants.AUTHORIZATION;
import static org.smartregister.fhir.gateway.plugins.Constants.KEYCLOAK_UUID;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpStatus;
import org.hl7.fhir.r4.model.Bundle;
import org.smartregister.fhir.gateway.plugins.Constants;
import org.smartregister.fhir.gateway.plugins.SyncAccessDecision;
import org.smartregister.fhir.gateway.plugins.helper.PractitionerDetailsEndpointHelper;
import org.smartregister.fhir.gateway.plugins.utils.JwtUtils;
import org.smartregister.fhir.gateway.plugins.utils.RestUtils;
import org.smartregister.fhir.gateway.plugins.utils.Utils;
import org.smartregister.model.practitioner.PractitionerDetails;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/PractitionerDetail")
public class PractitionerDetailEndpoint extends BaseEndpoint {
    private final PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;

    public PractitionerDetailEndpoint() {
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
            String authHeader = request.getHeader(AUTHORIZATION);
            String token = authHeader.replace("Bearer ", "");
            DecodedJWT jwt = JWT.decode(token);
            List<String> roles = JwtUtils.getUserRolesFromJWT(jwt);
            String keycloakUuid = request.getParameter(KEYCLOAK_UUID);

            Bundle bundle = getPractitionerDetailsBundle(keycloakUuid, roles, request);
            String resultContent = fhirR4JsonParser.encodeResourceToString(bundle);

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

    private Bundle getPractitionerDetailsBundle(
            String keycloakUuid, List<String> roles, HttpServletRequest request) {
        if (roles.contains(SyncAccessDecision.SyncAccessDecisionConstants.ROLE_SUPERVISOR)) {
            return practitionerDetailsEndpointHelper.getSupervisorPractitionerDetailsByKeycloakId(
                    keycloakUuid);
        } else {
            PractitionerDetails practitionerDetails =
                    practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId(
                            keycloakUuid);
            if (org.smartregister.utils.Constants.PRACTITIONER_NOT_FOUND.equals(
                    practitionerDetails.getId())) {
                return Utils.createEmptyBundle(
                        request.getRequestURL() + "?" + request.getQueryString());
            } else {
                return Utils.createBundle(Collections.singletonList(practitionerDetails));
            }
        }
    }
}
