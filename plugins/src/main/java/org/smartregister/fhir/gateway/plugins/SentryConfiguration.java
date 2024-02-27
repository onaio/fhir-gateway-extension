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

    @Value("${sentry.dsn:}")
    static String dsn;

    @Value("${sentry.release:}")
    static String release;

    @Value("${sentry.environment:}")
    static String environment;

    @Value("#{${sentry.tags: {}} ?: {}}")
    static Map<String, String> tags;

    @Value("${sentry.debug: false}")
    static boolean debug;

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
