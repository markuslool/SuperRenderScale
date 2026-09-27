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
    vec4 res = vec4(0.014215, 0.06851, 0.02459, 0.123171);
    res += vec4(0.213184, 0.101687, 0.054399, 0.104529) * t0.r + vec4(0.015489, 0.027674, -0.125125, -0.136995) * t0.g + vec4(0.001667, -0.084088, -0.150262, -0.059922) * t0.b;
    res += vec4(0.210097, 0.150284, 0.010962, -0.134452) * t1.r + vec4(0.226176, 0.045224, 0.152342, 0.232213) * t1.g + vec4(0.200483, -0.145049, 0.198482, 0.11269) * t1.b;
    res += vec4(-0.088464, 0.182671, 0.113985, 0.202897) * t2.r + vec4(-0.093476, -0.087718, -0.134013, -0.297006) * t2.g + vec4(0.062882, -0.096012, 0.174923, 0.048317) * t2.b;
    res += vec4(-0.028211, -0.07103, -0.042031, 0.038981) * t3.r + vec4(0.060682, 0.061618, -0.065396, 0.361846) * t3.g + vec4(-0.056195, 0.170908, 0.014972, -0.191737) * t3.b;
    res += vec4(0.179149, -0.074749, -0.072235, -0.316) * t4.r + vec4(-0.089571, -0.010678, 0.019882, 0.603213) * t4.g + vec4(-0.061004, -0.18425, -0.000786, -0.475919) * t4.b;
    res += vec4(-0.140595, -0.046085, -0.147538, -0.075395) * t5.r + vec4(-0.016091, -0.058145, 0.111341, 0.143081) * t5.g + vec4(0.147908, 0.057732, 0.210839, 0.136447) * t5.b;
    res += vec4(-0.117478, 0.119188, 0.139792, -0.035876) * t6.r + vec4(0.134581, -0.098047, -0.143376, -0.195667) * t6.g + vec4(-0.081489, -0.0104, 0.168237, 0.158077) * t6.b;
    res += vec4(-0.045184, 0.168501, -0.059703, 0.205115) * t7.r + vec4(-0.088969, -0.008288, 0.172126, 0.223154) * t7.g + vec4(0.188453, 0.102184, -0.027966, -0.156205) * t7.b;
    res += vec4(0.084446, 0.031799, 0.104184, 0.024769) * t8.r + vec4(-0.017145, -0.084668, -0.088116, -0.182456) * t8.g + vec4(-0.146448, -0.044204, 0.037187, -0.019065) * t8.b;
    fragColor = max(res, vec4(0.0));
}
