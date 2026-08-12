package zhiwo.claj.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import zhiwo.claj.proxy.MinecraftClajProxy;

/** Sends messages/popups to the host player(s) on the integrated server. */
public final class MinecraftChat {
	private MinecraftChat() {}

	public static void sendHostMessage(String text) {
		MinecraftServer server = MinecraftClajProxy.getHostServer();
		if (server == null) return;
		Component message = Component.literal("[CLaJ] " + text);
		server.execute(() -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.sendSystemMessage(message);
			}
		});
	}

}
