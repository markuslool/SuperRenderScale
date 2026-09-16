package dev.zelo.renderscale;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;

//? >= 1.21.11 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;

//? 1.21.11
//import com.mojang.blaze3d.platform.DepthTestFunction;

import static net.minecraft.client.renderer.RenderPipelines.GLOBALS_SNIPPET;
//?}
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.zelo.renderscale.accessors.MainRenderTargetSetter;
import dev.zelo.renderscale.config.RenderScaleConfig;
import dev.zelo.renderscale.platform.Platform;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

//? > 26.1 {
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import net.minecraft.client.renderer.BindGroupLayouts;
//?}

//? >= 1.21.5 && < 1.21.11
//import dev.zelo.renderscale.accessors.GICommandEncoderThing;

//? >= 1.21.5 {
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
//?}

//? >= 1.21.11 {
import com.mojang.blaze3d.systems.CommandEncoder;
//?}

//? > 1.21.1
import net.minecraft.util.profiling.Profiler;

//? fabric {
import dev.zelo.renderscale.platform.fabric.FabricPlatform;
//?} neoforge {
/*import dev.zelo.renderscale.platform.neoforge.NeoforgePlatform;
 *///?} forge {
/*import dev.zelo.renderscale.platform.forge.ForgePlatform;
 *///?}

//? >= 1.21.5 {

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;

//? > 26.1 {
import java.util.Optional;
//? } else
//import java.util.OptionalInt;

//?}

// This class is part of the common project meaning it is shared between all supported loaders. Code written here can only
// import and access the vanilla codebase, libraries used by vanilla, and optionally third party libraries that provide
// common compatible binaries. This means common code can not directly use loader specific concepts such as Forge events
// however it will be compatible with all supported mod loaders.
public class RenderScale {
    private static Minecraft client = Minecraft.getInstance();

    // This is RenderScale's renderTarget (scaled)
    @Nullable
    public RenderTarget renderTarget;

    // This is Minecraft's renderTarget (native res)
    @Nullable
    public RenderTarget clientRenderTarget;

    @Nullable
    private RenderTarget fsrIntermediateTarget;

    //? >= 1.21.11 {
    @Nullable
    private RenderTarget taaHistoryA;
    @Nullable
    private RenderTarget taaHistoryB;
    private boolean taaFlipFlop = false;
    private boolean taaPrimed = false;
    private final dev.zelo.renderscale.temporal.TemporalJitter temporalJitter = new dev.zelo.renderscale.temporal.TemporalJitter();
    private dev.zelo.renderscale.temporal.Vec4UniformBuffer taaUbo;
    private dev.zelo.renderscale.temporal.Vec4UniformBuffer rcasUbo;
    private dev.zelo.renderscale.temporal.Vec4UniformBuffer reprojUbo;
    //?}

    //? > 26.1 {
    // Depth-reprojection state (FSR2-style camera motion vectors).
    // curVP/invCurVP describe THIS frame, prevVP is last frame's.
    private final Matrix4f reprojCurProj = new Matrix4f();
    private final Matrix4f reprojCurView = new Matrix4f();
    private final Matrix4f reprojCurVP = new Matrix4f();
    private final Matrix4f reprojPrevVP = new Matrix4f();
    private final Matrix4f reprojTmp = new Matrix4f();
    private boolean hasReproj = false;
    private int debugReprojCounter = 0;
    private long captureCalls = 0L;
    private long blitCalls = 0L;
    private float lastCapturedM33 = Float.NaN;
    private boolean lastCaptureOutlineFlag = false;
    private String lastProjSetup = "?";

