package org.smartregister.fhir.gateway.plugins;

public class Constants {
    public static final String APPLICATION = "application";
    public static final String CARE_TEAM = "CareTeam";
    public static final String CARE_TEAM_TAG_URL = "https://smartregister.org/care-team-tag-id";
    public static final String CODE_URL_VALUE_SEPARATOR = "|";
    public static final String EMPTY_STRING = "";
    public static final String LOCATION = "Location";
    public static final String LOCATION_TAG_URL = "https://smartregister.org/location-tag-id";
    public static final String ORGANISATION_TAG_URL =
            "https://smartregister.org/organisation-tag-id";
    public static final String ORGANIZATION = "Organization";
    public static final String PARAM_VALUES_SEPARATOR = ",";
    public static final String PROXY_TO_ENV = "PROXY_TO";
    public static final String SYNC_STRATEGY = "syncStrategy";
    public static final String TAG_SEARCH_PARAM = "_tag";

    public static final String AUTHORIZATION = "Authorization";

    public static final String KEYCLOAK_UUID = "keycloak-uuid";
    public static final String IDENTIFIER = "identifier";

    public interface Literals {
        String EQUALS = "=";
    }
}
