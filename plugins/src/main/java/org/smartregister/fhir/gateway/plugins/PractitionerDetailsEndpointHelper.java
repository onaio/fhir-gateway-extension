package org.smartregister.fhir.gateway.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.BaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Group;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.OrganizationAffiliation;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.ParentChildrenMap;
import org.smartregister.model.practitioner.FhirPractitionerDetails;
import org.smartregister.model.practitioner.PractitionerDetails;
import org.springframework.lang.Nullable;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;

public class PractitionerDetailsEndpointHelper {
    private static final Logger logger =
            LoggerFactory.getLogger(PractitionerDetailsEndpointHelper.class);
    public static final String PRACTITIONER_GROUP_CODE = "405623001";
    public static final String HTTP_SNOMED_INFO_SCT = "http://snomed.info/sct";
    public static final Bundle EMPTY_BUNDLE = new Bundle();
    private final IGenericClient r4FHIRClient;

    private static LocationHierarchyEndpointHelper locationHierarchyEndpointHelper;

    public PractitionerDetailsEndpointHelper(IGenericClient fhirClient) {
        this.r4FHIRClient = fhirClient;
        this.locationHierarchyEndpointHelper = new LocationHierarchyEndpointHelper(r4FHIRClient);
    }

    private IGenericClient getFhirClientForR4() {
        return r4FHIRClient;
    }

    public PractitionerDetails getPractitionerDetailsByKeycloakId(String keycloakUUID) {
        PractitionerDetails practitionerDetails = new PractitionerDetails();

        logger.info("Searching for practitioner with user id: " + keycloakUUID);
        Practitioner practitioner = getPractitionerByIdentifier(keycloakUUID);

        if (practitioner != null) {

            practitionerDetails = getPractitionerDetailsByPractitioner(practitioner);

        } else {
            logger.error("Practitioner with KC identifier: " + keycloakUUID + " not found");
            practitionerDetails.setId(org.smartregister.utils.Constants.PRACTITIONER_NOT_FOUND);
        }

        return practitionerDetails;
    }

    public Bundle getSupervisorPractitionerDetailsByKeycloakId(String keycloakUuid) {
        Bundle bundle = new Bundle();

        logger.info("Searching for practitioner with user id: " + keycloakUuid);
        Practitioner practitioner = getPractitionerByIdentifier(keycloakUuid);

        if (practitioner != null) {

            bundle = getAttributedPractitionerDetailsByPractitioner(practitioner);

        } else {
            logger.error("Practitioner with KC identifier: " + keycloakUuid + " not found");
        }

        return bundle;
    }

    private Bundle getAttributedPractitionerDetailsByPractitioner(Practitioner practitioner) {
        Bundle responseBundle = new Bundle();
        List<Practitioner> attributedPractitioners = new ArrayList<>();
        PractitionerDetails practitionerDetails =
                getPractitionerDetailsByPractitioner(practitioner);

        List<CareTeam> careTeamList =
                practitionerDetails.getFhirPractitionerDetails().getCareTeams();
        // Get other guys.

        Set<String> careTeamManagingOrganizationIds =
                getManagingOrganizationsOfCareTeamIds(careTeamList);
        List<OrganizationAffiliation> organizationAffiliations =
                getOrganizationAffiliationsByOrganizationIds(careTeamManagingOrganizationIds);

        List<String> supervisorCareTeamOrganizationLocationIds =
                getLocationIdsByOrganizationAffiliations(organizationAffiliations);

        List<LocationHierarchy> locationHierarchies =
                getLocationsHierarchy(supervisorCareTeamOrganizationLocationIds);
        List<String> attributedLocationsList = getAttributedLocations(locationHierarchies);
        List<String> attributedOrganizationIds =
                getOrganizationIdsByLocationIds(attributedLocationsList);

        // Get care teams by organization Ids
        List<CareTeam> attributedCareTeams =
                getCareTeamsByOrganizationIds(attributedOrganizationIds);

        for (CareTeam careTeam : careTeamList) {
            attributedCareTeams.removeIf(it -> it.getId().equals(careTeam.getId()));
        }

        careTeamList.addAll(attributedCareTeams);

        for (CareTeam careTeam : careTeamList) {
            // Add current supervisor practitioners
            attributedPractitioners.addAll(
                    careTeam.getParticipant().stream()
                            .filter(
                                    it ->
                                            it.hasMember()
                                                    && it.getMember()
                                                            .getReference()
                                                            .startsWith(
                                                                    Enumerations.ResourceType
                                                                            .PRACTITIONER
                                                                            .toCode()))
                            .map(
                                    it ->
                                            getPractitionerByIdentifier(
                                                    getReferenceIDPart(
                                                            it.getMember().getReference())))
                            .collect(Collectors.toList()));
        }

        List<Bundle.BundleEntryComponent> bundleEntryComponentList = new ArrayList<>();

        for (Practitioner attributedPractitioner : attributedPractitioners) {
            bundleEntryComponentList.add(
                    new Bundle.BundleEntryComponent()
                            .setResource(
                                    getPractitionerDetailsByPractitioner(attributedPractitioner)));
        }

        responseBundle.setEntry(bundleEntryComponentList);
        responseBundle.setTotal(bundleEntryComponentList.size());
        return responseBundle;
    }

