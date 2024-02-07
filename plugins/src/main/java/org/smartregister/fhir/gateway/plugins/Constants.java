package org.smartregister.fhir.gateway.plugins;

public class Constants {
    public static final String APPLICATION = "application";
    public static final String CARE_TEAM = "CareTeam";
    public static final String CARE_TEAM_TAG_URL_ENV = "CARE_TEAM_TAG_URL";
    public static final String DEFAULT_CARE_TEAM_TAG_URL =
            "https://smartregister.org/care-team-tag-id";
    public static final String CODE_URL_VALUE_SEPARATOR = "|";
    public static final String EMPTY_STRING = "";
    public static final String LOCATION = "Location";
    public static final String LOCATION_TAG_URL_ENV = "LOCATION_TAG_URL";
    public static final String DEFAULT_LOCATION_TAG_URL =
            "https://smartregister.org/location-tag-id";
    public static final String ORGANISATION_TAG_URL_ENV = "ORGANISATION_TAG_URL";
    public static final String DEFAULT_ORGANISATION_TAG_URL =
            "https://smartregister.org/organisation-tag-id";
    public static final String ORGANIZATION = "Organization";
    public static final String PARAM_VALUES_SEPARATOR = ",";
    public static final String PROXY_TO_ENV = "PROXY_TO";
    public static final String SYNC_STRATEGY = "syncStrategy";
    public static final String TAG_SEARCH_PARAM = "_tag";

    public static final String AUTHORIZATION = "Authorization";

    public static final String KEYCLOAK_UUID = "keycloak-uuid";
    public static final String IDENTIFIER = "identifier";

    public static final String PAGINATION_PAGE_SIZE = "_count";

    public static final String PAGINATION_PAGE_NUMBER = "_page";

    public static final int PAGINATION_DEFAULT_PAGE_SIZE = 20;

    public static final int PAGINATION_DEFAULT_PAGE_NUMBER = 1;

    public interface Literals {
        String EQUALS = "=";
    }
}
