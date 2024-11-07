package org.smartregister.fhir.gateway.plugins;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.impl.GenericClient;

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

    public static IGenericClient createFhirClientForR4(FhirContext fhirContext) {
        String fhirServer = System.getenv(Constants.PROXY_TO_ENV);
        return fhirContext.newRestfulGenericClient(fhirServer);
    }

    public static String getBinaryResourceReference(Composition composition) {

        String id = "";
        if (composition != null && composition.getSection() != null) {
            Optional<Integer> firstIndex =
                    composition.getSection().stream()
                            .filter(
                                    sectionComponent ->
                                            sectionComponent.getFocus().getIdentifier() != null
                                                    && sectionComponent
                                                                    .getFocus()
                                                                    .getIdentifier()
                                                                    .getValue()
                                                            != null
                                                    && sectionComponent
                                                            .getFocus()
                                                            .getIdentifier()
                                                            .getValue()
                                                            .equals(
                                                                    Constants.AppConfigJsonKey
                                                                            .APPLICATION))
                            .map(
                                    sectionComponent ->
                                            composition.getSection().indexOf(sectionComponent))
                            .findFirst();

            Integer result = firstIndex.orElse(-1);
            Composition.SectionComponent sectionComponent = composition.getSection().get(result);
            Reference focus = sectionComponent != null ? sectionComponent.getFocus() : null;
            id = focus != null ? focus.getReference() : null;
        }
        return id;
    }

    public static Binary readApplicationConfigBinaryResource(
            String binaryResourceId, FhirContext fhirContext) {
        IGenericClient client = Utils.createFhirClientForR4(fhirContext);
        Binary binary = null;
        if (!binaryResourceId.isBlank()) {
            binary = client.read().resource(Binary.class).withId(binaryResourceId).execute();
        }
        return binary;
    }

    public static String findSyncStrategy(Binary binary) {

        byte[] bytes =
                binary != null && binary.getDataElement() != null
                        ? Base64.getDecoder().decode(binary.getDataElement().getValueAsString())
                        : null;
        String syncStrategy = org.smartregister.utils.Constants.EMPTY_STRING;
        if (bytes != null) {
            String json = new String(bytes);
            JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);
            JsonArray jsonArray =
                    jsonObject.getAsJsonArray(Constants.AppConfigJsonKey.SYNC_STRATEGY);
            if (jsonArray != null && !jsonArray.isEmpty())
                syncStrategy = jsonArray.get(0).getAsString();
        }

        return syncStrategy;
    }

    public static String generateHash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes());
        return Hex.encodeHexString(hashBytes);
    }

    /**
     * This is a recursive function which updates the result bundle with results of all pages
     * whenever there's an entry for Bundle.LINK_NEXT
     *
     * @param fhirClient the Generic FHIR Client instance
     * @param resultBundle the result bundle from the first request
     */
    public static void fetchAllBundlePagesAndInject(
            IGenericClient fhirClient, Bundle resultBundle) {

        if (resultBundle.getLink(Bundle.LINK_NEXT) != null) {

            cleanUpServerBaseUrl((GenericClient) fhirClient, resultBundle);

            Bundle pageResultBundle = fhirClient.loadPage().next(resultBundle).execute();

            resultBundle.getEntry().addAll(pageResultBundle.getEntry());
            resultBundle.setLink(pageResultBundle.getLink());

            fetchAllBundlePagesAndInject(fhirClient, resultBundle);
        }

        resultBundle.setLink(
                resultBundle.getLink().stream()
                        .filter(
                                bundleLinkComponent ->
                                        !Bundle.LINK_NEXT.equals(bundleLinkComponent.getRelation()))
                        .collect(Collectors.toList()));
        resultBundle.getMeta().setLastUpdated(resultBundle.getMeta().getLastUpdated());
    }

    public static void fetchAllBundlePagesAndInject(
            IGenericClient fhirClient, Bundle resultBundle, int records) {

        if (resultBundle.getLink(Bundle.LINK_NEXT) != null
                && resultBundle.getEntry().size() < records) {

            cleanUpServerBaseUrl((GenericClient) fhirClient, resultBundle);

            Bundle pageResultBundle = fhirClient.loadPage().next(resultBundle).execute();

            //  int lastPageIndex = 0;
            //    String lastLocationFilter = resultBundle.getLink(Bundle.LINK_NEXT);

            if (records < pageResultBundle.getEntry().size()) {
                resultBundle.getEntry().addAll(pageResultBundle.getEntry());
            } else if (records == pageResultBundle.getEntry().size()) {
                resultBundle.getEntry().addAll(pageResultBundle.getEntry());
            } else {
                resultBundle.getEntry().addAll(pageResultBundle.getEntry().subList(0, records));
                //  lastPageIndex = records;
            }

            resultBundle.setLink(pageResultBundle.getLink());

            fetchAllBundlePagesAndInject(fhirClient, resultBundle);
        }

        resultBundle.setLink(
                resultBundle.getLink().stream()
                        .filter(
                                bundleLinkComponent ->
                                        !Bundle.LINK_NEXT.equals(bundleLinkComponent.getRelation()))
                        .collect(Collectors.toList()));
        resultBundle.getMeta().setLastUpdated(resultBundle.getMeta().getLastUpdated());
    }

    public static void cleanUpServerBaseUrl(GenericClient fhirClient, Bundle resultBundle) {
        String cleanUrl =
                cleanBaseUrl(
                        resultBundle.getLink(Bundle.LINK_NEXT).getUrl(), fhirClient.getUrlBase());
        resultBundle
                .getLink()
                .replaceAll(
                        bundleLinkComponent ->
                                Bundle.LINK_NEXT.equals(bundleLinkComponent.getRelation())
                                        ? new Bundle.BundleLinkComponent(
                                                new StringType(Bundle.LINK_NEXT),
                                                new UriType(cleanUrl))
                                        : bundleLinkComponent);
    }

    public static String cleanBaseUrl(String originalUrl, String fhirServerBaseUrl) {
        return originalUrl.indexOf('?') > -1
                ? fhirServerBaseUrl + originalUrl.substring(originalUrl.indexOf('?'))
                : fhirServerBaseUrl;
    }

    public static String replaceQueryParamValue(String url, String queryParam, String newValue) {
        // Find the start of the query string
        int questionMarkIndex = url.indexOf("?");
        if (questionMarkIndex == -1) {
            return url; // No query parameters to replace
        }

        String baseUrl = url.substring(0, questionMarkIndex);
        String queryString = url.substring(questionMarkIndex + 1);

        // Use StringBuilder to manipulate the query string
        StringBuilder queryBuilder = new StringBuilder(queryString);
        String paramToReplace = queryParam + "=";

        // Find the index of the parameter to replace
        int paramIndex = queryBuilder.indexOf(paramToReplace);
        if (paramIndex != -1) {
            // Calculate the start and end positions for replacement
            int valueStartIndex = paramIndex + paramToReplace.length();
            int valueEndIndex = valueStartIndex;

            // Find the end of the current value
            while (valueEndIndex < queryBuilder.length()
                    && queryBuilder.charAt(valueEndIndex) != '&') {
                valueEndIndex++;
            }

            // Replace the old value with the new value
            queryBuilder.replace(valueStartIndex, valueEndIndex, newValue);
        }

        // Return the full URL
        return baseUrl + "?" + queryBuilder.toString();
    }
}