    @NotNull
    public static List<String> getAttributedLocations(List<LocationHierarchy> locationHierarchies) {
        List<ParentChildrenMap> parentChildrenList =
                locationHierarchies.stream()
                        .flatMap(
                                locationHierarchy ->
                                        locationHierarchy
                                                .getLocationHierarchyTree()
                                                .getLocationsHierarchy()
                                                .getParentChildren()
                                                .stream())
                        .collect(Collectors.toList());
        return parentChildrenList.stream()
                .flatMap(parentChildren -> parentChildren.getChildIdentifiers().stream())
                .map(it -> getReferenceIDPart(it.toString()))
                .collect(Collectors.toList());
    }

    private List<String> getOrganizationIdsByLocationIds(List<String> attributedLocationsList) {
        if (attributedLocationsList == null || attributedLocationsList.isEmpty()) {
            return new ArrayList<>();
        }

        Bundle organizationAffiliationsBundle =
                getFhirClientForR4()
                        .search()
                        .forResource(OrganizationAffiliation.class)
                        .where(
                                OrganizationAffiliation.LOCATION.hasAnyOfIds(
                                        attributedLocationsList))
                        .returnBundle(Bundle.class)
                        .execute();

        return organizationAffiliationsBundle.getEntry().stream()
                .map(
                        bundleEntryComponent ->
                                getReferenceIDPart(
                                        ((OrganizationAffiliation)
                                                        bundleEntryComponent.getResource())
                                                .getOrganization()
                                                .getReference()))
                .distinct()
                .collect(Collectors.toList());
    }

    private String getPractitionerIdentifier(Practitioner practitioner) {
        String practitionerId = Constants.EMPTY_STRING;
        if (practitioner.getIdElement() != null
                && practitioner.getIdElement().getIdPart() != null) {
            practitionerId = practitioner.getIdElement().getIdPart();
        }
        return practitionerId;
    }

    public PractitionerDetails getPractitionerDetailsByPractitioner(Practitioner practitioner) {
        String practitionerId = getPractitionerIdentifier(practitioner);

        PractitionerDetails practitionerDetails;

        if (CacheHelper.INSTANCE.skipCache()) {
            practitionerDetails =
                    getPractitionerDetailsByPractitionerCore(practitionerId, practitioner);
        } else {
            practitionerDetails =
                    (PractitionerDetails)
                            CacheHelper.INSTANCE.resourceCache.get(
                                    practitionerId,
                                    key ->
                                            getPractitionerDetailsByPractitionerCore(
                                                    practitionerId, practitioner));
        }

        return practitionerDetails;
    }

