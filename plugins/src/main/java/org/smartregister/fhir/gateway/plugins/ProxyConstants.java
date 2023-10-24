package org.smartregister.fhir.gateway.plugins;

import ca.uhn.fhir.rest.api.Constants;
import org.apache.http.entity.ContentType;

public class ProxyConstants {

  public static final String CARE_TEAM_TAG_URL = "https://smartregister.org/care-team-tag-id";

  public static final String LOCATION_TAG_URL = "https://smartregister.org/location-tag-id";

  public static final String ORGANISATION_TAG_URL = "https://smartregister.org/organisation-tag-id";

  public static final String TAG_SEARCH_PARAM = "_tag";

  public static final String PARAM_VALUES_SEPARATOR = ",";

  public static final String CODE_URL_VALUE_SEPARATOR = "|";

  public static final String HTTP_URL_SEPARATOR = "/";

  // Note we should not set charset here; otherwise GCP FHIR store complains about Content-Type.
  static final ContentType JSON_PATCH_CONTENT = ContentType.create(Constants.CT_JSON_PATCH);
  public static final String SYNC_STRATEGY = "syncStrategy";
  public static final String REALM_ACCESS = "realm_access";

  public static final String APPLICATION = "application";

  public interface Literals {
    String EQUALS = "=";
  }
}
