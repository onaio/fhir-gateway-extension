package org.smartregister.fhir.gateway.plugins.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Practitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.fhir.gateway.plugins.Constants;
import org.smartregister.fhir.gateway.plugins.utils.Utils;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import jakarta.annotation.Nullable;

/**
 * Base helper class containing common functionality for FHIR endpoint helpers. This class provides
 * shared methods for FHIR operations that are used across different endpoint helpers.
 */
public abstract class BaseFhirEndpointHelper {

    private static final Logger logger = LoggerFactory.getLogger(BaseFhirEndpointHelper.class);

    protected final IGenericClient r4FHIRClient;

    protected BaseFhirEndpointHelper(IGenericClient fhirClient) {
        this.r4FHIRClient = fhirClient;
    }

    protected IGenericClient getFhirClientForR4() {
        return r4FHIRClient;
    }

    /** Get a practitioner by their Keycloak identifier */
    @Nullable
    public Practitioner getPractitionerByIdentifier(String keycloakUUID) {
        logger.info("Searching for practitioner with user id: " + keycloakUUID);
        try {
            Bundle practitionerBundle =
                    getFhirClientForR4()
                            .search()
                            .forResource(Practitioner.class)
                            .where(
                                    Practitioner.IDENTIFIER
                                            .exactly()
                                            .systemAndIdentifier(
                                                    Constants.DEFAULT_LOCATION_TAG_URL,
                                                    keycloakUUID))
                            .returnBundle(Bundle.class)
                            .execute();

            if (practitionerBundle != null && practitionerBundle.hasEntry()) {
                return (Practitioner) practitionerBundle.getEntry().get(0).getResource();
            }
        } catch (Exception e) {
            logger.error("Error searching for practitioner with identifier: " + keycloakUUID, e);
        }
        return null;
    }

    /** Get practitioner identifier from practitioner resource */
    public String getPractitionerIdentifier(Practitioner practitioner) {
        String practitionerId = null;
        if (practitioner != null && practitioner.hasIdentifier()) {
            practitionerId =
                    practitioner.getIdentifier().stream()
                            .filter(
                                    identifier ->
                                            Constants.DEFAULT_LOCATION_TAG_URL.equals(
                                                    identifier.getSystem()))
                            .findFirst()
                            .map(identifier -> identifier.getValue())
                            .orElse(null);
        }
        return practitionerId;
    }

    /** Get location by ID */
    @Nullable
    public Location getLocationById(String locationId) {
        Location location = null;
        try {
            location =
                    getFhirClientForR4()
                            .fetchResourceFromUrl(
                                    Location.class,
                                    "Location/"
                                            + Utils.extractLogicalId(
                                                    org.hl7.fhir.r4.model.ResourceType.Location,
                                                    locationId));
        } catch (Exception e) {
            logger.error("Error fetching location with ID: " + locationId, e);
        }
        return location;
    }

    /** Get multiple locations by IDs */
    @Nullable
    public Bundle getLocationById(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return getFhirClientForR4()
                    .fetchResourceFromUrl(
                            Bundle.class, "Location?_id=" + StringUtils.join(ids, ","));
        } catch (Exception e) {
            logger.error("Error fetching locations with IDs: " + ids, e);
            return null;
        }
    }

    /**
     * Get practitioner location IDs by Keycloak ID - this is the main method that was causing the
     * dependency issue
     */
    public List<String> getPractitionerLocationIdsByKeycloakId(String keycloakUUID) {
        logger.info("Searching for Practitioner with user id: " + keycloakUUID);
        Practitioner practitioner = getPractitionerByIdentifier(keycloakUUID);
        List<String> locationIds = new ArrayList<>();

        if (practitioner != null) {
            String practitionerId = getPractitionerIdentifier(practitioner);
            if (CacheHelper.INSTANCE.skipCache()) {
                locationIds = getPractitionerLocationIdsByKeycloakIdCore(practitionerId);
            } else {
                locationIds =
                        CacheHelper.INSTANCE.listStringCache.get(
                                keycloakUUID,
                                key -> getPractitionerLocationIdsByKeycloakIdCore(practitionerId));
            }
        } else {
            logger.error("Practitioner with KC identifier : " + keycloakUUID + " not found");
        }
        return locationIds;
    }

    /**
     * Core implementation for getting practitioner location IDs. This method should be implemented
     * by subclasses that need practitioner-specific location logic.
     */
    protected abstract List<String> getPractitionerLocationIdsByKeycloakIdCore(
            String practitionerId);

    /** Generate admin levels for location queries */
    public List<String> generateAdminLevels(String minLevel, String maxLevel) {
        List<String> adminLevels = new ArrayList<>();

        // If both parameters are null, return empty list (original behavior)
        if (minLevel == null && maxLevel == null) {
            return adminLevels;
        }

        int min = minLevel != null ? Integer.parseInt(minLevel) : Constants.DEFAULT_MIN_ADMIN_LEVEL;
        int max = maxLevel != null ? Integer.parseInt(maxLevel) : Constants.DEFAULT_MAX_ADMIN_LEVEL;

        if (min > max) {
            throw new IllegalArgumentException(
                    "administrativeLevelMin cannot be greater than administrativeLevelMax");
        }

        for (int i = min; i <= max; i++) {
            adminLevels.add(String.valueOf(i));
        }
        return adminLevels;
    }

    /** Extract sync locations from request parameter */
    public List<String> extractSyncLocations(String syncLocationsParam) {
        List<String> selectedSyncLocations = new ArrayList<>();
        if (StringUtils.isNotBlank(syncLocationsParam)) {
            Collections.addAll(selectedSyncLocations, syncLocationsParam.split(","));
        }
        return selectedSyncLocations;
    }
}
