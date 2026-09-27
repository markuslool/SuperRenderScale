#version 420

// FSRCNNX x2 8-0-4-1 by igv (LGPL-3.0), ported from the mpv user shader.
// Aggregation stage: the SUBCONV1 target holds 4 sub-pixels packed per input
// texel (channels x,y,z,w = 2x2 block). This pass renders at 2x input size and
// unpacks one luma value per output pixel.
// mpv original: res[index.x * 2 + index.y] with index = fract(pos * size) * 2,
// i.e. channel = (x & 1) * 2 + (y & 1). Y orientation note: if mpv's hook
// space turns out y-flipped vs GL framebuffer space, the top/bottom sub-pixel
// channels swap — visually negligible for detail placement, kept as-is.

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;
out vec4 fragColor;

void main()
{
    ivec2 outPx = ivec2(gl_FragCoord.xy);
    ivec2 base = outPx / 2;
    ivec2 idx = outPx - base * 2;
    ivec2 sizeIn = textureSize(InSampler, 0);
    vec4 packed = texelFetch(InSampler, clamp(base, ivec2(0), sizeIn - ivec2(1)), 0);
    int ch = idx.x * 2 + idx.y;
    float y = ch == 0 ? packed.x : (ch == 1 ? packed.y : (ch == 2 ? packed.z : packed.w));
    fragColor = vec4(y, y, y, 1.0);
}
