//? > 26.1 {
package dev.zelo.renderscale.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Intentional no-op passthrough (kept so the projection cache is never
 * polluted by us).
 *
 * <p>Do NOT jitter here: on 26.2 the level render consumes
 * {@code CameraRenderState.projectionMatrix}, filled via
 * {@code Projection.getMatrix(dest)} whose return value — the only thing
 * this injection could modify — is discarded with {@code pop} inside
 * {@code Camera.extractRenderState}. Any "jitter" applied here reaches no
 * frame; it only advanced the shared Halton sequence and desynced the CPU
 * side (stale constant offset = ghosting without detail). Real jittering
 * happens in {@code MixinCameraExtract}.
 */
@Mixin(Projection.class)
@MixinEnvironment(type = MixinEnvironment.Env.CLIENT)
public abstract class MixinProjection {
    @ModifyReturnValue(method = "getMatrix", at = @At("RETURN"), require = 0)
    private Matrix4f renderScale$jitterProjection(Matrix4f original) {
        return original;
    }
}
//?} else {
/*// Projections on older versions are jittered via MixinGameRendererTemporal.
*///?}
