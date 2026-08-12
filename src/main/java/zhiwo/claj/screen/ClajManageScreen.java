package zhiwo.claj.screen;

import java.util.ArrayList;
import java.util.List;

import arc.struct.ObjectMap;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajLink;
import com.xpdustry.claj.api.ClajProxy;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import zhiwo.claj.ClajConfig;
import zhiwo.claj.ClajServers;
import zhiwo.claj.proxy.MinecraftClajProxy;

/**
 * Main management screen: create a room on a CLaJ relay server (public or custom, with ping),
 * copy the link, close the room, edit settings and add custom servers.
 * The server list is scrollable with no fixed row limit.
 */
public class ClajManageScreen extends Screen {
	record ServerInfo(String name, String host, String port, int ping, boolean compatible) {}

	private final Screen parent;
	private final List<ServerInfo> servers = new ArrayList<>();
	private int selectedIndex = -1;
	private Component status = Component.empty();
	private int statusColor = 0xFFFFFFFF;
	private boolean fetchingOnline;
	private int refreshGen;
	private ServerListWidget serverList;
	private Button createButton;
	/** True while the user manually closed the room: later async close callbacks are ignored. */
	private boolean manualClose;

	public ClajManageScreen(Screen parent) {
		super(Component.translatable("claj.manage.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		refreshServers();
		buildWidgets();
	}

	@Override
	protected void repositionElements() {
		buildWidgets();
	}

	private void refreshServers() {
		servers.clear();
		ClajServers.loadCustom();
		for (ObjectMap.Entry<String, String> e : ClajServers.custom) {
			ServerInfo info = parse(e.key, e.value);
			if (info != null) servers.add(info);
		}
		synchronized (ClajServers.online) {
			for (ObjectMap.Entry<String, String> e : ClajServers.online) {
				ServerInfo info = parse(e.key, e.value);
				if (info != null) servers.add(info);
			}
		}

		// If the public list is still empty (first open), fetch it in the background and refresh.
		if (ClajServers.online.isEmpty() && !fetchingOnline) {
			fetchingOnline = true;
			ClajServers.refreshOnline(() -> {
				fetchingOnline = false;
				refreshServers();
				buildWidgets();
			}, e -> fetchingOnline = false);
		}
		pingAll();
	}

	static ServerInfo parse(String name, String address) {
		try {
			int idx = address.lastIndexOf(':');
			if (idx <= 0 || idx == address.length() - 1) return null;
			String host = address.substring(0, idx);
			int port = Integer.parseInt(address.substring(idx + 1));
			if (port < 0 || port > 0xFFFF) return null;
			return new ServerInfo(name, host, String.valueOf(port), -1, false);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Pings all servers in parallel; every answer updates only its own row
	 * (incremental, no full rebuild), so fast servers appear first and slow ones later.
	 */
	private void pingAll() {
		Claj.get().cancelPingers();
		int gen = ++refreshGen;
		int total = servers.size();
		if (total == 0) return;
		for (int i = 0; i < total; i++) {
			int index = i;
			ServerInfo server = servers.get(i);
			Claj.get().pingHost(server.host(), Integer.parseInt(server.port()), state -> {
				if (gen != refreshGen) return; // stale callback from a previous refresh
				if (index < servers.size()) {
					ServerInfo current = servers.get(index);
					int ping = state.ping;
					boolean compatible = state.version == Claj.get().provider.getVersion().majorVersion;
					servers.set(index, new ServerInfo(current.name(), current.host(), current.port(), ping, compatible));
					if (serverList != null) serverList.updateServer(current.name(), ping, compatible);
				}
			}, e -> {
				if (gen != refreshGen) return; // stale callback from a previous refresh
				if (index < servers.size()) {
					ServerInfo current = servers.get(index);
					servers.set(index, new ServerInfo(current.name(), current.host(), current.port(), Integer.MAX_VALUE, false));
					if (serverList != null) serverList.updateServer(current.name(), Integer.MAX_VALUE, false);
				}
			});
		}
	}

	private void buildWidgets() {
		clearWidgets();
		int w = Math.min(460, width - 60);
		int x = (width - w) / 2;
		int y = Math.max(16, (height - 340) / 2 + 4);

		ClajProxy proxy = Claj.get().proxies.get();
		ClajLink link = proxy.link();

		if (proxy.roomCreated() && link != null) {
			addRenderableWidget(Button.builder(Component.translatable("claj.manage.copy"), b -> {
				minecraft.keyboardHandler.setClipboard(link.toString());
				status = Component.translatable("claj.manage.copied");
				statusColor = 0xFF55FF55;
			}).bounds(x, y, (w - 10) / 2, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("claj.manage.close"), b -> {
				manualClose = true;
				proxy.closeRoom();
				status = Component.translatable("claj.manage.room-closed");
				statusColor = 0xFF55FF55;
				buildWidgets();
			}).bounds(x + (w - 10) / 2 + 10, y, (w - 10) / 2, 20).build());
			y += 30;
		} else {
			Component hint = MinecraftClajProxy.getHostServer() != null
					? Component.translatable("claj.manage.ready")
					: Component.translatable("claj.manage.no-room");
			addRenderableWidget(new StringWidget(x, y, hint, font));
			y += 20;
		}

		// Server list header + refresh
		addRenderableWidget(new StringWidget(x, y, Component.translatable("claj.manage.servers"), font));
		addRenderableWidget(Button.builder(Component.translatable("claj.manage.refresh"), b -> {
			refreshServers();
			buildWidgets();
		}).bounds(x + w - 60, y - 2, 60, 16).build());
		y += 22;

		// Scrollable server list: fills the space between the header and the bottom buttons.
		int listBottom = height - 94;
		int listHeight = Math.max(24, listBottom - y);
		serverList = new ServerListWidget(minecraft, w, listHeight, y, this::onServerClick, false);
		serverList.updateSizeAndPosition(w, listHeight, x, y);
		String selectName = selectedIndex >= 0 && selectedIndex < servers.size() ? servers.get(selectedIndex).name() : null;
		serverList.setServers(servers, selectName);
		addRenderableWidget(serverList);

		int cy = height - 88;
		createButton = Button.builder(Component.translatable("claj.manage.create"), b -> createRoom()).bounds(x, cy, w, 20).build();
		createButton.active = selectedIndex >= 0;
		addRenderableWidget(createButton);

		int by = height - 62;
		int col = (w - 20) / 3;
		addRenderableWidget(Button.builder(Component.translatable("claj.manage.settings"), b -> minecraft.setScreenAndShow(new ClajSettingsScreen(this)))
				.bounds(x, by, col, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("claj.manage.add-server"), b -> addServer())
				.bounds(x + col + 10, by, col, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> goBack())
				.bounds(x + 2 * (col + 10), by, col, 20).build());
	}

	private void onServerClick(ServerInfo server) {
		selectedIndex = -1;
		for (int i = 0; i < servers.size(); i++) {
			if (servers.get(i).name().equals(server.name())) {
				selectedIndex = i;
				break;
			}
		}
		if (createButton != null) createButton.active = selectedIndex >= 0;
		status = Component.translatable("claj.manage.selected", server.name());
		statusColor = 0xFFFFFFFF;
	}

	private void createRoom() {
		if (selectedIndex < 0 || selectedIndex >= servers.size()) return;
		ServerInfo server = servers.get(selectedIndex);
		manualClose = false;

		ClajConfig.Data config = ClajConfig.data();
		ClajProxy proxy = Claj.get().proxies.get();
		proxy.setDefaultConfiguration(config.roomPublic, config.roomProtected, (short) config.roomPassword, true);

		// Hosting requires the integrated server (single-player world).
		if (MinecraftClajProxy.getHostServer() == null) {
			status = Component.translatable("claj.manage.host-first");
			statusColor = 0xFFFF5555;
			return;
		}

		// Ensure the integrated server is listening on TCP (auto open to LAN).
		if (proxy instanceof MinecraftClajProxy mcProxy) {
			try {
				mcProxy.prepareHostServer();
			} catch (Exception e) {
				status = Component.translatable("claj.manage.failed", e.getMessage() == null ? e.toString() : e.getMessage());
				statusColor = 0xFFFF5555;
				return;
			}
		}

		status = Component.translatable("claj.manage.creating", server.name());
		statusColor = 0xFFFFFF55;

		Claj.get().createRoom(server.host(), Integer.parseInt(server.port()),
				link -> {
					status = Component.translatable("claj.manage.created");
					statusColor = 0xFF55FF55;
					minecraft.keyboardHandler.setClipboard(link.toString());
					buildWidgets();
				},
				reason -> {
					// The close callback fires multiple times (manual close, disconnect event...).
					// A manual close must show the plain message, not the raw reason.
					if (manualClose) {
						status = Component.translatable("claj.manage.room-closed");
						statusColor = 0xFF55FF55;
					} else {
						status = Component.translatable("claj.manage.closed",
								reason == null ? "" : Component.translatable("claj.reason." + reason.name()).getString());
						statusColor = 0xFFFF5555;
					}
					buildWidgets();
				},
				e -> {
					String msg = e.getMessage() == null ? e.toString() : e.getMessage();
					// The protocol layer throws this English error when a room already exists.
					if (msg != null && msg.contains("already created")) {
						status = Component.translatable("claj.manage.already-created");
					} else {
						status = Component.translatable("claj.manage.failed", msg);
					}
					statusColor = 0xFFFF5555;
				});
	}

	private void addServer() {
		minecraft.setScreenAndShow(new AddServerScreen(this, name -> {
			refreshServers();
			// Select the newly added server so it is immediately visible.
			for (int i = 0; i < servers.size(); i++) {
				if (servers.get(i).name().equals(name)) {
					selectedIndex = i;
					break;
				}
			}
			buildWidgets();
		}));
	}

	private void goBack() {
		if (parent != null) minecraft.setScreenAndShow(parent);
		else onClose();
	}

	@Override
	public void onClose() {
		if (parent != null) minecraft.setScreenAndShow(parent);
		else super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		extractor.fill(0, 0, width, height, 0xAA000000);
		extractor.text(font, getTitle(), (width - font.width(getTitle())) / 2, 8, 0xFFFFFF);

		ClajProxy proxy = Claj.get().proxies.get();
		if (proxy.roomCreated() && proxy.link() != null) {
			String link = proxy.link().toString();
			int x = (width - Math.min(font.width(link), width - 40)) / 2;
			extractor.text(font, link, x, height - 34, 0xFF55FF55);
		}
		if (!status.getString().isEmpty()) {
			// Below the bottom button row (height - 62 .. height - 42).
			extractor.text(font, status, (width - font.width(status)) / 2, height - 18, statusColor);
		}
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
	}
}
