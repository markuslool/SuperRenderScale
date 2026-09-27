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
uniform sampler2D CSampler;

out vec4 fragColor;

float bcubic(float x) {
    float ax = abs(x);
    if (ax <= 1.0) return 1.25 * ax * ax * ax - 2.25 * ax * ax + 1.0;
    else if (ax < 2.0) return -0.75 * ax * ax * ax + 3.75 * ax * ax - 6.0 * ax + 3.0;
    else return 0.0;
}

vec3 bicubicFast(vec2 src, vec2 sizeC) {
    vec2 b = floor(src);
    vec2 fr = src - b;
    float w0x = bcubic(fr.x + 1.0);
    float w1x = bcubic(fr.x);
    float w2x = bcubic(fr.x - 1.0);
    float w3x = bcubic(fr.x - 2.0);
    float w0y = bcubic(fr.y + 1.0);
    float w1y = bcubic(fr.y);
    float w2y = bcubic(fr.y - 1.0);
    float w3y = bcubic(fr.y - 2.0);
    float s01x = max(w0x + w1x, 1e-6);
    float s23x = max(w2x + w3x, 1e-6);
    float s01y = max(w0y + w1y, 1e-6);
    float s23y = max(w2y + w3y, 1e-6);
    vec2 c0 = vec2(b.x + 0.5 - w0x / s01x, b.y + 0.5 - w0y / s01y);
    vec2 c1 = vec2(b.x + 1.5 + w3x / s23x, b.y + 1.5 + w3y / s23y);
    vec3 p00 = texture(CSampler, c0 / sizeC).rgb;
    vec3 p10 = texture(CSampler, vec2(c1.x, c0.y) / sizeC).rgb;
    vec3 p01 = texture(CSampler, vec2(c0.x, c1.y) / sizeC).rgb;
    vec3 p11 = texture(CSampler, c1 / sizeC).rgb;
    return p00 * (s01x * s01y) + p10 * (s23x * s01y)
         + p01 * (s01x * s23y) + p11 * (s23x * s23y);
}

void main()
{
    ivec2 outPx = ivec2(gl_FragCoord.xy);
    ivec2 sizeA = textureSize(ASampler, 0);
    ivec2 base = outPx / 2;
    ivec2 srcPos = clamp(base, ivec2(0), max(sizeA - ivec2(1), ivec2(0)));
    vec4 h0 = texelFetch(ASampler, srcPos, 0);
    vec4 h1 = texelFetch(BSampler, srcPos, 0);
    float r0 = -0.05406484 + 0.18188652 * h0.x + -0.11138799 * h0.y + 0.0 * h0.z + 0.04893895 * h0.w + -0.26454246 * h1.x + -0.12442312 * h1.y + 0.2164399 * h1.z + 0.14291254 * h1.w;
    float r1 = -0.06912914 + -0.12114154 * h0.x + 0.01911076 * h0.y + 0.0 * h0.z + 0.22076604 * h0.w + -0.13411053 * h1.x + -0.26480228 * h1.y + 0.03552284 * h1.z + 0.18353365 * h1.w;
    float r2 = -0.02773324 + 0.26175156 * h0.x + -0.17024228 * h0.y + 0.0 * h0.z + 0.13260815 * h0.w + 0.29646519 * h1.x + -0.1393415 * h1.y + 0.15854256 * h1.z + 0.08273026 * h1.w;
    float r3 = -0.04145041 + -0.02710576 * h0.x + -0.04498478 * h0.y + 0.0 * h0.z + 0.30890229 * h0.w + 0.46022913 * h1.x + -0.27585495 * h1.y + -0.02191502 * h1.z + 0.11665517 * h1.w;
    float r4 = -0.05507801 + 0.16728599 * h0.x + -0.10426343 * h0.y + 0.0 * h0.z + 0.05030774 * h0.w + -0.27646038 * h1.x + -0.12892076 * h1.y + 0.21126063 * h1.z + 0.14618707 * h1.w;
    float r5 = -0.06848279 + -0.11891898 * h0.x + 0.0165188 * h0.y + 0.0 * h0.z + 0.21925679 * h0.w + -0.13996188 * h1.x + -0.2660968 * h1.y + 0.03594201 * h1.z + 0.18567848 * h1.w;
    float r6 = -0.02679126 + 0.25492048 * h0.x + -0.16670273 * h0.y + 0.0 * h0.z + 0.13277297 * h0.w + 0.30193925 * h1.x + -0.13702856 * h1.y + 0.15273193 * h1.z + 0.07978099 * h1.w;
    float r7 = -0.03987298 + -0.03328554 * h0.x + -0.04206326 * h0.y + 0.0 * h0.z + 0.30454692 * h0.w + 0.45268238 * h1.x + -0.27454376 * h1.y + -0.02761699 * h1.z + 0.11629739 * h1.w;
    float r8 = -0.05394451 + 0.17475235 * h0.x + -0.10751094 * h0.y + 0.0 * h0.z + 0.0509226 * h0.w + -0.26299158 * h1.x + -0.12644109 * h1.y + 0.2113504 * h1.z + 0.14287786 * h1.w;
    float r9 = -0.06904436 + -0.12380193 * h0.x + 0.02014951 * h0.y + 0.0 * h0.z + 0.22023076 * h0.w + -0.1481913 * h1.x + -0.26577014 * h1.y + 0.0336936 * h1.z + 0.18572439 * h1.w;
    float r10 = -0.02639337 + 0.25630841 * h0.x + -0.16617803 * h0.y + 0.0 * h0.z + 0.12929502 * h0.w + 0.29466975 * h1.x + -0.13441075 * h1.y + 0.15334813 * h1.z + 0.07865208 * h1.w;
    float r11 = -0.03981203 + -0.02666855 * h0.x + -0.04669757 * h0.y + 0.0 * h0.z + 0.30562681 * h0.w + 0.46075901 * h1.x + -0.27689418 * h1.y + -0.02424962 * h1.z + 0.11763262 * h1.w;
    ivec2 idx = outPx - base * 2;
    int k = idx.y * 2 + idx.x;
    float rr = k == 0 ? r0 : (k == 1 ? r1 : (k == 2 ? r2 : r3));
    float gg = k == 0 ? r4 : (k == 1 ? r5 : (k == 2 ? r6 : r7));
    float bb = k == 0 ? r8 : (k == 1 ? r9 : (k == 2 ? r10 : r11));
    vec2 sizeC = vec2(textureSize(CSampler, 0));
    vec2 srcc = (vec2(outPx) + 0.5) * 0.5 - 0.5;
    vec3 baseCol = bicubicFast(srcc, sizeC);
    fragColor = vec4(clamp(baseCol + vec3(rr, gg, bb), 0.0, 1.0), 1.0);
}
