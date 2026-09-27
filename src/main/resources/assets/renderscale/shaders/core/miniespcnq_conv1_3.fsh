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
    vec4 res = vec4(0.095144, 0.135711, 0.063146, 0.122446);
    res += vec4(-0.048926, -0.027345, -0.098322, 0.093445) * t0.r + vec4(-0.089099, 0.077986, -0.151769, -0.074174) * t0.g + vec4(-0.196893, -0.014935, 0.163041, 0.085982) * t0.b;
    res += vec4(0.070479, 0.177165, 0.064405, -0.026245) * t1.r + vec4(0.210104, -0.167581, 0.081892, -0.016238) * t1.g + vec4(0.087478, -0.155442, -0.135714, -0.188255) * t1.b;
    res += vec4(-0.110765, -0.097107, 0.120221, 0.021122) * t2.r + vec4(-0.00877, 0.096729, 0.176634, -0.078985) * t2.g + vec4(-0.07956, 0.070288, -0.039287, -0.05196) * t2.b;
    res += vec4(-0.20417, 0.157519, 0.002484, -0.172229) * t3.r + vec4(0.136326, -0.044129, 0.12716, -0.011296) * t3.g + vec4(0.137899, -0.188419, 0.032492, 0.07462) * t3.b;
    res += vec4(0.012473, 0.216884, 0.140501, 0.113795) * t4.r + vec4(0.129547, -0.011526, -0.024603, 0.270679) * t4.g + vec4(0.062684, -0.097224, -0.028642, -0.10347) * t4.b;
    res += vec4(0.189447, -0.049569, 0.173031, -0.066736) * t5.r + vec4(-0.265302, -0.057117, 0.20512, 0.219289) * t5.g + vec4(-0.009429, 0.081648, 0.059852, 0.09319) * t5.b;
    res += vec4(-0.143778, -0.144049, -0.200463, 0.047326) * t6.r + vec4(-0.118418, 0.139773, -0.104147, -0.05374) * t6.g + vec4(0.123403, 0.083261, 0.078852, -0.010409) * t6.b;
    res += vec4(0.163975, 0.184068, 0.139394, -0.116559) * t7.r + vec4(0.048682, 0.006979, -0.053691, 0.118027) * t7.g + vec4(0.028832, -0.075432, -0.108769, -0.031664) * t7.b;
    res += vec4(0.174651, -0.014143, -0.177979, 0.056179) * t8.r + vec4(0.024932, -0.114648, -0.012354, -0.062819) * t8.g + vec4(-0.211787, -0.003226, -0.107653, -0.085922) * t8.b;
    fragColor = max(res, vec4(0.0));
}
