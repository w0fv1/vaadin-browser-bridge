package dev.w0fv1.vaadmin.remotebrowser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BrowserProfileIdTest {
    @Test
    void acceptsSafeProfileIdentifier() {
        BrowserProfileId profileId = new BrowserProfileId("account_1");

        assertThat(profileId.value()).isEqualTo("account_1");
        assertThat(profileId.toString()).isEqualTo("account_1");
    }

    @Test
    void rejectsUnsafeProfileIdentifier() {
        assertThatThrownBy(() -> new BrowserProfileId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrowserProfileId(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrowserProfileId(" account "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrowserProfileId("../escape"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrowserProfileId("a".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
