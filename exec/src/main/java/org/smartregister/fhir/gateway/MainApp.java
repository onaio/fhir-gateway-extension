package org.smartregister.fhir.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * This class shows the minimum that is required to create a FHIR Gateway with all AccessChecker
 * plugins defined in "com.google.fhir.gateway.plugin" and "org.smartregister.fhir.gateway.plugins.
 */
@SpringBootApplication(
        scanBasePackages = {"org.smartregister.fhir.gateway", "com.google.fhir.gateway.plugin"})
@ServletComponentScan({"org.smartregister.fhir.gateway.plugins", "com.google.fhir.gateway"})
public class MainApp {

    public static void main(String[] args) {
        SpringApplication.run(MainApp.class, args);
    }
}
