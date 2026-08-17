package zhiwo.claj.state;

import arc.util.serialization.Jval;

/**
 * Room state shown in the CLaJ room browser. Encoded as a small JSON document
 * (bounded by the CLaJ state size limit of 8128 bytes).
 */
public class MinecraftRoomState {
	public String name = "";
	public String motd = "";
	public String mode = "";
	public String version = "";
	public int players;
	public int maxPlayers;
	public long port = 25565;

	public String encode() {
		Jval value = Jval.newObject();
		Jval.JsonMap map = value.asObject();
		map.put("name", Jval.valueOf(name));
		map.put("motd", Jval.valueOf(motd));
		map.put("mode", Jval.valueOf(mode));
		map.put("version", Jval.valueOf(version));
		map.put("players", Jval.valueOf(players));
		map.put("max", Jval.valueOf(maxPlayers));
		map.put("port", Jval.valueOf(port));
		return value.toString(Jval.Jformat.plain);
	}

	public static MinecraftRoomState decode(String json) {
		MinecraftRoomState state = new MinecraftRoomState();
		if (json == null || json.isEmpty()) return state;
		try {
			Jval value = Jval.read(json);
			if (!value.isObject()) return state;
			Jval.JsonMap map = value.asObject();
			Jval v;
			if ((v = map.get("name")) != null) state.name = v.asString();
			if ((v = map.get("motd")) != null) state.motd = v.asString();
			if ((v = map.get("mode")) != null) state.mode = v.asString();
			if ((v = map.get("version")) != null) state.version = v.asString();
			if ((v = map.get("players")) != null && v.isNumber()) state.players = v.asInt();
			if ((v = map.get("max")) != null && v.isNumber()) state.maxPlayers = v.asInt();
			if ((v = map.get("port")) != null && v.isNumber()) state.port = v.asLong();
		} catch (Exception ignored) {
			// invalid state, return defaults
		}
		return state;
	}

	/** Builds the current host state, or {@code null} if not hosting. */
	public static MinecraftRoomState current() {
		net.minecraft.server.MinecraftServer server = zhiwo.claj.proxy.MinecraftClajProxy.getHostServer();
		if (server == null || !server.isRunning()) return null;
		MinecraftRoomState state = new MinecraftRoomState();
		try {
			String motd = server.getMotd();
			state.name = motd == null || motd.isEmpty() ? "Minecraft Server" : motd;
			state.motd = motd;
			state.mode = server.isHardcore() ? "Hardcore" : "Survival";
			state.version = server.getServerVersion();
			state.players = server.getPlayerCount();
			state.maxPlayers = server.getPlayerList().getMaxPlayers();
			state.port = server.getPort();
		} catch (Exception e) {
			return null;
		}
		return state;
	}
}
