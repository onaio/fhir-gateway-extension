package org.smartregister.fhir.gateway.plugins.endpoint;

import static org.smartregister.fhir.gateway.plugins.Constants.AUTHORIZATION;

import java.io.IOException;

import org.apache.http.HttpStatus;
import org.hl7.fhir.r4.model.Bundle;
import org.smartregister.fhir.gateway.plugins.Constants;
import org.smartregister.fhir.gateway.plugins.helper.LocationHierarchyEndpointHelper;
import org.smartregister.fhir.gateway.plugins.helper.PractitionerDetailsEndpointHelper;
import org.smartregister.fhir.gateway.plugins.utils.RestUtils;

import com.auth0.jwt.interfaces.DecodedJWT;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/LocationHierarchy")
public class LocationHierarchyEndpoint extends BaseEndpoint {
    private final LocationHierarchyEndpointHelper locationHierarchyEndpointHelper;

    public LocationHierarchyEndpoint() throws IOException {
        this.locationHierarchyEndpointHelper =
                new LocationHierarchyEndpointHelper(
                        fhirR4Context.newRestfulGenericClient(
                                System.getenv(Constants.PROXY_TO_ENV)));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        RestUtils.addCorsHeaders(response);
        try {
            RestUtils.checkAuthentication(request, tokenVerifier);
            String identifier = request.getParameter(Constants.IDENTIFIER);
            String authHeader = request.getHeader(AUTHORIZATION);
            DecodedJWT verifiedJwt = tokenVerifier.decodeAndVerifyBearerToken(authHeader);

            String resultContent;
            if (identifier != null && !identifier.isEmpty()) {
                Bundle resultBundle =
                        locationHierarchyEndpointHelper.handleIdentifierRequest(
                                request, identifier);
                resultContent = fhirR4JsonParser.encodeResourceToString(resultBundle);
            } else {
                PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper =
                        new PractitionerDetailsEndpointHelper(
                                fhirR4Context.newRestfulGenericClient(
                                        System.getenv(Constants.PROXY_TO_ENV)));
                Bundle resultBundle =
                        locationHierarchyEndpointHelper.handleNonIdentifierRequest(
                                request, practitionerDetailsEndpointHelper, verifiedJwt);
                resultContent = fhirR4JsonParser.encodeResourceToString(resultBundle);
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
