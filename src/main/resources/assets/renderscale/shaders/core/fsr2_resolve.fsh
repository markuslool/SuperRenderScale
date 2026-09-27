#version 420

#define A_GPU 1
#define A_GLSL 1

#moj_import <minecraft:globals.glsl>

// InSampler MUST be the raw jittered low-res frame (never an EASU/base
// upsample: resampling before accumulation destroys the subpixel jitter
// information that temporal super-resolution stacks into extra detail).
uniform sampler2D InSampler;       // current frame, raw, jittered
uniform sampler2D HistorySampler;  // previous FSR2 output
uniform sampler2D DepthSampler;    // current depth (same size as InSampler source)
uniform sampler2D HistoryDepthSampler; // previous depth, packed RG (NEAREST)

// Must match RenderPipeline.withUniform("Reproj", UNIFORM_BUFFER).
// Per-pixel camera motion (FSR2-style); x=1 enables, 0 = jitter fallback.
// y = debug view: 0 off, 1 depth grayscale, 2 motion vectors as color.
layout(std140) uniform Reproj {
    mat4 PrevVP;
    mat4 InvVP;
    vec4 ReprojFlags;
};

// Must match RenderPipeline.withUniform("TaaParams", UNIFORM_BUFFER).
// xy = CURRENT frame jitter in output UV units (always real, even when
// reprojection is on: the Lanczos reconstruction below needs it for
// jitter cancellation), z = history blend weight,
// w = reactive strength 0..1 (FSR2 reactive mask approximation).
layout(std140) uniform TaaParams {
    vec4 Params;
};

out vec4 fragColor;

// FSR2 accumulates in YCoCg: luma decorrelated from chroma, so clamping
// history to the neighbourhood box kills ghosts without desaturating edges.
vec3 rgbToYCoCg(vec3 c) {
    float co = c.r - c.b;
    float t = c.b + co * 0.5;
    float cg = c.g - t;
    float y = t + cg * 0.5;
    return vec3(y, co, cg);
}

vec3 yCoCgToRgb(vec3 c) {
    float t = c.x - c.z * 0.5;
    float g = c.z + t;
    float b = t - c.y * 0.5;
    float r = b + c.y;
    return vec3(r, g, b);
}

// Lanczos-2 lobe: sinc(x) * sinc(x/2), zero past |x| = 2.
float lanczosW(float x) {
    x = abs(x);
    if (x < 1e-4) return 1.0;
    if (x >= 2.0) return 0.0;
    float pix = 3.14159265 * x;
    float s1 = sin(pix) / pix;
    float s2 = sin(pix * 0.5) / (pix * 0.5);
    return s1 * s2;
}

