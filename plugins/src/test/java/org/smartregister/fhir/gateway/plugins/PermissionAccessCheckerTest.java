package org.smartregister.fhir.gateway.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Enumerations;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.io.Resources;
import com.google.fhir.gateway.PatientFinderImp;
import com.google.fhir.gateway.interfaces.AccessChecker;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

@RunWith(MockitoJUnitRunner.class)
@Ignore
public class PermissionAccessCheckerTest {

    @Mock protected DecodedJWT jwtMock;

    @Mock protected Claim claimMock;

    // TODO consider making a real request object from a URL string to avoid over-mocking.
    @Mock protected RequestDetailsReader requestMock;

    // Note this is an expensive class to instantiate, so we only do this once for all tests.
    protected static final FhirContext fhirContext = FhirContext.forR4();

    void setUpFhirBundle(String filename) throws IOException {
        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);
        URL url = Resources.getResource(filename);
        byte[] obsBytes = Resources.toByteArray(url);
        when(requestMock.loadRequestContents()).thenReturn(obsBytes);
    }

    @Before
    public void setUp() throws IOException {
        when(jwtMock.getClaim(PermissionAccessChecker.Factory.REALM_ACCESS_CLAIM))
                .thenReturn(claimMock);
        when(jwtMock.getClaim(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM))
                .thenReturn(claimMock);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);
    }

    protected AccessChecker getInstance() {
        return new PermissionAccessChecker.Factory()
                .create(jwtMock, null, fhirContext, PatientFinderImp.getInstance(fhirContext));
    }

    @Test
    public void testManagePatientRoleCanAccessGetPatient() throws IOException {
        // Query: GET/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("MANAGE_PATIENT"));
        when(claimMock.asMap()).thenReturn(map);
        when(claimMock.asString()).thenReturn("ecbis-saa");

        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testGetPatientRoleCanAccessGetPatient() throws IOException {
        // Query: GET/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("GET_PATIENT"));
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testGetPatientWithoutRoleCannotAccessGetPatient() throws IOException {
        // Query: GET/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of(""));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.GET);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(false));
    }

    @Test
    public void testDeletePatientRoleCanAccessDeletePatient() throws IOException {
        // Query: DELETE/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("DELETE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.DELETE);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testManagePatientRoleCanAccessDeletePatient() throws IOException {
        // Query: DELETE/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("MANAGE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.DELETE);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testDeletePatientWithoutRoleCannotAccessDeletePatient() throws IOException {
        // Query: DELETE/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of(""));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.DELETE);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(false));
    }

    @Test
    public void testPutWithManagePatientRoleCanAccessPutPatient() throws IOException {
        // Query: PUT/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("MANAGE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getResourceName()).thenReturn("Patient");
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);

        AccessChecker testInstance = getInstance();
        assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
    }

    @Test
    public void testPutPatientWithRoleCanAccessPutPatient() throws IOException {
        // Query: PUT/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("PUT_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getResourceName()).thenReturn("Patient");
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);

        AccessChecker testInstance = getInstance();
        assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
    }

    @Test
    public void testPutPatientWithoutRoleCannotAccessPutPatient() throws IOException {
        // Query: PUT/PID
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of(""));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getResourceName()).thenReturn("Patient");
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.PUT);

        AccessChecker testInstance = getInstance();
        assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
    }

    @Test
    public void testPostPatientWithRoleCanAccessPostPatient() throws IOException {
        // Query: /POST
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("POST_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getResourceName()).thenReturn("Patient");
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(true));
    }

    @Test
    public void testPostPatientWithoutRoleCannotAccessPostPatient() throws IOException {
        // Query: /POST
        setUpFhirBundle("test_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of(""));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);
        when(requestMock.getResourceName()).thenReturn(Enumerations.ResourceType.PATIENT.name());
        when(requestMock.getResourceName()).thenReturn("Patient");
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        assertThat(testInstance.checkAccess(requestMock).canAccess(), equalTo(false));
    }

    @Test
    public void testManageResourceRoleCanAccessBundlePutResources() throws IOException {
        setUpFhirBundle("bundle_transaction_put_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("MANAGE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testPutResourceRoleCanAccessBundlePutResources() throws IOException {
        setUpFhirBundle("bundle_transaction_put_patient.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("PUT_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testDeleteResourceRoleCanAccessBundleDeleteResources() throws IOException {
        setUpFhirBundle("bundle_transaction_delete.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("DELETE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testWithCorrectRolesCanAccessDifferentTypeBundleResources() throws IOException {
        setUpFhirBundle("bundle_transaction_patient_and_non_patients.json");

        Map<String, Object> map = new HashMap<>();
        map.put(
                PermissionAccessChecker.Factory.ROLES,
                Arrays.asList("PUT_PATIENT", "PUT_OBSERVATION", "PUT_ENCOUNTER"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testManageResourcesCanAccessDifferentTypeBundleResources() throws IOException {
        setUpFhirBundle("bundle_transaction_patient_and_non_patients.json");

        Map<String, Object> map = new HashMap<>();
        map.put(
                PermissionAccessChecker.Factory.ROLES,
                Arrays.asList("MANAGE_PATIENT", "MANAGE_OBSERVATION", "MANAGE_ENCOUNTER"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testManageResourcesWithMissingRoleCannotAccessDifferentTypeBundleResources()
            throws IOException {
        setUpFhirBundle("bundle_transaction_patient_and_non_patients.json");

        Map<String, Object> map = new HashMap<>();
        map.put(
                PermissionAccessChecker.Factory.ROLES,
                Arrays.asList("MANAGE_PATIENT", "MANAGE_ENCOUNTER"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        AccessChecker testInstance = getInstance();
        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(false));
    }

    @Test(expected = InvalidRequestException.class)
    public void testBundleResourceNonTransactionTypeThrowsException() throws IOException {
        setUpFhirBundle("bundle_empty.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of());
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        AccessChecker testInstance = getInstance();
        Assert.assertFalse(testInstance.checkAccess(requestMock).canAccess());
    }

    @Test
    public void testAccessGrantedWhenManageResourcePresentForTypeBundleResources()
            throws IOException {
        setUpFhirBundle("test_bundle_transaction.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("MANAGE_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        PermissionAccessChecker testInstance = Mockito.spy((PermissionAccessChecker) getInstance());
        when(testInstance.isDevMode()).thenReturn(true);

        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testAccessGrantedWhenAllRolesPresentForTypeBundleResources() throws IOException {
        setUpFhirBundle("test_bundle_transaction.json");

        Map<String, Object> map = new HashMap<>();
        map.put(
                PermissionAccessChecker.Factory.ROLES,
                Arrays.asList("PUT_PATIENT", "POST_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        PermissionAccessChecker testInstance = Mockito.spy((PermissionAccessChecker) getInstance());
        when(testInstance.isDevMode()).thenReturn(true);

        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(true));
    }

    @Test
    public void testAccessDeniedWhenSingleRoleMissingForTypeBundleResources() throws IOException {
        setUpFhirBundle("test_bundle_transaction.json");

        Map<String, Object> map = new HashMap<>();
        map.put(PermissionAccessChecker.Factory.ROLES, List.of("PUT_PATIENT"));
        map.put(PermissionAccessChecker.Factory.FHIR_CORE_APPLICATION_ID_CLAIM, "ecbis-saa");
        when(claimMock.asMap()).thenReturn(map);

        when(requestMock.getResourceName()).thenReturn(null);
        when(requestMock.getRequestType()).thenReturn(RequestTypeEnum.POST);

        PermissionAccessChecker testInstance = Mockito.spy((PermissionAccessChecker) getInstance());
        when(testInstance.isDevMode()).thenReturn(true);

        boolean canAccess = testInstance.checkAccess(requestMock).canAccess();

        assertThat(canAccess, equalTo(false));
    }

    @Test
    public void testGenerateSyncStrategyIdsCacheKey() {
        String testUserId = "my-test-user-id";
        Map<String, String[]> strategyIdMap =
                Map.of(Constants.SyncStrategy.CARE_TEAM, new String[] {"id-1, id-2,id-3"});
        String cacheKey =
                PermissionAccessChecker.generateSyncStrategyIdsCacheKey(
                        testUserId, Constants.SyncStrategy.CARE_TEAM, strategyIdMap);

        Assert.assertEquals(testUserId, cacheKey);
    }
}
