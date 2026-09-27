#version 420

// MiniESPCN x2 student, ported from the Magpie HLSL effect to a
// RenderScale fullscreen pass. 448 MAC per LR pixel (v2: fused
// depthwise+mix, Keys a=-0.75 fast-bicubic base via 4 bilinear taps).
// out = bicubic(input) + pixelshuffle(residual). Border taps are
// zero-padded (OOB reads contribute 0), matching the HLSL ConvInAt
// and the LDS halo fill. All taps are texelFetch except the base,
// which uses LINEAR sampling for the bicubic factorization.

#moj_import <minecraft:globals.glsl>

uniform sampler2D ASampler;
uniform sampler2D BSampler;
out vec4 fragColor;

vec4 fetchA(ivec2 p, ivec2 size) {
    if (p.x < 0 || p.y < 0 || p.x >= size.x || p.y >= size.y) return vec4(0.0);
    return texelFetch(ASampler, p, 0);
}

vec4 fetchB(ivec2 p, ivec2 size) {
    if (p.x < 0 || p.y < 0 || p.x >= size.x || p.y >= size.y) return vec4(0.0);
    return texelFetch(BSampler, p, 0);
}

void main()
{
    ivec2 px = ivec2(gl_FragCoord.xy);
    ivec2 sizeA = textureSize(ASampler, 0);
    ivec2 sizeB = textureSize(BSampler, 0);
    vec4 a00 = fetchA(px + ivec2(-1, -1), sizeA);
    vec4 a01 = fetchA(px + ivec2(0, -1), sizeA);
    vec4 a02 = fetchA(px + ivec2(1, -1), sizeA);
    vec4 a10 = fetchA(px + ivec2(-1, 0), sizeA);
    vec4 a11 = fetchA(px + ivec2(0, 0), sizeA);
    vec4 a12 = fetchA(px + ivec2(1, 0), sizeA);
    vec4 a20 = fetchA(px + ivec2(-1, 1), sizeA);
    vec4 a21 = fetchA(px + ivec2(0, 1), sizeA);
    vec4 a22 = fetchA(px + ivec2(1, 1), sizeA);
    vec4 b00 = fetchB(px + ivec2(-1, -1), sizeB);
    vec4 b01 = fetchB(px + ivec2(0, -1), sizeB);
    vec4 b02 = fetchB(px + ivec2(1, -1), sizeB);
    vec4 b10 = fetchB(px + ivec2(-1, 0), sizeB);
    vec4 b11 = fetchB(px + ivec2(0, 0), sizeB);
    vec4 b12 = fetchB(px + ivec2(1, 0), sizeB);
    vec4 b20 = fetchB(px + ivec2(-1, 1), sizeB);
    vec4 b21 = fetchB(px + ivec2(0, 1), sizeB);
    vec4 b22 = fetchB(px + ivec2(1, 1), sizeB);
    float dd0 = -0.2953001 + 0.29473385 * a00.x + -0.27938545 * a01.x + -0.32611609 * a02.x + -0.21406537 * a10.x + 0.18631905 * a11.x + -0.18335369 * a12.x + 0.25259283 * a20.x + 0.14280711 * a21.x + 0.12083134 * a22.x;
    dd0 = max(dd0, 0.0);
    float dd1 = 0.00093597 + -0.01774557 * a00.y + 0.5108276 * a01.y + -0.14973108 * a02.y + 0.16380697 * a10.y + -0.27491587 * a11.y + -0.10408708 * a12.y + 0.13380213 * a20.y + 0.36896679 * a21.y + -0.24754354 * a22.y;
    dd1 = max(dd1, 0.0);
    float dd2 = 0.36427721 + 0.18034732 * a00.z + 0.37944365 * a01.z + 0.1879863 * a02.z + -0.11395153 * a10.z + -0.17341846 * a11.z + -0.24298315 * a12.z + 0.42524007 * a20.z + -0.24270931 * a21.z + 0.10519138 * a22.z;
    dd2 = max(dd2, 0.0);
    float dd3 = -0.00181334 + 0.30501351 * a00.w + -0.3465209 * a01.w + 0.08054377 * a02.w + 0.32070887 * a10.w + -0.08424827 * a11.w + 0.32307452 * a12.w + -0.10427049 * a20.w + -0.08309247 * a21.w + -0.10653395 * a22.w;
    dd3 = max(dd3, 0.0);
    float dd4 = 0.27258146 + 0.25217441 * b00.x + 0.24041332 * b01.x + 0.08466747 * b02.x + 0.26998442 * b10.x + -0.41971773 * b11.x + -0.03626684 * b12.x + -0.2332582 * b20.x + 0.35432723 * b21.x + -0.22175917 * b22.x;
    dd4 = max(dd4, 0.0);
    float dd5 = 0.19431154 + 0.11699944 * b00.y + -0.03764445 * b01.y + 0.05250711 * b02.y + 0.28002504 * b10.y + -0.29250687 * b11.y + 0.18186094 * b12.y + -0.21083903 * b20.y + 0.33316213 * b21.y + -0.26967639 * b22.y;
    dd5 = max(dd5, 0.0);
    float dd6 = 0.18507861 + 0.33416194 * b00.z + -0.39570564 * b01.z + 0.16074768 * b02.z + -0.13770342 * b10.z + 0.21390098 * b11.z + 0.2244128 * b12.z + 0.14123616 * b20.z + -0.23904249 * b21.z + -0.01330857 * b22.z;
    dd6 = max(dd6, 0.0);
    float dd7 = 0.13275233 + 0.44177091 * b00.w + -0.11236133 * b01.w + 0.15034501 * b02.w + 0.05022745 * b10.w + -0.14873876 * b11.w + -0.02753476 * b12.w + 0.47148442 * b20.w + 0.02323339 * b21.w + 0.31813931 * b22.w;
    dd7 = max(dd7, 0.0);
    float m0 = max(-0.0105511 + 0.23893574 * dd0 + -0.22112367 * dd1 + 0.26730087 * dd2 + -0.27745202 * dd3 + 0.01885196 * dd4 + -0.07015944 * dd5 + -0.20938161 * dd6 + -0.27239075 * dd7, 0.0);
    float m1 = max(0.16749687 + -0.19400816 * dd0 + 0.11819948 * dd1 + 0.28354201 * dd2 + 0.38432097 * dd3 + -0.38294816 * dd4 + -0.3774831 * dd5 + -0.24942046 * dd6 + -0.182274 * dd7, 0.0);
    float m2 = max(0.15090957 + -0.3053734 * dd0 + -0.2138685 * dd1 + 0.03127264 * dd2 + -0.32615057 * dd3 + 0.37086537 * dd4 + -0.41237903 * dd5 + 0.3731845 * dd6 + 0.25194082 * dd7, 0.0);
    float m3 = max(-0.21178354 + 0.14872295 * dd0 + 0.03390982 * dd1 + 0.24781023 * dd2 + -0.31735602 * dd3 + 0.49785298 * dd4 + 0.19937588 * dd5 + 0.20497259 * dd6 + 0.29186466 * dd7, 0.0);
    fragColor = vec4(m0, m1, m2, m3);
}
