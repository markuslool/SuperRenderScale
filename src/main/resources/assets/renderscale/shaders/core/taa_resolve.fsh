#version 420

#define A_GPU 1
#define A_GLSL 1

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;      // current frame (EASU base or scaled target)
uniform sampler2D HistorySampler; // previous high-res output
uniform sampler2D DepthSampler;   // current depth (same size as InSampler source)

// Must match RenderPipeline.withUniform("Reproj", UNIFORM_BUFFER).
// Per-pixel camera motion (FSR2-style); x=1 enables, 0 = jitter fallback.
// y = debug view: 0 off, 1 depth grayscale, 2 motion vectors as color.
layout(std140) uniform Reproj {
    mat4 PrevVP;
    mat4 InvVP;
    vec4 ReprojFlags;
};

// Must match RenderPipeline.withUniform("TaaParams", UNIFORM_BUFFER).
// xy = jitter in output UV units, z = history blend weight, w = sharpness 0..1.
layout(std140) uniform TaaParams {
    vec4 Params;
};

out vec4 fragColor;

void main() {
    vec2 juv = Params.xy;
    float blendBase = Params.z;
    float sharp = Params.w;

    vec2 outSize = vec2(ScreenSize.x, ScreenSize.y);
    vec2 inSize = vec2(textureSize(InSampler, 0));
    vec2 uv = gl_FragCoord.xy / outSize;

    // Per-pixel reprojection: depth -> world (current inverse) -> previous clip.
    // Covers camera AND jitter motion, so no separate jitter offset is needed.
    // Depth texture may be empty on backends with transient depth (Vulkan):
    // then ReprojFlags.z = 0 forces the far plane, which is still EXACT for
    // pure rotation (distance-independent) and only approximates translation.
    vec2 historyUv;
    if (ReprojFlags.x > 0.5) {
        float depth = ReprojFlags.z > 0.5 ? texture(DepthSampler, uv).r : 1.0;
        vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec4 world = InvVP * ndc;
        world /= world.w;
        vec4 prev = PrevVP * world;
        prev /= prev.w;
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

    vec3 curr = texture(InSampler, uv).rgb;

    // 5-tap cross neighbourhood box in INPUT texel space
    // (gl_FragCoord is output space; the input may be smaller!).
    // taps are reused as the blur for the unsharp mask: 7 taps total
    // instead of 15. corners are uncovered, diff-rejection below covers them.
    ivec2 base = ivec2(uv * inSize);
    ivec2 lo = ivec2(inSize) - ivec2(1);
    vec3 t0 = texelFetch(InSampler, clamp(base, ivec2(0), lo), 0).rgb;
    vec3 t1 = texelFetch(InSampler, clamp(base + ivec2(-1, 0), ivec2(0), lo), 0).rgb;
    vec3 t2 = texelFetch(InSampler, clamp(base + ivec2(1, 0), ivec2(0), lo), 0).rgb;
    vec3 t3 = texelFetch(InSampler, clamp(base + ivec2(0, -1), ivec2(0), lo), 0).rgb;
    vec3 t4 = texelFetch(InSampler, clamp(base + ivec2(0, 1), ivec2(0), lo), 0).rgb;
    vec3 mn = min(curr, min(min(t0, t1), min(t2, min(t3, t4))));
    vec3 mx = max(curr, max(max(t0, t1), max(t2, max(t3, t4))));

    // unsharp mask on the CURRENT frame only (never amplify history residue)
    vec3 blur = (t1 + t2 + t3 + t4) * 0.25;
    vec3 sharpCurr = curr + (curr - blur) * sharp;

    vec3 hist = texture(HistorySampler, historyUv).rgb;
    hist = clamp(hist, mn, mx);

    // reject history where it disagrees with the current frame (compare
    // against the UNSHARPENED frame so static sharp detail isn't rejected).
    // this is what actually kills trails on bright/small/disoccluded pixels:
    // static detail has hist ~= curr (kept), ghosts have large diff (dropped).
    // NOTE: low-contrast motion (grass-on-grass) barely registers in length(),
    // hence the high gain: camera-translation smear is handled by blendBase.
    float diff = clamp(length(hist - curr) * 2.5, 0.0, 1.0);
    float blend = mix(blendBase, 1.0, diff * diff);

    // reprojected outside the frame (fast turns, behind camera): no valid history
    vec2 oob = step(historyUv, vec2(-0.05)) + step(vec2(1.05), historyUv);
    blend = mix(blend, 1.0, clamp(oob.x + oob.y, 0.0, 1.0));

    vec3 col = mix(sharpCurr, hist, 1.0 - blend);

    fragColor = vec4(col, 1.0);
}
