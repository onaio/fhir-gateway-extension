package org.smartregister.fhir.gateway.plugins;

import java.util.Map;

import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.google.common.annotations.VisibleForTesting;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import jakarta.annotation.PostConstruct;

@ConditionalOnProperty(
    prefix="sentry",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
@Configuration
public class SentryConfiguration {

    @Value("${sentry.options.dsn:}")
    private String dsn;

    @Value("${sentry.options.release:}")
    private String release;

    @Value("${sentry.options.environment:}")
    private String environment;

    @Value("#{${sentry.options.tags: {}} ?: {}}")
    private Map<String, String> tags;

    @Value("${sentry.options.debug: false}")
    private boolean debug;

    @PostConstruct
    public void initialize() {
        if (dsn != null && !dsn.trim().isEmpty()) {
            initializeSentry();
        }
    }

    @VisibleForTesting
    public void initializeSentry() {
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
    public void populateTags(SentryOptions sentryOptions) {
        try {
            for (Map.Entry<String, String> extraTagsEntry : tags.entrySet()) {
                String key = extraTagsEntry.getKey();
                if (key != null && !key.trim().isEmpty())
                    sentryOptions.setTag(extraTagsEntry.getKey(), extraTagsEntry.getValue());
            }
        } catch (Exception e) {
            LogFactory.getLog(this.getClass()).error(e);
        }
    }
}