void main() {
    vec2 juv = Params.xy;
    float blendBase = Params.z;
    float reactiveStrength = clamp(Params.w, 0.0, 1.0);

    vec2 outSize = vec2(ScreenSize.x, ScreenSize.y);
    vec2 inSize = vec2(textureSize(InSampler, 0));
    vec2 uv = gl_FragCoord.xy / outSize;

    // ---- pass 0: per-pixel reprojection (same as TAAU) ----
    // Depth -> world (current inverse) -> previous clip. Covers camera AND
    // jitter motion, so no separate jitter offset is needed for HISTORY.
    // Depth texture may be empty on backends with transient depth (Vulkan):
    // then ReprojFlags.z = 0 forces the far plane, which is still EXACT for
    // pure rotation (distance-independent) and only approximates translation.
    vec2 historyUv;
    float repDepth = 1.0;
    if (ReprojFlags.x > 0.5) {
        float depth = 1.0;
        if (ReprojFlags.z > 0.5) {
            // 3x3 nearest-depth dilation in INPUT texel space: the foreground
            // wins, so thin occluders (grass, fences) reproject with their own
            // motion instead of the background's (kills edge halo).
            vec2 depthSize = vec2(textureSize(DepthSampler, 0));
            ivec2 dBase = ivec2(uv * depthSize);
            ivec2 dLo = ivec2(depthSize) - ivec2(1);
            for (int oy = -1; oy <= 1; ++oy)
                for (int ox = -1; ox <= 1; ++ox)
                    depth = min(depth, texelFetch(DepthSampler, clamp(dBase + ivec2(ox, oy), ivec2(0), dLo), 0).r);
        }
        vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec4 world = InvVP * ndc;
        world /= world.w;
        vec4 prev = PrevVP * world;
        prev /= prev.w;
        repDepth = prev.z * 0.5 + 0.5;
        historyUv = prev.xy * 0.5 + 0.5;
    } else {
        historyUv = uv - juv;
    }

    if (ReprojFlags.y > 0.5) {
        if (ReprojFlags.y < 1.5) {
            float dd = texture(DepthSampler, uv).r;
            fragColor = vec4(vec3(dd), 1.0);
        } else {
            // gain 8: slow turns read as smooth gradients, hard flicks saturate
            vec2 mm = clamp((uv - historyUv) * 8.0, -1.0, 1.0);
            fragColor = vec4(mm * 0.5 + 0.5, 0.6, 1.0);
        }
        return;
    }

    // ---- pass 1: jitter-cancelled Lanczos-2 reconstruction ----
    // The input was rendered with the projection shifted by +juv, so the
    // un-jittered sample point is (uv - juv). A 4x4 Lanczos-2 footprint
    // around it in INPUT pixels gathers the true subpixel samples; stacking
    // these across frames (each with different jitter) is what actually
    // resolves detail beyond the low-res frame. The same 16 taps build the
    // YCoCg neighbourhood box for history clamping below.
    ivec2 lo = ivec2(inSize) - ivec2(1);
    vec2 center = (uv - juv) * inSize;
    vec2 t0 = floor(center) - 1.0;
    float wx[4];
    float wy[4];
    for (int i = 0; i < 4; ++i) {
        wx[i] = lanczosW(center.x - (t0.x + float(i) + 0.5));
        wy[i] = lanczosW(center.y - (t0.y + float(i) + 0.5));
    }
    vec3 currYCoCg = vec3(0.0);
    vec3 mn = vec3(1e6);
    vec3 mx = vec3(-1e6);
    float lumaSum = 0.0;
    float wSum = 0.0;
    for (int j = 0; j < 4; ++j) {
        for (int i = 0; i < 4; ++i) {
            ivec2 px = clamp(ivec2(t0) + ivec2(i, j), ivec2(0), lo);
            vec3 t = rgbToYCoCg(texelFetch(InSampler, px, 0).rgb);
            float w = wx[i] * wy[j];
            currYCoCg += t * w;
            wSum += w;
            mn = min(mn, t);
            mx = max(mx, t);
            lumaSum += t.x;
        }
    }
    currYCoCg /= max(wSum, 1e-4);
    // Slight AABB rounding: quantised history jitters at the box walls.
    vec3 e = vec3(0.002);
    mn -= e;
    mx += e;

    // ---- pass 2: sample + clamp history ----
    vec3 hist = rgbToYCoCg(texture(HistorySampler, historyUv).rgb);
    vec3 histClamped = clamp(hist, mn, mx);

    // first frame after enable/resize/mode-switch: no valid history yet.
    // while moving fast, lean on the current frame (standard trick, detail
    // re-accumulates when still).
    float blend = blendBase;

    // diff rejection in YCoCg: static detail has hist ~= curr (kept),
    // ghosts have large diff (dropped). High gain on purpose: a misaligned
    // history pixel must never survive, it would smear as a trail.
    float diff = clamp(length((histClamped - currYCoCg) * vec3(1.0, 0.5, 0.5)) * 3.0, 0.0, 1.0);
    blend = mix(blend, 1.0, diff * diff);

    // lock: thin subpixel detail (large luma range in the box) must not
    // accumulate much history, or it shimmers and crawls (FSR2 "lock").
    float yRange = mx.x - mn.x;
    float lock = clamp(yRange * 6.0, 0.0, 1.0);
    lock *= lock;
    blend = mix(blend, 1.0, lock * 0.5);

    // reactive approximation: no engine reactive mask exists, so treat
    // near-white-hot emissive pixels (lava, fire, torches, glowing ores)
    // plus high local variance as reactive: they flicker, history smears them.
    // scaled by the user reactive slider (Params.w).
    float lumaAvg = lumaSum / 16.0;
    float hot = clamp((currYCoCg.x - 0.75) / 0.25, 0.0, 1.0);
    float varReac = clamp(yRange * 4.0, 0.0, 1.0) * clamp((lumaAvg - 0.35) / 0.4, 0.0, 1.0);
    float reactive = clamp(hot + varReac * 0.5, 0.0, 1.0) * reactiveStrength;
    blend = mix(blend, 1.0, reactive * reactive);

    // luma stability: flickering light changes brightness with no motion;
    // history would drag the old value along as a light-trail.
    float lumaDiff = clamp(abs(histClamped.x - currYCoCg.x) * 5.0, 0.0, 1.0);
    blend = mix(blend, 1.0, lumaDiff * lumaDiff);

    // disocclusion: reproject this pixel's depth into the previous frame and
    // compare with the stored history depth (packed RG, ~16 bit). A different
    // surface means the history texel is stale (uncovered background) -> drop.
    // Needs real depth (ReprojFlags.z) and camera matrices (ReprojFlags.x).
    if (ReprojFlags.x > 0.5 && ReprojFlags.z > 0.5) {
        float histD = dot(texture(HistoryDepthSampler, historyUv).rg, vec2(1.0, 1.0 / 255.0));
        float occThresh = 0.004 + repDepth * repDepth * 0.02;
        blend = mix(blend, 1.0, step(occThresh, abs(histD - repDepth)));
    }

    // reprojected outside the frame (fast turns, behind camera): no valid history
    vec2 oob = step(historyUv, vec2(-0.05)) + step(vec2(1.05), historyUv);
    blend = mix(blend, 1.0, clamp(oob.x + oob.y, 0.0, 1.0));

    // NOTE: no inline sharpening here (unlike taa_resolve): FSR2 sharpens
    // AFTER accumulation with a dedicated RCAS pass in Java.
    vec3 acc = mix(currYCoCg, histClamped, 1.0 - blend);

    fragColor = vec4(yCoCgToRgb(acc), 1.0);
}
