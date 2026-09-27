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
    vec4 res = vec4(0.080331, 0.039573, 0.002814, -0.002391);
    res += vec4(-0.163134, -0.13643, 0.151245, -0.005844) * t0.r + vec4(-0.109105, -0.137323, -0.00739, -0.054594) * t0.g + vec4(0.082076, 0.03213, 0.210612, -0.014161) * t0.b;
    res += vec4(0.084167, -0.119532, -0.081743, 0.131346) * t1.r + vec4(0.059555, -0.070198, -0.01557, -0.065246) * t1.g + vec4(-0.060535, 0.095284, -0.154393, -0.059455) * t1.b;
    res += vec4(-0.190099, -0.029313, 0.020437, -0.058607) * t2.r + vec4(-0.07415, -0.145017, 0.026866, -0.078488) * t2.g + vec4(0.119618, 0.154284, 0.057741, 0.071085) * t2.b;
    res += vec4(0.270951, 0.198382, 0.044918, 0.069078) * t3.r + vec4(-0.102063, 0.192855, -0.051904, 0.002247) * t3.g + vec4(-0.077759, 0.146846, -0.108033, -0.160713) * t3.b;
    res += vec4(0.35206, -0.002737, -0.063848, 0.300704) * t4.r + vec4(0.288911, 0.106625, 0.11276, 0.252345) * t4.g + vec4(-0.144793, 0.122647, -0.06337, 0.283743) * t4.b;
    res += vec4(-0.084778, -0.000859, 0.071685, 0.004534) * t5.r + vec4(0.228072, 0.22755, -0.178641, -0.0257) * t5.g + vec4(-0.200311, 0.187349, -0.257824, -0.05494) * t5.b;
    res += vec4(0.036556, 0.166563, -0.145571, -0.136727) * t6.r + vec4(-0.118457, -0.139663, 0.168562, 0.027723) * t6.g + vec4(0.106542, -0.10657, 0.249289, 0.087806) * t6.b;
    res += vec4(-0.234005, -0.032838, -0.029134, -0.018425) * t7.r + vec4(-0.059413, -0.04647, 0.151937, 0.00686) * t7.g + vec4(-0.0336, 0.021761, -0.055488, -0.107184) * t7.b;
    res += vec4(-0.041155, -0.035731, 0.113481, -3.129614) * t8.r + vec4(0.132289, 0.165596, -0.196546, -2.84494) * t8.g + vec4(0.114394, 0.171811, 0.230277, -2.622521) * t8.b;
    fragColor = max(res, vec4(0.0));
}
