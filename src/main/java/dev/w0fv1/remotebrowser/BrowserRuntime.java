package dev.w0fv1.remotebrowser;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BrowserRuntime {
    private static final Duration LAUNCH_TIMEOUT = Duration.ofSeconds(30);

    private final BrowserRuntimeProperties properties;
    private final ObjectMapper mapper;

    public BrowserRuntime(BrowserRuntimeProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    ChromeBrowser launch(Path profileDirectory, BrowserEnvironmentProfile environment) {
        ChromeProcess process = ChromeProcess.start(
                executable(), arguments(profileDirectory, environment), profileDirectory, LAUNCH_TIMEOUT);
        CdpConnection connection = null;
        try {
            connection = CdpConnection.open(process.browserEndpoint(), mapper);
            ChromeBrowser browser = new ChromeBrowser(process, connection);
            grantPermissions(browser, environment);
            return browser;
        } catch (RuntimeException failure) {
            if (connection != null) connection.close();
            process.close();
            throw failure;
        }
    }

    Path executable() {
        if (properties.executable() != null) return properties.executable();
        return properties.channel().executable();
    }

    List<String> arguments(Path profileDirectory, BrowserEnvironmentProfile environment) {
        List<String> arguments = new ArrayList<>(List.of(
                "--user-data-dir=" + profileDirectory,
                "--remote-debugging-port=0",
                "--remote-allow-origins=*",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-background-timer-throttling",
                "--disable-blink-features=AutomationControlled",
                "--window-size=" + BrowserSession.DEFAULT_VIEWPORT_WIDTH
                        + "," + BrowserSession.DEFAULT_VIEWPORT_HEIGHT));
        if (properties.mode().headless()) arguments.add("--headless=new");
        if (environment.mode() == BrowserEnvironmentMode.MANAGED) {
            arguments.add("--accept-lang=" + acceptLanguage(environment.locale()));
            arguments.add("--force-device-scale-factor=" + environment.deviceScaleFactor());
        }
        return List.copyOf(arguments);
    }

    private static String acceptLanguage(Locale locale) {
        String tag = locale.toLanguageTag();
        String language = locale.getLanguage();
        if (tag.equalsIgnoreCase(language)) return language;
        return tag + "," + language;
    }

    private void grantPermissions(ChromeBrowser browser, BrowserEnvironmentProfile environment) {
        if (environment.permissions().isEmpty()) return;
        ArrayNode permissions = mapper.createArrayNode();
        environment.permissions().stream().sorted().forEach(permission -> permissions.add(permission.cdpName()));
        ObjectNode parameters = mapper.createObjectNode();
        parameters.set("permissions", permissions);
        browser.call("Browser.grantPermissions", parameters);
    }
}
