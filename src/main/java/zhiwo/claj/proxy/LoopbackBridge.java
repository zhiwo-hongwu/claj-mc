package zhiwo.claj.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

import arc.net.DcReason;

import com.xpdustry.claj.api.net.VirtualConnection;

import zhiwo.claj.ClajMod;
import zhiwo.claj.transport.FrameUtil;

/**
 * One bridge per remote CLaJ player (host side).
 *
 * <p>Opens a real TCP connection to the local integrated Minecraft server (loopback) and pumps
 * bytes in both directions:
 * <ul>
 *   <li>local server → relay: read the raw byte stream and send it in chunks (no frame parsing,
 *       so arbitrarily large server packets like world sync work).</li>
 *   <li>relay → local server: write the chunk bytes back to the socket. The remote joiner sends
 *       complete Minecraft frames (length prefix stripped), so they are re-framed here.</li>
 * </ul>
 */
public class LoopbackBridge implements Runnable {
	private static final int CONNECT_TIMEOUT = 5000;
	/** Chunk size must stay safely below the ArcNet object buffer limit (32768). */
	private static final int CHUNK_SIZE = 24 * 1024;
	private static final int MAX_QUEUE = 512;

	private final MinecraftClajProxy proxy;
	private final VirtualConnection con;
	private final int serverPort;

	private final Object lock = new Object();
	private final ArrayDeque<byte[]> outbound = new ArrayDeque<>();
	private volatile boolean running = true;
	private volatile Socket socket;

	public LoopbackBridge(MinecraftClajProxy proxy, VirtualConnection con, int serverPort) {
		this.proxy = proxy;
		this.con = con;
		this.serverPort = serverPort;
	}

	public void start() {
		Thread thread = new Thread(this, "Claj Bridge " + con.getID());
		thread.setDaemon(true);
		thread.start();
	}

	/** Called from the proxy thread with raw bytes coming from the relay. */
	public void write(byte[] payload) {
		boolean overflow;
		synchronized (lock) {
			if (!(overflow = outbound.size() >= MAX_QUEUE)) {
				outbound.addLast(payload);
				lock.notifyAll();
			}
		}
		// Overflow handling MUST be outside the lock: close() -> bridgeClosed() -> proxy takes
		// the bridges lock, while the proxy may hold bridges and call bridge.close() (AB-BA).
		if (overflow) {
			close();
			proxy.bridgeClosed(con.getID(), DcReason.error);
		}
	}

	public void close() {
		running = false;
		synchronized (lock) {
			lock.notifyAll();
		}
		try {
			if (socket != null) socket.close();
		} catch (IOException ignored) {}
	}

	@Override
	public void run() {
		try {
			Socket s = new Socket();
			socket = s;
			s.setTcpNoDelay(true);
			s.connect(new InetSocketAddress("127.0.0.1", serverPort), CONNECT_TIMEOUT);

			Thread writer = new Thread(this::writerLoop, "Claj Bridge Writer " + con.getID());
			writer.setDaemon(true);
			writer.start();

			// Read the local server's raw byte stream and send it in chunks.
			InputStream in = s.getInputStream();
			byte[] buf = new byte[CHUNK_SIZE];
			int n;
			while (running && (n = in.read(buf)) > 0) {
				byte[] chunk = java.util.Arrays.copyOf(buf, n);
				try {
					con.sendTCP(ByteBuffer.wrap(chunk));
				} catch (Throwable e) {
					break;
				}
			}
			// The local server closed the fake client: notify the relay.
			proxy.bridgeClosed(con.getID(), DcReason.closed);
		} catch (IOException e) {
			if (running) {
				ClajMod.LOGGER.warn("CLaJ bridge {}: local connection failed: {}", con.getID(), e.toString());
				proxy.bridgeClosed(con.getID(), DcReason.error);
			}
		} finally {
			running = false;
			try {
				if (socket != null) socket.close();
			} catch (IOException ignored) {}
		}
	}

	private void writerLoop() {
		try {
			OutputStream out = socket.getOutputStream();
			while (running) {
				byte[] payload;
				synchronized (lock) {
					while (outbound.isEmpty() && running) {
						lock.wait();
					}
					if (!running) break;
					payload = outbound.pollFirst();
				}
				if (payload != null) {
					try {
						// The joiner sends complete Minecraft frames (length prefix stripped),
						// so re-frame them before writing to the local server.
						FrameUtil.writeFrame(out, payload);
					} catch (IOException e) {
						break;
					}
				}
			}
		} catch (InterruptedException ignored) {
		} catch (IOException ignored) {
		} finally {
			close();
		}
	}
}
