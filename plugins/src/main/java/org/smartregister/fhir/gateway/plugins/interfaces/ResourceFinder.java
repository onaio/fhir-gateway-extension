package org.smartregister.fhir.gateway.plugins.interfaces;

import java.util.List;

import org.smartregister.fhir.gateway.plugins.model.BundleResources;

import com.google.fhir.gateway.interfaces.RequestDetailsReader;

public interface ResourceFinder {
    List<BundleResources> findResourcesInBundle(RequestDetailsReader request);
}
