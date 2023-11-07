package org.smartregister.fhir.gateway.plugins;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.fhir.gateway.plugins.interfaces.ResourceFinder;
import org.smartregister.model.practitioner.PractitionerDetails;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.fhir.gateway.FhirProxyServer;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.JwtUtil;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.AccessCheckerFactory;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.NoOpAccessDecision;
import com.google.fhir.gateway.interfaces.PatientFinder;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

public class PermissionAccessChecker implements AccessChecker {
    private static final Logger logger = LoggerFactory.getLogger(PermissionAccessChecker.class);
    private final ResourceFinder resourceFinder;
    private final List<String> userRoles;
    private SyncAccessDecision syncAccessDecision;

    private PermissionAccessChecker(
            FhirContext fhirContext,
            String keycloakUUID,
            List<String> userRoles,
            ResourceFinderImp resourceFinder,
            String applicationId,
            String syncStrategy,
            Map<String, List<String>> syncStrategyIds) {
        Preconditions.checkNotNull(userRoles);
        Preconditions.checkNotNull(resourceFinder);
        Preconditions.checkNotNull(applicationId);
        Preconditions.checkNotNull(syncStrategyIds);
        Preconditions.checkNotNull(syncStrategy);
        this.resourceFinder = resourceFinder;
        this.userRoles = userRoles;
        this.syncAccessDecision =
                new SyncAccessDecision(
                        fhirContext,
                        keycloakUUID,
                        applicationId,
                        true,
                        syncStrategyIds,
                        syncStrategy,
                        userRoles);
    }

