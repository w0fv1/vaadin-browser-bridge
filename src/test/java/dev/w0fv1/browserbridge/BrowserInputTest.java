package dev.w0fv1.browserbridge;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BrowserInputTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesCompletePointerMessage() throws Exception {
        JsonNode input = mapper.readTree("{\"type\":\"up\",\"seq\":17,\"x\":401.5,\"y\":144,\"button\":0,\"buttons\":0,\"pointerId\":1,\"clientTime\":12345}");

        BrowserInput pointer = BrowserInput.parse(input);

        assertEquals("up", pointer.type());
        assertEquals(17, pointer.sequence());
        assertEquals(401.5, pointer.x());
        assertEquals(144, pointer.y());
        assertEquals(0, pointer.button());
        assertEquals(0, pointer.buttons());
        assertEquals(1, pointer.pointerId());
        assertEquals(12345, pointer.clientTimeMillis());
    }

    @Test
    void rejectsPointerMessageWithoutFinalCoordinates() throws Exception {
        JsonNode input = mapper.readTree("{\"type\":\"up\",\"seq\":17,\"button\":0,\"buttons\":0,\"pointerId\":1,\"clientTime\":12345}");

        assertThrows(IllegalArgumentException.class, () -> BrowserInput.parse(input));
    }

    @Test
    void mapsMouseButtonsWithoutAcceptingUnknownValues() throws Exception {
        JsonNode right = mapper.readTree("{\"type\":\"context\",\"seq\":2,\"x\":1,\"y\":2,\"button\":2,\"buttons\":2,\"pointerId\":1,\"clientTime\":12345}");
        JsonNode unknown = mapper.readTree("{\"type\":\"down\",\"seq\":3,\"x\":1,\"y\":2,\"button\":9,\"buttons\":1,\"pointerId\":1,\"clientTime\":12345}");

        assertEquals(com.microsoft.playwright.options.MouseButton.RIGHT, BrowserInput.parse(right).mouseButton());
        assertThrows(IllegalArgumentException.class, () -> BrowserInput.parse(unknown));
    }
}
