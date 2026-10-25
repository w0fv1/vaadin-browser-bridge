package dev.w0fv1.browserbridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BrowserEnvironmentStoreTest {
    @TempDir
    Path profileRoot;

    @Test
    void readsNativeProfileWhenManifestIsMissing() {
        BrowserEnvironmentStore store = store();

        assertThat(store.read(BrowserProfileId.DEFAULT))
                .isEqualTo(BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT));
        assertThat(store.profileIds()).contains(BrowserProfileId.DEFAULT);
    }

    @Test
    void savesAndReadsManagedProfileWithoutTemporaryFiles() throws Exception {
        BrowserEnvironmentStore store = store();
        BrowserProfileId profileId = new BrowserProfileId("managed");
        BrowserEnvironmentProfile profile = BrowserEnvironmentProfile.managedProfile(
                profileId,
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("America/Toronto"),
                BrowserColorScheme.DARK,
                1.25,
                Set.of(BrowserPermission.GEOLOCATION, BrowserPermission.CLIPBOARD_WRITE));

        store.save(profile);

        Path profileDirectory = profileRoot.resolve("managed");
        try (Stream<Path> files = Files.list(profileDirectory)) {
            assertThat(files.count()).isOne();
        }
        String json = Files.readString(profileDirectory.resolve("environment.json"));
        assertThat(json).contains("\"MANAGED\"");
        assertThat(store.read(profileId)).isEqualTo(profile);
        assertThat(store.profileIds()).contains(profileId);
    }

    @Test
    void rejectsMalformedManifestAndMismatchedProfileId() throws Exception {
        BrowserEnvironmentStore store = store();
        BrowserProfileId profileId = new BrowserProfileId("broken");
        Path manifest = profileRoot.resolve("broken").resolve("environment.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{");

        assertThatThrownBy(() -> store.read(profileId))
                .isInstanceOf(IllegalArgumentException.class);

        Files.writeString(manifest, """
                {
                  "profileId": "other",
                  "mode": "NATIVE",
                  "locale": null,
                  "timezone": null,
                  "colorScheme": null,
                  "deviceScaleFactor": 0.0,
                  "permissions": []
                }
                """);
        assertThatThrownBy(() -> store.read(profileId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidTimezoneInManifest() throws Exception {
        BrowserEnvironmentStore store = store();
        BrowserProfileId profileId = new BrowserProfileId("bad-zone");
        Path manifest = profileRoot.resolve("bad-zone").resolve("environment.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
                {
                  "profileId": "bad-zone",
                  "mode": "MANAGED",
                  "locale": "en-US",
                  "timezone": "Mars/Base",
                  "colorScheme": "LIGHT",
                  "deviceScaleFactor": 1.0,
                  "permissions": []
                }
                """);

        assertThatThrownBy(() -> store.read(profileId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("时区");
    }

    private BrowserEnvironmentStore store() {
        return new BrowserEnvironmentStore(
                new BrowserRuntimeProperties(BrowserMode.HEADED, BrowserChannel.CHROME, profileRoot, null),
                new ObjectMapper());
    }
}