    public PractitionerDetails getPractitionerDetailsByPractitionerCore(
            String practitionerId, Practitioner practitioner) {

        PractitionerDetails practitionerDetails = new PractitionerDetails();
        FhirPractitionerDetails fhirPractitionerDetails = new FhirPractitionerDetails();

        logger.info("Searching for CareTeams with practitioner id: " + practitionerId);
        Bundle careTeams = getCareTeams(practitionerId);
        List<CareTeam> careTeamsList = mapBundleToCareTeams(careTeams);
        fhirPractitionerDetails.setCareTeams(careTeamsList);
        fhirPractitionerDetails.setPractitioners(Arrays.asList(practitioner));

        logger.info(
                "Searching for Organizations tied to CareTeams list of size: "
                        + careTeamsList.size());
        Set<String> careTeamManagingOrganizationIds =
                getManagingOrganizationsOfCareTeamIds(careTeamsList);

        Bundle careTeamManagingOrganizations =
                getOrganizationsById(careTeamManagingOrganizationIds);
        logger.info(
                "Managing Organizations fetched : "
                        + (careTeamManagingOrganizations != null
                                ? careTeamManagingOrganizations.getTotal()
                                : 0));

        List<Organization> managingOrganizationTeams =
                mapBundleToOrganizations(careTeamManagingOrganizations);

        List<PractitionerRole> practitionerRoleList =
                getPractitionerRolesByPractitionerId(practitionerId);
        logger.info("Practitioner Roles fetched : " + practitionerRoleList.size());

        Set<String> practitionerOrganizationIds =
                getOrganizationIdsByPractitionerRoles(practitionerRoleList);

        Bundle practitionerOrganizations = getOrganizationsById(practitionerOrganizationIds);

        List<Organization> teams = mapBundleToOrganizations(practitionerOrganizations);

        List<Organization> bothOrganizations =
                Stream.concat(managingOrganizationTeams.stream(), teams.stream())
                        .filter(distinctByKey(Organization::getId))
                        .collect(Collectors.toList());

        fhirPractitionerDetails.setOrganizations(bothOrganizations);
        fhirPractitionerDetails.setPractitionerRoles(practitionerRoleList);

        Bundle groupsBundle = getGroupsAssignedToPractitioner(practitionerId);
        logger.info(
                "Practitioner Groups fetched "
                        + (groupsBundle != null ? groupsBundle.getTotal() : 0));

        List<Group> groupsList = mapBundleToGroups(groupsBundle);
        fhirPractitionerDetails.setGroups(groupsList);
        fhirPractitionerDetails.setId(practitionerId);

        Set<String> organizationIds =
                Stream.concat(
                                careTeamManagingOrganizationIds.stream(),
                                practitionerOrganizationIds.stream())
                        .collect(Collectors.toSet());

        logger.info("Searching for locations by organizations : " + organizationIds.size());

        Bundle organizationAffiliationsBundle =
                getOrganizationAffiliationsByOrganizationIdsBundle(organizationIds);

        List<OrganizationAffiliation> organizationAffiliations =
                mapBundleToOrganizationAffiliation(organizationAffiliationsBundle);

        fhirPractitionerDetails.setOrganizationAffiliations(organizationAffiliations);

        List<String> locationIds =
                getLocationIdsByOrganizationAffiliations(organizationAffiliations);

        logger.info("Searching for location hierarchy list by locations identifiers");
        List<LocationHierarchy> locationHierarchyList = getLocationsHierarchy(locationIds);

        fhirPractitionerDetails.setLocationHierarchyList(locationHierarchyList);

        logger.info("Searching for locations by ids : " + locationIds);
        List<Location> locationsList = getLocationsByIds(locationIds);
        fhirPractitionerDetails.setLocations(locationsList);

        practitionerDetails.setId(practitionerId);
        practitionerDetails.setFhirPractitionerDetails(fhirPractitionerDetails);

        return practitionerDetails;
    }

    private List<Organization> mapBundleToOrganizations(Bundle organizationBundle) {
        return organizationBundle.getEntry().stream()
                .map(bundleEntryComponent -> (Organization) bundleEntryComponent.getResource())
                .collect(Collectors.toList());
    }

