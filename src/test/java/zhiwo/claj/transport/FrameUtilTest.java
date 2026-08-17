package zhiwo.claj.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrameUtilTest {

    @Test
    @DisplayName("Test VarInt roundtrip encoding and decoding")
    void testVarIntRoundtrip() throws IOException {
        int[] testValues = {0, 1, 127, 128, 255, 256, 65535, 2097151, Integer.MAX_VALUE};
        for (int value : testValues) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            FrameUtil.writeVarInt(out, value);
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            int decoded = FrameUtil.readVarInt(in);
            assertEquals(value, decoded);
        }
    }

    @Test
    @DisplayName("Test Frame read/write and clean EOF handling")
    void testFrameRoundtripAndEOF() throws IOException {
        byte[] payload = new byte[]{0x01, 0x02, 0x03, (byte) 0xAA, (byte) 0xFF};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FrameUtil.writeFrame(out, payload);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        byte[] read = FrameUtil.readFrame(in);
        assertArrayEquals(payload, read);

        // Subsequent read on exhausted stream should return null (clean EOF)
        assertNull(FrameUtil.readFrame(in));
    }

    @Test
    @DisplayName("Test unexpected EOF inside VarInt or payload")
    void testUnexpectedEOF() {
        // Multi-byte VarInt cut off
        byte[] truncatedVarInt = new byte[]{(byte) 0x80};
        assertThrows(EOFException.class, () -> FrameUtil.readVarInt(new ByteArrayInputStream(truncatedVarInt)));

        // Payload cut off
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            FrameUtil.writeVarInt(out, 10);
            out.write(new byte[]{1, 2, 3}); // only 3 bytes instead of 10
        } catch (IOException ignored) {}

        assertThrows(EOFException.class, () -> FrameUtil.readFrame(new ByteArrayInputStream(out.toByteArray())));
    }

    @Test
    @DisplayName("Test bytesOf with various container types")
    void testBytesOf() {
        byte[] expected = new byte[]{1, 2, 3, 4};
        assertArrayEquals(expected, FrameUtil.bytesOf(expected));
        assertArrayEquals(expected, FrameUtil.bytesOf(ByteBuffer.wrap(expected)));
        assertNull(FrameUtil.bytesOf("unsupported string"));
    }
}
