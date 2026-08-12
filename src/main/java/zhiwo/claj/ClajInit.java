package zhiwo.claj;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import com.xpdustry.claj.api.Claj;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.client.KeyMapping;

import zhiwo.claj.join.MinecraftClajJoiner;
import zhiwo.claj.screen.ClajManageScreen;

/**
 * Initializes the global CLaJ manager with the Minecraft implementation, registers the menu
 * keybind and wires up the lifecycle events.
 */
public final class ClajInit {
	private static KeyMapping menuKey;
	private static boolean initialized;

	private ClajInit() {}

	public static void init() {
		if (initialized) return;
		initialized = true;

		ClajConfig.load();
		ClajServers.loadCustom();
		// Pre-fetch the public server list in the background (falls back to the official mirror).
		ClajServers.refreshOnline(() -> ClajMod.LOGGER.info("CLaJ: loaded {} public servers", ClajServers.online.size),
				e -> ClajMod.LOGGER.warn("CLaJ: failed to fetch public servers", e));

		MinecraftClajProvider provider = new MinecraftClajProvider();
		Claj.init(provider, 1, 8); // 1 proxy + 8 parallel pingers (fast server list refresh)
		ClajMod.LOGGER.info("CLaJ API initialized (type={}, version={})", provider.getType(), provider.getVersion());

		registerKeybind();
		registerEvents();
	}

	private static void registerKeybind() {
		menuKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.claj.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KeyMapping.Category.MISC));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (menuKey.consumeClick()) {
				// Parent = the current screen, so Back returns to where the menu was opened from.
				minecraft.setScreenAndShow(new ClajManageScreen(minecraft.gui.screen()));
			}
		});
	}

	private static void registerEvents() {
		// Host side: close all CLaJ rooms when the integrated server stops.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			if (Claj.initialized()) {
				Claj.get().closeRooms();
				Claj.get().cancelPingers();
			}
		});

		// Joiner side: success/failure of the play connection.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> MinecraftClajJoiner.onClientJoined(handler));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MinecraftClajJoiner.onClientDisconnected(handler));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			MinecraftClajJoiner.stopCurrent();
			if (Claj.initialized()) Claj.get().dispose();
		});
	}

}