    @Override
    public AccessDecision checkAccess(RequestDetailsReader requestDetails) {
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

    private boolean checkUserHasRole(String resourceName, String requestType) {
        return checkIfRoleExists(getAdminRoleName(resourceName), this.userRoles)
                || checkIfRoleExists(
                        getRelevantRoleName(resourceName, requestType), this.userRoles);
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

    @Named(value = "permission")
    static class Factory implements AccessCheckerFactory {

        @VisibleForTesting static final String REALM_ACCESS_CLAIM = "realm_access";
        @VisibleForTesting static final String ROLES = "roles";

        @VisibleForTesting static final String FHIR_CORE_APPLICATION_ID_CLAIM = "fhir_core_app_id";

        @VisibleForTesting static final String PROXY_TO_ENV = "PROXY_TO";

        private List<String> getUserRolesFromJWT(DecodedJWT jwt) {
            Claim claim = jwt.getClaim(REALM_ACCESS_CLAIM);
            Map<String, Object> roles = claim.asMap();
            List<String> rolesList = (List) roles.get(ROLES);
            return rolesList;
        }

        private String getApplicationIdFromJWT(DecodedJWT jwt) {
            return JwtUtil.getClaimOrDie(jwt, FHIR_CORE_APPLICATION_ID_CLAIM);
        }

        private IGenericClient createFhirClientForR4(FhirContext fhirContext) {
            String fhirServer = System.getenv(PROXY_TO_ENV);
            IGenericClient client = fhirContext.newRestfulGenericClient(fhirServer);
            return client;
        }

        private Composition readCompositionResource(String applicationId, FhirContext fhirContext) {
            IGenericClient client = createFhirClientForR4(fhirContext);
            Bundle compositionBundle =
                    client.search()
                            .forResource(Composition.class)
                            .where(Composition.IDENTIFIER.exactly().identifier(applicationId))
                            .returnBundle(Bundle.class)
                            .execute();
            List<Bundle.BundleEntryComponent> compositionEntries =
                    compositionBundle != null
                            ? compositionBundle.getEntry()
                            : Collections.singletonList(new Bundle.BundleEntryComponent());
            Bundle.BundleEntryComponent compositionEntry =
                    !compositionEntries.isEmpty() ? compositionEntries.get(0) : null;
            return compositionEntry != null ? (Composition) compositionEntry.getResource() : null;
        }

        private String getBinaryResourceReference(Composition composition) {

            String id = "";
            if (composition != null && composition.getSection() != null) {
                Optional<Integer> firstIndex =
                        composition.getSection().stream()
                                .filter(
                                        v ->
                                                v.getFocus().getIdentifier() != null
                                                        && v.getFocus().getIdentifier().getValue()
                                                                != null
                                                        && v.getFocus()
                                                                .getIdentifier()
                                                                .getValue()
                                                                .equals(Constants.APPLICATION))
                                .map(v -> composition.getSection().indexOf(v))
                                .findFirst();

                Integer result = firstIndex.orElse(-1);
                Composition.SectionComponent sectionComponent =
                        composition.getSection().get(result);
                Reference focus = sectionComponent != null ? sectionComponent.getFocus() : null;
                id = focus != null ? focus.getReference() : null;
            }
            return id;
        }

        private Binary findApplicationConfigBinaryResource(
                String binaryResourceId, FhirContext fhirContext) {
            IGenericClient client = createFhirClientForR4(fhirContext);
            Binary binary = null;
            if (!binaryResourceId.isBlank()) {
                binary = client.read().resource(Binary.class).withId(binaryResourceId).execute();
            }
            return binary;
        }

        private String findSyncStrategy(Binary binary) {

            byte[] bytes =
                    binary != null && binary.getDataElement() != null
                            ? Base64.getDecoder().decode(binary.getDataElement().getValueAsString())
                            : null;
            String syncStrategy = org.smartregister.utils.Constants.EMPTY_STRING;
            if (bytes != null) {
                String json = new String(bytes);
                JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);
                JsonArray jsonArray = jsonObject.getAsJsonArray(Constants.SYNC_STRATEGY);
                if (jsonArray != null && !jsonArray.isEmpty())
                    syncStrategy = jsonArray.get(0).getAsString();
            }

            return syncStrategy;
        }

        Pair<Composition, PractitionerDetails> fetchCompositionAndPractitionerDetails(
                String subject, String applicationId, FhirContext fhirContext) {
            fhirContext.registerCustomType(PractitionerDetails.class);

            IGenericClient client = createFhirClientForR4(fhirContext);

            Bundle requestBundle = new Bundle();
            requestBundle.setType(Bundle.BundleType.BATCH);

            requestBundle.addEntry(
                    SyncAccessDecision.createBundleEntryComponent(
                            Bundle.HTTPVerb.GET, "Composition?identifier=" + applicationId, null));
            requestBundle.addEntry(
                    SyncAccessDecision.createBundleEntryComponent(
                            Bundle.HTTPVerb.GET,
                            "practitioner-details?keycloak-uuid=" + subject,
                            null));

            Bundle responsebundle = client.transaction().withBundle(requestBundle).execute();

            Pair<Composition, PractitionerDetails> result =
                    getCompositionPractitionerDetailsPair(applicationId, responsebundle);

            return result;
        }

        @NotNull
        private static Pair<Composition, PractitionerDetails> getCompositionPractitionerDetailsPair(
                String applicationId, Bundle responsebundle) {
            Composition composition = null;
            PractitionerDetails practitionerDetails = null;

            Bundle innerBundle;
            for (int i = 0; i < responsebundle.getEntry().size(); i++) {

                innerBundle = (Bundle) responsebundle.getEntry().get(i).getResource();
                if (innerBundle == null) continue;

                for (int j = 0; j < innerBundle.getEntry().size(); j++) {

                    if (innerBundle.getEntry().get(j).getResource() instanceof Composition) {
                        composition = (Composition) innerBundle.getEntry().get(j).getResource();
                    } else if (innerBundle.getEntry().get(j).getResource()
                            instanceof PractitionerDetails) {
                        practitionerDetails =
                                (PractitionerDetails) innerBundle.getEntry().get(j).getResource();
                    }
                }
            }

            if (composition == null)
                throw new IllegalStateException(
                        "No Composition resource found for application id '" + applicationId + "'");

            return Pair.of(composition, practitionerDetails);
        }

        Pair<String, PractitionerDetails> fetchSyncStrategyDetails(
                String subject, String applicationId, FhirContext fhirContext) {

            Pair<Composition, PractitionerDetails> compositionPractitionerDetailsPair =
                    fetchCompositionAndPractitionerDetails(subject, applicationId, fhirContext);
            Composition composition = compositionPractitionerDetailsPair.getLeft();
            PractitionerDetails practitionerDetails = compositionPractitionerDetailsPair.getRight();

            String binaryResourceReference = getBinaryResourceReference(composition);
            Binary binary =
                    findApplicationConfigBinaryResource(binaryResourceReference, fhirContext);

            return Pair.of(findSyncStrategy(binary), practitionerDetails);
        }

        @Override
        public AccessChecker create(
                DecodedJWT jwt,
                HttpFhirClient httpFhirClient,
                FhirContext fhirContext,
                PatientFinder patientFinder)
                throws AuthenticationException {

            Map<String, List<String>> syncStrategyIds;
            List<String> userRoles = getUserRolesFromJWT(jwt);
            String applicationId = getApplicationIdFromJWT(jwt);

            if (userRoles != null
                    && userRoles.contains(
                            SyncAccessDecision.SyncAccessDecisionConstants.ROLE_SERVICE_ACCOUNT)) {
                syncStrategyIds = new HashMap<>();
            } else {

                if (skipCache()) {
                    syncStrategyIds =
                            getSyncStrategyIds(jwt.getSubject(), applicationId, fhirContext);
                } else {
                    syncStrategyIds =
                            CacheHelper.INSTANCE.cache.get(
                                    jwt.getSubject(),
                                    k ->
                                            getSyncStrategyIds(
                                                    jwt.getSubject(), applicationId, fhirContext));
                }
            }
            return new PermissionAccessChecker(
                    fhirContext,
                    jwt.getSubject(),
                    userRoles,
                    ResourceFinderImp.getInstance(fhirContext),
                    applicationId,
                    syncStrategyIds != null ? syncStrategyIds.keySet().iterator().next() : "",
                    syncStrategyIds);
        }

        private boolean skipCache() {
            String duration = System.getenv(CacheHelper.OPENSRP_CACHE_EXPIRY_SECONDS);
            return StringUtils.isNotBlank(duration) && "0".equals(duration.trim());
        }

        private Map<String, List<String>> getSyncStrategyIds(
                String subjectId, String applicationId, FhirContext fhirContext) {
            Pair<String, PractitionerDetails> syncStrategyDetails =
                    fetchSyncStrategyDetails(subjectId, applicationId, fhirContext);

            String syncStrategy = syncStrategyDetails.getLeft();
            PractitionerDetails practitionerDetails = syncStrategyDetails.getRight();

            return collateSyncStrategyIds(syncStrategy, practitionerDetails);
        }

        @NotNull
        private static Map<String, List<String>> collateSyncStrategyIds(
                String syncStrategy, PractitionerDetails practitionerDetails) {
            Map<String, List<String>> resultMap = new HashMap<>();
            List<CareTeam> careTeams;
            List<Organization> organizations;
            List<String> careTeamIds;
            List<String> organizationIds;
            List<String> locationIds = new ArrayList<>();
            if (StringUtils.isNotBlank(syncStrategy)) {
                if (Constants.CARE_TEAM.equalsIgnoreCase(syncStrategy)) {
                    careTeams =
                            practitionerDetails != null
                                            && practitionerDetails.getFhirPractitionerDetails()
                                                    != null
                                    ? practitionerDetails
                                            .getFhirPractitionerDetails()
                                            .getCareTeams()
                                    : Collections.singletonList(new CareTeam());

                    careTeamIds =
                            careTeams.stream()
                                    .filter(careTeam -> careTeam.getIdElement() != null)
                                    .map(careTeam -> careTeam.getIdElement().getIdPart())
                                    .collect(Collectors.toList());

                    resultMap = Map.of(syncStrategy, careTeamIds);

                } else if (Constants.ORGANIZATION.equalsIgnoreCase(syncStrategy)) {
                    organizations =
                            practitionerDetails != null
                                            && practitionerDetails.getFhirPractitionerDetails()
                                                    != null
                                    ? practitionerDetails
                                            .getFhirPractitionerDetails()
                                            .getOrganizations()
                                    : Collections.singletonList(new Organization());

                    organizationIds =
                            organizations.stream()
                                    .filter(organization -> organization.getIdElement() != null)
                                    .map(organization -> organization.getIdElement().getIdPart())
                                    .collect(Collectors.toList());

                    resultMap = Map.of(syncStrategy, organizationIds);

                } else if (Constants.LOCATION.equalsIgnoreCase(syncStrategy)) {
                    locationIds =
                            practitionerDetails != null
                                            && practitionerDetails.getFhirPractitionerDetails()
                                                    != null
                                    ? PractitionerDetailsEndpointHelper.getAttributedLocations(
                                            practitionerDetails
                                                    .getFhirPractitionerDetails()
                                                    .getLocationHierarchyList())
                                    : locationIds;

                    resultMap = Map.of(syncStrategy, locationIds);
                }
            } else
                throw new IllegalStateException(
                        "Sync strategy not configured. Please confirm Keycloak fhir_core_app_id"
                            + " attribute for the user matches the Composition.json config official"
                            + " identifier value");

            return resultMap;
        }
    }
}
