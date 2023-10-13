/*
 * Copyright 2021-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.fhir.gateway.plugins.Constants.CODE_URL_VALUE_SEPARATOR;
import static org.smartregister.fhir.gateway.plugins.Constants.PROXY_TO_ENV;
import static org.smartregister.fhir.gateway.plugins.ProxyConstants.PARAM_VALUES_SEPARATOR;
import static org.smartregister.utils.Constants.*;
import static org.smartregister.utils.Constants.EMPTY_STRING;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.ExceptionUtil;
import com.google.fhir.gateway.FhirClientFactory;
import com.google.fhir.gateway.HttpFhirClient;
import com.google.fhir.gateway.TokenVerifier;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.ParentChildrenMap;
import org.smartregister.model.practitioner.FhirPractitionerDetails;
import org.smartregister.model.practitioner.PractitionerDetails;
import org.springframework.lang.Nullable;

/**
 * This is an example servlet that requires a valid JWT to be present as the Bearer Authorization
 * header. Although it is not a standard FHIR query, but it uses the FHIR server to construct the
 * response. In this example, it inspects the JWT and depending on its claims, constructs the list
 * of Patient IDs that the user has access to.
 *
 * <p>The two types of tokens resemble {@link com.google.fhir.gateway.plugin.ListAccessChecker} and
 * {@link com.google.fhir.gateway.plugin.PatientAccessChecker} expected tokens. But those are just
 * picked as examples and this custom endpoint is independent of any {@link
 * com.google.fhir.gateway.interfaces.AccessChecker}.
 */
@WebServlet("/PractitionerDetail")
public class PractitionerDetailEndpoint extends HttpServlet {

  private static final Logger logger = LoggerFactory.getLogger(PractitionerDetailEndpoint.class);
  private final TokenVerifier tokenVerifier;

  private final HttpFhirClient fhirClient;

  public static final String PRACTITIONER_GROUP_CODE = "405623001";
  public static final String HTTP_SNOMED_INFO_SCT = "http://snomed.info/sct";
  public static final Bundle EMPTY_BUNDLE = new Bundle();

  private FhirContext fhirR4Context = FhirContext.forR4();
  private IGenericClient r4FhirClient =
      fhirR4Context.newRestfulGenericClient(System.getenv(PROXY_TO_ENV));

  private IParser fhirR4JsonParser = fhirR4Context.newJsonParser().setPrettyPrint(true);

  private PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;

  private LocationHierarchyEndpointHelper locationHierarchyEndpointHelper;

  public PractitionerDetailEndpoint() throws IOException {
    this.tokenVerifier = TokenVerifier.createFromEnvVars();
    this.fhirClient = FhirClientFactory.createFhirClientFromEnvVars();
    this.locationHierarchyEndpointHelper = new LocationHierarchyEndpointHelper();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    try {
      String authHeader = request.getHeader("Authorization");
      if (authHeader == null) {
        ExceptionUtil.throwRuntimeExceptionAndLog(
            logger, "No Authorization header provided!", new AuthenticationException());
      }
      List<String> patientIds = new ArrayList<>();
      // Note for a more meaningful HTTP status code, we can catch AuthenticationException in:
      DecodedJWT jwt = tokenVerifier.decodeAndVerifyBearerToken(authHeader);
      String keycloakUuid = request.getParameter("keycloak-uuid");
      //    PractitionerDetails practitionerDetails =
      //        practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId(keycloakUuid);

      PractitionerDetails practitionerDetails = new PractitionerDetails();

      logger.info("Searching for practitioner with identifier: " + keycloakUuid);
      Practitioner practitioner = getPractitionerByIdentifier(keycloakUuid);

      if (practitioner != null) {

        practitionerDetails = getPractitionerDetailsByPractitioner(practitioner);

      } else {
        logger.error("Practitioner with KC identifier: " + keycloakUuid + " not found");
        practitionerDetails.setId(PRACTITIONER_NOT_FOUND);
      }
      String resultContent = fhirR4JsonParser.encodeResourceToString(practitionerDetails);
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

  public Bundle getSupervisorPractitionerDetailsByKeycloakId(String keycloakUuid) {
    Bundle bundle = new Bundle();

    logger.info("Searching for practitioner with identifier: " + keycloakUuid);
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
    PractitionerDetails practitionerDetails = getPractitionerDetailsByPractitioner(practitioner);

    List<CareTeam> careTeamList = practitionerDetails.getFhirPractitionerDetails().getCareTeams();
    // Get other guys.

    List<String> careTeamManagingOrganizationIds =
        getManagingOrganizationsOfCareTeamIds(careTeamList);
    List<String> supervisorCareTeamOrganizationLocationIds =
        getOrganizationAffiliationsByOrganizationIds(careTeamManagingOrganizationIds);
    List<String> officialLocationIds =
        getOfficialLocationIdentifiersByLocationIds(supervisorCareTeamOrganizationLocationIds);
    List<LocationHierarchy> locationHierarchies = getLocationsHierarchy(officialLocationIds);
    List<String> attributedLocationsList = getAttributedLocations(locationHierarchies);
    List<String> attributedOrganizationIds =
        getOrganizationIdsByLocationIds(attributedLocationsList);

    // Get care teams by organization Ids
    List<CareTeam> attributedCareTeams = getCareTeamsByOrganizationIds(attributedOrganizationIds);

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
                              .startsWith(Enumerations.ResourceType.PRACTITIONER.toCode()))
              .map(
                  it ->
                      getPractitionerByIdentifier(
                          getReferenceIDPart(it.getMember().getReference())))
              .collect(Collectors.toList()));
    }

