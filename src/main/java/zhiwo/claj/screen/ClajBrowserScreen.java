package zhiwo.claj.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import arc.struct.ObjectMap;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajRoom;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import zhiwo.claj.ClajServers;
import zhiwo.claj.state.MinecraftRoomState;

/**
 * Public room browser: lists the public rooms of every CLaJ relay server, grouped by server
 * (server name + ping + room count, then its rooms). Click a room to join it.
 */
public class ClajBrowserScreen extends Screen {
	private record Section(ClajManageScreen.ServerInfo server, List<ClajRoom<MinecraftRoomState>> rooms) {}

	private final Screen parent;
	private final List<Section> sections = new ArrayList<>();
	private int refreshGen;

	public ClajBrowserScreen(Screen parent) {
		super(Component.translatable("claj.browser.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		buildWidgets();
		if (sections.isEmpty()) refresh();
	}

	@Override
	protected void repositionElements() {
		buildWidgets();
	}

	private void refresh() {
		sections.clear();
		int gen = ++refreshGen;
		Claj.get().cancelPingers();
		ClajServers.loadCustom();

		// Only the player's own custom servers are browsed for public rooms.
		for (ObjectMap.Entry<String, String> e : ClajServers.custom) {
			ClajManageScreen.ServerInfo info = ClajManageScreen.parse(e.key, e.value);
			if (info != null) sections.add(new Section(info, new ArrayList<>()));
		}

		// Rebuild immediately (shows "refreshing"), then rebuild once when every server answered.
		buildWidgets();
		int total = sections.size();
		if (total == 0) return;
		AtomicInteger pending = new AtomicInteger(total);
		for (int i = 0; i < total; i++) {
			int index = i;
			Section section = sections.get(i);
			ClajManageScreen.ServerInfo server = section.server();
			Claj.get().pingHost(server.host(), Integer.parseInt(server.port()), state -> {
				if (gen != refreshGen) return; // stale callback from a previous refresh
				updateServer(index, state.ping, state.version == Claj.get().provider.getVersion().majorVersion);
				listRooms(index, pending);
			}, e -> {
				if (gen != refreshGen) return; // stale callback from a previous refresh
				updateServer(index, Integer.MAX_VALUE, false);
				if (pending.decrementAndGet() <= 0) buildWidgets();
			});
		}
	}

	private void updateServer(int index, int ping, boolean compatible) {
		if (index < 0 || index >= sections.size()) return;
		Section section = sections.get(index);
		ClajManageScreen.ServerInfo current = section.server();
		ClajManageScreen.ServerInfo updated = new ClajManageScreen.ServerInfo(
				current.name(), current.host(), current.port(), ping, compatible);
		sections.set(index, new Section(updated, section.rooms()));
	}

	private void listRooms(int index, AtomicInteger pending) {
		int gen = refreshGen;
		Section section = sections.get(index);
		ClajManageScreen.ServerInfo server = section.server();
		Claj.get().<MinecraftRoomState>serverRooms(server.host(), Integer.parseInt(server.port()), roomList -> {
			for (ClajRoom<?> room : roomList) {
				@SuppressWarnings("unchecked")
				ClajRoom<MinecraftRoomState> typed = (ClajRoom<MinecraftRoomState>) room;
				section.rooms().add(typed);
			}
			if (gen == refreshGen && pending.decrementAndGet() <= 0) buildWidgets();
		}, e -> {
			if (gen == refreshGen && pending.decrementAndGet() <= 0) buildWidgets();
		});
	}

	private void buildWidgets() {
		clearWidgets();
		int w = Math.min(460, width - 60);
		int x = (width - w) / 2;
		int y = 24;

		addRenderableWidget(Button.builder(Component.translatable("claj.manage.refresh"), b -> refresh()).bounds(x, y, (w - 10) / 2, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> goBack())
				.bounds(x + (w - 10) / 2 + 10, y, (w - 10) / 2, 20).build());
		y += 26;

		int bottom = height - 24;
		int maxRows = Math.max(1, (bottom - y - 4) / 22);

		int used = 0;
		for (Section section : sections) {
			if (used >= maxRows) break;
			// Server header row
			addRenderableWidget(new StringWidget(x, y, Component.literal(truncate(sectionLabel(section), 58)), font));
			used++;
			y += 22;

			if (section.rooms().isEmpty()) {
				addRenderableWidget(new StringWidget(x + 14, y, Component.translatable("claj.browser.no-rooms-server"), font));
				used++;
				y += 22;
				continue;
			}
			for (ClajRoom<MinecraftRoomState> room : section.rooms()) {
				if (used >= maxRows) break;
				addRenderableWidget(Button.builder(Component.literal(truncate(labelOf(room), 60)), b -> pickRoom(room))
						.bounds(x, y, w, 20).build());
				used++;
				y += 22;
			}
		}
		if (sections.isEmpty()) {
			addRenderableWidget(new StringWidget(x, y, Component.translatable("claj.manage.no-servers"), font));
		}
	}

	private static String truncate(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 1) + "…";
	}

	private static String sectionLabel(Section section) {
		ClajManageScreen.ServerInfo server = section.server();
		String ping;
		if (server.ping() < 0) {
			ping = "";
		} else if (server.ping() == Integer.MAX_VALUE) {
			ping = Component.translatable("claj.manage.offline").getString();
		} else {
			ping = server.ping() + "ms" + (server.compatible() ? "" : Component.translatable("claj.manage.incompatible").getString());
		}
		return server.name() + " [" + server.host() + ":" + server.port() + "]  " + ping
				+ "  " + Component.translatable("claj.browser.rooms-count", section.rooms().size()).getString();
	}

	private static String labelOf(ClajRoom<MinecraftRoomState> room) {
		MinecraftRoomState state = room.state;
		String base;
		if (state != null) {
			base = state.name + " [" + state.players + "/" + state.maxPlayers + "] " + state.mode
					+ (state.version == null || state.version.isEmpty() ? "" : " " + state.version);
		} else {
			base = Component.translatable("claj.browser.unknown").getString();
		}
		if (room.isProtected) base += Component.translatable("claj.browser.locked").getString();
		return base + " - " + room.link.encodedRoomId;
	}

	/**
	 * Picks a room: returns to the join screen this browser was opened from (reusing that exact
	 * instance - no new screen, no extra nesting) and starts joining immediately.
	 */
	private void pickRoom(ClajRoom<MinecraftRoomState> room) {
		if (parent instanceof ClajJoinScreen join) {
			minecraft.setScreenAndShow(parent);
			join.offerLink(room.link);
		} else {
			// Fallback (browser opened without a join-screen parent): open a fresh one.
			ClajJoinScreen fresh = new ClajJoinScreen(parent);
			minecraft.setScreenAndShow(fresh);
			fresh.offerLink(room.link);
		}
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
		extractor.text(font, getTitle(), (width - font.width(getTitle())) / 2, 6, 0xFFFFFF);
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
	}
}
