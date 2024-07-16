package org.smartregister.fhir.gateway.plugins;

import java.util.List;
import java.util.Map;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;

public class JwtUtils {
    @VisibleForTesting static final String REALM_ACCESS_CLAIM = "realm_access";
    @VisibleForTesting static final String ROLES = "roles";
    @VisibleForTesting static final String FHIR_CORE_APPLICATION_ID_CLAIM = "fhir_core_app_id";

    public static List<String> getUserRolesFromJWT(DecodedJWT jwt) {
        Claim claim = jwt.getClaim(REALM_ACCESS_CLAIM);
        Map<String, Object> roles = claim.asMap();
        return (List<String>) roles.get(ROLES);
    }

    public static String getApplicationIdFromJWT(DecodedJWT jwt) {
        return com.google.fhir.gateway.JwtUtil.getClaimOrDie(jwt, FHIR_CORE_APPLICATION_ID_CLAIM);
    }
}
