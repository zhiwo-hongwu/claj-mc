package zhiwo.claj;

import arc.struct.ArrayMap;
import arc.struct.ObjectMap;
import arc.util.Http;
import arc.util.serialization.Jval;

import net.minecraft.client.Minecraft;

/**
 * CLaJ server list: public servers fetched from the official list, plus the player's custom
 * servers persisted in {@link ClajConfig}.
 */
public final class ClajServers {
	public static final String PUBLIC_SERVERS_LINK =
			"https://raw.githubusercontent.com/xpdustry/claj/main/public-servers.hjson";
	public static final String MIRROR_PUBLIC_SERVERS_LINK = "https://claj.xpdustry.com/nodes";

	public static final ArrayMap<String, String> online = new ArrayMap<>();
	public static final ArrayMap<String, String> custom = new ArrayMap<>();

	private ClajServers() {}

	public static void refreshOnline(Runnable done, arc.func.Cons<Throwable> failed) {
		Http.get(PUBLIC_SERVERS_LINK,
				r -> setServers(r.getResultAsString(), done),
				t -> Http.get(MIRROR_PUBLIC_SERVERS_LINK,
						r -> setServers(r.getResultAsString(), done),
						_ -> post(() -> failed.get(t))));
	}

	private static void setServers(String json, Runnable done) {
		try {
			Jval.JsonMap list = Jval.read(json).asObject();
			synchronized (online) {
				online.clear();
				for (ObjectMap.Entry<String, Jval> e : list) {
					online.put(e.key, e.value.asString());
				}
			}
			post(done);
		} catch (Exception e) {
			post(done);
		}
	}

	public static void loadCustom() {
		custom.clear();
		for (java.util.Map.Entry<String, String> e : ClajConfig.data().customServers.entrySet()) {
			custom.put(e.getKey(), e.getValue());
		}
	}

	public static void saveCustom() {
		ClajConfig.data().customServers.clear();
		for (ObjectMap.Entry<String, String> e : custom) {
			ClajConfig.data().customServers.put(e.key, e.value);
		}
		ClajConfig.save();
	}

	private static void post(Runnable runnable) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) runnable.run();
		else minecraft.execute(runnable);
	}
}
