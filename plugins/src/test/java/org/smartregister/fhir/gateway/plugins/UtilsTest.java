package org.smartregister.fhir.gateway.plugins;

import java.util.Arrays;
import java.util.Base64;

import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class UtilsTest {

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
        String result = Utils.findSyncStrategy(null);
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
}
