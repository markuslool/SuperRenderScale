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
    vec4 res = vec4(0.032608, 0.080115, 0.026129, 0.017226);
    res += vec4(-0.0043, 0.000952, 0.139438, 0.117931) * t0.r + vec4(0.128075, 0.069366, -0.059365, 0.139385) * t0.g + vec4(-0.105718, -0.052791, 0.075481, -0.136119) * t0.b;
    res += vec4(-0.114298, 0.008245, -0.021961, -0.220909) * t1.r + vec4(0.005342, 0.09437, 0.022862, -0.1309) * t1.g + vec4(0.137679, 0.081297, 0.185629, -0.142553) * t1.b;
    res += vec4(0.092935, 0.043597, -0.096895, 0.091819) * t2.r + vec4(0.019755, -0.109835, -0.116302, 0.031629) * t2.g + vec4(0.105802, 0.07514, -0.019093, 0.138822) * t2.b;
    res += vec4(0.170486, 0.102168, -0.07051, 0.249083) * t3.r + vec4(0.070007, -0.141619, 0.057193, -0.082304) * t3.g + vec4(-0.160097, -0.080739, -0.066125, 0.12807) * t3.b;
    res += vec4(-0.039386, 0.254766, 0.062374, 0.02951) * t4.r + vec4(0.132598, -0.057123, 0.127074, -0.139259) * t4.g + vec4(0.028337, -0.176702, 0.024452, 0.087818) * t4.b;
    res += vec4(-0.131489, 0.008106, 0.169824, 0.131649) * t5.r + vec4(0.115213, -0.055199, 0.001307, -0.162134) * t5.g + vec4(0.031801, -0.149728, 0.119932, 0.121349) * t5.b;
    res += vec4(0.078324, 0.143617, -0.065896, 0.035813) * t6.r + vec4(0.042581, 0.036189, 0.106512, 0.03458) * t6.g + vec4(-0.146926, -0.022007, 0.053787, 0.108372) * t6.b;
    res += vec4(0.063293, 0.012684, -0.070598, -0.207245) * t7.r + vec4(0.193228, -0.208961, -0.021846, 0.015523) * t7.g + vec4(-0.163389, 0.141064, -0.162957, -0.032677) * t7.b;
    res += vec4(-0.063799, 0.080072, -0.098205, 0.143665) * t8.r + vec4(0.143926, 0.095091, 0.05872, 0.073908) * t8.g + vec4(0.117876, -0.068585, -0.042388, -0.201984) * t8.b;
    fragColor = max(res, vec4(0.0));
}
