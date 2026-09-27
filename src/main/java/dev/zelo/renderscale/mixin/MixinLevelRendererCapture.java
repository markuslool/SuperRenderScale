//? > 26.1 {
package dev.zelo.renderscale.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import dev.zelo.renderscale.RenderScale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the exact view/projection state used for the level each frame,
 * feeding depth-based reprojection (the core of FSR2-style temporal).
 * The projection comes from the real GPU upload (see
 * MixinProjectionMatrixBuffer); the view arg is rotation-only, composed
 * here with the camera position (view bobbing lives in the projection).
 */
@Mixin(LevelRenderer.class)
@MixinEnvironment(type = MixinEnvironment.Env.CLIENT)
public abstract class MixinLevelRendererCapture {
    @Inject(method = "render", at = @At("HEAD"))
    private void renderScale$captureVP(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
            boolean renderBlockOutline, CameraRenderState cameraState, Matrix4fc viewMatrix,
            GpuBufferSlice projectionMatrixBuffer, Vector4f fogColor, boolean panoramicMode,
            CallbackInfo ci) {
        RenderScale rs = RenderScale.getInstance();
        if (rs == null || cameraState == null) return;
        try {
            rs.captureCameraMatrices(cameraState, viewMatrix);
        } catch (Exception ignored) {
        }
    }
}
//?} else {
/*// Older versions keep jitter-only temporal (no depth reprojection yet).
*///?}
