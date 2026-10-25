package dev.w0fv1.browserbridge;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
public final class BrowserRuntime implements AutoCloseable {
    private final BrowserRuntimeProperties properties;
    private final Playwright playwright;

    public BrowserRuntime(BrowserRuntimeProperties properties) {
        this.properties = properties;
        this.playwright = Playwright.create();
    }

    BrowserContext launch(Path profileDirectory) {
        return playwright.chromium().launchPersistentContext(profileDirectory, launchOptions());
    }

    BrowserType.LaunchPersistentContextOptions launchOptions() {
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(properties.mode().headless())
                .setChannel(properties.channel().playwrightName())
                .setChromiumSandbox(true)
                .setArgs(List.of("--no-first-run", "--no-default-browser-check"));
        options.viewportSize = Optional.empty();
        return options;
    }

    BrowserRuntimeProperties properties() {
        return properties;
    }

    @Override
    @PreDestroy
    public void close() {
        playwright.close();
    }
}
