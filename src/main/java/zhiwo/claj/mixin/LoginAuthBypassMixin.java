package zhiwo.claj.mixin;

import java.net.InetSocketAddress;
import java.util.UUID;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the integrated server treat loopback CLaJ bridge connections as offline-mode logins.
 *
 * <p>Remote CLaJ players connect through the host's local loopback socket, so Mojang session
 * verification would fail (their session IP differs from 127.0.0.1). By forcing the offline login
 * path for loopback connections only, any player holding the CLaJ room link can join, exactly like
 * a LAN/cracked server. All other (non-loopback) connections keep the vanilla behavior.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class LoginAuthBypassMixin {
	@Redirect(method = "handleHello", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/Connection;isMemoryConnection()Z"))
	private boolean claj$forceOfflinePath(Connection connection) {
		return isLoopback(connection) || connection.isMemoryConnection();
	}

	@Redirect(method = "verifyLoginAndFinishConnectionSetup", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/Connection;getIntendedProfileId()Ljava/util/UUID;"))
	private UUID claj$skipIntendedProfileCheck(Connection connection) {
		return isLoopback(connection) ? null : connection.getIntendedProfileId();
	}

	private static boolean isLoopback(Connection connection) {
		try {
			// Respect the config switch (default on) and only affect loopback bridge connections.
			return zhiwo.claj.ClajConfig.data().onlineModeBypass
					&& connection.getRemoteAddress() instanceof InetSocketAddress inet
					&& inet.getAddress() != null && inet.getAddress().isLoopbackAddress();
		} catch (Exception e) {
			return false;
		}
	}
}
