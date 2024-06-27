package org.smartregister.fhir.gateway.plugins;

public class Constants {
    public static final String CARE_TEAM_TAG_URL_ENV = "CARE_TEAM_TAG_URL";
    public static final String DEFAULT_CARE_TEAM_TAG_URL =
            "https://smartregister.org/care-team-tag-id";
    public static final String CODE_URL_VALUE_SEPARATOR = "|";
    public static final String EMPTY_STRING = "";
    public static final String LOCATION_TAG_URL_ENV = "LOCATION_TAG_URL";
    public static final String DEFAULT_LOCATION_TAG_URL =
            "https://smartregister.org/location-tag-id";
    public static final String ORGANISATION_TAG_URL_ENV = "ORGANISATION_TAG_URL";
    public static final String DEFAULT_ORGANISATION_TAG_URL =
            "https://smartregister.org/organisation-tag-id";
    public static final String PARAM_VALUES_SEPARATOR = ",";
    public static final String PROXY_TO_ENV = "PROXY_TO";
    public static final String TAG_SEARCH_PARAM = "_tag";
    public static final String TYPE_SEARCH_PARAM = "type";
    public static final String DEFAULT_ADMIN_LEVEL_TYPE_URL =
            "https://smartregister.org/CodeSystem/administrative-level";
    public static final String AUTHORIZATION = "Authorization";
    public static final String KEYCLOAK_UUID = "keycloak-uuid";
    public static final String IDENTIFIER = "_id";
    public static final String MIN_ADMIN_LEVEL = "administrativeLevelMin";
    public static final String MAX_ADMIN_LEVEL = "administrativeLevelMax";
    public static final String DEFAULT_MAX_ADMIN_LEVEL = "50";
    public static final String PAGINATION_PAGE_SIZE = "_count";
    public static final String PAGINATION_PAGE_NUMBER = "_page";
    public static final int PAGINATION_DEFAULT_PAGE_SIZE = 20;
    public static final int PAGINATION_DEFAULT_PAGE_NUMBER = 1;
    public static final String SYNC_LOCATIONS = "_syncLocations";
    public static final String RELATED_ENTITY_TAG_URL_ENV = "RELATED_ENTITY_TAG_URL";
    public static final String DEFAULT_RELATED_ENTITY_TAG_URL =
            "https://smartregister.org/related-entity-location-tag-id";
    public static final String ROLE_ALL_LOCATIONS = "ALL_LOCATIONS";
    public static final String ROLE_ANDROID_CLIENT = "ANDROID_CLIENT";
    public static final String ROLE_WEB_CLIENT = "WEB_CLIENT";
    public static final String MODE = "mode";
    public static final String LIST = "list";
    public static final String CORS_ALLOW_HEADERS_KEY = "Access-Control-Allow-Headers";
    public static final String CORS_ALLOW_HEADERS_VALUE = "authorization, cache-control";
    public static final String CORS_ALLOW_METHODS_KEY = "Access-Control-Allow-Methods";
    public static final String CORS_ALLOW_METHODS_VALUE = "DELETE,POST,GET,OPTIONS,PUT,PATCH";
    public static final String CORS_ALLOW_ORIGIN_KEY = "Access-Control-Allow-Origin";
    public static final String CORS_ALLOW_ORIGIN_VALUE = "*";
    public static final String CORS_ALLOW_ORIGIN_ENV = "CORS_ALLOW_ORIGIN";
    public static final String UNDERSCORE = "_";
    public static final String[] CLIENT_ROLES = {ROLE_WEB_CLIENT, ROLE_ANDROID_CLIENT};

    public interface Literals {
        String EQUALS = "=";
    }

    public interface AppConfigJsonKey {
        String APPLICATION = "application";
        String SYNC_STRATEGY = "syncStrategy";
    }

    public interface SyncStrategy {
        String LOCATION = "Location";
        String RELATED_ENTITY_LOCATION = "RelatedEntityLocation";
        String ORGANIZATION = "Organization";
        String CARE_TEAM = "CareTeam";
    }

    public interface Header {
        @Deprecated String FHIR_GATEWAY_MODE = "fhir-gateway-mode";
        String MODE = "mode";
    }
}
