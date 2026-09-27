#version 420

// FSRCNNX x2 8-0-4-1 by igv (LGPL-3.0), ported from the mpv user shader.
// Final combine: the CNN restores luma detail only, so chroma comes from the
// bilinear base. out = bilinear(input) + (fsrcnnx_luma - bilinear_luma).
// At exactly 2x this is FSRCNNX luma + bilinear chroma; at larger ratios the
// detail target is bilinearly resampled to the output size (still an upgrade
// over a pure bilinear upscale).

#moj_import <minecraft:globals.glsl>

uniform sampler2D BaseSampler;
uniform sampler2D DetailSampler;

out vec4 fragColor;

const vec3 LUMA_W = vec3(0.2126, 0.7152, 0.0722);

void main()
{
    vec2 outSize = vec2(ScreenSize.x, ScreenSize.y);
    vec2 uv = gl_FragCoord.xy / outSize;
    vec3 base = texture(BaseSampler, uv).rgb;
    float yBase = dot(base, LUMA_W);
    float yNet = texture(DetailSampler, uv).r;
    fragColor = vec4(base + vec3(yNet - yBase), 1.0);
}
