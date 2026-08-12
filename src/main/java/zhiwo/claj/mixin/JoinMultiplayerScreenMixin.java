package zhiwo.claj.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import zhiwo.claj.screen.ClajJoinScreen;

/**
 * Adds a "Join via CLaJ" button to the multiplayer screen (bottom-left corner), so joining works
 * straight from the main menu.
 *
 * <p>JoinMultiplayerScreen overrides {@code repositionElements} without rebuilding widgets, so the
 * button instance would keep its pressed/hover state after returning from the CLaJ join screen
 * (visible as a white outline). The button is therefore rebuilt on every reposition instead.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
	@Unique
	private Button claj$button;

	protected JoinMultiplayerScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void claj$addJoinButton(CallbackInfo ci) {
		claj$addButton();
	}

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void claj$reposition(CallbackInfo ci) {
		// Rebuild the button: a fresh instance has no lingering pressed/hover state.
		if (claj$button != null) {
			this.removeWidget(claj$button);
			claj$button = null;
		}
		claj$addButton();
	}

	@Unique
	private void claj$addButton() {
		claj$button = Button.builder(Component.translatable("claj.join.button"),
				b -> Minecraft.getInstance().setScreenAndShow(new ClajJoinScreen((Screen) (Object) this)))
				.bounds(4, this.height - 30, 150, 20).build();
		this.addRenderableWidget(claj$button);
	}
}
