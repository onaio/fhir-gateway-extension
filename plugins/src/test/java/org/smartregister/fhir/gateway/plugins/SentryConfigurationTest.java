package org.smartregister.fhir.gateway.plugins;

import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.internal.WhiteboxImpl;

import io.sentry.Sentry;
import io.sentry.SentryOptions;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Sentry.class)
public class SentryConfigurationTest {

    private SentryConfiguration sentryConfiguration;

    @Before
    public void setUp() {
        sentryConfiguration = spy(new SentryConfiguration());
    }

    @Test
    public void testInitializeShouldNotInitializeSentryIfDsnIsEmpty() {
        WhiteboxImpl.setInternalState(sentryConfiguration, "dsn", "");
        sentryConfiguration.initialize();
        verify(sentryConfiguration, never()).initializeSentry();
    }

    @Test
    public void testInitializeShouldInitializeSentryIfDsnIsNotEmpty() {
        PowerMockito.mockStatic(Sentry.class);
        WhiteboxImpl.setInternalState(
                sentryConfiguration, "dsn", "https://examplePublicKey.sdsd.w/0");
        sentryConfiguration.initialize();
        verify(sentryConfiguration, atMost(1)).initializeSentry();
    }

    @Test
    public void testPopulateTagsShouldNotAddTagsIfNotPresent() {
        WhiteboxImpl.setInternalState(sentryConfiguration, "tags", new HashMap<>());
        SentryOptions sentryOptions = mock(SentryOptions.class);
        sentryConfiguration.populateTags(sentryOptions);
        verify(sentryOptions, never()).setTag(anyString(), anyString());
    }

    @Test
    public void testPopulateTagsShouldAddTagsToSentryOptions() {
        String releaseName = "release-name";
        String release = "release-a";
        Map<String, String> map = new HashMap<>();
        map.put(releaseName, release);
        WhiteboxImpl.setInternalState(sentryConfiguration, "tags", map);
        SentryOptions sentryOptions = mock(SentryOptions.class);
        sentryConfiguration.populateTags(sentryOptions);
        verify(sentryOptions, only()).setTag(eq(releaseName), eq(release));
    }
}
