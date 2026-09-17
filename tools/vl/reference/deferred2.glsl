/////////////////////////////////////
// Complementary Shaders by EminGT //
/////////////////////////////////////

/*
deferred2 — ADDED BY THE IRLITE INJECT (not part of pristine Complementary).
Marches the IRLite per-light volumetrics at REDUCED RESOLUTION: this program
writes ONLY colortex10, which shaders.properties sizes at a fraction of the
screen (size.buffer.colortex10 = IRLITE_VL_RESOLUTION, the pack's own
REFLECTION_RES mechanism). The pass viewport shrinks with its only render
target, so every covered pixel's ray march costs a quarter (Half) or a
sixteenth (Quarter) of the full-res price — the same mechanism Photon uses
for its quarter-res fog pass. program/composite1.glsl then upsamples the buffer
(depth-aware bilateral when UBO flags bit6 is set - the default - plain bilinear
otherwise), applies the underwater/lava parity and adds it to color.
Runs at the deferred stage (after solid gbuffers): depthtex0 holds the OPAQUE
depth there, which is exactly the march end the composite1-hosted march used
(z1) — beams deliberately continue behind translucents/BBS replay models.
The whole pass is program-toggled off when IRLITE_VOLUMETRIC is disabled
(program.world*\/deferred2.enabled in shaders.properties).
*/

//Common//
#include "/lib/common.glsl"

//////////Fragment Shader//////////Fragment Shader//////////Fragment Shader//////////
#ifdef FRAGMENT_SHADER

noperspective in vec2 texCoord;

//Pipeline Constants//

//Common Variables//
vec2 view = vec2(viewWidth, viewHeight);

//Common Functions//

//Includes//
#include "/lib/util/spaceConversion.glsl"

#define IRLITE_VL_PASS
#include "/lib/irlite/irlite_lights.glsl"

uniform sampler2D irl_blueNoise;    // 128x128 R8 void-and-cluster dither array, bound by the mod by name (only this TU declares it)

//Program//
void main() {
    vec3 irliteVL = vec3(0.0);

    #if defined IRLITE_ACTIVE && defined IRLITE_VOLUMETRIC
        // NOT texelFetch: gl_FragCoord lives in the REDUCED viewport while
        // depthtex0 stays full-res — normalized texCoord maps correctly.
        float z0 = texture2D(depthtex0, texCoord).r;   // opaque depth (translucents are not drawn yet)

        vec3 viewPos = ScreenToView(vec3(texCoord, z0));
        vec3 playerPos = ViewToPlayer(viewPos);

        // March-start dither: mod-owned spatial blue noise (vlC.w bit2, default ON) pushes the
        // banding into frequencies the eye discards; bit3 adds a golden-ratio temporal rotation
        // driven by the UBO frameIndex (vlB.w) — OFF by default (boils on moving lamps sans TAA).
        // This pass runs at reduced res; gl_FragCoord is pass-res, which is correct for dithering.
        bool useBn = (irlite_vlC.w & 4u) != 0u;
        float bn = texelFetch(irl_blueNoise, ivec2(gl_FragCoord.xy) & 127, 0).r;
        if ((irlite_vlC.w & 8u) != 0u) bn = fract(bn + float(int(irlite_vlB.w)) * 0.61803398875);
        float dither = texture2DLod(noisetex, texCoord * view / 128.0, 0.0).b;
        #ifdef TAA
            dither = fract(dither + goldenRatio * mod(float(frameCounter), 3600.0));
        #endif
        dither = useBn ? bn : dither;

        // Anchor the march at the bobbing eye (= ViewToPlayer(vec3(0)) = gbufferModelViewInverse[3].xyz; ~0 with view bobbing off, oscillates with it). The origin must carry the same MVI[3] the endpoint playerPos already does, or the shaft segment slides vs the bob-free lights and VL jitters under view bobbing.
        vec3 eyePlayer = gbufferModelViewInverse[3].xyz;
        irliteVL = irlite_volumetric(eyePlayer, playerPos,
                                     normalize(playerPos - eyePlayer), dither);
    #endif

    /* RENDERTARGETS: 10 */
    gl_FragData[0] = vec4(irliteVL, 1.0);
}

#endif

//////////Vertex Shader//////////Vertex Shader//////////Vertex Shader//////////
#ifdef VERTEX_SHADER

noperspective out vec2 texCoord;

//Attributes//

//Common Variables//

//Common Functions//

//Includes//

//Program//
void main() {
    gl_Position = ftransform();

    texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
}

#endif
