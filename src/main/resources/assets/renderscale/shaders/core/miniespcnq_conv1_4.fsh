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
    vec4 res = vec4(0.14889, 8.2e-05, -0.022531, 0.21264);
    res += vec4(0.083452, 0.090344, -0.471833, 0.092123) * t0.r + vec4(-0.098208, 0.029538, -0.238769, 0.101055) * t0.g + vec4(0.140155, 0.047197, -0.288877, -0.20727) * t0.b;
    res += vec4(-0.176548, -0.002736, -0.028913, -0.050137) * t1.r + vec4(0.173289, 0.053011, -0.121723, 0.099269) * t1.g + vec4(0.195923, -0.064894, -0.030501, -0.118508) * t1.b;
    res += vec4(-0.135399, 0.068998, -0.096636, -0.02607) * t2.r + vec4(0.036478, -0.02959, -0.003113, -0.067058) * t2.g + vec4(-0.093843, -0.102108, -0.150062, 0.119392) * t2.b;
    res += vec4(-0.184412, 0.2161, 0.096309, 0.146249) * t3.r + vec4(0.057368, 0.342416, 0.212291, 0.044821) * t3.g + vec4(-0.109096, 0.369789, -0.199546, 0.031104) * t3.b;
    res += vec4(0.254111, -0.215424, -0.021023, -0.176513) * t4.r + vec4(0.318708, -0.216388, 0.199704, -0.215129) * t4.g + vec4(0.14445, 0.022574, 0.361229, 0.160912) * t4.b;
    res += vec4(0.137084, 0.059346, -0.118816, 0.072591) * t5.r + vec4(-0.0886, 0.004311, 0.051871, -0.117843) * t5.g + vec4(-0.099565, 0.118404, 0.108298, 0.061635) * t5.b;
    res += vec4(-0.064335, -0.095692, -0.150428, -0.200974) * t6.r + vec4(0.116101, -0.033836, 0.022653, -0.026659) * t6.g + vec4(-0.038362, -0.283382, -0.161123, -0.097029) * t6.b;
    res += vec4(0.028212, -0.27921, 0.063738, 0.065768) * t7.r + vec4(0.185254, -0.32135, 0.249429, 0.035423) * t7.g + vec4(-0.09497, -0.285898, 0.029046, 0.221189) * t7.b;
    res += vec4(-0.067148, -0.000222, -0.050651, 0.174506) * t8.r + vec4(-0.062956, -0.000652, 0.0138, -0.128545) * t8.g + vec4(-0.19445, -0.045496, -0.021001, -0.084342) * t8.b;
    fragColor = max(res, vec4(0.0));
}
