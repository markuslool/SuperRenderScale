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
import com.mojang.blaze3d.GpuFormat;
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
    @Nullable
    private RenderTarget taaDepthA;
    @Nullable
    private RenderTarget taaDepthB;
    private boolean taaFlipFlop = false;
    private boolean taaPrimed = false;
    // 0 = none yet, 1 = TAAU, 2 = FSR2. Switching modes invalidates history.
    private int resolveMode = 0;
    private final dev.zelo.renderscale.temporal.TemporalJitter temporalJitter = new dev.zelo.renderscale.temporal.TemporalJitter();
    private dev.zelo.renderscale.temporal.Vec4UniformBuffer taaUbo;
    private dev.zelo.renderscale.temporal.Vec4UniformBuffer rcasUbo;
    private dev.zelo.renderscale.temporal.Vec4UniformBuffer reprojUbo;
    //?}

    //? > 26.1 {
    // Exact per-frame render matrices for FSR2-style reprojection.
    // curVP/invCurVP describe THIS frame, prevVP is last frame's.
    //
    // Why this machinery exists: on 26.2 the level render consumes
    // CameraRenderState.projectionMatrix, filled via Projection.getMatrix(dest)
    // whose RETURN value — the only thing a ModifyReturnValue mixin can touch —
    // is discarded with `pop` (see Camera.extractRenderState bytecode). Jitter
    // is therefore baked in by MixinCameraExtract at the end of
    // Camera.extractRenderState, and the FINAL matrix (jitter + view-bob +
    // hurt tilt + nausea, all composed by vanilla before upload) is captured
    // from ProjectionMatrixBuffer.getBuffer. Rebuilding perspective by hand
    // can never match that, so we don't try.
    // View bobbing lives in the projection on 26.2, so the view side stays a
    // plain rotation x translation composition.
    private final Matrix4f reprojCurProj = new Matrix4f();
    private final Matrix4f reprojCurView = new Matrix4f();
    private final Matrix4f reprojCurVP = new Matrix4f();
    private final Matrix4f reprojPrevVP = new Matrix4f();
    private final Matrix4f reprojTmp = new Matrix4f();
    private boolean hasReproj = false;
    // Handshake: set false by takeOver, true by capture. Guards stale matrices
    // (menus, panorama, early-out renderLevel): without a fresh capture the
    // resolve falls back to jitter-only instead of reprojecting garbage.
    private boolean capturedThisFrame = false;
    // Render-time jitter in output texels for the frame being resolved.
    // Recorded by applyCameraJitter keyed on state identity, snapshotted by
    // capture — never a global "latest" (several projections share version
    // numbers, so "latest polled" need not be the world's).
    private float renderJitterX = 0.0f;
    private float renderJitterY = 0.0f;
    private Object lastJitteredState = null;
    private float lastJitterX = 0.0f;
    private float lastJitterY = 0.0f;

    /** True when the camera projection should be jittered this frame. */
    public boolean shouldApplyJitter() {
        if (!isTemporalActive()) return false;
        try {
            if (isIrisShaderActive() && !RenderScale.getConfig().temporalWithShaders) return false;
        } catch (Exception ignored) {
        }
        return true;
    }

    /** takeOver side of the capture handshake (see capturedThisFrame). */
    public void beginReprojFrame() {
        capturedThisFrame = false;
    }

    /**
     * Called from MixinCameraExtract at the end of Camera.extractRenderState.
     * Advances the Halton sequence and bakes the offset into the state's
     * projection — the exact matrix the level upload copies — recording the
     * values by state identity for captureCameraMatrices.
     */
    public void applyCameraJitter(net.minecraft.client.renderer.state.level.CameraRenderState state) {
        if (state == null || state.projectionMatrix == null) return;
        try {
            // Perspective only: JOML perspective sets m33 to exactly 0.
            if (state.projectionMatrix.m33() != 0.0f) return;
            if (client == null || client.getWindow() == null) return;
            float[] j = pollTemporalJitter();
            float scale = Math.max(RenderScale.getConfig().getScale(), 0.01f);
            int w = Math.max(client.getWindow().getWidth(), 1);
            int h = Math.max(client.getWindow().getHeight(), 1);
            state.projectionMatrix.m20(state.projectionMatrix.m20() + j[0] * 2.0f * scale / (float) w);
            state.projectionMatrix.m21(state.projectionMatrix.m21() + j[1] * 2.0f * scale / (float) h);
            lastJitteredState = state;
            lastJitterX = j[0];
            lastJitterY = j[1];
        } catch (Exception ignored) {
        }
    }

    /**
     * Called from MixinProjectionMatrixBuffer: the exact projection vanilla
     * uploads for the level (jitter + bob + hurt + nausea baked in).
     */
    public void captureGpuProjection(Matrix4f mat) {
        if (mat == null) return;
        try {
            reprojCurProj.set(mat);
        } catch (Exception ignored) {
        }
    }

    /**
     * Called once per frame from the level renderer. Combines the exact
     * uploaded projection with the plain rotation x translation view.
     */
    public void captureCameraMatrices(net.minecraft.client.renderer.state.level.CameraRenderState state,
            org.joml.Matrix4fc viewRot) {
        capturedThisFrame = false;
        hasReproj = false;
        if (state == null || viewRot == null || state.pos == null) return;
        try {
            if (state.isPanoramicMode) return;
            reprojCurView.set(viewRot).translate((float) -state.pos.x, (float) -state.pos.y, (float) -state.pos.z);
            reprojCurVP.set(reprojCurProj).mul(reprojCurView);
            if (state == lastJitteredState) {
                renderJitterX = lastJitterX;
                renderJitterY = lastJitterY;
            } else {
                // Jitter unknown (something re-extracted after us): fall back
                // to zero — a static offset converges cleanly, a wrong one ghosts.
                renderJitterX = 0.0f;
                renderJitterY = 0.0f;
                return;
            }
            capturedThisFrame = true;
            hasReproj = true;
        } catch (Exception e) {
            hasReproj = false;
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
            .withSampler("HistoryDepthSampler")
            .withUniform("TaaParams", UniformType.UNIFORM_BUFFER)
            .build();
    public static final BindGroupLayout TAA_DEPTH_LAYOUT = BindGroupLayout.builder()
            .withSampler("InSampler")
            .build();
    public static final BindGroupLayout REPROJ_LAYOUT = BindGroupLayout.builder()
            .withUniform("Reproj", UniformType.UNIFORM_BUFFER)
            .build();
    public static final BindGroupLayout FSRCNNX_1SAMPLER = BindGroupLayout.builder()
            .withSampler("InSampler")
            .build();
    public static final BindGroupLayout FSRCNNX_2SAMPLER = BindGroupLayout.builder()
            .withSampler("ASampler")
            .withSampler("BSampler")
            .build();
    public static final BindGroupLayout FSRCNNX_3SAMPLER = BindGroupLayout.builder()
            .withSampler("ASampler")
            .withSampler("BSampler")
            .withSampler("CSampler")
            .build();
    public static final BindGroupLayout FSRCNNX_COMBINE_LAYOUT = BindGroupLayout.builder()
            .withSampler("BaseSampler")
            .withSampler("DetailSampler")
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
                    .withSampler("HistoryDepthSampler")
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    *///?}
                    .build();
    //?}

    //? >= 1.21.11 {
    public static RenderPipeline TAA_DEPTH_COPY_PIPELINE =
            RenderPipeline.builder(GLOBALS_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("renderscale", "pipeline/taa_depth_copy"))
                    .withVertexShader("core/screenquad")
                    .withFragmentShader(Identifier.fromNamespaceAndPath("renderscale", "core/taa_depth_copy"))
                    //? > 26.1 {
                    .withBindGroupLayout(TAA_DEPTH_LAYOUT)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    //?} else {
                    /*.withSampler("InSampler")
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    *///?}
                    .build();

    /**
     * FSR2-style resolve: same inputs as TAAU (jittered color, depth-dilated
     * camera reprojection, packed depth history) but accumulates in YCoCg
     * with a 3x3 AABB, thin-feature lock and a reactive approximation for
     * emissive/flicker pixels. Sharpening happens AFTER accumulation in a
     * dedicated RCAS pass ({@link #blitFsr2}), like real FSR2.
     */
    public static RenderPipeline FSR2_RESOLVE_PIPELINE =
            RenderPipeline.builder(GLOBALS_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("renderscale", "pipeline/fsr2_resolve"))
                    .withVertexShader("core/screenquad")
                    .withFragmentShader(Identifier.fromNamespaceAndPath("renderscale", "core/fsr2_resolve"))
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
                    .withSampler("HistoryDepthSampler")
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    *///?}
                    .build();
    //?}

    //? > 26.1 {
    private static RenderPipeline fsrcnnxPipeline(String name, BindGroupLayout layout) {
        return RenderPipeline.builder(GLOBALS_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("renderscale", "pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Identifier.fromNamespaceAndPath("renderscale", "core/" + name))
                .withBindGroupLayout(layout)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
    }

    public static final RenderPipeline FSRCNNX_FEAT1_PIPELINE = fsrcnnxPipeline("fsrcnnx_feat1", FSRCNNX_1SAMPLER);
    public static final RenderPipeline FSRCNNX_FEAT2_PIPELINE = fsrcnnxPipeline("fsrcnnx_feat2", FSRCNNX_1SAMPLER);
    public static final RenderPipeline FSRCNNX_MAP11_PIPELINE = fsrcnnxPipeline("fsrcnnx_map11", FSRCNNX_2SAMPLER);
    public static final RenderPipeline FSRCNNX_MAP12_PIPELINE = fsrcnnxPipeline("fsrcnnx_map12", FSRCNNX_2SAMPLER);
    public static final RenderPipeline FSRCNNX_MAP21_PIPELINE = fsrcnnxPipeline("fsrcnnx_map21", FSRCNNX_2SAMPLER);
    public static final RenderPipeline FSRCNNX_MAP22_PIPELINE = fsrcnnxPipeline("fsrcnnx_map22", FSRCNNX_2SAMPLER);
    public static final RenderPipeline FSRCNNX_MAP31_PIPELINE = fsrcnnxPipeline("fsrcnnx_map31", FSRCNNX_2SAMPLER);
    public static final RenderPipeline FSRCNNX_MAP32_PIPELINE = fsrcnnxPipeline("fsrcnnx_map32", FSRCNNX_2SAMPLER);
    public static final RenderPipeline FSRCNNX_MAP41_PIPELINE = fsrcnnxPipeline("fsrcnnx_map41", FSRCNNX_2SAMPLER);
    public static final RenderPipeline FSRCNNX_MAP42_PIPELINE = fsrcnnxPipeline("fsrcnnx_map42", FSRCNNX_2SAMPLER);
    public static final RenderPipeline FSRCNNX_RES1_PIPELINE = fsrcnnxPipeline("fsrcnnx_res1", FSRCNNX_3SAMPLER);
    public static final RenderPipeline FSRCNNX_RES2_PIPELINE = fsrcnnxPipeline("fsrcnnx_res2", FSRCNNX_3SAMPLER);
    public static final RenderPipeline FSRCNNX_SUBCONV_PIPELINE = fsrcnnxPipeline("fsrcnnx_subconv", FSRCNNX_2SAMPLER);
    public static final RenderPipeline FSRCNNX_AGGR_PIPELINE = fsrcnnxPipeline("fsrcnnx_aggr", FSRCNNX_1SAMPLER);
    public static final RenderPipeline FSRCNNX_COMBINE_PIPELINE = fsrcnnxPipeline("fsrcnnx_combine", FSRCNNX_COMBINE_LAYOUT);

    /**
     * MiniESPCN x2 student (Magpie port): conv_in 3->8 k3 + ReLU, fused
     * depthwise 3x3 + mix 1x1 + ReLU, then to_residual 8->12 1x1 (linear) +
     * pixel-shuffle + fast Keys bicubic (a=-0.75) base. Full-RGB residual net,
     * 448 MAC per LR pixel. Same fixed 2x geometry as FSRCNNX. Reuses the
     * FSRCNNX sampler layouts (1/2/3 samplers) so no new bind groups needed.
     */
    public static final RenderPipeline MINIESPCN_FEAT0_PIPELINE = fsrcnnxPipeline("miniespcn_feat0", FSRCNNX_1SAMPLER);
    public static final RenderPipeline MINIESPCN_FEAT1_PIPELINE = fsrcnnxPipeline("miniespcn_feat1", FSRCNNX_1SAMPLER);
    public static final RenderPipeline MINIESPCN_MIX0_PIPELINE = fsrcnnxPipeline("miniespcn_mix0", FSRCNNX_2SAMPLER);
    public static final RenderPipeline MINIESPCN_MIX1_PIPELINE = fsrcnnxPipeline("miniespcn_mix1", FSRCNNX_2SAMPLER);
    public static final RenderPipeline MINIESPCN_COMBINE_PIPELINE = fsrcnnxPipeline("miniespcn_combine", FSRCNNX_3SAMPLER);

    /**
     * MiniESPCN x2 Q (Magpie port): pure ESPCN, no bicubic base. conv1 3->32
     * k3 + ReLU (8 passes), conv2 32->16 k5 + ReLU (4 passes over all 8
     * conv1 textures), conv3 16->12 k3 linear + pixel-shuffle straight to
     * the 2x output (unclamped, like the HLSL). ~15.4k MAC per LR pixel —
     * heavyweight, needs a discrete GPU. Same fixed 2x geometry as the rest.
     */
    public static final BindGroupLayout MINIESPCNQ_8SAMPLER = BindGroupLayout.builder()
            .withSampler("S0")
            .withSampler("S1")
            .withSampler("S2")
            .withSampler("S3")
            .withSampler("S4")
            .withSampler("S5")
            .withSampler("S6")
            .withSampler("S7")
            .build();
    public static final BindGroupLayout MINIESPCNQ_4SAMPLER = BindGroupLayout.builder()
            .withSampler("T0")
            .withSampler("T1")
            .withSampler("T2")
            .withSampler("T3")
            .build();

    public static final RenderPipeline[] MINIESPCNQ_CONV1_PIPELINES = new RenderPipeline[]{
            fsrcnnxPipeline("miniespcnq_conv1_0", FSRCNNX_1SAMPLER),
            fsrcnnxPipeline("miniespcnq_conv1_1", FSRCNNX_1SAMPLER),
            fsrcnnxPipeline("miniespcnq_conv1_2", FSRCNNX_1SAMPLER),
            fsrcnnxPipeline("miniespcnq_conv1_3", FSRCNNX_1SAMPLER),
            fsrcnnxPipeline("miniespcnq_conv1_4", FSRCNNX_1SAMPLER),
            fsrcnnxPipeline("miniespcnq_conv1_5", FSRCNNX_1SAMPLER),
            fsrcnnxPipeline("miniespcnq_conv1_6", FSRCNNX_1SAMPLER),
            fsrcnnxPipeline("miniespcnq_conv1_7", FSRCNNX_1SAMPLER),
    };
    public static final RenderPipeline[] MINIESPCNQ_CONV2_PIPELINES = new RenderPipeline[]{
            fsrcnnxPipeline("miniespcnq_conv2_0", MINIESPCNQ_8SAMPLER),
            fsrcnnxPipeline("miniespcnq_conv2_1", MINIESPCNQ_8SAMPLER),
            fsrcnnxPipeline("miniespcnq_conv2_2", MINIESPCNQ_8SAMPLER),
            fsrcnnxPipeline("miniespcnq_conv2_3", MINIESPCNQ_8SAMPLER),
    };
    public static final RenderPipeline MINIESPCNQ_CONV3_PIPELINE =
            fsrcnnxPipeline("miniespcnq_conv3", MINIESPCNQ_4SAMPLER);

    private final RenderTarget[] miniespcnqT1 = new RenderTarget[8];
    private final RenderTarget[] miniespcnqT2 = new RenderTarget[4];
    private static final String[] MINIESPCNQ_S_NAMES =
            new String[]{"S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7"};
    private static final String[] MINIESPCNQ_T_NAMES =
            new String[]{"T0", "T1", "T2", "T3"};

    @Nullable
    private RenderTarget miniespcnFeat0;
    @Nullable
    private RenderTarget miniespcnFeat1;
    @Nullable
    private RenderTarget miniespcnMix0;
    @Nullable
    private RenderTarget miniespcnMix1;

    @Nullable
    private RenderTarget fsrcnnxFeat1;
    @Nullable
    private RenderTarget fsrcnnxFeat2;
    @Nullable
    private RenderTarget fsrcnnxBufA;
    @Nullable
    private RenderTarget fsrcnnxBufB;
    @Nullable
    private RenderTarget fsrcnnxBufC;
    @Nullable
    private RenderTarget fsrcnnxBufD;
    @Nullable
    private RenderTarget fsrcnnxRes1;
    @Nullable
    private RenderTarget fsrcnnxRes2;
    @Nullable
    private RenderTarget fsrcnnxSubconv;
    @Nullable
    private RenderTarget fsrcnnxAggr;

    private RenderTarget fsrcnnxEnsure(@Nullable RenderTarget target, String label, int w, int h) {
        if (target == null) {
            return new TextureTarget(label, w, h, false, GpuFormat.RGBA16_FLOAT);
        }
        if (target.width != w || target.height != h) {
            target.resize(w, h);
        }
        return target;
    }

    private void fsrcnnxPassN(RenderPipeline pipeline, String label, RenderTarget dest,
            String[] samplerNames, RenderTarget[] sources, FilterMode mode) {
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> label, dest.getColorTextureView(), Optional.empty())) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            for (int i = 0; i < samplerNames.length; i++) {
                renderPass.bindTexture(samplerNames[i], sources[i].getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(mode));
            }
            renderPass.draw(3, 1, 0, 0);
        }
    }

    private void fsrcnnxPass(RenderPipeline pipeline, String label, RenderTarget dest,
            String nameA, RenderTarget srcA) {
        fsrcnnxPassN(pipeline, label, dest,
                new String[]{nameA}, new RenderTarget[]{srcA}, FilterMode.NEAREST);
    }

    private void fsrcnnxPass(RenderPipeline pipeline, String label, RenderTarget dest,
            String nameA, RenderTarget srcA, String nameB, RenderTarget srcB, FilterMode mode) {
        fsrcnnxPassN(pipeline, label, dest,
                new String[]{nameA, nameB}, new RenderTarget[]{srcA, srcB}, mode);
    }

    private void fsrcnnxPass(RenderPipeline pipeline, String label, RenderTarget dest,
            String nameA, RenderTarget srcA, String nameB, RenderTarget srcB) {
        fsrcnnxPass(pipeline, label, dest, nameA, srcA, nameB, srcB, FilterMode.NEAREST);
    }

    private void fsrcnnxPass(RenderPipeline pipeline, String label, RenderTarget dest,
            String nameA, RenderTarget srcA, String nameB, RenderTarget srcB,
            String nameC, RenderTarget srcC) {
        fsrcnnxPass(pipeline, label, dest, nameA, srcA, nameB, srcB, nameC, srcC, FilterMode.NEAREST);
    }

    private void fsrcnnxPass(RenderPipeline pipeline, String label, RenderTarget dest,
            String nameA, RenderTarget srcA, String nameB, RenderTarget srcB,
            String nameC, RenderTarget srcC, FilterMode mode) {
        fsrcnnxPassN(pipeline, label, dest,
                new String[]{nameA, nameB, nameC}, new RenderTarget[]{srcA, srcB, srcC}, mode);
    }

    private void fsrcnnxPass(RenderPipeline pipeline, String label, RenderTarget dest,
            String[] samplerNames, RenderTarget[] sources) {
        fsrcnnxPassN(pipeline, label, dest, samplerNames, sources, FilterMode.NEAREST);
    }

    private boolean blitFsrcnnx(final RenderTarget input, final RenderTarget output, final FilterMode filter) {
        try {
            int iw = input.width;
            int ih = input.height;
            int ow = output.width;
            int oh = output.height;
            if (iw <= 0 || ih <= 0 || ow <= 0 || oh <= 0) return false;
            if (iw >= ow || ih >= oh) return false;
            if (ow > iw * 2 + 1 || oh > ih * 2 + 1) return false;

            int mw = iw * 2;
            int mh = ih * 2;
            fsrcnnxFeat1 = fsrcnnxEnsure(fsrcnnxFeat1, "RenderScale FSRCNNX feat1", iw, ih);
            fsrcnnxFeat2 = fsrcnnxEnsure(fsrcnnxFeat2, "RenderScale FSRCNNX feat2", iw, ih);
            fsrcnnxBufA = fsrcnnxEnsure(fsrcnnxBufA, "RenderScale FSRCNNX bufA", iw, ih);
            fsrcnnxBufB = fsrcnnxEnsure(fsrcnnxBufB, "RenderScale FSRCNNX bufB", iw, ih);
            fsrcnnxBufC = fsrcnnxEnsure(fsrcnnxBufC, "RenderScale FSRCNNX bufC", iw, ih);
            fsrcnnxBufD = fsrcnnxEnsure(fsrcnnxBufD, "RenderScale FSRCNNX bufD", iw, ih);
            fsrcnnxRes1 = fsrcnnxEnsure(fsrcnnxRes1, "RenderScale FSRCNNX res1", iw, ih);
            fsrcnnxRes2 = fsrcnnxEnsure(fsrcnnxRes2, "RenderScale FSRCNNX res2", iw, ih);
            fsrcnnxSubconv = fsrcnnxEnsure(fsrcnnxSubconv, "RenderScale FSRCNNX subconv", iw, ih);
            fsrcnnxAggr = fsrcnnxEnsure(fsrcnnxAggr, "RenderScale FSRCNNX aggr", mw, mh);

            fsrcnnxPass(FSRCNNX_FEAT1_PIPELINE, "FSRCNNX feat1", fsrcnnxFeat1, "InSampler", input);
            fsrcnnxPass(FSRCNNX_FEAT2_PIPELINE, "FSRCNNX feat2", fsrcnnxFeat2, "InSampler", input);
            fsrcnnxPass(FSRCNNX_MAP11_PIPELINE, "FSRCNNX map11", fsrcnnxBufA,
                    "ASampler", fsrcnnxFeat1, "BSampler", fsrcnnxFeat2);
            fsrcnnxPass(FSRCNNX_MAP12_PIPELINE, "FSRCNNX map12", fsrcnnxBufB,
                    "ASampler", fsrcnnxFeat1, "BSampler", fsrcnnxFeat2);
            fsrcnnxPass(FSRCNNX_MAP21_PIPELINE, "FSRCNNX map21", fsrcnnxBufC,
                    "ASampler", fsrcnnxBufA, "BSampler", fsrcnnxBufB);
            fsrcnnxPass(FSRCNNX_MAP22_PIPELINE, "FSRCNNX map22", fsrcnnxBufD,
                    "ASampler", fsrcnnxBufA, "BSampler", fsrcnnxBufB);
            fsrcnnxPass(FSRCNNX_MAP31_PIPELINE, "FSRCNNX map31", fsrcnnxBufA,
                    "ASampler", fsrcnnxBufC, "BSampler", fsrcnnxBufD);
            fsrcnnxPass(FSRCNNX_MAP32_PIPELINE, "FSRCNNX map32", fsrcnnxBufB,
                    "ASampler", fsrcnnxBufC, "BSampler", fsrcnnxBufD);
            fsrcnnxPass(FSRCNNX_MAP41_PIPELINE, "FSRCNNX map41", fsrcnnxBufC,
                    "ASampler", fsrcnnxBufA, "BSampler", fsrcnnxBufB);
            fsrcnnxPass(FSRCNNX_MAP42_PIPELINE, "FSRCNNX map42", fsrcnnxBufD,
                    "ASampler", fsrcnnxBufA, "BSampler", fsrcnnxBufB);
            fsrcnnxPass(FSRCNNX_RES1_PIPELINE, "FSRCNNX res1", fsrcnnxRes1,
                    "ASampler", fsrcnnxBufC, "BSampler", fsrcnnxBufD, "CSampler", fsrcnnxFeat1);
            fsrcnnxPass(FSRCNNX_RES2_PIPELINE, "FSRCNNX res2", fsrcnnxRes2,
                    "ASampler", fsrcnnxBufC, "BSampler", fsrcnnxBufD, "CSampler", fsrcnnxFeat2);
            fsrcnnxPass(FSRCNNX_SUBCONV_PIPELINE, "FSRCNNX subconv", fsrcnnxSubconv,
                    "ASampler", fsrcnnxRes1, "BSampler", fsrcnnxRes2);
            fsrcnnxPass(FSRCNNX_AGGR_PIPELINE, "FSRCNNX aggr", fsrcnnxAggr, "InSampler", fsrcnnxSubconv);
            fsrcnnxPass(FSRCNNX_COMBINE_PIPELINE, "FSRCNNX combine", output,
                    "BaseSampler", input, "DetailSampler", fsrcnnxAggr, FilterMode.LINEAR);
            return true;
        } catch (Exception e) {
            Constants.LOG.error("[RenderScale {}] FSRCNNX pass failed, falling back", Constants.BUILD_TAG, e);
            return false;
        }
    }

    /**
     * MiniESPCN x2 upscale: 4 LR passes (conv_in split 0-3/4-7, fused
     * dw+mix split 0-3/4-7) + final combine at output size (to_residual +
     * shuffle + bicubic base, written straight to {@code output}).
     * The base sampler needs LINEAR for the bicubic factorization; the
     * detail inputs go through texelFetch so the filter mode is irrelevant
     * for them.
     */
    private boolean blitMiniEspcn(final RenderTarget input, final RenderTarget output, final FilterMode filter) {
        try {
            int iw = input.width;
            int ih = input.height;
            int ow = output.width;
            int oh = output.height;
            if (iw <= 0 || ih <= 0 || ow <= 0 || oh <= 0) return false;
            if (iw >= ow || ih >= oh) return false;
            if (ow > iw * 2 + 1 || oh > ih * 2 + 1) return false;

            miniespcnFeat0 = fsrcnnxEnsure(miniespcnFeat0, "RenderScale MiniESPCN feat0", iw, ih);
            miniespcnFeat1 = fsrcnnxEnsure(miniespcnFeat1, "RenderScale MiniESPCN feat1", iw, ih);
            miniespcnMix0 = fsrcnnxEnsure(miniespcnMix0, "RenderScale MiniESPCN mix0", iw, ih);
            miniespcnMix1 = fsrcnnxEnsure(miniespcnMix1, "RenderScale MiniESPCN mix1", iw, ih);

            fsrcnnxPass(MINIESPCN_FEAT0_PIPELINE, "MiniESPCN feat0", miniespcnFeat0, "InSampler", input);
            fsrcnnxPass(MINIESPCN_FEAT1_PIPELINE, "MiniESPCN feat1", miniespcnFeat1, "InSampler", input);
            fsrcnnxPass(MINIESPCN_MIX0_PIPELINE, "MiniESPCN mix0", miniespcnMix0,
                    "ASampler", miniespcnFeat0, "BSampler", miniespcnFeat1);
            fsrcnnxPass(MINIESPCN_MIX1_PIPELINE, "MiniESPCN mix1", miniespcnMix1,
                    "ASampler", miniespcnFeat0, "BSampler", miniespcnFeat1);
            fsrcnnxPass(MINIESPCN_COMBINE_PIPELINE, "MiniESPCN combine", output,
                    "ASampler", miniespcnMix0, "BSampler", miniespcnMix1, "CSampler", input,
                    FilterMode.LINEAR);
            return true;
        } catch (Exception e) {
            Constants.LOG.error("[RenderScale {}] MiniESPCN pass failed, falling back", Constants.BUILD_TAG, e);
            return false;
        }
    }

    /**
     * MiniESPCN-Q x2 upscale: 8 conv1 passes (3->32 k3 ReLU) + 4 conv2
     * passes (32->16 k5 ReLU over all 8 conv1 textures) + conv3 (16->12 k3
     * linear + shuffle) written straight to {@code output}.
     * Everything goes through texelFetch, so the filter mode is irrelevant.
     */
    private boolean blitMiniEspcnQ(final RenderTarget input, final RenderTarget output, final FilterMode filter) {
        try {
            int iw = input.width;
            int ih = input.height;
            int ow = output.width;
            int oh = output.height;
            if (iw <= 0 || ih <= 0 || ow <= 0 || oh <= 0) return false;
            if (iw >= ow || ih >= oh) return false;
            if (ow > iw * 2 + 1 || oh > ih * 2 + 1) return false;

            for (int i = 0; i < 8; i++) {
                miniespcnqT1[i] = fsrcnnxEnsure(miniespcnqT1[i], "RenderScale MiniESPCN-Q conv1." + i, iw, ih);
                fsrcnnxPass(MINIESPCNQ_CONV1_PIPELINES[i], "MiniESPCN-Q conv1." + i,
                        miniespcnqT1[i], "InSampler", input);
            }
            for (int p = 0; p < 4; p++) {
                miniespcnqT2[p] = fsrcnnxEnsure(miniespcnqT2[p], "RenderScale MiniESPCN-Q conv2." + p, iw, ih);
                fsrcnnxPass(MINIESPCNQ_CONV2_PIPELINES[p], "MiniESPCN-Q conv2." + p,
                        miniespcnqT2[p], MINIESPCNQ_S_NAMES, miniespcnqT1);
            }
            fsrcnnxPass(MINIESPCNQ_CONV3_PIPELINE, "MiniESPCN-Q conv3", output,
                    MINIESPCNQ_T_NAMES, miniespcnqT2);
            return true;
        } catch (Exception e) {
            Constants.LOG.error("[RenderScale {}] MiniESPCN-Q pass failed, falling back", Constants.BUILD_TAG, e);
            return false;
        }
    }
    //?} else {
    /*private boolean blitFsrcnnx(RenderTarget input, RenderTarget output, FilterMode filter) {
        return false;
    }

    private boolean blitMiniEspcn(RenderTarget input, RenderTarget output, FilterMode filter) {
        return false;
    }

    private boolean blitMiniEspcnQ(RenderTarget input, RenderTarget output, FilterMode filter) {
        return false;
    }
    *///?}

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
            return CONFIG.getConfig().temporal || CONFIG.getConfig().fsr2;
        } catch (Exception e) {
            return false;
        }
    }

    /** FSR2 resolve runs when enabled, unless a shaderpack owns the frame (unless forced). FSR2 wins over TAAU. */
    public boolean shouldUseFsr2() {
        try {
            RenderScaleConfig cfg = CONFIG.getConfig();
            if (!cfg.fsr2) return false;
            return !isIrisShaderActive() || cfg.temporalWithShaders;
        } catch (Exception e) {
            return false;
        }
    }

    /** Temporal resolve runs when enabled, unless a shaderpack owns the frame (unless forced). */
    public boolean shouldUseTemporal() {
        try {
            RenderScaleConfig cfg = CONFIG.getConfig();
            if (!cfg.temporal) return false;
            if (shouldUseFsr2()) return false;
            return !isIrisShaderActive() || cfg.temporalWithShaders;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * FSRCNNX runs when enabled (temporal wins if both are on) and the fixed
     * 2x network geometry fits: upscale only, output at most 2x the input.
     * MiniESPCN wins when both CNNs are on.
     */
    public boolean shouldUseFsrcnnx(RenderTarget input, RenderTarget output) {
        try {
            RenderScaleConfig cfg = CONFIG.getConfig();
            if (!cfg.fsrcnnx || cfg.miniespcn || shouldUseTemporal() || shouldUseFsr2()) return false;
            if (input == null || output == null) return false;
            return input.width < output.width && input.height < output.height
                    && output.width <= input.width * 2 + 1 && output.height <= input.height * 2 + 1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * MiniESPCN runs when enabled (temporal wins if both are on, MiniESPCN-Q
     * wins over it) and the fixed 2x network geometry fits: upscale only,
     * output at most 2x the input.
     */
    public boolean shouldUseMiniEspcn(RenderTarget input, RenderTarget output) {
        try {
            RenderScaleConfig cfg = CONFIG.getConfig();
            if (!cfg.miniespcn || cfg.miniespcnq || shouldUseTemporal() || shouldUseFsr2()) return false;
            if (input == null || output == null) return false;
            return input.width < output.width && input.height < output.height
                    && output.width <= input.width * 2 + 1 && output.height <= input.height * 2 + 1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * MiniESPCN-Q runs when enabled (temporal wins if both are on, and it wins
     * over MiniESPCN/FSRCNNX) and the fixed 2x network geometry fits: upscale
     * only, output at most 2x the input.
     */
    public boolean shouldUseMiniEspcnQ(RenderTarget input, RenderTarget output) {
        try {
            RenderScaleConfig cfg = CONFIG.getConfig();
            if (!cfg.miniespcnq || shouldUseTemporal() || shouldUseFsr2()) return false;
            if (input == null || output == null) return false;
            return input.width < output.width && input.height < output.height
                    && output.width <= input.width * 2 + 1 && output.height <= input.height * 2 + 1;
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
            if (shouldUseFsr2()) return "FSR2";
            if (shouldUseTemporal()) return "TAAU";
            if (getConfig().miniespcnq) return "MiniESPCN-Q";
            if (getConfig().miniespcn) return "MiniESPCN";
            if (getConfig().fsrcnnx) return "FSRCNNX";
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
        resize(taaDepthA);
        resize(taaDepthB);
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
    public void blitPlain(final RenderTarget input, final RenderTarget output, final FilterMode filter) {
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

    public void blitAndBlendToTexture(final RenderTarget input, final RenderTarget output, final FilterMode filter) {
        RenderSystem.assertOnRenderThread();

        //? < 1.21.11 {
        /*try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Blit render target", output.getColorTextureView(), OptionalInt.empty())) {
        *///? } else
        if (shouldUseFsr2()) {
            blitFsr2(input, output, filter);
        } else if (shouldUseTemporal()) {
            blitTemporal(input, output, filter);
        } else if (shouldUseMiniEspcnQ(input, output)) {
            if (!blitMiniEspcnQ(input, output, filter)) {
                blitPlain(input, output, filter);
            }
        } else if (shouldUseMiniEspcn(input, output)) {
            if (!blitMiniEspcn(input, output, filter)) {
                blitPlain(input, output, filter);
            }
        } else if (shouldUseFsrcnnx(input, output)) {
            if (!blitFsrcnnx(input, output, filter)) {
                blitPlain(input, output, filter);
            }
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
            blitPlain(input, output, filter);
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
        resolveMode = 0;
        //? > 26.1 {
        capturedThisFrame = false;
        hasReproj = false;
        //?}
        hasJitter = false;
        currentJitterX = 0.0f;
        currentJitterY = 0.0f;
    }

    /**
     * FSR2-style upscale: jitter-cancelled Lanczos-2 reconstruction straight
     * from the raw low-res frame -> YCoCg temporal accumulation
     * ({@code fsr2_resolve.fsh}) -> RCAS sharpen -> screen.
     * History buffers are shared with TAAU; switching modes drops history.
     * Unlike TAAU there is deliberately NO EASU pre-upsample here: resampling
     * before accumulation destroys the subpixel jitter information that the
     * resolve stacks into extra detail. FSR2 sharpens AFTER accumulation so
     * the sharpen never amplifies history residue.
     */
    private void blitFsr2(final RenderTarget input, final RenderTarget output, final FilterMode filter) {
        if (resolveMode != 2) {
            taaPrimed = false;
            resolveMode = 2;
        }
        if (taaHistoryA == null) taaHistoryA = new MainTarget(output.width, output.height);
        else if (taaHistoryA.width != output.width || taaHistoryA.height != output.height) taaHistoryA.resize(output.width, output.height);
        if (taaHistoryB == null) taaHistoryB = new MainTarget(output.width, output.height);
        else if (taaHistoryB.width != output.width || taaHistoryB.height != output.height) taaHistoryB.resize(output.width, output.height);

        RenderTarget readHistory = taaFlipFlop ? taaHistoryA : taaHistoryB;
        RenderTarget writeHistory = taaFlipFlop ? taaHistoryB : taaHistoryA;
        taaFlipFlop = !taaFlipFlop;

        // The resolve samples the RAW input itself (jitter-cancelled Lanczos),
        // so no base upsample pass is needed or wanted here.

        // Render-time jitter snapshotted by capture (exact for this frame).
        // Reused WITHOUT advancing (advancing here would desync the resolve
        // from the render by one frame).
        //? > 26.1 {
        // Exact render-time jitter snapshotted by capture (never a global latest).
        float jx = renderJitterX;
        float jy = renderJitterY;
        //?} else {
        /*float jx = hasJitter ? currentJitterX : pollTemporalJitter()[0];
        float jy = hasJitter ? currentJitterY : currentJitterY;
        *///?}
        hasJitter = false;
        // Diagnostic bypass: current frame only, no history at all. If this
        // looks sharp and still (no swimming), jitter cancellation is right
        // and the ghosting comes from the history/reprojection side.
        boolean noHistory = false;
        try {
            noHistory = getConfig().fsr2NoHistory;
        } catch (Exception ignored) {
        }
        // first frame after enable/resize/mode-switch: no valid history yet.
        // while moving fast, lean on the current frame: kills trails at the cost of
        // temporal detail (standard trick, detail re-accumulates when still).
        float blendBase = noHistory ? 1.0f : (taaPrimed ? (0.1f + 0.75f * camMotion) : 1.0f);
        if (!noHistory) taaPrimed = true;
        float reactive = 0.5f;
        float debugView = 0.0f;
        float useDepthTex = 0.0f;
        try {
            reactive = getConfig().getFsr2ReactiveStrength();
            debugView = Math.max(0, Math.min(2, getConfig().temporalDebug));
            if (getConfig().temporalReprojDepth) useDepthTex = 1.0f;
        } catch (Exception ignored) {
        }

        // depth history for the disocclusion test (only when real depth is in
        // use; the first frame after enabling self-heals: a mismatch just
        // drops history once via blend = 1).
        RenderTarget readDepth = null;
        RenderTarget writeDepth = null;
        if (useDepthTex > 0.5f) {
            if (taaDepthA == null) taaDepthA = new MainTarget(output.width, output.height);
            else if (taaDepthA.width != output.width || taaDepthA.height != output.height) taaDepthA.resize(output.width, output.height);
            if (taaDepthB == null) taaDepthB = new MainTarget(output.width, output.height);
            else if (taaDepthB.width != output.width || taaDepthB.height != output.height) taaDepthB.resize(output.width, output.height);
            readDepth = taaFlipFlop ? taaDepthA : taaDepthB;
            writeDepth = taaFlipFlop ? taaDepthB : taaDepthA;
        }

        if (taaUbo == null) taaUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale TAA params");

        // pass 1: FSR2 resolve raw input + history -> writeHistory(native).
        // TaaParams.xy ALWAYS carries the real current-frame jitter (even with
        // reprojection on: historyUv from matrices covers history, but the
        // Lanczos reconstruction needs it for jitter cancellation).
        // TaaParams.w carries reactive strength (not sharpness: the RCAS
        // pass below owns sharpening).
        //? > 26.1 {
        CommandEncoder fsr2Encoder = RenderSystem.getDevice().createCommandEncoder();
        // Depth reprojection covers camera AND jitter motion for HISTORY;
        // fall back to plain jitter offset when no matrices were captured
        // (menus, first frames). capturedThisFrame guards stale matrices.
        boolean useReproj = hasReproj && capturedThisFrame && getConfig().temporalReproj;
        Matrix4f invCurVP = reprojTmp.set(reprojCurVP);
        if (useReproj) invCurVP.invert();
        if (reprojUbo == null) reprojUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale reproj");
        com.mojang.blaze3d.buffers.GpuBufferSlice fsr2Params = taaUbo.upload(fsr2Encoder,
                jx / Math.max(output.width, 1),
                jy / Math.max(output.height, 1),
                blendBase, reactive);
        com.mojang.blaze3d.buffers.GpuBufferSlice fsr2Reproj =
                reprojUbo.uploadReproj(fsr2Encoder, reprojPrevVP, invCurVP, useReproj ? 1.0f : 0.0f, debugView, useDepthTex);
        try (RenderPass renderPass = fsr2Encoder.createRenderPass(() -> "FSR2 resolve", writeHistory.getColorTextureView(), Optional.empty())) {
            renderPass.setPipeline(FSR2_RESOLVE_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.bindTexture("HistorySampler", readHistory.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.bindTexture("DepthSampler", input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.bindTexture("HistoryDepthSampler", readDepth != null ? readDepth.getColorTextureView() : input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.setUniform("TaaParams", fsr2Params);
            renderPass.setUniform("Reproj", fsr2Reproj);
            renderPass.draw(3, 1, 0, 0);
        }
        reprojPrevVP.set(reprojCurVP);
        //?} else {
        /*com.mojang.blaze3d.buffers.GpuBufferSlice fsr2Params = taaUbo.upload(
                jx / Math.max(output.width, 1),
                jy / Math.max(output.height, 1),
                blendBase, reactive);
        if (reprojUbo == null) reprojUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale reproj");
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "FSR2 resolve", writeHistory.getColorTextureView(), OptionalInt.empty())) {
            renderPass.setPipeline(FSR2_RESOLVE_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.bindTexture("HistorySampler", readHistory.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.bindTexture("DepthSampler", input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.bindTexture("HistoryDepthSampler", readDepth != null ? readDepth.getColorTextureView() : input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.setUniform("TaaParams", fsr2Params);
                renderPass.setUniform("Reproj", reprojUbo.uploadReproj(0.0f, debugView, useDepthTex));
                renderPass.draw(0, 3);
            }
        }
        *///?}

        // pass 1b: pack current depth into writeDepth for next frame's disocclusion test
        //? > 26.1 {
        if (writeDepth != null) {
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "FSR2 depth copy", writeDepth.getColorTextureView(), Optional.empty())) {
                renderPass.setPipeline(TAA_DEPTH_COPY_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.draw(3, 1, 0, 0);
            }
        }
        //?} else {
        /*if (writeDepth != null) {
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "FSR2 depth copy", writeDepth.getColorTextureView(), OptionalInt.empty())) {
                renderPass.setPipeline(TAA_DEPTH_COPY_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.draw(0, 3);
            }
        }
        *///?}

        // pass 2: RCAS sharpen writeHistory -> output (FSR2 always sharpens last)
        //? > 26.1 {
        if (rcasUbo == null) rcasUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale RCAS params");
        CommandEncoder fsr2RcasEncoder = RenderSystem.getDevice().createCommandEncoder();
        com.mojang.blaze3d.buffers.GpuBufferSlice fsr2RcasParams = rcasUbo.upload(fsr2RcasEncoder, getConfig().getFsr2RcasAttenuation(), 0.0f, 0.0f, 0.0f);
        try (RenderPass renderPass = fsr2RcasEncoder.createRenderPass(() -> "FSR2: RCAS", output.getColorTextureView(), Optional.empty())) {
            renderPass.setPipeline(FSR_RCAS_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InSampler", writeHistory.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.setUniform("RcasParams", fsr2RcasParams);
            renderPass.draw(3, 1, 0, 0);
        }
        rcasUbo.nextFrame();
        //?} else {
        /*if (rcasUbo == null) rcasUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale RCAS params");
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "FSR2: RCAS", output.getColorTextureView(), OptionalInt.empty())) {
            renderPass.setPipeline(FSR_RCAS_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InSampler", writeHistory.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.setUniform("RcasParams", rcasUbo.upload(getConfig().getFsr2RcasAttenuation(), 0.0f, 0.0f, 0.0f));
            renderPass.draw(0, 3);
        }
        rcasUbo.nextFrame();
        *///?}

        taaUbo.nextFrame();
        if (reprojUbo != null) reprojUbo.nextFrame();
    }

    private void blitTemporal(final RenderTarget input, final RenderTarget output, final FilterMode filter) {
        if (resolveMode != 1) {
            taaPrimed = false;
            resolveMode = 1;
        }
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

        // Render-time jitter snapshotted by capture (exact for this frame).
        // Reused WITHOUT advancing (advancing here would desync the resolve
        // from the render by one frame).
        //? > 26.1 {
        // Exact render-time jitter snapshotted by capture (never a global latest).
        float jx = renderJitterX;
        float jy = renderJitterY;
        //?} else {
        /*float jx = hasJitter ? currentJitterX : pollTemporalJitter()[0];
        float jy = hasJitter ? currentJitterY : currentJitterY;
        *///?}
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

        // depth history for the disocclusion test (only when real depth is in
        // use; the first frame after enabling self-heals: a mismatch just
        // drops history once via blend = 1).
        RenderTarget readDepth = null;
        RenderTarget writeDepth = null;
        if (useDepthTex > 0.5f) {
            if (taaDepthA == null) taaDepthA = new MainTarget(output.width, output.height);
            else if (taaDepthA.width != output.width || taaDepthA.height != output.height) taaDepthA.resize(output.width, output.height);
            if (taaDepthB == null) taaDepthB = new MainTarget(output.width, output.height);
            else if (taaDepthB.width != output.width || taaDepthB.height != output.height) taaDepthB.resize(output.width, output.height);
            readDepth = taaFlipFlop ? taaDepthA : taaDepthB;
            writeDepth = taaFlipFlop ? taaDepthB : taaDepthA;
        }

        if (taaUbo == null) taaUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale TAA params");

        // pass 1: temporal resolve input(low-res) + history -> writeHistory(native)
        //? > 26.1 {
        CommandEncoder taaEncoder = RenderSystem.getDevice().createCommandEncoder();
        // Depth reprojection covers camera AND jitter motion; fall back to plain
        // jitter offset when no matrices were captured (menus, first frames).
        // capturedThisFrame guards stale matrices.
        boolean useReproj = hasReproj && capturedThisFrame && getConfig().temporalReproj;
        Matrix4f invCurVP = reprojTmp.set(reprojCurVP);
        if (useReproj) invCurVP.invert();
        if (reprojUbo == null) reprojUbo = new dev.zelo.renderscale.temporal.Vec4UniformBuffer("RenderScale reproj");
        com.mojang.blaze3d.buffers.GpuBufferSlice taaParams = taaUbo.upload(taaEncoder,
                useReproj ? 0.0f : jx / Math.max(output.width, 1),
                useReproj ? 0.0f : jy / Math.max(output.height, 1),
                blendBase, sharp);
        com.mojang.blaze3d.buffers.GpuBufferSlice reprojParams =
                reprojUbo.uploadReproj(taaEncoder, reprojPrevVP, invCurVP, useReproj ? 1.0f : 0.0f, debugView, useDepthTex);
        try (RenderPass renderPass = taaEncoder.createRenderPass(() -> "TAA resolve", writeHistory.getColorTextureView(), Optional.empty())) {
            renderPass.setPipeline(TAA_RESOLVE_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InSampler", resolveInput.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filter));
            renderPass.bindTexture("HistorySampler", readHistory.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.bindTexture("DepthSampler", input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.bindTexture("HistoryDepthSampler", readDepth != null ? readDepth.getColorTextureView() : input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
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
                renderPass.bindTexture("HistoryDepthSampler", readDepth != null ? readDepth.getColorTextureView() : input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.setUniform("TaaParams", taaParams);
                renderPass.setUniform("Reproj", reprojUbo.uploadReproj(0.0f, debugView, useDepthTex));
                renderPass.draw(0, 3);
            }
        }
        *///?}

        // pass 1b: pack current depth into writeDepth for next frame's disocclusion test
        //? > 26.1 {
        if (writeDepth != null) {
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "TAA depth copy", writeDepth.getColorTextureView(), Optional.empty())) {
                renderPass.setPipeline(TAA_DEPTH_COPY_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.draw(3, 1, 0, 0);
            }
        }
        //?} else {
        /*if (writeDepth != null) {
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "TAA depth copy", writeDepth.getColorTextureView(), OptionalInt.empty())) {
                renderPass.setPipeline(TAA_DEPTH_COPY_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", input.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.draw(0, 3);
            }
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
