package org.smartregister.fhir.gateway.plugins;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.google.common.annotations.VisibleForTesting;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import jakarta.annotation.PostConstruct;

@ConditionalOnProperty(
        prefix = "sentry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@Configuration
public class SentryConfiguration {

    static final Logger logger = LoggerFactory.getLogger(SentryConfiguration.class);

    private static String dsn;

    private static String release;
    private static String environment;
    private static Map<String, String> tags;
    private static boolean debug;

    @Value("${sentry.dsn:}")
    public void setDsn(String dsn) {
        SentryConfiguration.dsn = dsn;
    }

    @Value("${sentry.release:}")
    public void setRelease(String release) {
        SentryConfiguration.release = release;
    }

    @Value("${sentry.environment:}")
    public void setEnvironment(String environment) {
        SentryConfiguration.environment = environment;
    }

    @Value("#{${sentry.tags: {}} ?: {}}")
    public void setTags(Map<String, String> tags) {
        SentryConfiguration.tags = tags;
    }

    @Value("${sentry.debug: false}")
    public void setDebug(boolean debug) {
        SentryConfiguration.debug = debug;
    }

    @PostConstruct
    public static void initialize() {
        if (dsn != null && !dsn.trim().isEmpty()) {
            initializeSentry();
        }
    }

    @VisibleForTesting
    public static void initializeSentry() {
        Sentry.init(
                sentryOptions -> {
                    sentryOptions.setDsn(dsn);
                    sentryOptions.setRelease(release);
                    sentryOptions.setEnvironment(environment);
                    sentryOptions.setDebug(debug);
                    populateTags(sentryOptions);
                });
    }

    @VisibleForTesting
    public static void populateTags(SentryOptions sentryOptions) {
        try {
            for (Map.Entry<String, String> extraTagsEntry : tags.entrySet()) {
                String key = extraTagsEntry.getKey();
                if (key != null && !key.trim().isEmpty())
                    sentryOptions.setTag(extraTagsEntry.getKey(), extraTagsEntry.getValue());
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
}
