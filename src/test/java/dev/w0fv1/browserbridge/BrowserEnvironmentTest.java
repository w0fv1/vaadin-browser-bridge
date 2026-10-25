package dev.w0fv1.browserbridge;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ColorScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
final class BrowserEnvironmentTest {
    @TempDir
    Path profileRoot;

    @Test
    void realChromeAppliesManagedEnvironmentConsistently() throws Exception {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot);
        BrowserEnvironmentStore environments = new BrowserEnvironmentStore(properties, new ObjectMapper());
        BrowserEnvironmentProfile environment = BrowserEnvironmentProfile.managedProfile(
                new BrowserProfileId("managed"),
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("Asia/Tokyo"),
                ColorScheme.DARK,
                1.5,
                Set.of(BrowserPermission.GEOLOCATION));
        environments.save(environment);

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            BrowserContext context = runtime.launch(
                    profileRoot.resolve("managed"), environment);
            try {
                Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
                page.route("http://localhost/**", route -> route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                        .setContentType("text/html")
                        .setBody("<html><body>environment</body></html>")));
                page.navigate("http://localhost/environment");
                String userAgent = (String) page.evaluate("navigator.userAgent");
                String surfaceJson = (String) page.evaluate("""
                        async () => {
                          const permission = await navigator.permissions.query({name: 'geolocation'});
                          return JSON.stringify({
                            language: navigator.language,
                            timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                            deviceScaleFactor: window.devicePixelRatio,
                            dark: matchMedia('(prefers-color-scheme: dark)').matches,
                            geolocation: permission.state
                          });
                        }
                        """);
                JsonNode surface = new ObjectMapper().readTree(surfaceJson);

                assertThat(userAgent).contains("Chrome/");
                assertThat(surface.path("language").asString()).isEqualTo("fr-CA");
                assertThat(surface.path("timezone").asString()).isEqualTo("Asia/Tokyo");
                assertThat(surface.path("deviceScaleFactor").asDouble()).isEqualTo(1.5);
                assertThat(surface.path("dark").asBoolean()).isTrue();
                assertThat(surface.path("geolocation").asString()).isEqualTo("granted");
            } finally {
                context.close();
            }
        }
    }
}
