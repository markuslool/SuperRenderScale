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
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the exact view/projection state used for the level each frame,
 * feeding depth-based reprojection (the core of FSR2-style temporal).
 * Public fields on {@code CameraRenderState} make this allocation-free.
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
        if (rs == null || cameraState == null || cameraState.pos == null) return;
        try {
            // viewMatrix arg is rotation-only; the projection comes from
            // Camera.projection inside capture (CameraRenderState's copy is stale).
            rs.captureCameraMatrices(viewMatrix,
                    cameraState.pos.x, cameraState.pos.y, cameraState.pos.z, renderBlockOutline);
        } catch (Exception ignored) {
        }
    }
}
//?} else {
/*// Older versions keep jitter-only temporal (no depth reprojection yet).
*///?}
