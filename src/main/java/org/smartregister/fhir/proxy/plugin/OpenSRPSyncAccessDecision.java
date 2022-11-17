/*
 * Copyright 2021-2022 Ona Systems, Inc
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
package org.smartregister.fhir.proxy.plugin;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;

import com.google.fhir.proxy.ProxyConstants;
import com.google.fhir.proxy.interfaces.AccessDecision;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpResponse;
import org.apache.http.util.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenSRPSyncAccessDecision implements AccessDecision {

	private String applicationId;

	private final List<String> syncStrategy;

	private boolean accessGranted;

	private List<String> careTeamIds;

	private List<String> locationIds;

	private List<String> organizationIds;

	public OpenSRPSyncAccessDecision(String applicationId, boolean accessGranted, List<String> locationIds, List<String> careTeamIds,
			List<String> organizationIds, List<String> syncStrategy) {
		this.applicationId = applicationId;
		this.accessGranted = accessGranted;
		this.careTeamIds = careTeamIds;
		this.locationIds = locationIds;
		this.organizationIds = organizationIds;
		this.syncStrategy = syncStrategy;
	}

	@Override
	public boolean canAccess() {
		return accessGranted;
	}

	@Override
	public void preProcess(ServletRequestDetails servletRequestDetails) {
		// TODO: Disable access for a user who adds tags to organisations, locations or care teams that they do not have access to
		//  This does not bar access to anyone who uses their own sync tags to circumvent
		//  the filter. The aim of this feature based on scoping was to pre-filter the data for the user
		if (isSyncUrl(servletRequestDetails)) {
			// This prevents access to a user who has no location/organisation/team assigned to them
			if (locationIds.size() == 0 && careTeamIds.size() == 0 && organizationIds.size() == 0) {
				locationIds.add(
						"CR1bAeGgaYqIpsNkG0iidfE5WVb5BJV1yltmL4YFp3o6mxj3iJPhKh4k9ROhlyZveFC8298lYzft8SIy8yMNLl5GVWQXNRr1sSeBkP2McfFZjbMYyrxlNFOJgqvtccDKKYSwBiLHq2By5tRupHcmpIIghV7Hp39KgF4iBDNqIGMKhgOIieQwt5BRih5FgnwdHrdlK9ix");
			}
			addSyncFilters(servletRequestDetails, getSyncTags(locationIds, careTeamIds, organizationIds));
		}
	}

	/**
	 * Adds filters to the {@link ServletRequestDetails} for the _tag property to allow filtering by specific code-url-values that match specific locations, teams
	 * or organisations
	 *
	 * @param servletRequestDetails
	 * @param syncTags
	 */
	private void addSyncFilters(ServletRequestDetails servletRequestDetails, Pair<String, Map<String, String[]>> syncTags) {
		List<String> paramValues = new ArrayList<>();

		for (Map.Entry<String, String[]> codeUrlValuesMap : syncTags.getValue().entrySet()) {
			String codeUrl = codeUrlValuesMap.getKey();
			for (String codeValue : codeUrlValuesMap.getValue()) {
				StringBuilder paramValueSb = new StringBuilder(codeUrl.length() + codeValue.length() + 2);
				paramValueSb.append(codeUrl);
				paramValueSb.append(ProxyConstants.CODE_URL_VALUE_SEPARATOR);
				paramValueSb.append(codeValue);
				paramValues.add(paramValueSb.toString());
			}
		}

		String[] prevTagFilters = servletRequestDetails.getParameters().get(ProxyConstants.SEARCH_PARAM_TAG);
		if (prevTagFilters != null && prevTagFilters.length > 1) {
			Collections.addAll(paramValues, prevTagFilters);
		}

		servletRequestDetails.addParameter(ProxyConstants.SEARCH_PARAM_TAG, paramValues.toArray(new String[0]));
	}

	@Override
	public String postProcess(HttpResponse response) throws IOException {
		return null;
	}

	/**
	 * Generates a map of Code.url to multiple Code.Value which contains all the possible
	 * filters that will be used in syncing
	 *
	 * @param locationIds
	 * @param careTeamIds
	 * @param organizationIds
	 * @return Pair of URL to [Code.url, [Code.Value]] map. The URL is complete url
	 */
	private Pair<String, Map<String, String[]>> getSyncTags(List<String> locationIds, List<String> careTeamIds,
			List<String> organizationIds) {
		StringBuilder sb = new StringBuilder();
		Map<String, String[]> map = new HashMap<>();

		sb.append(ProxyConstants.SEARCH_PARAM_TAG);
		sb.append(ProxyConstants.Literals.EQUALS);

		addTags(ProxyConstants.LOCATION_TAG_URL, locationIds, map, sb);
		addTags(ProxyConstants.ORGANISATION_TAG_URL, organizationIds, map, sb);
		addTags(ProxyConstants.CARE_TEAM_TAG_URL, careTeamIds, map, sb);

		return new ImmutablePair<>(sb.toString(), map);
	}

	private void addTags(String tagUrl, List<String> values, Map<String, String[]> map, StringBuilder urlStringBuilder) {
		int len = values.size();
		if (len > 0) {
			if (urlStringBuilder.length() != (ProxyConstants.SEARCH_PARAM_TAG + ProxyConstants.Literals.EQUALS).length()) {
				urlStringBuilder.append(ProxyConstants.PARAM_VALUES_SEPARATOR);
			}

			map.put(tagUrl, values.toArray(new String[0]));

			int i = 0;
			for (String tagValue : values) {
				urlStringBuilder.append(tagUrl);
				urlStringBuilder.append(ProxyConstants.CODE_URL_VALUE_SEPARATOR);
				urlStringBuilder.append(tagValue);

				if (i != len - 1) {
					urlStringBuilder.append(ProxyConstants.PARAM_VALUES_SEPARATOR);
				}
				i++;
			}
		}
	}

	private boolean isSyncUrl(ServletRequestDetails servletRequestDetails) {
		if (servletRequestDetails.getRequestType() == RequestTypeEnum.GET && !TextUtils.isEmpty(
				servletRequestDetails.getResourceName())) {
			String requestPath = servletRequestDetails.getRequestPath();
			return isResourceTypeRequest(requestPath.replace(servletRequestDetails.getFhirServerBase(), ""));
		}

		return false;
	}

	private boolean isResourceTypeRequest(String requestPath) {
		if (!TextUtils.isEmpty(requestPath)) {
			String[] sections = requestPath.split("/");

			return sections.length == 1 || (sections.length == 2 && TextUtils.isEmpty(sections[1]));
		}

		return false;
	}
}