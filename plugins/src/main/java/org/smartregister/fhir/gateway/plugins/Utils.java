package org.smartregister.fhir.gateway.plugins;

import java.util.*;

import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

public class Utils {

    public static Bundle addPaginationLinks(
            StringBuilder urlBuilder,
            Bundle resultBundle,
            int page,
            int totalEntries,
            int count,
            Map<String, String[]> parameters) {
        resultBundle.setTotal(totalEntries);

        int nextPage =
                page < ((float) totalEntries / count) ? page + 1 : 0; // 0 indicates no next page
        int prevPage = page > 1 ? page - 1 : 0; // 0 indicates no previous page

        Bundle.BundleLinkComponent selfLink = new Bundle.BundleLinkComponent();
        List<Bundle.BundleLinkComponent> link = new ArrayList<>();
        String selfUrl = constructUpdatedUrl(new StringBuilder(urlBuilder), parameters);
        selfLink.setRelation(IBaseBundle.LINK_SELF);
        selfLink.setUrl(selfUrl);
        link.add(selfLink);
        resultBundle.setLink(link);

        if (nextPage > 0) {
            parameters.put(
                    Constants.PAGINATION_PAGE_NUMBER, new String[] {String.valueOf(nextPage)});
            String nextUrl = constructUpdatedUrl(new StringBuilder(urlBuilder), parameters);
            Bundle.BundleLinkComponent nextLink = new Bundle.BundleLinkComponent();
            nextLink.setRelation(IBaseBundle.LINK_NEXT);
            nextLink.setUrl(nextUrl);
            resultBundle.addLink(nextLink);
        }
        if (prevPage > 0) {
            parameters.put(
                    Constants.PAGINATION_PAGE_NUMBER, new String[] {String.valueOf(prevPage)});
            String prevUrl = constructUpdatedUrl(new StringBuilder(urlBuilder), parameters);
            Bundle.BundleLinkComponent previousLink = new Bundle.BundleLinkComponent();
            previousLink.setRelation(IBaseBundle.LINK_PREV);
            previousLink.setUrl(prevUrl);
            resultBundle.addLink(previousLink);
        }
        return resultBundle;
    }

    private static String constructUpdatedUrl(
            StringBuilder urlBuilder, Map<String, String[]> parameters) {
        urlBuilder.append("?");
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            String paramName = entry.getKey();
            String[] paramValues = entry.getValue();

            for (String paramValue : paramValues) {
                urlBuilder.append(paramName).append("=").append(paramValue).append("&");
            }
        }

        // Remove the trailing '&' if present
        if (urlBuilder.charAt(urlBuilder.length() - 1) == '&') {
            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }

        return urlBuilder.toString();
    }

    /**
     * Creates a Bundle object containing a list of resources. This method set the total number of
     * records in the resourceList
     *
     * @param resourceList The list of resources to include in the Bundle.
     * @return A Bundle object containing the provided resources.
     */
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
