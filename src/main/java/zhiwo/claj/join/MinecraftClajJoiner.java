package zhiwo.claj.join;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

import com.xpdustry.claj.api.Claj;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import zhiwo.claj.ClajMod;
import zhiwo.claj.transport.FrameUtil;

/**
 * Joiner side. Redirects the local Minecraft client to a loopback listener socket and bridges the
 * raw byte stream to the CLaJ relay through a {@link RelayClient}.
 */
public final class MinecraftClajJoiner {
	private static volatile MinecraftClajJoiner current;

	private final String host;
	private final int port;
	private final ByteBuffer joinPacket;
	private Runnable success;
	private volatile boolean done;
	private volatile boolean joined;
	private volatile int localPort = -1;
	private volatile ServerSocket listener;
	private volatile RelayClient relay;
	private volatile Socket clientSocket;

	private MinecraftClajJoiner(String host, int port, ByteBuffer joinPacket, Runnable success) {
		this.host = host;
		this.port = port;
		this.joinPacket = joinPacket;
		this.success = success;
	}

	/** Entry point, invoked from {@code MinecraftClajProvider#connectClient} on the client thread. */
	public static void startJoin(String host, int port, ByteBuffer joinPacket, Runnable success) {
		stopCurrent();
		MinecraftClajJoiner joiner = new MinecraftClajJoiner(host, port, joinPacket, success);
		current = joiner;
		try {
			joiner.start();
		} catch (Exception e) {
			// Failure must NOT invoke the success callback (H4): the UI would briefly show
			// "joined" and close the screen. The ConnectScreen shows the native failure instead.
			ClajMod.LOGGER.warn("CLaJ: failed to start join", e);
			joiner.close();
		}
	}

	public static void stopCurrent() {
		MinecraftClajJoiner joiner = current;
		if (joiner != null) joiner.close();
		current = null;
	}

	private void start() throws Exception {
		listener = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
		localPort = listener.getLocalPort();
		ClajMod.LOGGER.info("CLaJ: local listener ready on 127.0.0.1:{}", localPort);

		relay = new RelayClient(joinPacket, this::onRoomClose);
		Thread relayThread = new Thread(() -> {
			try {
				// ArcNet requires the update thread to run before/during connect.
				relay.start();
				relay.connect(5000, host, port, port);
			} catch (Exception e) {
				ClajMod.LOGGER.warn("CLaJ: relay connection failed", e);
				close();
			}
		}, "Claj Relay Client");
		relayThread.setDaemon(true);
		relayThread.start();

		Thread acceptThread = new Thread(this::acceptLoop, "Claj Accept");
		acceptThread.setDaemon(true);
		acceptThread.start();

		Minecraft minecraft = Minecraft.getInstance();
		String addressStr = "127.0.0.1:" + localPort;
		ServerData serverData = new ServerData("CLaJ", addressStr, ServerData.Type.OTHER);
		ServerAddress address = ServerAddress.parseString(addressStr);
		// NOTE: transferState MUST be null - a non-null TransferState makes the client perform a
		// transfer handshake, which a normal server rejects ("server does not accept transfers").
		ConnectScreen.startConnecting(minecraft.gui.screen(), minecraft, address, serverData, false, null);
	}

	private void acceptLoop() {
		try {
			// Loop so a reconnecting client (login retry, resource pack reload...) is accepted.
			while (!done) {
				Socket socket = listener.accept();
				socket.setTcpNoDelay(true);
				clientSocket = socket;
				pump(socket); // blocks until this connection closes, then accept the next one
			}
		} catch (IOException e) {
			if (!done) ClajMod.LOGGER.warn("CLaJ: local accept failed", e);
		} finally {
			close();
		}
	}

	private void pump(Socket socket) {
		Thread writer = null;
		try {
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();

			writer = new Thread(() -> {
				try {
					while (!done) {
						byte[] payload = relay.takeLocalFrame();
						if (payload == null) break;
						// Raw byte stream: the host's bridge sends unframed chunks, so no
						// length prefix is added here (Minecraft parses frames from the stream).
						out.write(payload);
						out.flush();
					}
				} catch (IOException | InterruptedException ignored) {
				} finally {
					try {
						socket.close();
					} catch (IOException ignored) {}
				}
			}, "Claj Local Writer");
			writer.setDaemon(true);
			writer.start();

			while (!done) {
				byte[] payload = FrameUtil.readFrame(in);
				if (payload == null) break;
				if (!relay.offerFrame(payload)) {
					ClajMod.LOGGER.warn("CLaJ: too many frames before relay ready, aborting");
					break;
				}
			}
		} catch (IOException e) {
			// the local client disconnected
		} finally {
			// Keep the relay alive for a reconnect (e.g. resource-pack reload); only tear it
			// down when the whole join session is being closed. On a plain client disconnect,
			// drop any stale queued frames so a reconnect does not replay them.
			if (done) relay.closeAll();
			else relay.clearLocalQueue();
			// Close the socket and interrupt the writer so it cannot linger on a dead socket
			// across reconnects (M2).
			try {
				socket.close();
			} catch (IOException ignored) {}
			if (writer != null) writer.interrupt();
		}
	}

	private void onRoomClose() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.getConnection() != null) {
			minecraft.execute(() -> minecraft.getConnection().getConnection()
					.disconnect(Component.translatable("claj.join.room-closed")));
		}
	}

	/** Called from {@code ClientPlayConnectionEvents.JOIN} when the local client enters the world. */
	public static void onClientJoined(ClientPacketListener handler) {
		MinecraftClajJoiner joiner = current;
		if (joiner == null || joiner.done || joiner.joined) return;
		if (!joiner.isOurConnection(handler)) return;

		joiner.joined = true;
		Runnable success = joiner.success;
		joiner.success = null;
		if (success != null) success.run();

		// The pinger pool is no longer needed while playing.
		if (Claj.initialized()) Claj.get().stopPingers();
		ClajMod.LOGGER.info("CLaJ: successfully joined the room");
	}

	/** Called from {@code ClientPlayConnectionEvents.DISCONNECT} when the local play connection ends. */
	public static void onClientDisconnected(ClientPacketListener handler) {
		MinecraftClajJoiner joiner = current;
		if (joiner == null) return;
		if (!joiner.isOurConnection(handler)) return;
		boolean wasJoined = joiner.joined;
		joiner.close();
		if (!wasJoined) {
			ClajMod.LOGGER.info("CLaJ: failed to join the room (disconnected before play state)");
		} else {
			ClajMod.LOGGER.info("CLaJ: left the room");
		}
	}

	private boolean isOurConnection(ClientPacketListener handler) {
		if (localPort <= 0) return false;
		if (handler == null || handler.getConnection() == null) return false;
		if (!(handler.getConnection().getRemoteAddress() instanceof InetSocketAddress address)) return false;
		return address.getAddress() != null
				&& address.getAddress().isLoopbackAddress()
				&& address.getPort() == localPort;
	}

	private void close() {
		done = true;
		try {
			if (listener != null) listener.close();
		} catch (IOException ignored) {}
		try {
			if (clientSocket != null) clientSocket.close();
		} catch (IOException ignored) {}
		if (relay != null) relay.closeAll();
		if (current == this) current = null;
	}
}