    List<Bundle.BundleEntryComponent> bundleEntryComponentList = new ArrayList<>();

    for (Practitioner attributedPractitioner : attributedPractitioners) {
      bundleEntryComponentList.add(
          new Bundle.BundleEntryComponent()
              .setResource(getPractitionerDetailsByPractitioner(attributedPractitioner)));
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
    List<String> attributedLocationsList =
        parentChildrenList.stream()
            .flatMap(parentChildren -> parentChildren.getChildIdentifiers().stream())
            .map(it -> getReferenceIDPart(it.toString()))
            .collect(Collectors.toList());
    return attributedLocationsList;
  }

  private List<String> getOrganizationIdsByLocationIds(List<String> attributedLocationsList) {
    if (attributedLocationsList == null || attributedLocationsList.isEmpty()) {
      return new ArrayList<>();
    }

    Bundle organizationAffiliationsBundle =
        getFhirClientForR4()
            .search()
            .forResource(OrganizationAffiliation.class)
            .where(OrganizationAffiliation.LOCATION.hasAnyOfIds(attributedLocationsList))
            .returnBundle(Bundle.class)
            .execute();

    return organizationAffiliationsBundle.getEntry().stream()
        .map(
            bundleEntryComponent ->
                getReferenceIDPart(
                    ((OrganizationAffiliation) bundleEntryComponent.getResource())
                        .getOrganization()
                        .getReference()))
        .distinct()
        .collect(Collectors.toList());
  }

  private String getPractitionerIdentifier(Practitioner practitioner) {
    String practitionerId = EMPTY_STRING;
    if (practitioner.getIdElement() != null && practitioner.getIdElement().getIdPart() != null) {
      practitionerId = practitioner.getIdElement().getIdPart();
    }
    return practitionerId;
  }

