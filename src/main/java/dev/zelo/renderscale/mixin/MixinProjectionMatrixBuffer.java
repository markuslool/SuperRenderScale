//? > 26.1 {
package dev.zelo.renderscale.mixin;

import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import dev.zelo.renderscale.RenderScale;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures the EXACT projection vanilla uploads for the level render.
 *
 * <p>{@code GameRenderer.renderLevel} copies
 * {@code CameraRenderState.projectionMatrix} (already jittered by
 * {@code MixinCameraExtract}), composes view-bob / hurt tilt / nausea on top
 * and uploads the result through this buffer. Hand-rebuilding that matrix
 * from setup params can never match it, so the reprojection reads it here
 * instead. The HUD-3D buffer uses the {@code getBuffer(Projection)}
 * overload, so this only fires for raw-matrix uploads (the level one runs
 * right before {@code LevelRenderer.render} every frame).
 */
@Mixin(ProjectionMatrixBuffer.class)
@MixinEnvironment(type = MixinEnvironment.Env.CLIENT)
public abstract class MixinProjectionMatrixBuffer {
    @Inject(method = "getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;", at = @At("HEAD"))
    private void renderScale$captureProjection(Matrix4f matrix, CallbackInfoReturnable<?> cir) {
        RenderScale rs = RenderScale.getInstance();
        if (rs == null || matrix == null) return;
        try {
            rs.captureGpuProjection(matrix);
        } catch (Exception ignored) {
        }
    }
}
//?} else {
/*// Exact projection capture only exists where depth reprojection exists (> 26.1).
*///?}
