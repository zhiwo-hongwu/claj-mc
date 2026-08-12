package zhiwo.claj.proxy;


import arc.net.DcReason;
import arc.struct.IntMap;

import com.xpdustry.claj.api.ClajProvider;
import com.xpdustry.claj.api.ClajProxy;
import com.xpdustry.claj.api.net.VirtualConnection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;

import zhiwo.claj.ClajConfig;
import zhiwo.claj.transport.FrameUtil;

/**
 * Host-side CLaJ proxy.
 *
 * <p>Every remote CLaJ player is turned into a real loopback TCP connection to the local
 * integrated Minecraft server, so the server itself needs no modifications: it just sees normal
 * network clients. The mod automatically opens the world to LAN (which starts the TCP listener)
 * when a room is created, and closes it again when the room is closed.
 */
public class MinecraftClajProxy extends ClajProxy {
	private final IntMap<LoopbackBridge> bridges = new IntMap<>(4);
	private volatile int hostPort = -1;
	private volatile boolean autoPublished;

	public MinecraftClajProxy(ClajProvider provider) {
		super(provider);
		// Every fake client has its own loopback connection; the CLaJ broadcast feature is not used.
		broadcastSupported = false;
	}

	/** @return the running integrated server, or {@code null}. */
	public static MinecraftServer getHostServer() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft == null ? null : minecraft.getSingleplayerServer();
	}

	/**
	 * Ensures the integrated server is reachable over TCP (opens to LAN if needed).
	 * Must be called on the client thread. Returns the TCP port.
	 *
	 * @throws IllegalStateException if there is no running world or the LAN port cannot be opened.
	 */
	public int prepareHostServer() {
		MinecraftServer server = getHostServer();
		if (server == null || !server.isRunning()) {
			throw new IllegalStateException("Please enter a world first (single-player)");
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (!(server instanceof IntegratedServer integrated)) {
			throw new IllegalStateException("CLaJ hosting requires the integrated server (single-player world)");
		}
		if (!integrated.isPublished()) {
			if (!minecraft.isSameThread()) {
				throw new IllegalStateException("prepareHostServer must be called on the client thread");
			}
			int port = ClajConfig.data().lanPort > 0 ? ClajConfig.data().lanPort : 25565;
			try {
				if (!integrated.publishServer(MinecraftServer.MultiplayerScope.LAN, port)) {
					throw new IllegalStateException("Failed to open the world to LAN on port " + port);
				}
				autoPublished = true;
				zhiwo.claj.ClajMod.LOGGER.info("CLaJ: opened world to LAN on port {}", port);
			} catch (Exception e) {
				throw new IllegalStateException("Cannot open the world to LAN on port " + port + ": " + e.getMessage(), e);
			}
		}
		hostPort = server.getPort();
		if (hostPort <= 0) {
			throw new IllegalStateException("Cannot determine the server port");
		}
		return hostPort;
	}

	/** Closes the LAN listener again if this mod opened it. Client thread. */
	public void unprepareHostServer() {
		if (!autoPublished) return;
		autoPublished = false;
		MinecraftServer server = getHostServer();
		if (server instanceof IntegratedServer integrated && integrated.isPublished()) {
			try {
				integrated.unpublishServer();
				zhiwo.claj.ClajMod.LOGGER.info("CLaJ: world no longer open to LAN");
			} catch (Exception e) {
				zhiwo.claj.ClajMod.LOGGER.warn("CLaJ: failed to close the LAN listener", e);
			}
		}
	}


	@Override
	protected VirtualConnection conConnected(int conId, long addressHash) {
		if (!roomCreated() || conId == CON_BROADCAST) return null;
		VirtualConnection con = super.conConnected(conId, addressHash);

		int port = hostPort;
		if (port <= 0) {
			closeQuietly(con, DcReason.error);
			zhiwo.claj.ClajMod.LOGGER.warn("CLaJ: integrated server not prepared, rejecting connection {}", conId);
			return con;
		}

		LoopbackBridge bridge = new LoopbackBridge(this, con, port);
		synchronized (bridges) {
			bridges.put(conId, bridge);
		}
		bridge.start();
		return con;
	}

	@Override
	protected VirtualConnection conDisconnected(int conId, DcReason reason) {
		if (!roomCreated()) return null;
		if (conId == CON_BROADCAST) return null;
		closeBridge(conId);
		return super.conDisconnected(conId, reason);
	}

	private void closeBridge(int conId) {
		LoopbackBridge bridge;
		synchronized (bridges) {
			bridge = bridges.get(conId);
			if (bridge != null) bridges.remove(conId);
		}
		if (bridge != null) bridge.close();
	}

	@Override
	protected VirtualConnection conReceived(int conId, Object object) {
		if (!roomCreated()) return null;
		if (conId == CON_BROADCAST) return null;
		LoopbackBridge bridge;
		synchronized (bridges) {
			bridge = bridges.get(conId);
		}
		if (bridge != null) {
			byte[] payload = FrameUtil.bytesOf(object);
			if (payload != null) bridge.write(payload);
		}
		return null;
	}

	@Override
	protected VirtualConnection conIdle(int conId) {
		return null; // idling is handled by the local server itself
	}

	/** Called by a {@link LoopbackBridge} when its local connection is gone. */
	void bridgeClosed(int conId, DcReason reason) {
		synchronized (bridges) {
			bridges.remove(conId);
		}
		VirtualConnection con = getConnection(conId);
		if (con != null && con.isConnected()) {
			close(con, reason); // notify the relay and clean up locally
		}
	}

	@Override
	public void closeAllConnections(DcReason reason) {
		synchronized (bridges) {
			for (LoopbackBridge bridge : bridges.values()) {
				bridge.close();
			}
			bridges.clear();
		}
		super.closeAllConnections(reason);
	}

	@Override
	public void close(DcReason reason) {
		synchronized (bridges) {
			for (LoopbackBridge bridge : bridges.values()) {
				bridge.close();
			}
			bridges.clear();
		}
		super.close(reason);
		// Close the LAN listener on the client thread if we opened it.
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null) {
			minecraft.execute(this::unprepareHostServer);
		}
	}
}
