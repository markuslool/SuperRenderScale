package dev.zelo.renderscale.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.kikugie.fletching_table.annotation.MixinEnvironment;
import dev.zelo.renderscale.RenderScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Camera jitter for temporal upscaling, same trick as BSL/Complementary TAA:
 * offset projection by sub-pixel Halton sequence so each frame samples
 * slightly different texels, then {@code taa_resolve.fsh} accumulates history.
 *
 * <p>Matches all versions: 1.20.1/1.21.1 use {@code getProjectionMatrix(double)},
 * 1.21.11+/26.x use {@code getProjectionMatrix(float)} — matching by name
 * covers whichever overload exists on this version.
 */
@Mixin(GameRenderer.class)
@MixinEnvironment(type = MixinEnvironment.Env.CLIENT)
public abstract class MixinGameRendererTemporal {
    @ModifyReturnValue(method = "getProjectionMatrix", at = @At("RETURN"), require = 0)
    private Matrix4f renderScale$jitterProjection(Matrix4f original) {
        RenderScale rs = RenderScale.getInstance();
        //? >= 1.21.11 {
        if (rs == null || !rs.isTemporalActive()) return original;

        // Don't double-jitter when shaderpacks do their own TAA,
        // unless the user forces our temporal under shaders (pack TAA off).
        try {
            if (rs.isIrisShaderActive() && !RenderScale.getConfig().temporalWithShaders) return original;
        } catch (Exception ignored) {
            // no iris / no config — proceed with our own jitter
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return original;
        // getWidth() is already scaled by MixinWindow — correct, projection is for scaled target
        // Jitter was polled once in MixinGameRenderer.takeOver; only read it here
        // so multiple getProjectionMatrix calls per frame (main/culling/hand) stay in sync.
        if (!rs.hasTemporalJitter()) return original;
        int w = Math.max(mc.getWindow().getWidth(), 1);
        int h = Math.max(mc.getWindow().getHeight(), 1);

        // Stored jitter is in OUTPUT texels, but the projection renders
        // into the SCALED target: nativeW = scaledW / scale.
        float scale = 1.0f;
        try {
            scale = Math.max(RenderScale.getConfig().getScale(), 0.01f);
        } catch (Exception ignored) {}

        // NDC offset: jitterPx * 2 / size
        float dx = rs.currentJitterX() * 2.0f * scale / (float) w;
        float dy = rs.currentJitterY() * 2.0f * scale / (float) h;

        // JOML perspective: m20/m21 hold the off-center terms
        original.m20(original.m20() + dx);
        original.m21(original.m21() + dy);
        return original;
        //?} else {
        /*return original;
        *///?}
    }
}
