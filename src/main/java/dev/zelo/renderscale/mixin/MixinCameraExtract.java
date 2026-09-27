//? > 26.1 {
package dev.zelo.renderscale.mixin;

import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import dev.zelo.renderscale.RenderScale;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bakes the per-frame Halton jitter into the main camera projection.
 *
 * <p>Why here and not in {@code Projection.getMatrix}: on 26.2 the level
 * render consumes {@code CameraRenderState.projectionMatrix}, filled via
 * {@code Projection.getMatrix(dest)} whose <em>return value</em> — the only
 * thing a {@code ModifyReturnValue} mixin can touch — is discarded with
 * {@code pop} (see {@code Camera.extractRenderState} bytecode). Jittering
 * the return value therefore never reaches the GPU (it only corrupted the
 * CPU-side sequence). Mutating the state field instead lands in the exact
 * matrix vanilla copies, bob-composes and uploads.
 */
@Mixin(Camera.class)
@MixinEnvironment(type = MixinEnvironment.Env.CLIENT)
public abstract class MixinCameraExtract {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void renderScale$jitterProjection(CameraRenderState cameraState, float partialTick, CallbackInfo ci) {
        RenderScale rs = RenderScale.getInstance();
        if (rs == null || cameraState == null) return;
        if (!rs.shouldApplyJitter()) return;
        rs.applyCameraJitter(cameraState);
    }
}
//?} else {
/*// Camera-state jitter only exists where depth reprojection exists (> 26.1).
*///?}
