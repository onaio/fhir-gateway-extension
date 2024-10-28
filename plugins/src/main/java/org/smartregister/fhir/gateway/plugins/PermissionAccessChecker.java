package org.smartregister.fhir.gateway.plugins;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Organization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.fhir.gateway.plugins.interfaces.ResourceFinder;
import org.smartregister.model.practitioner.PractitionerDetails;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.fhir.gateway.FhirProxyServer;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.AccessCheckerFactory;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.NoOpAccessDecision;
import com.google.fhir.gateway.interfaces.PatientFinder;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.SearchStyleEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import jakarta.annotation.Nonnull;

public class PermissionAccessChecker implements AccessChecker {
    private static final Logger logger = LoggerFactory.getLogger(PermissionAccessChecker.class);
    private final ResourceFinder resourceFinder;
    private final List<String> userRoles;
    private SyncAccessDecision syncAccessDecision;
    private final String applicationId;
    private final FhirContext fhirContext;
    private final DecodedJWT jwt;

    private PermissionAccessChecker(
            FhirContext fhirContext,
            DecodedJWT jwt,
            List<String> userRoles,
            ResourceFinderImp resourceFinder,
            String applicationId) {
        Preconditions.checkNotNull(userRoles);
        Preconditions.checkNotNull(resourceFinder);
        this.resourceFinder = resourceFinder;
        this.userRoles = userRoles;
        this.applicationId = applicationId;
        this.jwt = jwt;
        this.fhirContext = fhirContext;
    }

    @Override
    public AccessDecision checkAccess(RequestDetailsReader requestDetails) {

        initSyncAccessDecision(requestDetails);

        //  For a Bundle requestDetails.getResourceName() returns null
        if (requestDetails.getRequestType() == RequestTypeEnum.POST
                && requestDetails.getResourceName() == null) {
            return processBundle(requestDetails);

        } else {

            boolean userHasRole =
                    checkUserHasRole(
                            requestDetails.getResourceName(),
                            requestDetails.getRequestType().name());

            RequestTypeEnum requestType = requestDetails.getRequestType();

            switch (requestType) {
                case GET:
                    return processGet(userHasRole);
                case DELETE:
                    return processDelete(userHasRole);
                case POST:
                    return processPost(userHasRole);
                case PUT:
                    return processPut(userHasRole);
                default:
                    // TODO handle other cases like PATCH
                    return NoOpAccessDecision.accessDenied();
            }
        }
    }

    private void initSyncAccessDecision(RequestDetailsReader requestDetailsReader) {
        Map<String, List<String>> syncStrategyIds;

        Composition composition = fetchComposition();
        String syncStrategy = readSyncStrategyFromComposition(composition);

        if (CacheHelper.INSTANCE.skipCache()) {
            syncStrategyIds =
                    getSyncStrategyIds(jwt.getSubject(), syncStrategy, requestDetailsReader);
        } else {
            syncStrategyIds =
                    CacheHelper.INSTANCE.cache.get(
                            generateSyncStrategyIdsCacheKey(
                                    jwt.getSubject(),
                                    syncStrategy,
                                    requestDetailsReader.getParameters()),
                            key ->
                                    getSyncStrategyIds(
                                            jwt.getSubject(), syncStrategy, requestDetailsReader));
        }

        this.syncAccessDecision =
                new SyncAccessDecision(
                        fhirContext,
                        jwt.getSubject(),
                        true,
                        syncStrategyIds,
                        syncStrategyIds.keySet().iterator().next(),
                        userRoles);
    }

    private String generateSyncStrategyIdsCacheKey(
            String userId, String syncStrategy, Map<String, String[]> parameters) {

        String key = null;
        switch (syncStrategy) {
            case Constants.SyncStrategy.RELATED_ENTITY_LOCATION:
                try {

                    String[] syncLocations =
                            parameters.getOrDefault(
                                    Constants.SYNC_LOCATIONS_SEARCH_PARAM, new String[] {});

                    if (syncLocations.length == 0) {
                        key = userId;
                    } else {
                        key = Utils.generateHash(getSortedInput(syncLocations[0]));
                    }

                } catch (NoSuchAlgorithmException exception) {
                    logger.error(exception.getMessage());
                }

                break;

            default:
                key = userId;
        }

        return key;
    }