    private Bundle getGroupsAssignedToPractitioner(String practitionerId) {
        return getFhirClientForR4()
                .search()
                .forResource(Group.class)
                .where(Group.MEMBER.hasId(practitionerId))
                .where(
                        Group.CODE
                                .exactly()
                                .systemAndCode(HTTP_SNOMED_INFO_SCT, PRACTITIONER_GROUP_CODE))
                .returnBundle(Bundle.class)
                .execute();
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> uniqueKeyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(uniqueKeyExtractor.apply(t));
    }

    private List<PractitionerRole> getPractitionerRolesByPractitionerId(String practitionerId) {
        Bundle practitionerRoles = getPractitionerRoles(practitionerId);
        return mapBundleToPractitionerRolesWithOrganization(practitionerRoles);
    }

    private Set<String> getOrganizationIdsByPractitionerRoles(
            List<PractitionerRole> practitionerRoles) {
        return practitionerRoles.stream()
                .filter(practitionerRole -> practitionerRole.hasOrganization())
                .map(it -> getReferenceIDPart(it.getOrganization().getReference()))
                .collect(Collectors.toSet());
    }

    private Practitioner getPractitionerByIdentifier(String identifier) {
        Bundle resultBundle =
                getFhirClientForR4()
                        .search()
                        .forResource(Practitioner.class)
                        .where(Practitioner.IDENTIFIER.exactly().identifier(identifier))
                        .returnBundle(Bundle.class)
                        .execute();

        return resultBundle != null
                ? (Practitioner) resultBundle.getEntryFirstRep().getResource()
                : null;
    }

    private List<CareTeam> getCareTeamsByOrganizationIds(List<String> organizationIds) {
        if (organizationIds.isEmpty()) return new ArrayList<>();

        Bundle bundle =
                getFhirClientForR4()
                        .search()
                        .forResource(CareTeam.class)
                        .where(
                                CareTeam.PARTICIPANT.hasAnyOfIds(
                                        organizationIds.stream()
                                                .map(
                                                        it ->
                                                                Enumerations.ResourceType
                                                                                .ORGANIZATION
                                                                                .toCode()
                                                                        + org.smartregister.utils
                                                                                .Constants
                                                                                .FORWARD_SLASH
                                                                        + it)
                                                .collect(Collectors.toList())))
                        .returnBundle(Bundle.class)
                        .execute();

        return bundle.getEntry().stream()
                .filter(it -> ((CareTeam) it.getResource()).hasManagingOrganization())
                .map(it -> ((CareTeam) it.getResource()))
                .collect(Collectors.toList());
    }

    private Bundle getCareTeams(String practitionerId) {
        return getFhirClientForR4()
                .search()
                .forResource(CareTeam.class)
                .where(
                        CareTeam.PARTICIPANT.hasId(
                                Enumerations.ResourceType.PRACTITIONER.toCode()
                                        + org.smartregister.utils.Constants.FORWARD_SLASH
                                        + practitionerId))
                .returnBundle(Bundle.class)
                .execute();
    }

    private Bundle getPractitionerRoles(String practitionerId) {
        return getFhirClientForR4()
                .search()
                .forResource(PractitionerRole.class)
                .where(PractitionerRole.PRACTITIONER.hasId(practitionerId))
                .returnBundle(Bundle.class)
                .execute();
    }

    private static String getReferenceIDPart(String reference) {
        return reference.substring(
                reference.lastIndexOf(org.smartregister.utils.Constants.FORWARD_SLASH) + 1);
    }

    private Bundle getOrganizationsById(Set<String> organizationIds) {
        return organizationIds.isEmpty()
                ? EMPTY_BUNDLE
                : getFhirClientForR4()
                        .search()
                        .forResource(Organization.class)
                        .where(
                                new ReferenceClientParam(BaseResource.SP_RES_ID)
                                        .hasAnyOfIds(organizationIds))
                        .returnBundle(Bundle.class)
                        .execute();
    }

    private @Nullable List<Location> getLocationsByIds(List<String> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return new ArrayList<>();
        }

        Bundle locationsBundle =
                getFhirClientForR4()
                        .search()
                        .forResource(Location.class)
                        .where(
                                new ReferenceClientParam(BaseResource.SP_RES_ID)
                                        .hasAnyOfIds(locationIds))
                        .returnBundle(Bundle.class)
                        .execute();

