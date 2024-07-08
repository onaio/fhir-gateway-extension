package org.smartregister.fhir.gateway.plugins.endpoint;

import static org.smartregister.fhir.gateway.plugins.Constants.AUTHORIZATION;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.hl7.fhir.r4.model.Bundle;
import org.smartregister.fhir.gateway.plugins.Constants;
import org.smartregister.fhir.gateway.plugins.LocationHierarchyEndpointHelper;
import org.smartregister.fhir.gateway.plugins.PractitionerDetailsEndpointHelper;
import org.smartregister.fhir.gateway.plugins.RestUtils;

import com.auth0.jwt.interfaces.DecodedJWT;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

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
