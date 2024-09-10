package org.smartregister.fhir.gateway.plugins;

import org.springframework.web.client.RestTemplate;

public class RestTemplateUtil {
    private volatile RestTemplate restTemplate;

    public RestTemplate getRestTemplate() {
        RestTemplate localReferenceRestTemplate = restTemplate;
        if (localReferenceRestTemplate == null) {
            synchronized (this) {
                localReferenceRestTemplate = restTemplate;
                if (localReferenceRestTemplate == null) {
                    restTemplate = localReferenceRestTemplate = new RestTemplate();
                }
            }
        }
        return localReferenceRestTemplate;
    }
}
