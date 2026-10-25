package dev.w0fv1.browserbridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
final class BrowserSessionLeaseTest {
    @TempDir
    Path profileRoot;

    @Test
    void sharesSessionUntilLastLeaseCloses() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot);

        try (BrowserRuntime runtime = new BrowserRuntime(properties)) {
            BrowserSessionManager manager = new BrowserSessionManager(runtime, properties);
            try {
                BrowserSessionLease first = manager.openDefault("about:blank");
                BrowserSessionLease second = manager.openDefault("about:blank");

                assertThat(second.session().id()).isEqualTo(first.session().id());
                assertThat(first.session().browserInfo().contextOpen()).isTrue();

                first.close();
                assertThat(second.session().browserInfo().contextOpen()).isTrue();

                second.close();
                assertThat(second.session().browserInfo().contextOpen()).isFalse();

                try (BrowserSessionLease reopened = manager.openDefault("about:blank")) {
                    assertThat(reopened.session().id()).isNotEqualTo(second.session().id());
                    assertThat(reopened.session().browserInfo().contextOpen()).isTrue();
                }
            } finally {
                manager.shutdown();
            }
        }
    }
}