    /**
     * Called once per frame from the level renderer. Builds the projection
     * MANUALLY from Camera.projection's setup params (fov/size/near/far) instead
     * of trusting getMatrix output (proved degenerate more than once), then
     * applies the same jitter the GPU path gets via MixinProjection.
     * View is composed as V = R * T(-pos).
     */
    public void captureCameraMatrices(org.joml.Matrix4fc viewRot, double px, double py, double pz, boolean outlineFlag) {
        if (viewRot == null) return;
        captureCalls++;
        lastCaptureOutlineFlag = outlineFlag;
        try {
            if (client == null || client.gameRenderer == null || client.gameRenderer.mainCamera() == null) return;
            var projection = ((dev.zelo.renderscale.mixin.accessors.MixinCameraAccessor) client.gameRenderer.mainCamera())
                    .renderScale$getProjection();
            if (projection == null) return;
            float pfov = projection.fov();
            float pw = projection.width();
            float ph = projection.height();
            float pn = projection.zNear();
            float pf = projection.zFar();
            lastProjSetup = pfov + "," + pw + "x" + ph + "," + pn + "-" + pf;
            if (!(pw > 0 && ph > 0 && pfov > 1 && pfov < 179 && pn > 0 && pf > pn)) {
                Constants.LOG.warn("[RenderScale {}] insane projection setup ({}), reprojection skipped",
                        Constants.BUILD_TAG, lastProjSetup);
                hasReproj = false;
                return;
            }
            reprojCurProj.identity().perspective((float) Math.toRadians(pfov), pw / ph, pn, pf);
            applyProjectionJitter(reprojCurProj);
            lastCapturedM33 = reprojCurProj.m33();
            // Sanity gate: a real perspective matrix. Never feed garbage to history.
            float m00 = reprojCurProj.m00();
            float m11 = reprojCurProj.m11();
            if (!Float.isFinite(m00) || !Float.isFinite(m11)
                    || m00 < 0.05f || m00 > 20.0f || m11 < 0.05f || m11 > 20.0f
                    || reprojCurProj.m33() != 0.0f) {
                Constants.LOG.warn("[RenderScale {}] insane rebuilt projection (m00={}, m11={}), reprojection skipped",
                        Constants.BUILD_TAG, m00, m11);
                hasReproj = false;
                return;
            }
            reprojCurView.set(viewRot).translate((float) -px, (float) -py, (float) -pz);
            reprojCurVP.set(reprojCurProj).mul(reprojCurView);
            hasReproj = true;
        } catch (Exception e) {
            hasReproj = false;
        }
    }

    /**
     * Sub-pixel jitter shared by the GPU path (MixinProjection) and the CPU
     * capture above: Halton texels polled once per frame in takeOver.
     */
    public void applyProjectionJitter(Matrix4f mat) {
        if (!hasTemporalJitter()) return;
        try {
            if (client == null || client.getWindow() == null) return;
            float scale = Math.max(RenderScale.getConfig().getScale(), 0.01f);
            int w = Math.max(client.getWindow().getWidth(), 1);
            int h = Math.max(client.getWindow().getHeight(), 1);
            mat.m20(mat.m20() + currentJitterX * 2.0f * scale / (float) w);
            mat.m21(mat.m21() + currentJitterY * 2.0f * scale / (float) h);
        } catch (Exception ignored) {
        }
    }
    //?}

    //? > 26.1 {
    public static final BindGroupLayout RCAS_LAYOUT = BindGroupLayout.builder()
            .withSampler("InSampler")
            .withUniform("RcasParams", UniformType.UNIFORM_BUFFER)
            .build();
    public static final BindGroupLayout TAA_LAYOUT = BindGroupLayout.builder()
            .withSampler("InSampler")
            .withSampler("HistorySampler")
            .withSampler("DepthSampler")
            .withUniform("TaaParams", UniformType.UNIFORM_BUFFER)
            .build();
    public static final BindGroupLayout REPROJ_LAYOUT = BindGroupLayout.builder()
            .withUniform("Reproj", UniformType.UNIFORM_BUFFER)
            .build();
    //?}

