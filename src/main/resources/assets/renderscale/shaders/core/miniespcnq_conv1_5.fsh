#version 420

// MiniESPCN x2 Q (Magpie port): pure ESPCN, no bicubic base.
// conv1 3->32 k3 ReLU, conv2 32->16 k5 ReLU (4 parts), conv3
// 16->12 k3 linear + pixel-shuffle straight to 2x output.
// ~15.4k MAC per LR pixel (heavyweight; needs a discrete GPU).
// Border taps are zero-padded (OOB reads contribute 0), matching
// the HLSL passes. The final output is NOT clamped (the HLSL has
// no saturate here); all taps are texelFetch (exact).
#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;
out vec4 fragColor;

vec3 fetch0(ivec2 p, ivec2 size) {
    if (p.x < 0 || p.y < 0 || p.x >= size.x || p.y >= size.y) return vec3(0.0);
    return texelFetch(InSampler, p, 0).rgb;
}

void main()
{
    ivec2 px = ivec2(gl_FragCoord.xy);
    ivec2 sizeIn = textureSize(InSampler, 0);
    vec3 t0 = fetch0(px + ivec2(-1, -1), sizeIn);
    vec3 t1 = fetch0(px + ivec2(0, -1), sizeIn);
    vec3 t2 = fetch0(px + ivec2(1, -1), sizeIn);
    vec3 t3 = fetch0(px + ivec2(-1, 0), sizeIn);
    vec3 t4 = fetch0(px + ivec2(0, 0), sizeIn);
    vec3 t5 = fetch0(px + ivec2(1, 0), sizeIn);
    vec3 t6 = fetch0(px + ivec2(-1, 1), sizeIn);
    vec3 t7 = fetch0(px + ivec2(0, 1), sizeIn);
    vec3 t8 = fetch0(px + ivec2(1, 1), sizeIn);
    vec4 res = vec4(0.036878, 0.155069, -0.000422, -0.117663);
    res += vec4(0.180418, -0.073404, -0.831375, -0.042926) * t0.r + vec4(0.048396, -0.177448, -0.125556, -0.071155) * t0.g + vec4(0.082997, -0.027789, -0.179506, -0.162595) * t0.b;
    res += vec4(0.124483, -0.200179, 0.257062, -0.083004) * t1.r + vec4(-0.154055, -0.08874, -0.110438, 0.16656) * t1.g + vec4(0.026367, 0.166728, 0.191728, -0.072174) * t1.b;
    res += vec4(-0.026805, -0.223999, -0.091221, -0.1622) * t2.r + vec4(0.185018, 0.067124, -0.152725, 0.093357) * t2.g + vec4(-0.057447, 0.109866, 0.097619, 0.024233) * t2.b;
    res += vec4(0.059692, 0.014568, 0.189439, -0.156979) * t3.r + vec4(-0.180305, -0.070858, 0.234286, 0.156169) * t3.g + vec4(0.167588, -0.113802, -0.040031, 0.156752) * t3.b;
    res += vec4(0.108627, 0.054317, 0.241407, -0.156753) * t4.r + vec4(0.123312, -0.037924, -0.116623, 0.176365) * t4.g + vec4(0.216138, 0.032388, -0.174254, 0.070442) * t4.b;
    res += vec4(-0.087265, 0.256681, 0.012586, -0.129253) * t5.r + vec4(-0.14283, 0.218161, 0.092287, -0.045339) * t5.g + vec4(-0.095107, -0.034645, 0.060708, 0.221722) * t5.b;
    res += vec4(0.069114, -0.086126, -0.178096, -0.095414) * t6.r + vec4(-0.103164, 0.066487, 0.04772, 0.106127) * t6.g + vec4(-0.079999, 0.090363, -0.011616, 0.01997) * t6.b;
    res += vec4(0.214575, 0.213802, 0.055682, -0.187605) * t7.r + vec4(-0.07536, 0.1053, 0.0696, -0.09227) * t7.g + vec4(0.100551, -0.102224, 0.067973, 0.011103) * t7.b;
    res += vec4(-0.003938, -0.032107, 0.021584, 0.089059) * t8.r + vec4(-0.097933, 0.015048, -0.097398, -0.120805) * t8.g + vec4(-0.025074, -0.15779, -0.014587, 0.106481) * t8.b;
    fragColor = max(res, vec4(0.0));
}
