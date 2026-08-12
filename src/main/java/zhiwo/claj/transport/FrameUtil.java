package zhiwo.claj.transport;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.xpdustry.claj.common.packets.RawPacket;

/**
 * Minecraft protocol framing helpers.
 *
 * <p>A Minecraft TCP packet on the wire is: {@code VarInt(length) + payload}. The CLaJ relay only
 * accepts ArcNet-framed messages of limited size, so we strip the length prefix and transport the
 * raw payload (which starts with the packet id VarInt, always {@code < 0x80} for serverbound ids)
 * inside a single ArcNet message. The length prefix is re-added on the other side before the bytes
 * are written to the real Minecraft socket.
 */
public final class FrameUtil {
	/** Safety limit for a single Minecraft frame payload. Serverbound frames never exceed this. */
	public static final int MAX_FRAME = 24 * 1024;

	private FrameUtil() {}

	/** Reads a {@code VarInt} from the stream. */
	public static int readVarInt(InputStream in) throws IOException {
		int value = 0;
		int position = 0;
		int current;
		while (true) {
			current = in.read();
			if (current == -1) throw new EOFException("Unexpected end of stream while reading VarInt");
			value |= (current & 0x7F) << position;
			if ((current & 0x80) == 0) break;
			position += 7;
			if (position >= 32) throw new IOException("VarInt is too big");
		}
		return value;
	}

	/** Writes a {@code VarInt} to the stream. */
	public static void writeVarInt(OutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.write(value);
	}

	/**
	 * Reads one Minecraft frame from the stream and returns its payload (length prefix stripped).
	 *
	 * @return the frame payload, or {@code null} on EOF at a frame boundary
	 */
	public static byte[] readFrame(InputStream in) throws IOException {
		int length = readVarInt(in);
		if (length < 0 || length > MAX_FRAME) throw new IOException("Invalid Minecraft frame length: " + length);
		byte[] payload = new byte[length];
		readFully(in, payload);
		return payload;
	}

	/** Writes {@code payload} as a full Minecraft frame (with length prefix). */
	public static void writeFrame(OutputStream out, byte[] payload) throws IOException {
		writeVarInt(out, payload.length);
		out.write(payload);
		out.flush();
	}

	public static void readFully(InputStream in, byte[] data) throws IOException {
		int read = 0;
		while (read < data.length) {
			int n = in.read(data, read, data.length - read);
			if (n == -1) throw new EOFException("Unexpected end of stream");
			read += n;
		}
	}

	/** Extracts a defensive copy of the raw bytes from a wrapped CLaJ object. */
	public static byte[] bytesOf(Object object) {
		if (object instanceof RawPacket raw) {
			ByteBuffer data = raw.data();
			byte[] out = new byte[data.remaining()];
			data.duplicate().get(out);
			return out;
		}
		if (object instanceof ByteBuffer buffer) {
			byte[] out = new byte[buffer.remaining()];
			buffer.duplicate().get(out);
			return out;
		}
		if (object instanceof byte[] bytes) {
			return bytes.clone();
		}
		return null;
	}
}
