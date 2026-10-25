package dev.w0fv1.browserbridge;

import com.microsoft.playwright.options.ColorScheme;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BrowserEnvironmentProfileTest {
    private static final BrowserProfileId PROFILE_ID = new BrowserProfileId("profile-1");

    @Test
    void nativeProfileDoesNotOverrideBrowserEnvironment() {
        BrowserEnvironmentProfile profile = BrowserEnvironmentProfile.nativeProfile(PROFILE_ID);

        assertThat(profile.mode()).isEqualTo(BrowserEnvironmentMode.NATIVE);
        assertThat(profile.locale()).isNull();
        assertThat(profile.timezone()).isNull();
        assertThat(profile.colorScheme()).isNull();
        assertThat(profile.deviceScaleFactor()).isZero();
        assertThat(profile.permissions()).isEmpty();
    }

    @Test
    void rejectsOverridesInNativeProfile() {
        assertThatThrownBy(() -> new BrowserEnvironmentProfile(
                PROFILE_ID,
                BrowserEnvironmentMode.NATIVE,
                Locale.US,
                null,
                null,
                0,
                Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void managedProfileCarriesValidatedEnvironment() {
        BrowserEnvironmentProfile profile = BrowserEnvironmentProfile.managedProfile(
                PROFILE_ID,
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("America/Toronto"),
                ColorScheme.DARK,
                1.5,
                Set.of(BrowserPermission.GEOLOCATION, BrowserPermission.CLIPBOARD_READ));

        assertThat(profile.mode()).isEqualTo(BrowserEnvironmentMode.MANAGED);
        assertThat(profile.locale()).isEqualTo(Locale.forLanguageTag("fr-CA"));
        assertThat(profile.timezone()).isEqualTo(ZoneId.of("America/Toronto"));
        assertThat(profile.colorScheme()).isEqualTo(ColorScheme.DARK);
        assertThat(profile.deviceScaleFactor()).isEqualTo(1.5);
        assertThat(profile.permissions())
                .containsExactlyInAnyOrder(BrowserPermission.GEOLOCATION, BrowserPermission.CLIPBOARD_READ);
    }

    @Test
    void rejectsUnsupportedDeviceScaleFactor() {
        assertThatThrownBy(() -> BrowserEnvironmentProfile.managedProfile(
                PROFILE_ID, Locale.US, ZoneId.of("UTC"), ColorScheme.LIGHT, 0, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrowserEnvironmentProfile.managedProfile(
                PROFILE_ID, Locale.US, ZoneId.of("UTC"), ColorScheme.LIGHT, 4.1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrowserEnvironmentProfile.managedProfile(
                PROFILE_ID, Locale.US, ZoneId.of("UTC"), ColorScheme.LIGHT, Double.NaN, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
