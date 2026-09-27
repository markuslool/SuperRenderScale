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
    vec4 res = vec4(0.065241, 0.028374, 0.00174, 0.011487);
    res += vec4(0.181078, 0.055997, -0.204068, 0.084502) * t0.r + vec4(-0.056363, 0.088526, 0.01233, -0.07996) * t0.g + vec4(0.170094, -0.170942, 0.037292, 0.202563) * t0.b;
    res += vec4(0.123434, 0.089605, 0.134608, 0.072112) * t1.r + vec4(0.174343, 0.039088, 0.041016, -0.041614) * t1.g + vec4(0.108814, -0.055199, -0.190476, -0.154348) * t1.b;
    res += vec4(-0.159072, 0.020763, 0.051044, -0.010974) * t2.r + vec4(-0.080847, -0.041038, 0.111062, -0.011176) * t2.g + vec4(0.155562, 0.127235, -0.155553, 0.141207) * t2.b;
    res += vec4(-0.037809, 0.012312, 0.039245, 0.174562) * t3.r + vec4(-0.150578, -0.093583, -0.205462, 0.059435) * t3.g + vec4(0.185771, 0.018887, -0.067773, -0.059287) * t3.b;
    res += vec4(0.069194, 0.024992, 0.000534, -0.026702) * t4.r + vec4(-0.107102, -0.003521, 0.070125, -0.058649) * t4.g + vec4(0.029886, 0.188993, 0.211321, -0.150871) * t4.b;
    res += vec4(0.089556, 0.134851, 0.085922, 0.044695) * t5.r + vec4(0.081665, 0.146333, -0.079216, -0.010261) * t5.g + vec4(-0.011719, 0.130724, -0.179326, 0.054903) * t5.b;
    res += vec4(-0.01769, -0.104065, 0.122678, 0.157974) * t6.r + vec4(-0.146168, 0.069412, 0.143911, 0.122514) * t6.g + vec4(-0.108142, 0.074724, -0.044123, 0.101484) * t6.b;
    res += vec4(-0.007537, -0.165505, -0.123424, -0.085964) * t7.r + vec4(0.196646, -0.065723, -0.005149, -0.00898) * t7.g + vec4(-0.147041, 0.07921, 0.305046, 0.061476) * t7.b;
    res += vec4(-0.032137, 0.088181, -0.106284, 0.137257) * t8.r + vec4(-0.136372, -0.057707, 0.173071, -0.05397) * t8.g + vec4(0.07235, -0.117, 0.101983, -0.099622) * t8.b;
    fragColor = max(res, vec4(0.0));
}
