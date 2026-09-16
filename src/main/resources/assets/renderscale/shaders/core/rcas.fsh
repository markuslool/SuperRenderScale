#version 420
//#version 150

#define A_GPU 1
#define A_GLSL 1
#define FSR_RCAS_F 1

#moj_import <renderscale:ffx_a.glsl>

uniform sampler2D InSampler;

out vec4 fragColor;

// Must match RenderPipeline.withUniform("RcasParams", UNIFORM_BUFFER).
// x = RCAS attenuation 0 (sharpest) .. 1 (softest); 0.2 = old hardcoded look.
layout(std140) uniform RcasParams {
    vec4 Params;
};

AF4 FsrRcasLoadF(ASU2 p)
{
ivec2 size = textureSize(InSampler, 0);

// RCAS samples one pixel outside the current location at image edges.
ivec2 position = clamp(ivec2(p), ivec2(0), size - ivec2(1));

return texelFetch(InSampler, position, 0);
}

void FsrRcasInputF(inout AF1 r, inout AF1 g, inout AF1 b)
{
// Leave empty unless you require an input colour conversion.
}

#moj_import <renderscale:ffx_fsr1.glsl>

void main()
{
AU2 outputPixel = AU2(gl_FragCoord.xy);
AU4 fsrConst0;
FsrRcasCon(fsrConst0, Params.x);

AF3 colour;
FsrRcasF(
colour.r,
colour.g,
colour.b,
outputPixel,
fsrConst0
);

fragColor = vec4(colour, 1.0);
}
