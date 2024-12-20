package org.smartregister.fhir.gateway.plugins;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.IUntypedQuery;

public class UtilsTest {

    private FhirContext fhirContextMock;

    @Before
    public void setUp() {
        fhirContextMock = Mockito.mock(FhirContext.class);
        IGenericClient clientMock = Mockito.mock(IGenericClient.class);

        Mockito.when(fhirContextMock.newRestfulGenericClient(Mockito.anyString()))
                .thenReturn(clientMock);
    }

    @Test
    public void testCreateEmptyBundle() {
        String requestURL = "http://example.com/fhir/Bundle";
        Bundle result = Utils.createEmptyBundle(requestURL);
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getId());
        Assert.assertEquals(0, result.getTotal());
        Assert.assertEquals(Bundle.BundleType.SEARCHSET, result.getType());
        Assert.assertEquals(1, result.getLink().size());
        Assert.assertEquals(Bundle.LINK_SELF, result.getLink().get(0).getRelation());
        Assert.assertEquals(requestURL, result.getLink().get(0).getUrl());
    }

    @Test
    public void testGetBinaryResourceReferenceWithNullComposition() {
        String result = Utils.getBinaryResourceReference(null);

        Assert.assertEquals("", result);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetBinaryResourceReferenceWithEmptySection() {
        Composition composition = new Composition();
        String result = Utils.getBinaryResourceReference(composition);
        Assert.assertEquals("", result);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testGetBinaryResourceReferenceWithNoMatchingSection() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        sectionComponent.setFocus(
                new Reference().setIdentifier(new Identifier().setValue("otherValue")));
        composition.setSection(Arrays.asList(sectionComponent));
        Utils.getBinaryResourceReference(composition);
    }

    @Test
    public void testGetBinaryResourceReferenceWithMatchingSection() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        Identifier identifier = new Identifier();
        identifier.setValue(Constants.AppConfigJsonKey.APPLICATION);
        Reference reference = new Reference();
        reference.setIdentifier(identifier);
        reference.setReference("Binary/1234");
        sectionComponent.setFocus(reference);
        composition.setSection(Arrays.asList(sectionComponent));
        String result = Utils.getBinaryResourceReference(composition);
        Assert.assertEquals("Binary/1234", result);
    }

    @Test
    public void testGetBinaryResourceReferenceWithNullFocus() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        sectionComponent.setFocus(null);
        sectionComponent.setFocus(
                new Reference()
                        .setIdentifier(
                                new Identifier().setValue(Constants.AppConfigJsonKey.APPLICATION)));
        composition.setSection(Arrays.asList(sectionComponent));
        String result = Utils.getBinaryResourceReference(composition);
        Assert.assertNull(result);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testGetBinaryResourceReferenceWithNoIdentifier() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        Reference reference = new Reference();
        reference.setReference("Binary/5678");
        sectionComponent.setFocus(reference);
        sectionComponent.setFocus(new Reference());
        composition.setSection(Arrays.asList(sectionComponent));
        Utils.getBinaryResourceReference(composition);
    }

    @Test
    public void testGetBinaryResourceReferenceWithNoFocusReference() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        sectionComponent.setFocus(
                new Reference()
                        .setIdentifier(
                                new Identifier().setValue(Constants.AppConfigJsonKey.APPLICATION)));
        composition.setSection(Arrays.asList(sectionComponent));
        String result = Utils.getBinaryResourceReference(composition);
        Assert.assertNull(result);
    }

    @Test
    public void testFindSyncStrategyWithNullBinary() {
        String result = Utils.findSyncStrategy((Binary) null);
        Assert.assertEquals(org.smartregister.utils.Constants.EMPTY_STRING, result);
    }

    @Test
    public void testFindSyncStrategyWithEmptySyncStrategyArray() {
        Binary binary = new Binary();
        JsonObject jsonObject = new JsonObject();
        jsonObject.add(Constants.AppConfigJsonKey.SYNC_STRATEGY, new JsonArray());
        String json = jsonObject.toString();
        String encodedJson = Base64.getEncoder().encodeToString(json.getBytes());
        binary.setDataElement(new Base64BinaryType(encodedJson));
        String result = Utils.findSyncStrategy(binary);
        Assert.assertEquals(org.smartregister.utils.Constants.EMPTY_STRING, result);
    }

    @Test
    public void testFindSyncStrategyWithValidSyncStrategy() {
        Binary binary = new Binary();
        JsonObject jsonObject = new JsonObject();
        JsonArray syncStrategyArray = new JsonArray();
        syncStrategyArray.add("PUSH");
        jsonObject.add(Constants.AppConfigJsonKey.SYNC_STRATEGY, syncStrategyArray);
        String json = jsonObject.toString();
        String encodedJson = Base64.getEncoder().encodeToString(json.getBytes());
        binary.setDataElement(new Base64BinaryType(encodedJson));

        String result = Utils.findSyncStrategy(binary);
        Assert.assertEquals("PUSH", result);
    }

    @Test
    public void testFindSyncStrategyWithMultipleSyncStrategies() {
        Binary binary = new Binary();
        JsonObject jsonObject = new JsonObject();
        JsonArray syncStrategyArray = new JsonArray();
        syncStrategyArray.add("PUSH");
        syncStrategyArray.add("PULL");
        jsonObject.add(Constants.AppConfigJsonKey.SYNC_STRATEGY, syncStrategyArray);

        String json = jsonObject.toString();
        String encodedJson = Base64.getEncoder().encodeToString(json.getBytes());
        binary.setDataElement(new Base64BinaryType(encodedJson));

        String result = Utils.findSyncStrategy(binary);
        Assert.assertEquals("PUSH", result);
    }

    @Test
    public void testReadApplicationConfigBinaryResourceWithEmptyResourceId() {
        Binary result = Utils.readApplicationConfigBinaryResource("", fhirContextMock);
        Assert.assertNull(result);
    }

    @Test
    public void testReadApplicationConfigBinaryResourceReturnsBinary() {
        Binary binary = new Binary();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("appId", "test-app");
        jsonObject.addProperty("appTitle", "Test App");

        String json = jsonObject.toString();
        String encodedJson = Base64.getEncoder().encodeToString(json.getBytes());
        binary.setDataElement(new Base64BinaryType(encodedJson));
        binary.setId("test-binary-id");

        IGenericClient client = Mockito.mock(IGenericClient.class);
        Mockito.doReturn(client)
                .when(fhirContextMock)
                .newRestfulGenericClient(ArgumentMatchers.any());

        IUntypedQuery<IBaseBundle> binaryIUntypedQuery = Mockito.mock(IUntypedQuery.class);
        Mockito.doReturn(binaryIUntypedQuery).when(client).search();

        IQuery<IBaseBundle> binaryIQuery = Mockito.mock(IQuery.class);

        Mockito.doReturn(binaryIQuery).when(binaryIUntypedQuery).forResource(Binary.class);

        IQuery<IBaseBundle> iQuery = Mockito.mock(IQuery.class);
        Mockito.doReturn(iQuery).when(binaryIQuery).where(ArgumentMatchers.any(ICriterion.class));

        Bundle bundle = new Bundle();
        bundle.getEntry().add(new Bundle.BundleEntryComponent().setResource(binary));

        Mockito.doReturn(bundle).when(iQuery).execute();

        Binary result =
                Utils.readApplicationConfigBinaryResource("test-binary-id", fhirContextMock);

        Assert.assertNotNull(result);
        Assert.assertEquals("test-binary-id", result.getId());

        byte[] binaryDataByteArray =
                Base64.getDecoder().decode(result.getDataElement().getValueAsString());
        String decodedJson = new String(binaryDataByteArray, StandardCharsets.UTF_8);

        Assert.assertEquals("{\"appId\":\"test-app\",\"appTitle\":\"Test App\"}", decodedJson);
    }
}
