package zhiwo.claj.screen;

import java.util.ArrayList;
import java.util.List;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajLink;
import com.xpdustry.claj.api.ClajPinger;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import zhiwo.claj.ClajConfig;
import zhiwo.claj.ClajServers;

/**
 * Join screen: enter a {@code claj://host:port/roomId} link (with optional 4-digit PIN), or pick
 * one of YOUR custom CLaJ relay servers from the scrollable list (click a server to delete it).
 * Click "Browse rooms" for the public rooms of your custom servers.
 */
public class ClajJoinScreen extends Screen {
	private final Screen parent;
	private final List<ClajManageScreen.ServerInfo> servers = new ArrayList<>();

	private EditBox linkBox;
	private EditBox passwordBox;
	private Button joinButton;
	private ServerListWidget serverList;
	private Component status = Component.empty();
	private int statusColor = 0xFFFFFFFF;
	private String savedLink = "";
	private String savedPin = "";
	private int refreshGen;

	public ClajJoinScreen(Screen parent) {
		super(Component.translatable("claj.join.title"));
		this.parent = parent;
	}

	/**
	 * Called by the room browser when a room is picked: fills the link and starts joining
	 * immediately. The browser reuses THIS instance (no new screen, no extra nesting).
	 */
	public void offerLink(ClajLink link) {
		if (linkBox != null) {
			linkBox.setValue(link.toString());
			join(); // join() persists the link as lastLink
		}
	}

	@Override
	protected void init() {
		buildWidgets();
		refreshServers();
	}

	@Override
	protected void repositionElements() {
		buildWidgets();
	}

