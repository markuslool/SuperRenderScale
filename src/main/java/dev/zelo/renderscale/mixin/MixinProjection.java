//? > 26.1 {
package dev.zelo.renderscale.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import dev.zelo.renderscale.RenderScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Camera jitter for temporal upscaling on 26.2+, where projections live in
 * {@link Projection} instead of {@code GameRenderer.getProjectionMatrix}
 * (older versions are handled by {@code MixinGameRendererTemporal}).
 *
 * <p>Only true perspective matrices are jittered: JOML perspective sets m33
 * to exactly 0 while ortho sets it to 1, so UI/shadow ortho projections
 * pass through untouched.
 *
 * <p>NEVER mutate the returned matrix in place: it may be the projection's
 * cached internal matrix. Always work on a copy.
 */
@Mixin(Projection.class)
@MixinEnvironment(type = MixinEnvironment.Env.CLIENT)
public abstract class MixinProjection {
    @ModifyReturnValue(method = "getMatrix", at = @At("RETURN"), require = 0)
    private Matrix4f renderScale$jitterProjection(Matrix4f original) {
        RenderScale rs = RenderScale.getInstance();
        if (rs == null || !rs.isTemporalActive()) return original;
        if (original.m33() != 0.0f) return original;

        // Don't double-jitter when shaderpacks do their own TAA,
        // unless the user forces our temporal under shaders.
        try {
            if (rs.isIrisShaderActive() && !RenderScale.getConfig().temporalWithShaders) return original;
        } catch (Exception ignored) {
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return original;
        if (!rs.hasTemporalJitter()) return original;

        // Same values the CPU capture uses (single source: takeOver poll).
        Matrix4f out = new Matrix4f(original);
        rs.applyProjectionJitter(out);
        return out;
    }
}
//?} else {
/*// Projections on older versions are jittered via MixinGameRendererTemporal.
*///?}
