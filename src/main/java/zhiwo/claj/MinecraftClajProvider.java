package zhiwo.claj;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import arc.net.NetListener;
import arc.util.Log;
import arc.util.Threads;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.api.ClajPinger;
import com.xpdustry.claj.api.ClajProvider;
import com.xpdustry.claj.api.ClajProxy;
import com.xpdustry.claj.common.packets.ConnectionPayloadPacket;
import com.xpdustry.claj.common.packets.RawPacket;
import com.xpdustry.claj.common.status.ClajType;
import com.xpdustry.claj.common.status.ClajVersion;
import com.xpdustry.claj.common.status.MessageType;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import zhiwo.claj.state.MinecraftRoomState;

/**
 * Minecraft implementation of the CLaJ provider.
 *
 * <p>The transport is a raw byte bridge: Minecraft protocol packets are forwarded as opaque
 * chunks between the CLaJ relay and either the local integrated server (host side) or the local
 * Minecraft client (joiner side). No Minecraft protocol parsing happens inside CLaJ.</p>
 */
public class MinecraftClajProvider implements ClajProvider {
	private static final ThreadLocal<RawPacket> WRAPPER_RAW = Threads.local(RawPacket::new);

	/** CLaJ implementation type, must match between hosts and joiners. */
	public static final ClajType IMPL_TYPE = ClajType.of("Minecraft");
	/** CLaJ protocol version. Major version must match the relay server. */
	public static final ClajVersion VERSION = ClajVersion.of(2, 4, 3);

	private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "Claj Worker");
		t.setDaemon(true);
		return t;
	});

	@Override
	public void postTask(Runnable task) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			task.run();
		} else if (minecraft.isSameThread()) {
			task.run();
		} else {
			minecraft.execute(task);
		}
	}

	@Override
	public ExecutorService getExecutor() {
		return executor;
	}

	@Override
	public ClajProxy newProxy() {
		return new zhiwo.claj.proxy.MinecraftClajProxy(this);
	}

	@Override
	public ClajPinger newPinger() {
		return new ClajPinger(this);
	}

	@Override
	public void handleProxyError(ClajProxy proxy, Throwable error) {
		Log.err("Error while hosting the CLaJ room", error);
	}

	@Override
	public void handlePingerError(ClajPinger pinger, Throwable error) {
		Log.err("Error while running a CLaJ pinger", error);
	}

	@Override
	public ClajType getType() {
		return IMPL_TYPE;
	}

	@Override
	public ClajVersion getVersion() {
		return VERSION;
	}

	@Override
	public NetListener getConnectionListener(ClajProxy proxy) {
		return null; // Events are not dispatched to a server: the loopback bridge speaks to the socket directly.
	}

	@Override
	public ByteBuffer writeRoomState(ClajProxy proxy) {
		MinecraftRoomState state = MinecraftRoomState.current();
		if (state == null) return null;
		byte[] data = state.encode().getBytes(StandardCharsets.UTF_8);
		return ByteBuffer.wrap(data);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T readRoomState(long roomId, ClajType type, ByteBuffer buff) {
		if (!IMPL_TYPE.equals(type) || buff == null) return null;
		byte[] data = new byte[buff.remaining()];
		buff.get(data);
		try {
			return (T) MinecraftRoomState.decode(new String(data, StandardCharsets.UTF_8));
		} catch (Exception e) {
			Log.err("Failed to decode CLaJ room state", e);
			return null;
		}
	}

	@Override
	public void connectClient(String host, int port, Runnable success, ByteBuffer joinPacket) {
		// Runs on the pinger executor; hop to the client thread and start the local bridge.
		postTask(() -> zhiwo.claj.join.MinecraftClajJoiner.startJoin(host, port, joinPacket, success));
	}

	@Override
	public ConnectionPayloadPacket.Serializer getPacketWrapperSerializer() {
		return new ConnectionPayloadPacket.Serializer() {
			@Override
			public void read(ConnectionPayloadPacket packet, ByteBufferInput read) {
				packet.object = WRAPPER_RAW.get().read(read.buffer);
			}

			@Override
			public void write(ConnectionPayloadPacket packet, ByteBufferOutput write) {
				if (packet.object instanceof RawPacket raw) {
					RawPacket.write(raw.data(), write);
				} else if (packet.object instanceof ByteBuffer buffer) {
					RawPacket.write(buffer, write);
				} else {
					throw new IllegalArgumentException("Unsupported wrapped object: " + packet.object);
				}
			}
		};
	}

	@Override
	public void showTextMessage(ClajProxy proxy, String text) {
		zhiwo.claj.util.MinecraftChat.sendHostMessage(text);
	}

	@Override
	public void showMessage(ClajProxy proxy, MessageType message) {
		zhiwo.claj.util.MinecraftChat.sendHostMessage("CLaJ: " + Component.translatable("claj.message." + message.name()).getString());
	}

	@Override
	public void showPopup(ClajProxy proxy, String text) {
		zhiwo.claj.util.MinecraftChat.sendHostMessage(text);
	}
}
