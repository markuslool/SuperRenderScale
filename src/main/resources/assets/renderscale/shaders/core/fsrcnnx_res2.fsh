#version 420

// FSRCNNX x2 8-0-4-1 by igv (LGPL-3.0), ported from the mpv user shader to a
// RenderScale fullscreen pass. Stage: sub-band residuals 2 (mpv SAVE RES2).
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
    vec4 res = vec4(-0.0425243787467480,-0.3715015351772308,-0.0256227850914001,-0.2774516046047211);
    res += mat4(0.0238118842244148,0.0295480657368898,-0.0066418983042240,0.1021223962306976,-0.0568209178745747,-0.4355100393295288,-0.2700522541999817,-0.2060186564922333,-0.0689613372087479,-0.1689691990613937,-0.0306748505681753,-0.2461252212524414,-0.0057375836186111,-0.1892303228378296,-0.0285871494561434,-0.5032613277435303) * (texelFetch(ASampler, clamp(px, ivec2(0), loA), 0));
    res += mat4(0.5463213324546814,0.0972800329327583,0.0307560767978430,0.0678058937191963,-0.0356063023209572,-0.7013865113258362,0.1890443563461304,-0.1036657467484474,-0.1745826154947281,-0.2942218780517578,-0.0485423319041729,-0.2983124554157257,-0.0524431839585304,-0.3261034786701202,0.3217246532440186,0.1958018541336060) * (texelFetch(BSampler, clamp(px, ivec2(0), loB), 0));
    res += (texelFetch(CSampler, clamp(px, ivec2(0), loC), 0));
    res = max(res, vec4(0.0)) + vec4(0.1391339898109436,0.0960328355431557,0.6235341429710388,0.1177272796630859) * min(res, vec4(0.0));
    fragColor = (res);
}
