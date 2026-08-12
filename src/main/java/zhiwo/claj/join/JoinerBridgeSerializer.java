package zhiwo.claj.join;

import java.nio.ByteBuffer;

import arc.net.ArcNetException;
import arc.net.FrameworkMessage;
import arc.net.NetSerializer;
import arc.util.Threads;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.net.FrameworkSerializer;
import com.xpdustry.claj.common.packets.Packet;
import com.xpdustry.claj.common.packets.RawPacket;

/**
 * Serializer for the joiner's relay connection.
 *
 * <p>The relay forwards the host server's raw Minecraft bytes to the joiner without any CLaJ
 * envelope, and never sends CLaJ control packets to a joiner in a room (room closure is signalled
 * by the TCP disconnect). Therefore EVERY message except ArcNet framework messages ({@code -2})
 * is treated as raw Minecraft bytes - including data that happens to start with the CLaJ prefix
 * bytes {@code -3}/{@code -4}. Framework messages are only parsed when their exact known length matches, so an
 * arbitrary byte stream cannot be misinterpreted and silently swallowed/disconnected.
 */
public class JoinerBridgeSerializer implements NetSerializer, FrameworkSerializer {
	protected final ThreadLocal<ByteBufferOutput> write = Threads.local(ByteBufferOutput::new);
	protected final ThreadLocal<RawPacket> raw = Threads.local(RawPacket::new);

	@Override
	public Object read(ByteBuffer buffer) {
		if (!buffer.hasRemaining()) return null;
		int start = buffer.position();
		byte id = buffer.get();
		try {
			return switch (id) {
				// Keep framework messages (keep-alive / ping / registration) - ArcNet needs them.
				// Only try when the remaining length matches a known framework message
				// (Ping=6, RegisterTCP/UDP=5, KeepAlive/DiscoverHost=1 after the id byte),
				// otherwise a Minecraft chunk starting with 0xFE would be misinterpreted.
				case ClajNet.frameworkId -> {
					if (buffer.remaining() == 6 || buffer.remaining() == 5 || buffer.remaining() == 1) {
						yield readFramework(buffer);
					} else {
						buffer.position(buffer.position() - 1);
						yield raw.get().read(buffer);
					}
				}
				default -> {
					// Everything else is raw Minecraft bytes (including -3/-4 prefixed data).
					buffer.position(buffer.position() - 1);
					yield raw.get().read(buffer);
				}
			};
		} catch (Throwable t) {
			// A framework parse failed - treat the whole message as raw bytes.
			buffer.position(start);
			return raw.get().read(buffer);
		}
	}

	@Override
	public void write(ByteBuffer buffer, Object object) {
		switch (object) {
			case ByteBuffer buf -> buffer.put(buf);
			case FrameworkMessage framework -> writeFramework(buffer.put(ClajNet.frameworkId), framework);
			case RawPacket rawPacket -> buffer.put(rawPacket.data());
			default -> throw new ArcNetException("Unknown packet type: " + object.getClass());
		}
	}

}
