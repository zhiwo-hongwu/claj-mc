package zhiwo.claj;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLaJ (Copy Link and Join) - Minecraft 26.2 Fabric port.
 *
 * <p>This mod reuses the game-agnostic CLaJ protocol implementation
 * (common + api modules from <a href="https://github.com/xpdustry/claj">xpdustry/claj</a>)
 * and adds a Minecraft-specific transport:
 * <ul>
 *   <li><b>Host side:</b> a {@link zhiwo.claj.proxy.MinecraftClajProxy} that, for every remote
 *       CLaJ player, opens a real loopback TCP connection to the local integrated server and
 *       bridges raw bytes between the CLaJ relay and that socket. The Minecraft server sees a
 *       completely normal network client.</li>
 *   <li><b>Joiner side:</b> the Minecraft client is redirected to a local listener socket; a
 *       {@link zhiwo.claj.join.MinecraftClajJoiner} bridges the raw byte stream to the CLaJ relay
 *       through an ArcNet client connection (registered into the room with the CLaJ join packet).</li>
 * </ul>
 */
public class ClajMod implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("claj");

	@Override
	public void onInitializeClient() {
		LOGGER.info("CLaJ initializing...");
		zhiwo.claj.ClajInit.init();
		LOGGER.info("CLaJ initialized.");
	}
}
