#version 420

// FSRCNNX x2 8-0-4-1 by igv (LGPL-3.0), ported from the mpv user shader to a
// RenderScale fullscreen pass. Stage: sub-band residuals 1 (mpv SAVE RES1).
// The network runs on luma only; chroma is taken from the bilinear base in
// fsrcnnx_combine. All taps are texelFetch (exact, sampler-independent).

#moj_import <minecraft:globals.glsl>

uniform sampler2D ASampler;
uniform sampler2D BSampler;
uniform sampler2D CSampler;
out vec4 fragColor;

void main()
{
    ivec2 px = ivec2(gl_FragCoord.xy);
    ivec2 sizeA = textureSize(ASampler, 0);
    ivec2 loA = sizeA - ivec2(1);
    ivec2 sizeB = textureSize(BSampler, 0);
    ivec2 loB = sizeB - ivec2(1);
    ivec2 sizeC = textureSize(CSampler, 0);
    ivec2 loC = sizeC - ivec2(1);
    vec4 res = vec4(0.0245648548007011,-0.4467784762382507,0.0197526942938566,-0.0110000418499112);
    res += mat4(0.0302665308117867,-0.9262221455574036,-0.1161134764552116,-0.0506900474429131,0.2716045379638672,-0.0485871583223343,0.0044713355600834,-0.4274623394012451,0.0749531090259552,-0.3700785338878632,0.0350039415061474,-0.0540786534547806,-0.0607390031218529,-0.8019900321960449,0.0923245251178741,0.1258827745914459) * (texelFetch(ASampler, clamp(px, ivec2(0), loA), 0));
    res += mat4(-0.0649135261774063,0.0815236791968346,0.0067334296181798,0.1277425885200500,-0.0051357815973461,-0.1485908329486847,0.0074226572178304,0.0050623500719666,0.0588018335402012,-0.0692552924156189,0.1288725286722183,-0.0989386290311813,0.0427936837077141,0.0967708528041840,-0.0455632135272026,-0.0711275041103363) * (texelFetch(BSampler, clamp(px, ivec2(0), loB), 0));
    res += (texelFetch(CSampler, clamp(px, ivec2(0), loC), 0));
    res = max(res, vec4(0.0)) + vec4(0.9927186965942383,0.0570580027997494,1.3226752281188965,1.0069466829299927) * min(res, vec4(0.0));
    fragColor = (res);
}
