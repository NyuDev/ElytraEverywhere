package fr.nyuway.elytraeverywhere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Makes Baritone's lines much easier to see, especially far away.
 *
 * <p>{@code IRenderer.startLines(Color)} hard-codes {@code 0.4} alpha, so the
 * thin path line is quite transparent and all but vanishes over long distances.
 * We bump that single constant to a far more opaque value. (The alpha-taking
 * {@code startLines(Color, float)} overload is untouched, so anything that asks
 * for a specific alpha keeps it.)
 *
 * <p>{@code startLines} is minified to {@code a} in the Meteor build, matched by
 * descriptor. {@code IRenderer} is an interface, hence the interface mixin.
 */
@Mixin(targets = "baritone.utils.IRenderer", remap = false)
public interface IRendererMixin {

	@ModifyConstant(
			method = "a(Ljava/awt/Color;)Lnet/minecraft/client/render/BufferBuilder;",
			constant = @Constant(floatValue = 0.4f),
			remap = true
	)
	private static float elytraeverywhere$moreOpaqueLines(float original) {
		return 0.7f; // clearly visible but still a bit see-through, as requested
	}
}
