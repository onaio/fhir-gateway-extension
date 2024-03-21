package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.utils.Constants.LOCATION_RESOURCE;
import static org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.LocationHierarchyTree;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

public class LocationHierarchyEndpointHelper {

    private static final Logger logger =
            LoggerFactory.getLogger(LocationHierarchyEndpointHelper.class);

    private final IGenericClient r4FHIRClient;

    public LocationHierarchyEndpointHelper(IGenericClient fhirClient) {
        this.r4FHIRClient = fhirClient;
    }

    private IGenericClient getFhirClientForR4() {
        return r4FHIRClient;
    }

    public LocationHierarchy getLocationHierarchy(String locationId) {
        LocationHierarchy locationHierarchy;

        if (CacheHelper.INSTANCE.skipCache()) {
            locationHierarchy = getLocationHierarchyCore(locationId);
        } else {
            locationHierarchy =
                    (LocationHierarchy)
                            CacheHelper.INSTANCE.resourceCache.get(
                                    locationId, this::getLocationHierarchyCore);
        }
        return locationHierarchy;
    }

    public LocationHierarchy getLocationHierarchyCore(String locationId) {
        Location location = getLocationById(locationId);

        LocationHierarchyTree locationHierarchyTree = new LocationHierarchyTree();
        LocationHierarchy locationHierarchy = new LocationHierarchy();
        if (location != null) {
            logger.info("Building Location Hierarchy of Location Id : " + locationId);
            locationHierarchyTree.buildTreeFromList(getLocationHierarchy(locationId, location));
            StringType locationIdString = new StringType().setId(locationId).getIdElement();
            locationHierarchy.setLocationId(locationIdString);
            locationHierarchy.setId(LOCATION_RESOURCE + locationId);

            locationHierarchy.setLocationHierarchyTree(locationHierarchyTree);
        } else {
            logger.error("LocationHierarchy with identifier: " + locationId + " not found");
            locationHierarchy.setId(LOCATION_RESOURCE_NOT_FOUND);
        }
        return locationHierarchy;
    }

    private List<Location> getLocationHierarchy(String locationId, Location parentLocation) {
        return descendants(locationId, parentLocation);
    }

    public List<Location> getLocationHierarchyAsList(String locationId) {
        Location location = getLocationById(locationId);
        if (location != null) {
            return getLocationDescendants(locationId, location);
        } else {
            logger.error("LocationHierarchy with identifier: " + locationId + " not found");
            return new ArrayList<>();
        }
    }

    private List<Location> getLocationDescendants(String locationId, Location parentLocation) {
        List<Location> allLocations = new ArrayList<>();
        allLocations.add(parentLocation);

        Bundle childLocationBundle =
                getFhirClientForR4()
                        .search()
                        .forResource(Location.class)
                        .where(new ReferenceClientParam(Location.SP_PARTOF).hasAnyOfIds(locationId))
                        .returnBundle(Bundle.class)
                        .execute();

        if (childLocationBundle != null) {
            for (Bundle.BundleEntryComponent childLocation : childLocationBundle.getEntry()) {
                Location childLocationEntity = (Location) childLocation.getResource();
                allLocations.addAll(
                        getLocationDescendants(
                                childLocationEntity.getIdElement().getIdPart(),
                                childLocationEntity));
            }
        }

        return allLocations;
    }

    public List<Location> descendants(String locationId, Location parentLocation) {

        Bundle childLocationBundle =
                getFhirClientForR4()
                        .search()
                        .forResource(Location.class)
                        .where(new ReferenceClientParam(Location.SP_PARTOF).hasAnyOfIds(locationId))
                        .returnBundle(Bundle.class)
                        .execute();

        List<Location> allLocations = new ArrayList<>();
        if (parentLocation != null) {
            allLocations.add(parentLocation);
        }

        if (childLocationBundle != null) {
            for (Bundle.BundleEntryComponent childLocation : childLocationBundle.getEntry()) {
                Location childLocationEntity = (Location) childLocation.getResource();
                allLocations.add(childLocationEntity);
                allLocations.addAll(
                        descendants(childLocationEntity.getIdElement().getIdPart(), null));
            }
        }

        return allLocations;
    }

    private @Nullable Location getLocationById(String locationId) {
        Location location = null;
        try {
            location =
                    getFhirClientForR4()
                            .fetchResourceFromUrl(Location.class, "Location/" + locationId);
        } catch (ResourceNotFoundException e) {
            logger.error(e.getMessage());
        }
        return location;
    }
}
