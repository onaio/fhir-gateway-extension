package org.smartregister.fhir.gateway.plugins.endpoint;

import static org.smartregister.fhir.gateway.plugins.Constants.AUTHORIZATION;

import java.io.IOException;
import java.util.List;

import org.apache.http.HttpStatus;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.fhir.gateway.plugins.Constants;
import org.smartregister.fhir.gateway.plugins.helper.LocationHierarchyEndpointHelper;
import org.smartregister.fhir.gateway.plugins.utils.RestUtils;

import com.auth0.jwt.interfaces.DecodedJWT;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/LocationHierarchy")
public class LocationHierarchyEndpoint extends BaseEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(LocationHierarchyEndpoint.class);

    public LocationHierarchyEndpoint() {
        // No need to acquire client in constructor - will acquire per request
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        RestUtils.addCorsHeaders(response);

        // Acquire client from pool for this request
        // Note: The client must be returned in the finally block to prevent pool
        // exhaustion
        IGenericClient fhirClient = fhirClientPool.getClient();
        try {
            // Create helper with the acquired client
            // The helper and its nested helpers store references to the client, but they
            // are
            // scoped to this request and will be garbage collected after the method
            // returns.
            // The client itself is returned to the pool in the finally block.
            LocationHierarchyEndpointHelper locationHierarchyEndpointHelper =
                    new LocationHierarchyEndpointHelper(fhirClient);

            RestUtils.checkAuthentication(request, tokenVerifier);
            String identifier = request.getParameter(Constants.IDENTIFIER);
            String authHeader = request.getHeader(AUTHORIZATION);
            DecodedJWT verifiedJwt = tokenVerifier.decodeAndVerifyBearerToken(authHeader);

            // Always enable streaming when mode=list for better performance
            boolean useStreaming = Constants.LIST.equals(request.getParameter(Constants.MODE));

            if (identifier != null && !identifier.isEmpty()) {
                Bundle resultBundle =
                        locationHierarchyEndpointHelper.handleIdentifierRequest(
                                request, identifier);
                String resultContent = fhirR4JsonParser.encodeResourceToString(resultBundle);
                response.setContentType("application/json");
                writeUTF8StringToStream(response.getOutputStream(), resultContent);
            } else if (useStreaming) {
                // Use streaming for list mode (automatically enabled when mode=list)
                // Extract location IDs as in the non-streaming path
                java.util.List<String> locationIds =
                        getLocationIdsFromRequest(
                                request, verifiedJwt, locationHierarchyEndpointHelper);
                locationHierarchyEndpointHelper.streamPaginatedLocations(
                        request, response, locationIds);
            } else {
                Bundle resultBundle =
                        locationHierarchyEndpointHelper.handleNonIdentifierRequest(
                                request, verifiedJwt);
                String resultContent = fhirR4JsonParser.encodeResourceToString(resultBundle);
                response.setContentType("application/json");
                writeUTF8StringToStream(response.getOutputStream(), resultContent);
            }
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
        } finally {
            // Always return the client to the pool
            fhirClientPool.returnClient(fhirClient);
        }
    }

    private List<String> getLocationIdsFromRequest(
            HttpServletRequest request,
            DecodedJWT verifiedJwt,
            LocationHierarchyEndpointHelper locationHierarchyEndpointHelper) {
        try {
            // Get location IDs from the sync locations parameter if available
            String syncLocationsParam = request.getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);
            if (syncLocationsParam != null && !syncLocationsParam.isEmpty()) {
                return java.util.Arrays.asList(syncLocationsParam.split(","));
            }

            // If no sync locations, get practitioner's assigned locations
            String practitionerId = verifiedJwt.getSubject();
            if (practitionerId != null && !practitionerId.isEmpty()) {
                // Use the helper to get practitioner's location IDs
                return locationHierarchyEndpointHelper.getPractitionerLocationIdsByKeycloakId(
                        practitionerId);
            }

            // Fallback to empty list if no location IDs can be determined
            return java.util.Collections.emptyList();
        } catch (Exception e) {
            // Log error and return empty list as fallback
            logger.warn("Failed to resolve location IDs from request", e);
            return java.util.Collections.emptyList();
        }
    }
}
