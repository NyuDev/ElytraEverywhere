package fr.nyuway.elytraeverywhere.mixin;

import fr.nyuway.elytraeverywhere.runtime.MessageThrottle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rate-limits a few Baritone status lines so they stop flooding the chat (see
 * {@link MessageThrottle} for which, and why they're harmless).
 *
 * <p>Every Baritone chat line ultimately flows through {@code Helper.logDirect}.
 * The {@code logDirect(String)} overload is the single entry point for the two
 * spammy elytra messages, so a HEAD inject there - cancelled when the throttle
 * says "shown too recently" - drops the duplicates without touching anything else
 * Baritone prints.
 *
 * <p>{@code Helper} lives in Baritone's public {@code baritone.api} package, which
 * is <b>not</b> minified, so {@code logDirect} keeps its real name. The descriptor
 * is {@code (Ljava/lang/String;)V} - no Minecraft type - so it's identical on every
 * Minecraft version (1.21.x and 26.x alike); {@code remap = false} throughout.
 */
@Mixin(targets = "baritone.api.utils.Helper", remap = false)
public interface HelperMixin {

	@Inject(method = "logDirect(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false)
	private void elytraeverywhere$throttleSpam(String message, CallbackInfo ci) {
		if (MessageThrottle.shouldSuppress(message)) {
			ci.cancel();
		}
	}
}