  private PractitionerDetails getPractitionerDetailsByPractitioner(Practitioner practitioner) {

    PractitionerDetails practitionerDetails = new PractitionerDetails();
    FhirPractitionerDetails fhirPractitionerDetails = new FhirPractitionerDetails();
    String practitionerId = getPractitionerIdentifier(practitioner);

    logger.info("Searching for care teams for practitioner with id: " + practitioner);
    Bundle careTeams = getCareTeams(practitionerId);
    List<CareTeam> careTeamsList = mapBundleToCareTeams(careTeams);
    fhirPractitionerDetails.setCareTeams(careTeamsList);
    fhirPractitionerDetails.setPractitioners(Arrays.asList(practitioner));

    logger.info("Searching for Organizations tied with CareTeams: ");
    List<String> careTeamManagingOrganizationIds =
        getManagingOrganizationsOfCareTeamIds(careTeamsList);

    Bundle careTeamManagingOrganizations = getOrganizationsById(careTeamManagingOrganizationIds);
    logger.info("Managing Organization are fetched");

    List<Organization> managingOrganizationTeams =
        mapBundleToOrganizations(careTeamManagingOrganizations);

    logger.info("Searching for organizations of practitioner with id: " + practitioner);

    List<PractitionerRole> practitionerRoleList =
        getPractitionerRolesByPractitionerId(practitionerId);
    logger.info("Practitioner Roles are fetched");

    List<String> practitionerOrganizationIds =
        getOrganizationIdsByPractitionerRoles(practitionerRoleList);

    Bundle practitionerOrganizations = getOrganizationsById(practitionerOrganizationIds);

    List<Organization> teams = mapBundleToOrganizations(practitionerOrganizations);
    // TODO Fix Distinct
    List<Organization> bothOrganizations =
        Stream.concat(managingOrganizationTeams.stream(), teams.stream())
            .distinct()
            .collect(Collectors.toList());

    fhirPractitionerDetails.setOrganizations(bothOrganizations);
    fhirPractitionerDetails.setPractitionerRoles(practitionerRoleList);

    Bundle groupsBundle = getGroupsAssignedToPractitioner(practitionerId);
    logger.info("Groups are fetched");

    List<Group> groupsList = mapBundleToGroups(groupsBundle);
    fhirPractitionerDetails.setGroups(groupsList);
    fhirPractitionerDetails.setId(practitionerId);

    logger.info("Searching for locations by organizations");

    Bundle organizationAffiliationsBundle =
        getOrganizationAffiliationsByOrganizationIdsBundle(
            Stream.concat(
                    careTeamManagingOrganizationIds.stream(), practitionerOrganizationIds.stream())
                .distinct()
                .collect(Collectors.toList()));

    List<OrganizationAffiliation> organizationAffiliations =
        mapBundleToOrganizationAffiliation(organizationAffiliationsBundle);

    fhirPractitionerDetails.setOrganizationAffiliations(organizationAffiliations);

    List<String> locationIds =
        getLocationIdentifiersByOrganizationAffiliations(organizationAffiliations);

    List<String> locationsIdentifiers =
        getOfficialLocationIdentifiersByLocationIds(
            locationIds); // TODO Investigate why the Location ID and official identifiers are
    // different

    logger.info("Searching for location hierarchy list by locations identifiers");
    List<LocationHierarchy> locationHierarchyList = getLocationsHierarchy(locationsIdentifiers);
    fhirPractitionerDetails.setLocationHierarchyList(locationHierarchyList);

    logger.info("Searching for locations by ids");
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
        .where(Group.CODE.exactly().systemAndCode(HTTP_SNOMED_INFO_SCT, PRACTITIONER_GROUP_CODE))
        .returnBundle(Bundle.class)
        .execute();
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  private List<PractitionerRole> getPractitionerRolesByPractitionerId(String practitionerId) {
    Bundle practitionerRoles = getPractitionerRoles(practitionerId);
    return mapBundleToPractitionerRolesWithOrganization(practitionerRoles);
  }

  private List<String> getOrganizationIdsByPractitionerRoles(
      List<PractitionerRole> practitionerRoles) {
    return practitionerRoles.stream()
        .filter(practitionerRole -> practitionerRole.hasOrganization())
        .map(it -> getReferenceIDPart(it.getOrganization().getReference()))
        .collect(Collectors.toList());
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
                                Enumerations.ResourceType.ORGANIZATION.toCode()
                                    + FORWARD_SLASH
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
    logger.info("Searching for Care Teams with practitioner id :" + practitionerId);

    return getFhirClientForR4()
        .search()
        .forResource(CareTeam.class)
        .where(
            CareTeam.PARTICIPANT.hasId(
                Enumerations.ResourceType.PRACTITIONER.toCode() + FORWARD_SLASH + practitionerId))
        .returnBundle(Bundle.class)
        .execute();
  }

  private Bundle getPractitionerRoles(String practitionerId) {
    logger.info("Searching for Practitioner roles  with practitioner id :" + practitionerId);
    return getFhirClientForR4()
        .search()
        .forResource(PractitionerRole.class)
        .where(PractitionerRole.PRACTITIONER.hasId(practitionerId))
        .returnBundle(Bundle.class)
        .execute();
  }

  private static String getReferenceIDPart(String reference) {
    return reference.substring(reference.indexOf(FORWARD_SLASH) + 1);
  }

  private Bundle getOrganizationsById(List<String> organizationIds) {
    return organizationIds.isEmpty()
        ? EMPTY_BUNDLE
        : getFhirClientForR4()
            .search()
            .forResource(Organization.class)
            .where(new ReferenceClientParam(BaseResource.SP_RES_ID).hasAnyOfIds(organizationIds))
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
            .where(new ReferenceClientParam(BaseResource.SP_RES_ID).hasAnyOfIds(locationIds))
            .returnBundle(Bundle.class)
            .execute();

    return locationsBundle.getEntry().stream()
        .map(bundleEntryComponent -> ((Location) bundleEntryComponent.getResource()))
        .collect(Collectors.toList());
  }

  private @Nullable List<String> getOfficialLocationIdentifiersByLocationIds(
      List<String> locationIds) {
    if (locationIds == null || locationIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<Location> locations = getLocationsByIds(locationIds);

    return locations.stream()
        .map(
            it ->
                it.getIdentifier().stream()
                    .filter(
                        id -> id.hasUse() && id.getUse().equals(Identifier.IdentifierUse.OFFICIAL))
                    .map(it2 -> it2.getValue())
                    .collect(Collectors.toList()))
        .flatMap(it3 -> it3.stream())
        .collect(Collectors.toList());
  }

  private List<String> getOrganizationAffiliationsByOrganizationIds(List<String> organizationIds) {
    if (organizationIds == null || organizationIds.isEmpty()) {
      return new ArrayList<>();
    }
    Bundle organizationAffiliationsBundle =
        getOrganizationAffiliationsByOrganizationIdsBundle(organizationIds);
    List<OrganizationAffiliation> organizationAffiliations =
        mapBundleToOrganizationAffiliation(organizationAffiliationsBundle);
    return getLocationIdentifiersByOrganizationAffiliations(organizationAffiliations);
  }

  private Bundle getOrganizationAffiliationsByOrganizationIdsBundle(List<String> organizationIds) {
    return organizationIds.isEmpty()
        ? EMPTY_BUNDLE
        : getFhirClientForR4()
            .search()
            .forResource(OrganizationAffiliation.class)
            .where(OrganizationAffiliation.PRIMARY_ORGANIZATION.hasAnyOfIds(organizationIds))
            .returnBundle(Bundle.class)
            .execute();
  }

  private List<String> getLocationIdentifiersByOrganizationAffiliations(
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

  private List<String> getManagingOrganizationsOfCareTeamIds(List<CareTeam> careTeamsList) {
    logger.info("Searching for Organizations with care teams list of size:" + careTeamsList.size());
    return careTeamsList.stream()
        .filter(careTeam -> careTeam.hasManagingOrganization())
        .flatMap(it -> it.getManagingOrganization().stream())
        .map(it -> getReferenceIDPart(it.getReference()))
        .collect(Collectors.toList());
  }

  private List<CareTeam> mapBundleToCareTeams(Bundle careTeams) {
    return careTeams.getEntry().stream()
        .map(bundleEntryComponent -> (CareTeam) bundleEntryComponent.getResource())
        .collect(Collectors.toList());
  }

  private List<PractitionerRole> mapBundleToPractitionerRolesWithOrganization(
      Bundle practitionerRoles) {
    return practitionerRoles.getEntry().stream()
        .map(it -> (PractitionerRole) it.getResource())
        .collect(Collectors.toList());
  }

  private List<Group> mapBundleToGroups(Bundle groupsBundle) {
    return groupsBundle.getEntry().stream()
        .map(bundleEntryComponent -> (Group) bundleEntryComponent.getResource())
        .collect(Collectors.toList());
  }

  private List<OrganizationAffiliation> mapBundleToOrganizationAffiliation(
      Bundle organizationAffiliationBundle) {
    return organizationAffiliationBundle.getEntry().stream()
        .map(bundleEntryComponent -> (OrganizationAffiliation) bundleEntryComponent.getResource())
        .collect(Collectors.toList());
  }

  // a

  public static String createSearchTagValues(Map.Entry<String, String[]> entry) {
    return entry.getKey()
        + CODE_URL_VALUE_SEPARATOR
        + StringUtils.join(
            entry.getValue(), PARAM_VALUES_SEPARATOR + entry.getKey() + CODE_URL_VALUE_SEPARATOR);
  }

  private List<LocationHierarchy> getLocationsHierarchy(List<String> locationsIdentifiers) {
    List<LocationHierarchy> locationHierarchyList = new ArrayList<>();
    TokenParam identifier;
    LocationHierarchy locationHierarchy;
    for (String locationsIdentifier : locationsIdentifiers) {
      locationHierarchy = locationHierarchyEndpointHelper.getLocationHierarchy(locationsIdentifier);
      locationHierarchyList.add(locationHierarchy);
    }
    return locationHierarchyList;
  }

  private IGenericClient getFhirClientForR4() {
    return r4FhirClient;
  }
}
