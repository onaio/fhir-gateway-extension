package org.smartregister.fhir.gateway.plugins.interfaces;

import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import java.util.List;
import org.smartregister.fhir.gateway.plugins.BundleResources;

public interface ResourceFinder {

  List<BundleResources> findResourcesInBundle(RequestDetailsReader request);
}