    //? >= 1.21.11 {
    public static RenderPipeline FSR_EASU_PIPELINE =
            RenderPipeline.builder(GLOBALS_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("renderscale", "pipeline/fsr_easu"))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Identifier.fromNamespaceAndPath("renderscale", "core/easu"))
                //? <= 26.1 {
                    /*//? 1.21.11 {
                /^.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)

                    ^///?}
                    .withSampler("InSampler")
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    *///?} else {

                .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    //?}

                .build();

    public static RenderPipeline FSR_RCAS_PIPELINE =
            RenderPipeline.builder(GLOBALS_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("renderscale", "pipeline/fsr_rcas"))
                    .withVertexShader("core/screenquad")
                    .withFragmentShader(Identifier.fromNamespaceAndPath("renderscale", "core/rcas"))
                    //? > 26.1 {
                    .withBindGroupLayout(RCAS_LAYOUT)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    //?} else {
                    /*.withUniform("RcasParams", UniformType.UNIFORM_BUFFER)
                    .withSampler("InSampler")
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    *///?}
                    .build();

    public static RenderPipeline TAA_RESOLVE_PIPELINE =
            RenderPipeline.builder(GLOBALS_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("renderscale", "pipeline/taa_resolve"))
                    .withVertexShader("core/screenquad")
                    .withFragmentShader(Identifier.fromNamespaceAndPath("renderscale", "core/taa_resolve"))
                    //? > 26.1 {
                    .withBindGroupLayout(TAA_LAYOUT)
                    .withBindGroupLayout(REPROJ_LAYOUT)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    //?} else {
                    /*.withUniform("TaaParams", UniformType.UNIFORM_BUFFER)
                    .withUniform("Reproj", UniformType.UNIFORM_BUFFER)
                    .withSampler("InSampler")
                    .withSampler("HistorySampler")
                    .withSampler("DepthSampler")
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    *///?}
                    .build();
    //?}


    private static RenderScale instance;
    private boolean shouldScale = false;
    public boolean hasRun = false;

    //? >= 1.21.11 {
    // Current-frame jitter in pixels (Halton), polled once per frame by projection mixin.
    private float currentJitterX = 0.0f;
    private float currentJitterY = 0.0f;
    private boolean hasJitter = false;

    // Camera motion tracking for adaptive history weight.
    private double lastCamX, lastCamY, lastCamZ;
    private float lastCamYaw, lastCamPitch;
    private boolean hasLastCam = false;
    private float camMotion = 0.0f;

    public boolean isTemporalActive() {
        if (instance == null) return false;
        try {
            return CONFIG.getConfig().temporal;
        } catch (Exception e) {
            return false;
        }
    }

    /** Temporal resolve runs when enabled, unless a shaderpack owns the frame (unless forced). */
    public boolean shouldUseTemporal() {
        try {
            RenderScaleConfig cfg = CONFIG.getConfig();
            if (!cfg.temporal) return false;
            return !isIrisShaderActive() || cfg.temporalWithShaders;
        } catch (Exception e) {
            return false;
        }
    }

    /** Advance Halton sequence, store for both projection mixin and resolve pass. */
    public float[] pollTemporalJitter() {
        float[] j = temporalJitter.nextJitter();
        currentJitterX = j[0];
        currentJitterY = j[1];
        hasJitter = true;
        pollCameraMotion();
        return j;
    }

    /**
     * Camera motion 0 (still) .. 1 (fast turn/flight), from player pos/look delta.
     * Player look tracks the camera in 1st and 3rd person, good enough for a blend weight.
     * Call once per frame (done by {@link #pollTemporalJitter}).
     */
    public float pollCameraMotion() {
        float motion = 0.0f;
        try {
            if (client == null || client.player == null) {
                hasLastCam = false;
                camMotion = 0.0f;
                return 0.0f;
            }
            double x = client.player.getX();
            double y = client.player.getY();
            double z = client.player.getZ();
            float yaw = client.player.getYRot();
            float pitch = client.player.getXRot();
            if (hasLastCam) {
                double dx = x - lastCamX, dy = y - lastCamY, dz = z - lastCamZ;
                double pd = Math.sqrt(dx * dx + dy * dy + dz * dz);
                float yd = Math.abs(yaw - lastCamYaw) % 360.0f;
                if (yd > 180.0f) yd = 360.0f - yd;
                float pdd = Math.abs(pitch - lastCamPitch);
                // Translation is only approximately compensated (far-plane motion),
                // so weight it HARD: walking (~0.07/frame) must already cut history.
                // Rotation is exact, it needs less help.
                motion = (float) Math.min(1.0, pd / 0.12 + yd / 15.0 + pdd / 15.0);
                // teleport / snap turn: drop history, next frame renders clean
                if (pd > 5.0 || yd > 60.0f) taaPrimed = false;
            }
            lastCamX = x;
            lastCamY = y;
            lastCamZ = z;
            lastCamYaw = yaw;
            lastCamPitch = pitch;
            hasLastCam = true;
        } catch (Exception ignored) {
        }
        // fast attack, slow release so trails fade smoothly after stopping
        camMotion = Math.max(motion, camMotion * 0.85f);
        return camMotion;
    }

    public float currentJitterX() { return currentJitterX; }
    public float currentJitterY() { return currentJitterY; }
    public boolean hasTemporalJitter() { return hasJitter; }

    /**
     * True when an Iris shaderpack is active. Shaderpacks own the frame
     * (many run their own TAA on view-dependent effects like water):
     * our history blend would only smear their speculars, so temporal is bypassed.
     * Reflection to avoid a hard Iris dependency.
     */
    public boolean isIrisShaderActive() {
        try {
            if (PLATFORM == null || !PLATFORM.isModLoaded("iris")) return false;
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isShaderPackInUse").invoke(inst);
        } catch (Exception e) {
            return false;
        }
    }
    //?}

    public static final ConfigHolder<RenderScaleConfig> CONFIG = RenderScaleConfig.init();

    public static final Platform PLATFORM = createPlatformInstance();

    static Platform xplat() {
        return PLATFORM;
    }

    private static Platform createPlatformInstance() {
        //? fabric {
        return new FabricPlatform();
        //?} neoforge {
        /*return new NeoforgePlatform();
         *///?} forge {
        /*return new ForgePlatform();
         *///?}
    }

    // Fabric
    public static void init() {
        instance = new RenderScale();
    }

    // NeoForge made it so that the mod loads before Minecraft (but not fabric...), so this is needed to get the "actual" Minecraft instance
    public static void init(Minecraft client) {
        instance = new RenderScale();
        RenderScale.client = client;
    }

    public static RenderScale getInstance() {
        return instance;
    }

    public static RenderScaleConfig getConfig() {
        return CONFIG.getConfig();
    }

    public void onResolutionChanged() {
        if (getWindow() == null) return;

        ProfilerFiller profiler = getProfile();
        profiler.push("renderscale_resize_targets");

        resizeRenderTarget();

        profiler.pop();
    }

    public void setClientRenderTarget(RenderTarget renderTarget) {
        //? < 26.2 {
        /*((MainRenderTargetSetter) client).renderScale$setMainRenderTarget(renderTarget);
        *///? } else
        ((MainRenderTargetSetter) client.gameRenderer).renderScale$setMainRenderTarget(renderTarget);
    }

    public ProfilerFiller getProfile() {
        //? > 1.21.1 {
        return Profiler.get();
        //? } else
        //return RenderScale.client.getProfiler();
    }

    public void setShouldScale(boolean shouldScale) {
        ProfilerFiller profiler = getProfile();
        profiler.push("renderscale_rescaling");
        //? >= 1.21.5 {
        this.shouldScale = shouldScale;

        Window window = client.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        int scaledWidth = Math.clamp(width, 1, 65536);
        int scaledHeight = Math.clamp(height, 1, 65536);



        if (renderTarget == null) {
//            renderTarget = new TextureTarget("RenderScale", scaledWidth, scaledHeight, true);
            renderTarget = new MainTarget(scaledWidth, scaledHeight);
        }

        if (clientRenderTarget == null) {
            //? < 26.2 {
            /*clientRenderTarget = client.getMainRenderTarget();
            *///? } else
            clientRenderTarget = client.gameRenderer.mainRenderTarget();
        }

        if (shouldScale) {
            setClientRenderTarget(renderTarget);

//            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(renderTarget.getDepthTexture(), 1.0);
//            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(renderTarget.getColorTexture(), 0);
        } else {
            try {
                setClientRenderTarget(clientRenderTarget);

//                RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(renderTarget.getColorTexture(), clientRenderTarget.getColorTexture(), 0, 0, 0, 0, 0, scaledWidth, scaledHeight);

                  //? >= 1.21.5 && < 1.21.11 {
                /*((GICommandEncoderThing) RenderSystem.getDevice().createCommandEncoder()).renderScale$copyAndResizeTexture(
                        renderTarget.getColorTexture(), clientRenderTarget.getColorTexture(),
                        0, 0, 0, 0, 0,
                        renderTarget.width, renderTarget.height,
                        width, height, false
                );
                ((GICommandEncoderThing) RenderSystem.getDevice().createCommandEncoder()).renderScale$copyAndResizeTexture(
                        renderTarget.getDepthTexture(), clientRenderTarget.getDepthTexture(),
                        0, 0, 0, 0, 0,
                        renderTarget.width, renderTarget.height,
                        width, height, true
                );
                  //~ if 1.21.5 && fabric 'getColorTextureView' -> 'getColorTexture'
                  clientRenderTarget.blitAndBlendToTexture(renderTarget.getColorTextureView());
                  *///?} else
                blitAndBlendToTexture(renderTarget, clientRenderTarget, CONFIG.getConfig().getFilter() ? FilterMode.LINEAR : FilterMode.NEAREST);
//                blitAndBlendToTexture(renderTarget, clientRenderTarget, FilterMode.LINEAR);
//                //?}
//                renderTarget.blitAndBlendToTexture(clientRenderTarget.getColorTextureView());
//                clientRenderTarget.copyDepthFrom(renderTarget);
//                renderTarget.blitToScreen();
            } catch (Exception e) {
                Constants.LOG.error("Error copying texture", e);
            }
        }
        //?} else {
        /*if (this.shouldScale == shouldScale) return;

        Window window = client.getWindow();
        if (renderTarget == null) {
            this.shouldScale = true;
            renderTarget = new MainTarget(window.getWidth(), window.getHeight());
        }

        this.shouldScale = shouldScale;

        if (shouldScale) {
            clientRenderTarget = client.getMainRenderTarget();

            setClientRenderTarget(renderTarget);
            //? <= 1.21.4 {
            /^renderTarget.bindWrite(true);
            ^///?}
        } else {
            setClientRenderTarget(clientRenderTarget);
            //? <= 1.21.4 {
            /^client.getMainRenderTarget().bindWrite(true);
            ^///?}

            //? <= 1.21.4 {
            /^//? <= 1.21.1 {
            /^¹// TODO: Forced to do this because of sodium + kubejs... does this affect macOS users?
            renderTarget.blitToScreen(window.getWidth(), window.getHeight(), false);
            ¹^///?} else {
            renderTarget.blitToScreen(window.getWidth(), window.getHeight());
            //?}
            ^///?} else {
            renderTarget.blitAndBlendToScreen(window.getWidth(), window.getHeight());
            //?}
        }
        *///?}
        profiler.pop();
    }

    // Takes into account shouldScale
    public double getCurrentScaleFactor() {
        return shouldScale ? getConfig().getScale() : 1;
    }

    // ---- Dynamic resolution: EMA frame time + stepped scale, loader-agnostic ----
    private long lastFrameNs = 0L;
    private double frameTimeEmaMs = 16.6;
    private long lastDynAdjustMs = 0L;

    /** Called at the start of every world frame (see MixinGameRenderer). */
    public void onFrameStart() {
        long now = System.nanoTime();
        if (lastFrameNs != 0L) {
            double dt = (now - lastFrameNs) / 1_000_000.0;
            if (dt > 0.0 && dt < 250.0) { // ignore tab-out / stalls
                frameTimeEmaMs += (dt - frameTimeEmaMs) * 0.05;
            }
            maybeAdjustDynamicScale(System.currentTimeMillis());
        }
        lastFrameNs = now;
    }

    public double getAverageFps() {
        return frameTimeEmaMs > 0.0 ? 1000.0 / frameTimeEmaMs : 0.0;
    }

    public String getUpscalerName() {
        //? >= 1.21.11 {
        try {
            if (shouldUseTemporal()) return "TAAU";
            if (getConfig().fsr) return "FSR1";
        } catch (Exception ignored) {
        }
        //?}
        try {
            float s = getConfig().getScale();
            if (s > 1.01f) return "SSAA";
            if (s < 0.99f) return "Upscaled";
            return "Native";
        } catch (Exception e) {
            return "?";
        }
    }

    private void maybeAdjustDynamicScale(long nowMs) {
        RenderScaleConfig cfg;
        try {
            cfg = CONFIG.getConfig();
        } catch (Exception e) {
            return;
        }
        if (!cfg.dynamicScale) {
            lastDynAdjustMs = nowMs;
            return;
        }
        if (nowMs - lastDynAdjustMs < 750) return; // hysteresis window
        lastDynAdjustMs = nowMs;

        double fps = getAverageFps();
        float cur = cfg.scale;
        float min = Math.max(cfg.dynMinScale, 25) / 100.0f;
        float max = Math.max(cfg.dynMaxScale, min * 100.0f) / 100.0f;
        float next = cur;
        if (fps < cfg.targetFps - 3 && cur > min) {
            next = Math.max(min, cur - 0.05f);
        } else if (fps > cfg.targetFps + 5 && cur < max) {
            next = Math.min(max, cur + 0.05f);
        }
        if (next != cur) {
            cfg.scale = next;
            // in-memory only (no disk spam); resize targets + re-prime temporal history
            onResolutionChanged();
        }
    }

    @Nullable
    private Window getWindow() {
        return client.getWindow();
    }

    public void resizeRenderTarget() {
        resize(renderTarget);
        resize(fsrIntermediateTarget);
    //? >= 1.21.11 {
        resize(taaHistoryA);
        resize(taaHistoryB);
        if (taaHistoryA != null && taaHistoryB != null) {
            // resolution changed -> history invalid, reset jitter to avoid ghost smear
            temporalJitter.reset();
            taaPrimed = false;
        }
        //?}
        //? <= 1.21.1 {
        /*resize(client.levelRenderer.entityTarget());

        if (hasRun) client.levelRenderer.onResourceManagerReload(client.getResourceManager());
        *///?}
    }

    public void resizeMinecraftRenderTargetSize() {
//        resize(client.levelRenderer.entityOutlineTarget());
    }

    public int clamp(int number, int min, int max) {
        return Math.max(min, Math.min(number, max));
    }

    private void resize(@Nullable RenderTarget renderTarget) {
        if (renderTarget == null) return;

        boolean prev = shouldScale;
        shouldScale = true;

        Window window = client.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        int scaledWidth = clamp(width, 1, 65536);
        int scaledHeight = clamp(height, 1, 65536);

        //? >= 1.21.2 {
        renderTarget.resize(scaledWidth, scaledHeight);
        //?} else {
        /*renderTarget.resize(scaledWidth, scaledHeight, true);
        *///?}
//        //? >= 1.21.6 {
//        //?} else {
//        /*renderTarget.resize(scaledWidth, scaledHeight);
//        *///?}

        shouldScale = prev;
    }

    //? >= 1.21.11 {
    public void blitAndBlendToTexture(final RenderTarget input, final RenderTarget output, final FilterMode filter) {
        RenderSystem.assertOnRenderThread();

        //? < 1.21.11 {
        /*try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Blit render target", output.getColorTextureView(), OptionalInt.empty())) {
        *///? } else
        if (shouldUseTemporal()) {
            blitTemporal(input, output, filter);
        } else if (getConfig().fsr) {
            if (fsrIntermediateTarget == null) {
                fsrIntermediateTarget = new MainTarget(output.width, output.height);
            } else if (fsrIntermediateTarget.width != output.width || fsrIntermediateTarget.height != output.height) {
                fsrIntermediateTarget.resize(output.width, output.height);
            }

            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "FSR: EASU", fsrIntermediateTarget.getColorTextureView(), /*? > 26.1 {*/ Optional /*?} else {*/ /*OptionalInt *//*?}*/.empty())) {
                renderPass.setPipeline(FSR_EASU_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filter));
                //? < 26.2 {
                /*renderPass.draw(0, 3);
                 *///?} else
                renderPass.draw(3, 1, 0, 0);
            }

            //? > 26.1 {
            if (rcasUbo == null) rcasUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale RCAS params");
            CommandEncoder rcasEncoder = RenderSystem.getDevice().createCommandEncoder();
            com.mojang.blaze3d.buffers.GpuBufferSlice rcasParams = rcasUbo.upload(rcasEncoder, getConfig().getRcasAttenuation(), 0.0f, 0.0f, 0.0f);
            try (RenderPass renderPass = rcasEncoder.createRenderPass(() -> "FSR: RCAS", output.getColorTextureView(), Optional.empty())) {
                renderPass.setPipeline(FSR_RCAS_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", fsrIntermediateTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filter));
                renderPass.setUniform("RcasParams", rcasParams);
                renderPass.draw(3, 1, 0, 0);
            }
            rcasUbo.nextFrame();
            //?} else {
            /*if (rcasUbo == null) rcasUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale RCAS params");
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "FSR: RCAS", output.getColorTextureView(), OptionalInt.empty())) {
                renderPass.setPipeline(FSR_RCAS_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", fsrIntermediateTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filter));
                renderPass.setUniform("RcasParams", rcasUbo.upload(getConfig().getRcasAttenuation(), 0.0f, 0.0f, 0.0f));
                renderPass.draw(0, 3);
            }
            rcasUbo.nextFrame();
            *///?}
        } else {
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Blit render target", output.getColorTextureView(), /*? > 26.1 {*/ Optional /*?} else {*/ /*OptionalInt *//*?}*/.empty())) {
                // Tracy blit is weird because I believe it's technically a debug pass.
                // However, it looks exactly the same as vanilla, so I'm assuming it's fine.
                renderPass.setPipeline(RenderPipelines.TRACY_BLIT);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filter));
                //? < 26.2 {
                /*renderPass.draw(0, 3);
                 *///?} else
                renderPass.draw(3, 1, 0, 0);
            }
        }

        // copying depth doesn't seem to do anything?
