package org.smartregister.fhir.gateway.plugins.endpoint;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.hl7.fhir.r4.model.Bundle;
import org.smartregister.fhir.gateway.plugins.Constants;
import org.smartregister.fhir.gateway.plugins.LocationHierarchyEndpointHelper;
import org.smartregister.fhir.gateway.plugins.RestUtils;
import org.smartregister.fhir.gateway.plugins.Utils;
import org.smartregister.model.location.LocationHierarchy;

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
            String administrativeLevelMin = request.getParameter(Constants.MIN_ADMIN_LEVEL);
            String administrativeLevelMax = request.getParameter(Constants.MAX_ADMIN_LEVEL);
            List<String> adminLevels =
                    locationHierarchyEndpointHelper.generateAdminLevels(
                            administrativeLevelMin, administrativeLevelMax);

            String mode = request.getParameter(Constants.MODE);

            String resultContent;
            if (Constants.LIST.equals(mode)) {
                Bundle resultBundle =
                        locationHierarchyEndpointHelper.getPaginatedLocations(request);
                resultContent = fhirR4JsonParser.encodeResourceToString(resultBundle);

            } else {
                LocationHierarchy locationHierarchy =
                        locationHierarchyEndpointHelper.getLocationHierarchy(
                                identifier, adminLevels);

                if (org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND.equals(
                        locationHierarchy.getId())) {
                    resultContent =
                            fhirR4JsonParser.encodeResourceToString(
                                    Utils.createEmptyBundle(
                                            request.getRequestURL()
                                                    + "?"
                                                    + request.getQueryString()));
                } else {
                    resultContent =
                            fhirR4JsonParser.encodeResourceToString(
                                    Utils.createBundle(
                                            Collections.singletonList(locationHierarchy)));
                }
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
