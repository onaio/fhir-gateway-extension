package org.smartregister.fhir.gateway.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServlet;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

public abstract class BaseEndpoint extends HttpServlet {
    public static Bundle createBundle(List<Resource> resourceList) {
        Bundle responseBundle = new Bundle();
        List<Bundle.BundleEntryComponent> bundleEntryComponentList = new ArrayList<>();

        for (Resource resource : resourceList) {
            bundleEntryComponentList.add(new Bundle.BundleEntryComponent().setResource(resource));
        }

        responseBundle.setEntry(bundleEntryComponentList);
        responseBundle.setTotal(bundleEntryComponentList.size());
        return responseBundle;
    }

    public static Bundle createEmptyBundle(String requestURL) {
        Bundle responseBundle = new Bundle();
        responseBundle.setId(UUID.randomUUID().toString());
        Bundle.BundleLinkComponent linkComponent = new Bundle.BundleLinkComponent();
        linkComponent.setRelation(Bundle.LINK_SELF);
        linkComponent.setUrl(requestURL);
        responseBundle.setLink(Collections.singletonList(linkComponent));
        responseBundle.setType(Bundle.BundleType.SEARCHSET);
        responseBundle.setTotal(0);
        return responseBundle;
    }
}
