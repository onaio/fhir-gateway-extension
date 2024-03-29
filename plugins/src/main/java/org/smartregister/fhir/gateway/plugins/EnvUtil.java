package org.smartregister.fhir.gateway.plugins;

public class EnvUtil {

    public static String getEnvironmentVar(String key, String defaultValue) {
        String var = System.getenv(key);
        if (var == null) {
            return defaultValue;
        }
        return var;
    }
}
