package dev.w0fv1.browserbridge;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BrowserInputTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesCompletePointerMessage() throws Exception {
        JsonNode input = mapper.readTree("""
                {"type":"up","seq":17,"x":401.5,"y":144,"button":0,"buttons":0,"clickCount":2,"shift":true,"ctrl":false}
                """);

        BrowserInput pointer = BrowserInput.parse(input);

        assertThat(pointer.type()).isEqualTo("up");
        assertThat(pointer.sequence()).isEqualTo(17);
        assertThat(pointer.x()).isEqualTo(401.5);
        assertThat(pointer.y()).isEqualTo(144);
        assertThat(pointer.button()).isEqualTo(BrowserMouseButton.LEFT);
        assertThat(pointer.buttons()).isZero();
        assertThat(pointer.clickCount()).isEqualTo(2);
        assertThat(pointer.modifiers()).containsExactly(BrowserModifier.SHIFT);
    }

    @Test
    void moveCarriesNoButton() throws Exception {
        JsonNode input = mapper.readTree("""
                {"type":"move","seq":1,"x":10,"y":20,"button":0,"buttons":0}
                """);

        BrowserInput pointer = BrowserInput.parse(input);

        assertThat(pointer.button()).isEqualTo(BrowserMouseButton.NONE);
        assertThat(pointer.clickCount()).isEqualTo(1);
        assertThat(pointer.modifiers()).isEmpty();
    }

    @Test
    void rejectsPointerMessageWithoutFinalCoordinates() throws Exception {
        JsonNode input = mapper.readTree("""
                {"type":"up","seq":17,"button":0,"buttons":0}
                """);

        assertThatThrownBy(() -> BrowserInput.parse(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsMouseButtonsWithoutAcceptingUnknownValues() throws Exception {
        JsonNode right = mapper.readTree("""
                {"type":"context","seq":2,"x":1,"y":2,"button":2,"buttons":2}
                """);
        JsonNode unknown = mapper.readTree("""
                {"type":"down","seq":3,"x":1,"y":2,"button":9,"buttons":1}
                """);

        assertThat(BrowserInput.parse(right).button()).isEqualTo(BrowserMouseButton.RIGHT);
        assertThatThrownBy(() -> BrowserInput.parse(unknown)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesKeyMessageWithCodeAndModifiers() throws Exception {
        JsonNode input = mapper.readTree("""
                {"type":"key","key":"A","code":"KeyA","keyCode":65,"location":0,"repeat":false,"shift":true}
                """);

        BrowserKeyInput key = BrowserKeyInput.parse(input);

        assertThat(key.key()).isEqualTo("A");
        assertThat(key.code()).isEqualTo("KeyA");
        assertThat(key.keyCode()).isEqualTo(65);
        assertThat(key.location()).isZero();
        assertThat(key.repeat()).isFalse();
        assertThat(key.modifiers()).isEqualTo(Set.of(BrowserModifier.SHIFT));
        assertThat(key.printable()).isTrue();
        assertThat(key.keypad()).isFalse();
    }

    @Test
    void commandModifiersMakeKeyNonPrintable() throws Exception {
        JsonNode input = mapper.readTree("""
                {"type":"key","key":"a","code":"KeyA","keyCode":65,"ctrl":true}
                """);

        assertThat(BrowserKeyInput.parse(input).printable()).isFalse();
    }

    @Test
    void rejectsKeyMessageWithoutKey() throws Exception {
        JsonNode input = mapper.readTree("""
                {"type":"key","key":"","code":"KeyA","keyCode":65}
                """);

        assertThatThrownBy(() -> BrowserKeyInput.parse(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void combinesModifierBitmask() {
        assertThat(BrowserModifier.mask(Set.of(BrowserModifier.ALT, BrowserModifier.SHIFT))).isEqualTo(9);
        assertThat(BrowserModifier.mask(Set.of())).isZero();
    }
}
