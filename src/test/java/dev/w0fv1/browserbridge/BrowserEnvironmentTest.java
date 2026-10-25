package dev.w0fv1.browserbridge;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BrowserEnvironmentTest {
    @Test
    void realChromeExposesNormalBrowserSurface() throws Exception {
        Path root = Path.of("build", "environment-profiles").toAbsolutePath();
        Files.createDirectories(root.resolve("default"));
        BrowserRuntimeConfiguration configuration = BrowserRuntimeConfiguration.from(Map.of(
                "browser.bridge.profile-root", root.toString()));
        try (Playwright playwright = Playwright.create()) {
            BrowserContext context = playwright.chromium().launchPersistentContext(
                    configuration.profileDirectory("default"), configuration.launchOptions());
            try {
                Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
                page.route("http://localhost/**", route -> route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                        .setContentType("text/html")
                        .setBody("<html><body>environment</body></html>")));
                page.navigate("http://localhost/environment");
                String userAgent = (String) page.evaluate("navigator.userAgent");
                int pluginCount = ((Number) page.evaluate("navigator.plugins.length")).intValue();
                boolean pdfViewer = (boolean) page.evaluate("navigator.pdfViewerEnabled === true");
                List<?> brands = (List<?>) page.evaluate("navigator.userAgentData.brands.map(item => item.brand)");
                String automationMarkers = (String) page.evaluate("""
                        () => JSON.stringify(Object.getOwnPropertyNames(window)
                          .filter(name => /playwright|puppeteer|selenium|__cdc/i.test(name)))
                        """);

                assertTrue(userAgent.contains("Chrome/") && !userAgent.contains("HeadlessChrome"));
                assertTrue(pluginCount > 0);
                assertTrue(pdfViewer);
                assertTrue(brands.contains("Google Chrome"));
                assertEquals("[]", automationMarkers);
            } finally {
                context.close();
            }
        }
    }
}
