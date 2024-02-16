package org.smartregister.fhir.gateway.plugins;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import org.apache.commons.logging.LogFactory;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(
    value = "SENTRY_ENABLED",
    havingValue = "true",
    matchIfMissing = false)
@Configuration
public class SentryConfiguration {

  @Value("${SENTRY_DSN:}")
  private String dsn;

  @Value("${SENTRY_RELEASE:}")
  private String release;

  @Value("${SENTRY_ENVIRONMENT:}")
  private String environment;

  @Value("#{${SENTRY_TAGS: {}} ?: {}}")
  private Map<String, String> tags;

  @Value("${SENTRY_DEBUG: false}")
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