        return locationsBundle.getEntry().stream()
                .map(bundleEntryComponent -> ((Location) bundleEntryComponent.getResource()))
                .collect(Collectors.toList());
    }

    private List<OrganizationAffiliation> getOrganizationAffiliationsByOrganizationIds(
            Set<String> organizationIds) {
        if (organizationIds == null || organizationIds.isEmpty()) {
            return new ArrayList<>();
        }
        Bundle organizationAffiliationsBundle =
                getOrganizationAffiliationsByOrganizationIdsBundle(organizationIds);
        return mapBundleToOrganizationAffiliation(organizationAffiliationsBundle);
    }

    private Bundle getOrganizationAffiliationsByOrganizationIdsBundle(Set<String> organizationIds) {
        return organizationIds.isEmpty()
                ? EMPTY_BUNDLE
                : getFhirClientForR4()
                        .search()
                        .forResource(OrganizationAffiliation.class)
                        .where(
                                OrganizationAffiliation.PRIMARY_ORGANIZATION.hasAnyOfIds(
                                        organizationIds))
                        .returnBundle(Bundle.class)
                        .execute();
    }

    private List<String> getLocationIdsByOrganizationAffiliations(
            List<OrganizationAffiliation> organizationAffiliations) {

        return organizationAffiliations.stream()
                .map(
                        organizationAffiliation ->
                                getReferenceIDPart(
                                        organizationAffiliation.getLocation().stream()
                                                .findFirst()
                                                .get()
                                                .getReference()))
                .collect(Collectors.toList());
    }

    private Set<String> getManagingOrganizationsOfCareTeamIds(List<CareTeam> careTeamsList) {
        return careTeamsList.stream()
                .filter(CareTeam::hasManagingOrganization)
                .flatMap(it -> it.getManagingOrganization().stream())
                .map(it -> getReferenceIDPart(it.getReference()))
                .collect(Collectors.toSet());
    }

    private List<CareTeam> mapBundleToCareTeams(Bundle careTeams) {
        return careTeams != null
                ? careTeams.getEntry().stream()
                        .map(bundleEntryComponent -> (CareTeam) bundleEntryComponent.getResource())
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }

    private List<PractitionerRole> mapBundleToPractitionerRolesWithOrganization(
            Bundle practitionerRoles) {
        return practitionerRoles != null
                ? practitionerRoles.getEntry().stream()
                        .map(it -> (PractitionerRole) it.getResource())
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }

    private List<Group> mapBundleToGroups(Bundle groupsBundle) {
        return groupsBundle != null
                ? groupsBundle.getEntry().stream()
                        .map(bundleEntryComponent -> (Group) bundleEntryComponent.getResource())
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }

    private List<OrganizationAffiliation> mapBundleToOrganizationAffiliation(
            Bundle organizationAffiliationBundle) {
        return organizationAffiliationBundle != null
                ? organizationAffiliationBundle.getEntry().stream()
                        .map(
                                bundleEntryComponent ->
                                        (OrganizationAffiliation)
                                                bundleEntryComponent.getResource())
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }

    static List<LocationHierarchy> getLocationsHierarchy(List<String> locationsIdentifiers) {
        List<LocationHierarchy> locationHierarchyList = new ArrayList<>();
        LocationHierarchy locationHierarchy;
        for (String locationsIdentifier : locationsIdentifiers) {
            locationHierarchy =
                    locationHierarchyEndpointHelper.getLocationHierarchy(locationsIdentifier);
            if (!org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND.equals(
                    locationHierarchy.getId())) locationHierarchyList.add(locationHierarchy);
        }
        return locationHierarchyList;
    }

    public static String createSearchTagValues(Map.Entry<String, String[]> entry) {
        return entry.getKey()
                + Constants.CODE_URL_VALUE_SEPARATOR
                + StringUtils.join(
                        entry.getValue(),
                        Constants.PARAM_VALUES_SEPARATOR
                                + entry.getKey()
                                + Constants.CODE_URL_VALUE_SEPARATOR);
    }
}
