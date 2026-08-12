package zhiwo.claj.join;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

import arc.net.Client;
import arc.net.DcReason;

import com.xpdustry.claj.common.ClajPackets.Connect;
import com.xpdustry.claj.common.ClajPackets.Disconnect;
import com.xpdustry.claj.common.net.ClientReceiver;
import com.xpdustry.claj.common.net.NetListenerFilter;
import com.xpdustry.claj.common.packets.RawPacket;

import zhiwo.claj.transport.FrameUtil;

/**
 * The joiner's persistent ArcNet connection to the CLaJ relay.
 *
 * <p>After connecting, the CLaJ join packet (the "magic packet") is sent first so the relay
 * registers this connection into the room. Then raw Minecraft frame payloads are exchanged:
 * client frames go up as raw ArcNet messages, host frames come down as raw {@link RawPacket}s
 * (the relay strips the CLaJ wrapper before forwarding).
 */
public class RelayClient extends Client {
	private final ClientReceiver receiver;
	private final ByteBuffer magic;
	private final Runnable onRoomClose;

	private static final int MAX_QUEUE = 512;

	private final Object lock = new Object();
	private final ArrayDeque<byte[]> toLocal = new ArrayDeque<>();
	private final ArrayDeque<byte[]> preQueue = new ArrayDeque<>();
	private final Object preLock = new Object();
	private volatile boolean magicSent;
	private volatile boolean closed;

	public RelayClient(ByteBuffer magic, Runnable onRoomClose) {
		super(32768, 32768, new JoinerBridgeSerializer());
		this.magic = magic;
		this.onRoomClose = onRoomClose;
		this.receiver = new ClientReceiver(this, NetListenerFilter.noIdleFilter);

		receiver.handle(Connect.class, _ -> {
			if (magic != null) {
				try {
					sendTCP(magic.duplicate());
				} catch (Throwable e) {
					zhiwo.claj.ClajMod.LOGGER.warn("CLaJ: failed to send join packet", e);
				}
			}
			// Flush buffered frames and expose readiness atomically (same lock as offerFrame),
			// so no frame can be stuck in the pre-queue after readiness (N2).
			synchronized (preLock) {
				flushPreQueue0();
				magicSent = true;
			}
		});

		receiver.handle(Disconnect.class, _ -> {
			if (getLastProtocolError() != null) {
				zhiwo.claj.ClajMod.LOGGER.warn("CLaJ: relay connection error", getLastProtocolError());
			}
			closeAll();
		});

		// Raw Minecraft frames forwarded by the relay (from the host's integrated server).
		receiver.handle(RawPacket.class, p -> {
			byte[] payload = FrameUtil.bytesOf(p);
			if (payload != null) pushLocal(payload);
		});
	}

	/**
	 * Atomically decides whether to queue (not ready yet) or send the frame.
	 * Ensures FIFO ordering and that no frame is stranded after readiness (N2).
	 *
	 * @return {@code false} if the frame was rejected (pre-queue full before readiness).
	 */
	public boolean offerFrame(byte[] payload) {
		synchronized (preLock) {
			if (!magicSent) {
				if (preQueue.size() >= 32) return false;
				preQueue.addLast(payload);
				return true;
			}
		}
		sendFrame(payload);
		return true;
	}

	/** Sends one Minecraft frame payload (length prefix stripped) to the relay. */
	public void sendFrame(byte[] payload) {
		if (payload.length > FrameUtil.MAX_FRAME) {
			zhiwo.claj.ClajMod.LOGGER.warn("CLaJ: frame too big ({}), disconnecting", payload.length);
			closeAll();
			return;
		}
		try {
			sendTCP(ByteBuffer.wrap(payload));
		} catch (Throwable e) {
			closeAll();
		}
	}

	/** Blocks until the next relay payload to write to the local Minecraft client is available. */
	public byte[] takeLocalFrame() throws InterruptedException {
		synchronized (lock) {
			while (toLocal.isEmpty()) {
				if (closed) return null;
				lock.wait();
			}
			return toLocal.pollFirst();
		}
	}

	/** Drops any queued frames (used when the local client reconnects: stale data must not be
	 *  written into the new socket). */
	public void clearLocalQueue() {
		synchronized (lock) {
			toLocal.clear();
		}
	}

	private void pushLocal(byte[] payload) {
		synchronized (lock) {
			if (toLocal.size() >= MAX_QUEUE) {
				// The local socket is not keeping up: drop the connection instead of growing memory.
				closeAll();
				return;
			}
			toLocal.addLast(payload);
			lock.notifyAll();
		}
	}

	private void flushPreQueue0() {
		byte[] frame;
		while ((frame = preQueue.pollFirst()) != null) {
			sendFrame(frame);
		}
	}

	public void closeAll() {
		boolean wasClosed;
		synchronized (lock) {
			wasClosed = closed;
			closed = true;
			lock.notifyAll();
		}
		if (!wasClosed && onRoomClose != null) {
			try {
				onRoomClose.run();
			} catch (Throwable ignored) {}
		}
		try {
			close(DcReason.closed);
		} catch (Throwable ignored) {}
	}

}
