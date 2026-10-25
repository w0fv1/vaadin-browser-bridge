package dev.w0fv1.browserbridge;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BrowserSessionManagerTest {
    @Test
    void createsSessionAndLocksBrowserContentSizeToWebViewportWithoutFlicker() throws Exception {
        String previous = System.getProperty("browser.bridge.profile-root");
        System.setProperty("browser.bridge.profile-root",
                Path.of("build", "manager-profiles").toAbsolutePath().toString());
        BrowserSessionManager manager = new BrowserSessionManager();
        try {
            String html = "data:text/html;charset=utf-8," + java.net.URLEncoder.encode("""
                    <!doctype html><html><body style="margin:0">
                    <script>
                    let resizeCount = 0;
                    window.addEventListener('resize', () => { resizeCount++; });
                    window.addEventListener('pointerdown', e => {
                      document.title = 'click:' + Math.round(e.clientX) + 'x' + Math.round(e.clientY)
                        + '/' + innerWidth + 'x' + innerHeight + ':resizes=' + resizeCount;
                    });
                    </script></body></html>
                    """, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
            BrowserSessionManager.BrowserSession session = manager.create(html);
            session.resizeViewport(1000, 600);
            byte[] firstFrame = session.capture();
            assertNotNull(firstFrame);
            assertTrue(firstFrame.length > 0);
            Thread.sleep(300);

            for (int index = 0; index < 8; index++) {
                byte[] frame = session.capture();
                assertNotNull(frame);
                assertTrue(frame.length > 0);
            }

            session.down(new BrowserInput("down", 1, 500, 300, 0, 1, 1, System.currentTimeMillis()));
            session.up(new BrowserInput("up", 2, 500, 300, 0, 0, 1, System.currentTimeMillis()));
            Thread.sleep(200);
            session.capture();

            BrowserSessionManager.BrowserInfo info = session.browserInfo();
            assertEquals("default", info.profileId());
            assertFalse(info.headless());
            assertEquals("chrome", info.channel());
            assertEquals(1, info.tabCount());
            assertEquals("click:500x300/1000x600:resizes=1", info.activeTitle());
            session.close();
        } finally {
            manager.shutdown();
            if (previous == null) System.clearProperty("browser.bridge.profile-root");
            else System.setProperty("browser.bridge.profile-root", previous);
        }
    }
}