	private void buildWidgets() {
		if (linkBox != null) savedLink = linkBox.getValue();
		if (passwordBox != null) savedPin = passwordBox.getValue();
		clearWidgets();

		int w = Math.min(460, width - 40);
		int x = (width - w) / 2;
		int y = Math.max(12, (height - 300) / 2);

		linkBox = new EditBox(font, x, y, w, 20, Component.translatable("claj.join.link"));
		linkBox.setMaxLength(128);
		linkBox.setHint(Component.translatable("claj.join.link.hint"));
		linkBox.setValue(savedLink.isEmpty() ? ClajConfig.data().lastLink : savedLink);
		linkBox.setResponder(s -> ClajConfig.data().lastLink = s);
		addRenderableWidget(linkBox);
		y += 26;

		int pwW = (int) (w * 0.30f), joinW = (int) (w * 0.35f), backW = w - pwW - joinW - 12;
		passwordBox = new EditBox(font, x, y, pwW, 20, Component.translatable("claj.join.password"));
		passwordBox.setMaxLength(4);
		passwordBox.setHint(Component.translatable("claj.join.password.hint"));
		passwordBox.setResponder(s -> {
			String filtered = s.replaceAll("[^0-9]", "");
			if (!filtered.equals(s)) {
				passwordBox.setValue(filtered);
			}
		});
		if (!savedPin.isEmpty()) passwordBox.setValue(savedPin);
		addRenderableWidget(passwordBox);

		joinButton = Button.builder(Component.translatable("claj.join.button"), b -> join())
				.bounds(x + pwW + 6, y, joinW, 20).build();
		addRenderableWidget(joinButton);

		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> goBack())
				.bounds(x + pwW + joinW + 12, y, backW, 20).build());
		y += 26;

		// Server list header + refresh
		addRenderableWidget(new StringWidget(x, y, Component.translatable("claj.join.rooms"), font));
		addRenderableWidget(Button.builder(Component.translatable("claj.manage.refresh"), b -> refreshServers())
				.bounds(x + w - 60, y - 2, 60, 16).build());
		y += 22;

		// Scrollable server list: fills the space down to the bottom buttons.
		int listBottom = height - 64;
		int listHeight = Math.max(24, listBottom - y);
		serverList = new ServerListWidget(minecraft, w, listHeight, y, this::confirmDeleteServer, true);
		serverList.updateSizeAndPosition(w, listHeight, x, y);
		serverList.setServers(servers, null);
		addRenderableWidget(serverList);

		int by = height - 38;
		addRenderableWidget(Button.builder(Component.translatable("claj.join.browse"), b -> minecraft.setScreenAndShow(new ClajBrowserScreen(this)))
				.bounds(x, by, (w - 10) / 2, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("claj.manage.add-server"), b -> minecraft.setScreenAndShow(new AddServerScreen(this, name -> refreshServers())))
				.bounds(x + (w - 10) / 2 + 10, by, (w - 10) / 2, 20).build());
	}

	private void confirmDeleteServer(ClajManageScreen.ServerInfo server) {
		minecraft.setScreenAndShow(new ConfirmScreen(ok -> {
			if (ok) {
				ClajServers.custom.removeKey(server.name());
				ClajServers.saveCustom();
				// Go back to the join screen and refresh its list immediately.
				minecraft.setScreenAndShow(ClajJoinScreen.this);
				refreshServers();
			} else {
				minecraft.setScreenAndShow(this);
			}
		}, Component.translatable("claj.manage.delete-server.confirm", server.name()),
				Component.translatable("claj.manage.delete-server.confirm.msg", server.name())));
	}

	/** Only the player's own custom servers are listed here. */
	private void refreshServers() {
		servers.clear();
		int gen = ++refreshGen;
		Claj.get().cancelPingers();
		ClajServers.loadCustom();
		for (var e : ClajServers.custom) {
			ClajManageScreen.ServerInfo info = ClajManageScreen.parse(e.key, e.value);
			if (info != null) servers.add(info);
		}

		// Rebuild the list once, then update each row incrementally as pings answer.
		buildWidgets();
		int total = servers.size();
		for (int i = 0; i < total; i++) {
			int index = i;
			ClajManageScreen.ServerInfo server = servers.get(i);
			Claj.get().pingHost(server.host(), Integer.parseInt(server.port()), state -> {
				if (gen != refreshGen) return; // stale callback from a previous refresh
				if (index < servers.size()) {
					ClajManageScreen.ServerInfo current = servers.get(index);
					int ping = state.ping;
					boolean compatible = state.version == Claj.get().provider.getVersion().majorVersion;
					servers.set(index, new ClajManageScreen.ServerInfo(current.name(), current.host(), current.port(), ping, compatible));
					if (serverList != null) serverList.updateServer(current.name(), ping, compatible);
				}
			}, e -> {
				if (gen != refreshGen) return; // stale callback from a previous refresh
				if (index < servers.size()) {
					ClajManageScreen.ServerInfo current = servers.get(index);
					servers.set(index, new ClajManageScreen.ServerInfo(current.name(), current.host(), current.port(), Integer.MAX_VALUE, false));
					if (serverList != null) serverList.updateServer(current.name(), Integer.MAX_VALUE, false);
				}
			});
		}
	}

	private short parsePassword() {
		String text = passwordBox.getValue().trim();
		if (text.isEmpty()) return ClajPinger.NO_PASSWORD;
		try {
			int value = Integer.parseInt(text);
			return value < 0 || value > 9999 ? ClajPinger.NO_PASSWORD : (short) value;
		} catch (NumberFormatException e) {
			return ClajPinger.NO_PASSWORD;
		}
	}

	private void join() {
		String text = linkBox.getValue().trim();
		if (text.isEmpty()) {
			status = Component.translatable("claj.join.status.enter-link");
			statusColor = 0xFFFF5555;
			return;
		}
		ClajLink link;
		try {
			link = ClajLink.fromString(text);
		} catch (Exception e) {
			status = Component.translatable("claj.join.status.invalid", e.getMessage());
			statusColor = 0xFFFF5555;
			return;
		}
		ClajConfig.data().lastLink = text;
		ClajConfig.save();

		status = Component.translatable("claj.join.status.joining");
		statusColor = 0xFFFFFF55;
		joinButton.active = false;

		Claj.get().joinRoom(link, parsePassword(),
				() -> {
					status = Component.translatable("claj.join.status.joined");
					statusColor = 0xFF55FF55;
					if (minecraft != null) minecraft.setScreenAndShow(null);
				},
				reason -> {
					status = Component.translatable("claj.join.reject." + reason.name());
					statusColor = 0xFFFF5555;
					joinButton.active = true;
				},
				e -> {
					status = Component.translatable("claj.join.status.failed",
							e.getMessage() == null ? e.toString() : e.getMessage());
					statusColor = 0xFFFF5555;
					joinButton.active = true;
				});
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
		if (!status.getString().isEmpty()) {
			extractor.text(font, status, (width - font.width(status)) / 2, height - 12, statusColor);
		}
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
	}
}
