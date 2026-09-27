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
    vec4 res = vec4(0.006809, 0.117143, -1.4e-05, 0.071221);
    res += vec4(0.014118, 0.128511, 0.032197, 0.075261) * t0.r + vec4(0.14344, -0.092233, 0.148884, -0.071971) * t0.g + vec4(0.131968, 0.09698, -0.008921, -0.067517) * t0.b;
    res += vec4(-0.207202, 0.078762, 0.178463, 0.11473) * t1.r + vec4(-0.018503, 0.211516, -0.048113, 0.185988) * t1.g + vec4(-0.197765, 0.065379, -0.017627, 0.000263) * t1.b;
    res += vec4(0.149843, 0.059863, -0.066572, -0.029909) * t2.r + vec4(0.083144, 0.26436, -0.024974, 0.170798) * t2.g + vec4(0.168518, -0.006328, -0.160651, 0.131994) * t2.b;
    res += vec4(0.090097, -0.022995, 0.003909, 0.154819) * t3.r + vec4(-0.074245, -0.144328, -0.19838, 0.124544) * t3.g + vec4(-0.198704, 0.126205, -0.04483, -0.077367) * t3.b;
    res += vec4(0.026146, -0.201009, 0.184724, -0.128966) * t4.r + vec4(-0.016577, -0.189752, 0.202625, -0.120236) * t4.g + vec4(-0.014391, 0.022416, 0.119231, 0.076662) * t4.b;
    res += vec4(0.093505, -0.062886, 0.105477, 0.118656) * t5.r + vec4(-0.065347, -0.165651, -0.026577, -0.148699) * t5.g + vec4(-0.021964, -0.294828, 0.116301, 0.095847) * t5.b;
    res += vec4(0.090274, -0.026063, 0.123993, -0.067278) * t6.r + vec4(-0.151377, -0.094896, 0.160441, 0.100516) * t6.g + vec4(-0.072464, 0.008329, 0.051649, -0.134884) * t6.b;
    res += vec4(0.044592, 0.062033, 0.039657, -0.19819) * t7.r + vec4(0.008246, 0.061842, 0.184947, 0.172425) * t7.g + vec4(0.150265, 0.119021, -0.035663, -0.1545) * t7.b;
    res += vec4(-0.13214, 0.043679, 0.135952, -0.082245) * t8.r + vec4(0.065655, 0.000791, 0.01207, -0.055058) * t8.g + vec4(0.143786, -0.093047, -0.03291, -0.109788) * t8.b;
    fragColor = max(res, vec4(0.0));
}
