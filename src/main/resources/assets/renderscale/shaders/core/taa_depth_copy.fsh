#version 420

// Packs the current depth into RG (~16 bit) for the TAA history depth buffer:
// the resolve pass compares it against reprojected depth for disocclusion.
// RGBA8 targets can't hold raw depth (clamp + 8-bit quantize), hence packing.

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

out vec4 fragColor;

void main()
{
    ivec2 px = ivec2(gl_FragCoord.xy);
    ivec2 sizeIn = textureSize(InSampler, 0);
    float d = texelFetch(InSampler, clamp(px, ivec2(0), sizeIn - ivec2(1)), 0).r;
    vec2 enc = fract(vec2(1.0, 255.0) * d);
    enc.x -= enc.y / 255.0;
    fragColor = vec4(enc, 0.0, 1.0);
}