//        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Blit render target", output.getDepthTextureView(), OptionalInt.empty())) {
////            renderPass.setPipeline(RenderPipelines.FOG_SNIPPET);
//            RenderSystem.bindDefaultUniforms(renderPass);
//            renderPass.bindTexture("InSampler2", input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
//            renderPass.draw(0, 3);
//        }
    }

    public void resetTemporalHistory() {
        temporalJitter.reset();
        taaFlipFlop = false;
        taaPrimed = false;
        hasJitter = false;
        currentJitterX = 0.0f;
        currentJitterY = 0.0f;
    }

    private void blitTemporal(final RenderTarget input, final RenderTarget output, final FilterMode filter) {
        if (taaHistoryA == null) taaHistoryA = new MainTarget(output.width, output.height);
        else if (taaHistoryA.width != output.width || taaHistoryA.height != output.height) taaHistoryA.resize(output.width, output.height);
        if (taaHistoryB == null) taaHistoryB = new MainTarget(output.width, output.height);
        else if (taaHistoryB.width != output.width || taaHistoryB.height != output.height) taaHistoryB.resize(output.width, output.height);

        RenderTarget readHistory = taaFlipFlop ? taaHistoryA : taaHistoryB;
        RenderTarget writeHistory = taaFlipFlop ? taaHistoryB : taaHistoryA;
        taaFlipFlop = !taaFlipFlop;

        // pass 0 (upscale only): edge-adaptive EASU base instead of bilinear,
        // so the temporal resolve accumulates an already-sharp image.
        // Supersampling (input >= output) skips this: bilinear downsample is fine.
        // Can be disabled via temporalEasu on weak GPUs (biggest single pass cost).
        RenderTarget resolveInput = input;
        if (getConfig().temporalEasu && (input.width < output.width || input.height < output.height)) {
            if (fsrIntermediateTarget == null) fsrIntermediateTarget = new MainTarget(output.width, output.height);
            else if (fsrIntermediateTarget.width != output.width || fsrIntermediateTarget.height != output.height) {
                fsrIntermediateTarget.resize(output.width, output.height);
            }
            //? > 26.1 {
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "TAA: EASU base", fsrIntermediateTarget.getColorTextureView(), Optional.empty())) {
                renderPass.setPipeline(FSR_EASU_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filter));
                renderPass.draw(3, 1, 0, 0);
            }
            //?} else {
            /*try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "TAA: EASU base", fsrIntermediateTarget.getColorTextureView(), OptionalInt.empty())) {
                renderPass.setPipeline(FSR_EASU_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filter));
                renderPass.draw(0, 3);
            }
            *///?}
            resolveInput = fsrIntermediateTarget;
        }

        // Jitter was polled by projection mixin earlier this frame.
        // Fallback to polling here (menu / first frame where projection never ran).
        float jx = hasJitter ? currentJitterX : pollTemporalJitter()[0];
        float jy = hasJitter ? currentJitterY : currentJitterY;
        hasJitter = false;
        float sharp = clamp(getConfig().temporalSharpness, 0, 100) / 100.0f;
        // first frame after enable/resize: no valid history yet, show current frame as-is.
        // while moving fast, lean on the current frame: kills trails at the cost of
        // temporal detail (standard trick, detail re-accumulates when still).
        float blendBase = taaPrimed ? (0.1f + 0.75f * camMotion) : 1.0f;
        taaPrimed = true;
        float debugView = 0.0f;
        float useDepthTex = 0.0f;
        try {
            debugView = Math.max(0, Math.min(2, getConfig().temporalDebug));
            if (getConfig().temporalReprojDepth) useDepthTex = 1.0f;
        } catch (Exception ignored) {
        }

        if (taaUbo == null) taaUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale TAA params");

        // pass 1: temporal resolve input(low-res) + history -> writeHistory(native)
        //? > 26.1 {
        CommandEncoder taaEncoder = RenderSystem.getDevice().createCommandEncoder();
        // Depth reprojection covers camera AND jitter motion; fall back to plain
        // jitter offset when no matrices were captured (menus, first frames).
        boolean useReproj = hasReproj && getConfig().temporalReproj;
        Matrix4f invCurVP = reprojTmp.set(reprojCurVP);
        if (useReproj) invCurVP.invert();
        if (reprojUbo == null) reprojUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale reproj");
        com.mojang.blaze3d.buffers.GpuBufferSlice taaParams = taaUbo.upload(taaEncoder,
                useReproj ? 0.0f : jx / Math.max(output.width, 1),
                useReproj ? 0.0f : jy / Math.max(output.height, 1),
                blendBase, sharp);
        com.mojang.blaze3d.buffers.GpuBufferSlice reprojParams =
                reprojUbo.uploadReproj(taaEncoder, reprojPrevVP, invCurVP, useReproj ? 1.0f : 0.0f, debugView, useDepthTex);
        // Throttled trace for diagnosing reprojection (before prevVP shift!).
        // Reprojects screen-center far point on CPU and logs where history lands.
        debugReprojCounter++;
        blitCalls++;
        if (debugReprojCounter % 300 == 0) {
            try {
                org.joml.Vector4f ndc = new org.joml.Vector4f(0.0f, 0.0f, 1.0f, 1.0f);
                org.joml.Vector4f w = new org.joml.Vector4f(ndc).mul(invCurVP);
                w.div(w.w);
                org.joml.Vector4f pp = new org.joml.Vector4f(w).mul(reprojPrevVP);
                pp.div(pp.w);
                String ppos = "?";
                if (client != null && client.player != null) {
                    ppos = client.player.getX() + "," + client.player.getY() + "," + client.player.getZ();
                }
                Constants.LOG.info(
                        "[RenderScale {}] reproj use={} centerPrevUV=({},{}) prevT=({},{},{}) curT=({},{},{}) viewT=({},{},{}) player=({}) motion={} caps={} blits={} m33={} outline={} setup={} prevVP={} curVP={}",
                        Constants.BUILD_TAG, useReproj, pp.x * 0.5 + 0.5, pp.y * 0.5 + 0.5,
                        reprojPrevVP.m30(), reprojPrevVP.m31(), reprojPrevVP.m32(),
                        reprojCurVP.m30(), reprojCurVP.m31(), reprojCurVP.m32(),
                        reprojCurView.m30(), reprojCurView.m31(), reprojCurView.m32(),
                        ppos, camMotion,
                        captureCalls, blitCalls, lastCapturedM33, lastCaptureOutlineFlag, lastProjSetup,
                        java.util.Arrays.toString(reprojPrevVP.get(new float[16])),
                        java.util.Arrays.toString(reprojCurVP.get(new float[16])));
            } catch (Exception ignored) {
            }
        }
        try (RenderPass renderPass = taaEncoder.createRenderPass(() -> "TAA resolve", writeHistory.getColorTextureView(), Optional.empty())) {
            renderPass.setPipeline(TAA_RESOLVE_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InSampler", resolveInput.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filter));
            renderPass.bindTexture("HistorySampler", readHistory.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.bindTexture("DepthSampler", input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.setUniform("TaaParams", taaParams);
            renderPass.setUniform("Reproj", reprojParams);
            renderPass.draw(3, 1, 0, 0);
        }
        reprojPrevVP.set(reprojCurVP);
        //?} else {
        /*com.mojang.blaze3d.buffers.GpuBufferSlice taaParams = taaUbo.upload(
                jx / Math.max(output.width, 1),
                jy / Math.max(output.height, 1),
                blendBase, sharp);
        if (reprojUbo == null) reprojUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale reproj");
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "TAA resolve", writeHistory.getColorTextureView(), OptionalInt.empty())) {
            renderPass.setPipeline(TAA_RESOLVE_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InSampler", resolveInput.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filter));
            renderPass.bindTexture("HistorySampler", readHistory.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.bindTexture("DepthSampler", input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.setUniform("TaaParams", taaParams);
            renderPass.setUniform("Reproj", reprojUbo.uploadReproj(0.0f, debugView, useDepthTex));
            renderPass.draw(0, 3);
        }
        *///?}

        // pass 2: history -> screen (TRACY blit keeps color space identical)
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "TAA present", output.getColorTextureView(), /*? > 26.1 {*/ Optional /*?} else {*/ /*OptionalInt *//*?}*/.empty())) {
            renderPass.setPipeline(RenderPipelines.TRACY_BLIT);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InSampler", writeHistory.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            //? < 26.2 {
            /*renderPass.draw(0, 3);
             *///?} else
            renderPass.draw(3, 1, 0, 0);
        }

        taaUbo.nextFrame();
        if (reprojUbo != null) reprojUbo.nextFrame();
    }
    //?}
}
