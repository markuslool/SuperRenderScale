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
    vec4 res = vec4(-0.13750222, 0.02523976, 0.04830199, 0.01796443);
    res += vec4(-0.05613898, 0.1470883, 0.61740065, -0.05152706) * t00.r + vec4(0.01422846, 0.16839787, 0.42039472, -0.16564234) * t00.g + vec4(-0.04390125, 0.20876433, 0.40986821, 0.04079892) * t00.b;
    res += vec4(-0.10858988, -0.22543618, -0.03533315, 0.17952152) * t01.r + vec4(0.13191421, -0.38234514, 0.14368422, 0.25145447) * t01.g + vec4(-0.09173645, -0.41587779, 0.08779536, 0.1456989) * t01.b;
    res += vec4(0.11873771, 0.19695404, 0.12716281, 0.38906527) * t02.r + vec4(-0.14137658, 0.1838873, 0.00794058, 0.3526983) * t02.g + vec4(-0.04678838, 0.2037003, 0.0153807, 0.21494995) * t02.b;
    res += vec4(0.00289587, 0.02966158, 0.1541021, 0.13053937) * t10.r + vec4(-0.05434709, 0.0337067, 0.00610894, -0.00865107) * t10.g + vec4(0.1690931, 0.06730001, 0.0381589, 0.04535147) * t10.b;
    res += vec4(0.03260602, -0.03028891, -0.38468078, -0.0863893) * t11.r + vec4(-0.11841732, 0.11391386, -0.33383483, 0.08649294) * t11.g + vec4(0.02886671, 0.09825905, -0.25209302, -0.05228272) * t11.b;
    res += vec4(0.10292736, 0.12557074, 0.3588649, 0.0772207) * t12.r + vec4(0.06937697, 0.10430824, 0.48279101, 0.07569156) * t12.g + vec4(-0.1076958, 0.04043112, 0.31669384, 0.03036209) * t12.b;
    res += vec4(0.01609439, 0.13590562, 0.30292717, 0.028035) * t20.r + vec4(0.11089959, 0.12837072, 0.33536282, -0.05930115) * t20.g + vec4(-0.14064641, -0.02582703, 0.21327364, -0.17876887) * t20.b;
    res += vec4(-0.00226326, 0.01860348, 0.28144303, 0.07953055) * t21.r + vec4(-0.14393961, -0.00203763, 0.22555859, 0.1017849) * t21.g + vec4(-0.07526301, 0.10076331, 0.31788692, 0.17230469) * t21.b;
    res += vec4(-0.16705161, 0.14493483, 0.1897642, 0.02464291) * t22.r + vec4(-0.11070587, 0.15367486, 0.12293227, 0.04080183) * t22.g + vec4(0.04130841, 0.12497956, -0.04776144, 0.11918914) * t22.b;
    fragColor = max(res, vec4(0.0));
}