    private String getSortedInput(String input) {
        return Arrays.stream(input.split(","))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(","));
    }

    private boolean checkUserHasRole(String resourceName, String requestType) {
        return StringUtils.isNotBlank(resourceName)
                && (checkIfRoleExists(getAdminRoleName(resourceName), this.userRoles)
                        || checkIfRoleExists(
                                getRelevantRoleName(resourceName, requestType), this.userRoles));
    }

    private AccessDecision processGet(boolean userHasRole) {
        return getAccessDecision(userHasRole);
    }

    private AccessDecision processDelete(boolean userHasRole) {
        return getAccessDecision(userHasRole);
    }

    private AccessDecision getAccessDecision(boolean userHasRole) {
        return userHasRole ? syncAccessDecision : NoOpAccessDecision.accessDenied();
    }

    private AccessDecision processPost(boolean userHasRole) {
        return getAccessDecision(userHasRole);
    }

    private AccessDecision processPut(boolean userHasRole) {
        return getAccessDecision(userHasRole);
    }

    private AccessDecision processBundle(RequestDetailsReader requestDetails) {
        boolean hasMissingRole = false;
        List<BundleResources> resourcesInBundle =
                resourceFinder.findResourcesInBundle(requestDetails);
        // Verify Authorization for individual requests in Bundle
        for (BundleResources bundleResources : resourcesInBundle) {
            if (!checkUserHasRole(
                    bundleResources.getResource().fhirType(),
                    bundleResources.getRequestType().name())) {

                if (isDevMode()) {
                    hasMissingRole = true;
                    logger.info(
                            "Missing role "
                                    + getRelevantRoleName(
                                            bundleResources.getResource().fhirType(),
                                            bundleResources.getRequestType().name()));
                } else {
                    return NoOpAccessDecision.accessDenied();
                }
            }
        }

        return (isDevMode() && !hasMissingRole) || !isDevMode()
                ? NoOpAccessDecision.accessGranted()
                : NoOpAccessDecision.accessDenied();
    }

    private String getRelevantRoleName(String resourceName, String methodType) {
        return methodType + "_" + resourceName.toUpperCase();
    }

    private String getAdminRoleName(String resourceName) {
        return "MANAGE_" + resourceName.toUpperCase();
    }

    @VisibleForTesting
    protected boolean isDevMode() {
        return FhirProxyServer.isDevMode();
    }

    private boolean checkIfRoleExists(String roleName, List<String> existingRoles) {
        return existingRoles.contains(roleName);
    }

    private Composition readCompositionResource(String applicationId, FhirContext fhirContext) {
        IGenericClient client = Utils.createFhirClientForR4(fhirContext);

        Bundle compositionBundle =
                client.search()
                        .forResource(Composition.class)
                        .where(Composition.IDENTIFIER.exactly().identifier(applicationId))
                        .usingStyle(SearchStyleEnum.POST)
                        .returnBundle(Bundle.class)
                        .execute();

        Bundle.BundleEntryComponent compositionEntry = compositionBundle.getEntryFirstRep();
        return compositionEntry != null ? (Composition) compositionEntry.getResource() : null;
    }

    PractitionerDetails fetchPractitionerDetails(String subject) {
        fhirContext.registerCustomType(PractitionerDetails.class);

        IGenericClient client = Utils.createFhirClientForR4(fhirContext);

        PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper =
                new PractitionerDetailsEndpointHelper(client);
        PractitionerDetails practitionerDetails =
                practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId(subject);

        if (practitionerDetails == null)
            throw new IllegalStateException(
                    "No PractitionerDetail resource found for user with id '" + subject + "'");

        return practitionerDetails;
    }

    private Composition fetchComposition() {
        Composition composition = readCompositionResource(applicationId, fhirContext);
        if (composition == null)
            throw new IllegalStateException(
                    "No Composition resource found for application id '" + applicationId + "'");

        return composition;
    }

    private String readSyncStrategyFromComposition(Composition composition) {
        String binaryResourceReference = Utils.getBinaryResourceReference(composition);
        Binary binary =
                Utils.readApplicationConfigBinaryResource(binaryResourceReference, fhirContext);
        return Utils.findSyncStrategy(binary);
    }

    private Map<String, List<String>> getSyncStrategyIds(
            String subjectId, String syncStrategy, RequestDetailsReader requestDetailsReader) {

        PractitionerDetails practitionerDetails = fetchPractitionerDetails(subjectId);

        return collateSyncStrategyIds(syncStrategy, practitionerDetails, requestDetailsReader);
    }

    private List<String> getLocationUuids(String[] syncLocations) {
        List<String> locationUuids = new ArrayList<>();
        String syncLocationParam;

        for (String syncLocation : syncLocations) {
            syncLocationParam = syncLocation;
            if (!syncLocationParam.isEmpty())
                locationUuids.addAll(
                        Set.of(syncLocationParam.split(Constants.PARAM_VALUES_SEPARATOR)));
        }

        return locationUuids;
    }

    @Nonnull
    private Map<String, List<String>> collateSyncStrategyIds(
            String syncStrategy,
            PractitionerDetails practitionerDetails,
            RequestDetailsReader requestDetailsReader) {
        Map<String, List<String>> resultMap;
        Set<String> syncStrategyIds;

        if (StringUtils.isNotBlank(syncStrategy)) {
            if (Constants.SyncStrategy.CARE_TEAM.equalsIgnoreCase(syncStrategy)) {
                List<CareTeam> careTeams =
                        practitionerDetails != null
                                        && practitionerDetails.getFhirPractitionerDetails() != null
                                ? practitionerDetails.getFhirPractitionerDetails().getCareTeams()
                                : new ArrayList<>();

                syncStrategyIds =
                        careTeams.stream()
                                .filter(careTeam -> careTeam.getIdElement() != null)
                                .map(careTeam -> careTeam.getIdElement().getIdPart())
                                .collect(Collectors.toSet());

            } else if (Constants.SyncStrategy.ORGANIZATION.equalsIgnoreCase(syncStrategy)) {
                List<Organization> organizations =
                        practitionerDetails != null
                                        && practitionerDetails.getFhirPractitionerDetails() != null
                                ? practitionerDetails
                                        .getFhirPractitionerDetails()
                                        .getOrganizations()
                                : new ArrayList<>();

                syncStrategyIds =
                        organizations.stream()
                                .filter(organization -> organization.getIdElement() != null)
                                .map(organization -> organization.getIdElement().getIdPart())
                                .collect(Collectors.toSet());

            } else if (Constants.SyncStrategy.LOCATION.equalsIgnoreCase(syncStrategy)) {
                syncStrategyIds =
                        practitionerDetails != null
                                        && practitionerDetails.getFhirPractitionerDetails() != null
                                ? PractitionerDetailsEndpointHelper.getAttributedLocations(
                                        practitionerDetails
                                                .getFhirPractitionerDetails()
                                                .getLocationHierarchyList())
                                : new HashSet<>();

            } else if (Constants.SyncStrategy.RELATED_ENTITY_LOCATION.equalsIgnoreCase(
                    syncStrategy)) {

                Map<String, String[]> parameters =
                        new HashMap<>(requestDetailsReader.getParameters());
                String[] syncLocations = parameters.get(Constants.SYNC_LOCATIONS_SEARCH_PARAM);

                if (this.userRoles.contains(Constants.ROLE_ALL_LOCATIONS)
                        && syncLocations != null) {
                    // Selected locations
                    List<String> locationUuids = getLocationUuids(syncLocations);
                    syncStrategyIds =
                            PractitionerDetailsEndpointHelper.getAttributedLocations(
                                    PractitionerDetailsEndpointHelper.getLocationsHierarchy(
                                            locationUuids));

                } else {

                    // Assigned locations
                    syncStrategyIds =
                            practitionerDetails != null
                                            && practitionerDetails.getFhirPractitionerDetails()
                                                    != null
                                    ? PractitionerDetailsEndpointHelper.getAttributedLocations(
                                            PractitionerDetailsEndpointHelper.getLocationsHierarchy(
                                                    practitionerDetails
                                                            .getFhirPractitionerDetails()
                                                            .getLocations()
                                                            .stream()
                                                            .map(
                                                                    location ->
                                                                            location.getIdElement()
                                                                                    .getIdPart())
                                                            .collect(Collectors.toList())))
                                    : new HashSet<>();
                }

            } else
                throw new IllegalStateException(
                        "'" + syncStrategy + "' sync strategy NOT supported!!");

            resultMap =
                    !syncStrategyIds.isEmpty()
                            ? Map.of(syncStrategy, new ArrayList<>(syncStrategyIds))
                            : null;

            if (resultMap == null) {
                throw new IllegalStateException(
                        "No Sync strategy ids found for selected sync strategy " + syncStrategy);
            }

        } else
            throw new IllegalStateException(
                    "App config Sync strategy or Keycloak NOT configured correctly. Please confirm"
                        + " Keycloak the fhir_core_app_id attribute for the user matches the"
                        + " Composition.json config's official identifier value. Additionally"
                        + " confirm the Keycloak fhir_core_app_id user attribute protocol mapper is"
                        + " set up correctly.");

        return resultMap;
    }

    @Named(value = "permission")
    static class Factory implements AccessCheckerFactory {

        @VisibleForTesting static final String REALM_ACCESS_CLAIM = "realm_access";
        @VisibleForTesting static final String ROLES = "roles";
        @VisibleForTesting static final String FHIR_CORE_APPLICATION_ID_CLAIM = "fhir_core_app_id";

        @Override
        public AccessChecker create(
                DecodedJWT jwt,
                HttpFhirClient httpFhirClient,
                FhirContext fhirContext,
                PatientFinder patientFinder)
                throws AuthenticationException {
            List<String> userRoles = JwtUtils.getUserRolesFromJWT(jwt);
            String applicationId = JwtUtils.getApplicationIdFromJWT(jwt);
            return new PermissionAccessChecker(
                    fhirContext,
                    jwt,
                    userRoles,
                    ResourceFinderImp.getInstance(fhirContext),
                    applicationId);
        }
    }
}
