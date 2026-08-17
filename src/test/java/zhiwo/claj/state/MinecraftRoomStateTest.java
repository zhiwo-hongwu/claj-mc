package zhiwo.claj.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftRoomStateTest {

    @Test
    @DisplayName("Test encoding and decoding of MinecraftRoomState")
    void testEncodeDecodeRoundtrip() {
        MinecraftRoomState state = new MinecraftRoomState();
        state.name = "My Test World";
        state.motd = "Welcome!";
        state.mode = "Survival";
        state.version = "26.2";
        state.players = 3;
        state.maxPlayers = 8;
        state.port = 25565;

        String encoded = state.encode();
        assertNotNull(encoded);

        MinecraftRoomState decoded = MinecraftRoomState.decode(encoded);
        assertEquals("My Test World", decoded.name);
        assertEquals("Welcome!", decoded.motd);
        assertEquals("Survival", decoded.mode);
        assertEquals("26.2", decoded.version);
        assertEquals(3, decoded.players);
        assertEquals(8, decoded.maxPlayers);
        assertEquals(25565, decoded.port);
    }

    @Test
    @DisplayName("Test decoding invalid or null JSON gracefully")
    void testDecodeInvalid() {
        MinecraftRoomState def1 = MinecraftRoomState.decode(null);
        assertNotNull(def1);
        assertEquals("", def1.name);

        MinecraftRoomState def2 = MinecraftRoomState.decode("invalid json {} []");
        assertNotNull(def2);
    }
}
