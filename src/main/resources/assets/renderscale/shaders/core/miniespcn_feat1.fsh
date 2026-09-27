#version 420

// MiniESPCN x2 student, ported from the Magpie HLSL effect to a
// RenderScale fullscreen pass. 448 MAC per LR pixel (v2: fused
// depthwise+mix, Keys a=-0.75 fast-bicubic base via 4 bilinear taps).
// out = bicubic(input) + pixelshuffle(residual). Border taps are
// zero-padded (OOB reads contribute 0), matching the HLSL ConvInAt
// and the LDS halo fill. All taps are texelFetch except the base,
// which uses LINEAR sampling for the bicubic factorization.

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
    vec3 t00 = fetch0(px + ivec2(-1, -1), sizeIn);
    vec3 t01 = fetch0(px + ivec2(0, -1), sizeIn);
    vec3 t02 = fetch0(px + ivec2(1, -1), sizeIn);
    vec3 t10 = fetch0(px + ivec2(-1, 0), sizeIn);
    vec3 t11 = fetch0(px + ivec2(0, 0), sizeIn);
    vec3 t12 = fetch0(px + ivec2(1, 0), sizeIn);
    vec3 t20 = fetch0(px + ivec2(-1, 1), sizeIn);
    vec3 t21 = fetch0(px + ivec2(0, 1), sizeIn);
    vec3 t22 = fetch0(px + ivec2(1, 1), sizeIn);
    vec4 res = vec4(0.01302827, 0.00561138, 0.00716217, 0.00674055);
    res += vec4(-0.07798785, -0.12654515, -0.12110808, 0.15783444) * t00.r + vec4(-0.11319524, -0.32177624, -0.14929432, 0.19671816) * t00.g + vec4(-0.05905711, -0.2400548, -0.03458354, 0.25860909) * t00.b;
    res += vec4(0.27937171, 0.0680327, 0.02960613, 0.05031441) * t01.r + vec4(0.34117147, 0.17510757, 0.04460474, 0.14986883) * t01.g + vec4(0.25535032, 0.07518551, 0.03974196, -0.06931515) * t01.b;
    res += vec4(-0.0966873, 0.31012571, 0.07673785, 0.04477091) * t02.r + vec4(-0.17195615, 0.40308145, 0.13023153, -0.05859974) * t02.g + vec4(-0.11824114, 0.30628309, -0.04679263, -0.01020504) * t02.b;
    res += vec4(0.03942341, 0.28405082, 0.09562408, -0.026471) * t10.r + vec4(-0.01312597, 0.25480062, 0.18233345, -0.24485999) * t10.g + vec4(0.07490352, 0.27780372, 0.09635559, -0.02560862) * t10.b;
    res += vec4(-0.25866324, 0.0618526, 0.00775689, -0.26620522) * t11.r + vec4(-0.11508906, -0.06841677, 0.06181265, -0.19007561) * t11.g + vec4(-0.23986678, 0.10260227, -0.04883248, -0.1388128) * t11.b;
    res += vec4(0.28447622, -0.21648584, 0.13150162, -0.03595137) * t12.r + vec4(0.20995599, -0.22007732, 0.09037436, -0.12832147) * t12.g + vec4(0.23597464, -0.28314212, 0.0923418, -0.11805857) * t12.b;
    res += vec4(0.14059156, 0.01073121, 0.02705524, 0.1931113) * t20.r + vec4(0.02135885, 0.13584396, -0.09382316, 0.17088477) * t20.g + vec4(-0.05276577, 0.02397647, -0.11612364, 0.0743475) * t20.b;
    res += vec4(-0.00888641, 0.01213061, -0.23177861, -0.02384197) * t21.r + vec4(0.0532305, 0.05842369, -0.14183488, 0.11893822) * t21.g + vec4(0.10958074, -0.01468751, -0.15430589, -0.02881589) * t21.b;
    res += vec4(0.12553851, 0.02129559, 0.27045926, 0.06997104) * t22.r + vec4(0.13802645, 0.03524936, 0.21289174, 0.10466419) * t22.g + vec4(0.11287726, 0.064041, 0.3883501, 0.15662172) * t22.b;
    fragColor = max(res, vec4(0.0));
}
