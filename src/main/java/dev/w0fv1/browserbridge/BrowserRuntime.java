package dev.w0fv1.browserbridge;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public final class BrowserRuntime implements AutoCloseable {
    private final BrowserRuntimeProperties properties;
    private final Playwright playwright;
    private final AtomicBoolean closed = new AtomicBoolean();

    public BrowserRuntime(BrowserRuntimeProperties properties) {
        this.properties = properties;
        this.playwright = Playwright.create();
    }

    BrowserContext launch(Path profileDirectory, BrowserEnvironmentProfile environmentProfile) {
        return playwright.chromium().launchPersistentContext(
                profileDirectory, launchOptions(environmentProfile));
    }

    BrowserType.LaunchPersistentContextOptions launchOptions(BrowserEnvironmentProfile environmentProfile) {
        List<String> arguments = new ArrayList<>(List.of("--no-first-run", "--no-default-browser-check"));
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(properties.mode().headless())
                .setChannel(properties.channel().playwrightName())
                .setChromiumSandbox(true);
        options.viewportSize = Optional.empty();
        if (environmentProfile.mode() == BrowserEnvironmentMode.MANAGED) {
            options.setLocale(environmentProfile.locale().toLanguageTag());
            options.setTimezoneId(environmentProfile.timezone().getId());
            options.setColorScheme(environmentProfile.colorScheme());
            arguments.add("--force-device-scale-factor=" + environmentProfile.deviceScaleFactor());
            if (!environmentProfile.permissions().isEmpty()) {
                options.setPermissions(environmentProfile.permissions().stream()
                        .sorted()
                        .map(BrowserPermission::value)
                        .toList());
            }
        }
        options.setArgs(arguments);
        return options;
    }

    @Override
    @PreDestroy
    public void close() {
        if (closed.compareAndSet(false, true)) {
            playwright.close();
        }
    }
}
