package org.smartregister.fhir.gateway.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Resource;
import org.smartregister.model.location.LocationHierarchy;

import com.google.fhir.gateway.TokenVerifier;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

@WebServlet("/LocationHierarchy")
public class LocationHierarchyEndpoint extends BaseEndpoint {
    private final TokenVerifier tokenVerifier;

    private final FhirContext fhirR4Context = FhirContext.forR4();
    private final IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);
    private final LocationHierarchyEndpointHelper locationHierarchyEndpointHelper;

    public LocationHierarchyEndpoint() throws IOException {
        this.tokenVerifier = TokenVerifier.createFromEnvVars();
        this.locationHierarchyEndpointHelper =
                new LocationHierarchyEndpointHelper(
                        fhirR4Context.newRestfulGenericClient(
                                System.getenv(Constants.PROXY_TO_ENV)));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            RestUtils.checkAuthentication(request, tokenVerifier);
            String identifier = request.getParameter(Constants.IDENTIFIER);
            String mode = request.getParameter(Constants.MODE);
            String resultContent;
            if (Objects.equals(mode, Constants.LIST)) {
                Location parentLocation =
                        locationHierarchyEndpointHelper.getLocationById(identifier);
                List<Location> locations =
                        locationHierarchyEndpointHelper.getDescendants(identifier, parentLocation);
                List<Resource> resourceLocations = new ArrayList<>(locations);
                if (locations.isEmpty()) {
                    resultContent =
                            fhirR4JsonParser.encodeResourceToString(
                                    createEmptyBundle(
                                            request.getRequestURL()
                                                    + "?"
                                                    + request.getQueryString()));
                } else {
                    resultContent =
                            fhirR4JsonParser.encodeResourceToString(
                                    createBundle(resourceLocations));
                }

            } else {
                LocationHierarchy locationHierarchy =
                        locationHierarchyEndpointHelper.getLocationHierarchy(identifier);

                if (org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND.equals(
                        locationHierarchy.getId())) {
                    resultContent =
                            fhirR4JsonParser.encodeResourceToString(
                                    createEmptyBundle(
                                            request.getRequestURL()
                                                    + "?"
                                                    + request.getQueryString()));
                } else {
                    resultContent =
                            fhirR4JsonParser.encodeResourceToString(
                                    createBundle(Collections.singletonList(locationHierarchy)));
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
