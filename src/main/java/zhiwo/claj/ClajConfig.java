package zhiwo.claj;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Simple JSON configuration for the CLaJ mod (custom servers + room settings).
 */
public final class ClajConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Path file;
	private static Data data = new Data();

	private ClajConfig() {}

	public static class Data {
		public Map<String, String> customServers = new LinkedHashMap<>();
		public boolean roomPublic = true;
		public boolean roomProtected = false;
		public int roomPassword = 0;
		public String lastLink = "";
		/** TCP port to use when automatically opening the world to LAN. {@code 0} = default 25565. */
		public int lanPort = 0;
		/** Allow loopback CLaJ bridge connections to join without Mojang session verification. */
		public boolean onlineModeBypass = true;
	}

	public static void load() {
		try {
			file = FabricLoader.getInstance().getConfigDir().resolve("claj.json");
			if (Files.exists(file)) {
				Data loaded = GSON.fromJson(Files.readString(file), Data.class);
				if (loaded != null) data = loaded;
			}
		} catch (Exception e) {
			ClajMod.LOGGER.warn("CLaJ: failed to load config", e);
		}
	}

	public static void save() {
		try {
			if (file == null) file = FabricLoader.getInstance().getConfigDir().resolve("claj.json");
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(data));
		} catch (IOException e) {
			ClajMod.LOGGER.warn("CLaJ: failed to save config", e);
		}
	}

	public static Data data() {
		return data;
	}
}
