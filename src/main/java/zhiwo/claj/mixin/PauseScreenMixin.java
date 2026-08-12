package zhiwo.claj.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import zhiwo.claj.screen.ClajManageScreen;

/**
 * Adds a "Manage CLaJ Room" button to the pause menu (top-right corner), giving the host quick
 * access to the CLaJ management screen while inside the world.
 *
 * <p>PauseScreen does NOT override {@code repositionElements}, so window resizes go through the
 * default {@code rebuildWidgets() -> init()} and this mixin re-creates the button at the new
 * position automatically (unlike JoinMultiplayerScreen which overrides it).</p>
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
	protected PauseScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void claj$addManageButton(CallbackInfo ci) {
		Button button = Button.builder(Component.translatable("claj.pause.manage"),
				b -> Minecraft.getInstance().setScreenAndShow(new ClajManageScreen((Screen) (Object) this)))
				.bounds(this.width - 154, 10, 148, 20).build();
		this.addRenderableWidget(button);
	}
}
