uniform mat4 projection;
uniform mat4 view;
uniform ivec3 selected;
uniform bool taa;
uniform bool ui;
uniform bool chiselMode;
uniform bool upscale;
uniform bool shadowsEnabled;
uniform ivec2 res;
uniform vec3 sun;
uniform vec3 mun;
uniform bool hasAtmosphere;
uniform double time;
uniform float timeOfDay;
uniform int offsetIdx;
uniform bool reverseChecker;
uniform ivec2 checkerStep;

uniform layout(binding = 0) sampler2D raster_color;
uniform layout(binding = 1) sampler2D raster_pos;
uniform layout(binding = 2) sampler2D raster_norm;
uniform layout(binding = 3) sampler3D atlas;
uniform layout(binding = 4) isampler3D blocks;
uniform layout(binding = 5) sampler3D lights;
uniform layout(binding = 6) sampler2D noises;

layout(std430, binding = 0) buffer playerSSBO
{
    float[] playerData;
};

in vec4 gl_FragCoord;

layout (location = 0) out vec4 finalColor;
layout (location = 1) out vec4 finalNormal;

float lerp(float invLerpValue, float toValue, float fromValue) {
    return toValue + invLerpValue * (fromValue - toValue);
}

float inverseLerp(float y, float fromY, float toY) {
    return (y - fromY) / (toY - fromY);
}

float clampedLerp(float toValue, float fromValue, float invLerpValue) {
    if (invLerpValue < 0.0) {
        return toValue;
    } else {
        return invLerpValue > 1.0 ? fromValue : lerp(invLerpValue, toValue, fromValue);
    }
}

float gradient(float y, float fromY, float toY, float fromValue, float toValue) {
    return clampedLerp(toValue, fromValue, inverseLerp(y, fromY, toY));
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Converts a color from linear light gamma to sRGB gamma
vec4 fromLinear(vec4 linearRGB)
{
    bvec4 cutoff = lessThan(linearRGB, vec4(0.0031308));
    vec4 higher = vec4(1.055)*pow(linearRGB, vec4(1.0/2.4)) - vec4(0.055);
    vec4 lower = linearRGB * vec4(12.92);

    return mix(higher, lower, cutoff);
}

// Converts a color from sRGB gamma to linear light gamma
vec4 toLinear(vec4 sRGB)
{
    bvec4 cutoff = lessThan(sRGB, vec4(0.04045));
    vec4 higher = pow((sRGB + vec4(0.055))/vec4(1.055), vec4(2.4));
    vec4 lower = sRGB/vec4(12.92);

    return mix(higher, lower, cutoff);
}

vec3 fromLinear(vec3 linearRGB)
{
    bvec3 cutoff = lessThan(linearRGB, vec3(0.0031308));
    vec3 higher = vec3(1.055)*pow(linearRGB, vec3(1.0/2.4)) - vec3(0.055);
    vec3 lower = linearRGB * vec3(12.92);

    return vec3(mix(higher, lower, cutoff));
}

// Converts a color from sRGB gamma to linear light gamma
vec3 toLinear(vec3 sRGB)
{
    bvec3 cutoff = lessThan(sRGB, vec3(0.04045));
    vec3 higher = pow((sRGB + vec3(0.055))/vec3(1.055), vec3(2.4));
    vec3 lower = sRGB/vec3(12.92);

    return vec3(mix(higher, lower, cutoff));
}

const int size = 1024;
const int height = 320;
const vec3 worldSize = vec3(size, height, size);
const int blockSize = 4;
const int blockTexSize = blockSize*2;
const float alphaMax = 0.95f;
const float one = fromLinear(vec4(1)).a;
const float eigth = 1f/8f;
const mat4 invView = inverse(view);

bool inBounds(vec3 pos, vec3 bounds) {
    return !(pos.x < 0 || pos.x >= bounds.x || pos.y < 0 || pos.y >= bounds.y || pos.z < 0 || pos.z >= bounds.z);
}

float noise(vec2 coords) {
    return (texture(noises, vec2(coords/1024)).r)-0.5f;
}
float whiteNoise(vec2 coords) {
    return (texture(noises, vec2(coords/1024)).g)-0.5f;
}

bool castsFullShadow(ivec2 block) {
    return block.x != 4 && block.x != 5 && block.x != 14 && block.x != 18 && block.x != 30 && block.x != 52 && block.x != 53 && block.x != 64 && block.x != 65;
}

vec4 getVoxel(int x, int y, int z, int bX, int bY, int bZ, int blockType, int blockSubtype) {
    if ((bX & 1) != 0) { x += blockSize; }
    if ((bY & 1) != 0) { y += blockSize; }
    if ((bZ & 1) != 0) { z += blockSize; }
    return texelFetch(atlas, ivec3(x+(blockType*8), ((abs(y-blockTexSize)-1)*blockTexSize)+z, blockSubtype), 0);
}
vec4 getVoxel(float x, float y, float z, float bX, float bY, float bZ, int blockType, int blockSubtype) {
    return getVoxel(int(x), int(y), int(z), int(bX), int(bY), int(bZ), blockType, blockSubtype);
}

vec3 stepMask(vec3 sideDist) {
    bvec3 b1 = lessThan(sideDist.xyz, sideDist.yzx);
    bvec3 b2 = lessThanEqual(sideDist.xyz, sideDist.zxy);
    bvec3 mask = bvec3(
    b1.x && b2.x,
    b1.y && b2.y,
    b1.z && b2.z
    );
    if(!any(mask)) {
        mask.z = true;
    }

    return vec3(mask);
}

float getCaustic(vec2 checkPos) {
    return noise((checkPos + (float(time) * 100)) * (16+(float(time)/(float(time)/32))))+0.5f;
}

ivec4 getBlock(float x, float y, float z) {
    return texelFetch(blocks, ivec3(z, y, x), 0);
}
float waterDepth = 1.f;
vec4 getLight(float x, float y, float z) {
    return min(vec4(1.2f), texture(lights, vec3(z, y, x)/vec3(size, height, size), 0)*vec4(10, 10, 10, 20));
}
vec3 ogPos = vec3(0);
vec3 sunColor = vec3(0);
vec3 source = vec3(0);

vec3 sunsetColor = vec3(1, 0.65f, 0.25f); //vec3(0.65, 0.65f, 1)
vec3 skyColor = vec3(0.36f, 0.54f, 1.2f); //vec3(0.56f, 0.56f, 0.7f)
float skyWhiteline = 0.9f; //0.8
float sunsetHeight = 1.5f; //1
float skyDensity = 1.f; //0.66
float sunBrightnessMul = 1.1f; //1

vec4 getLightingColor(vec3 lightPos, vec4 lighting, bool isSky, float fogginess, bool negateSun) {
    if (!hasAtmosphere) {
        fogginess = 0.f;
    }
    float ogY = ogPos.y;
    float sunHeight = sun.y/size;
    float scattering = gradient(lightPos.y, ogY-63, ogY+437, 1.5f, -0.5f);
    float sunDist = (distance(lightPos.xz, sun.xz)/(size*1.5f));
    float adjustedTime = clamp((sunDist*abs(1-clamp(sunHeight, 0.05f, 0.5f)))+scattering, 0.f, 1.f);
    float thickness = gradient(lightPos.y, 128, 1500-max(0, sunHeight*1000), 0.33+(sunHeight/2), 1);
    float sunSetness = min(1.f, max(abs(sunHeight*sunsetHeight), adjustedTime));
    float whiteY = max(ogY, 200)-135.f;
    float skyWhiteness = mix(max(0.33f, gradient(lightPos.y, (whiteY/4)+47, (whiteY/2)+436, 0, skyWhiteline)), 0.9f, clamp(abs(1-sunSetness), 0, 1.f));
    float sunBrightness = clamp(sunHeight+0.5, mix(0.f, 0.33f, skyWhiteness), 1);
    if (negateSun) {
        lighting.a = 0;
    }
    float whiteness = isSky ? skyWhiteness : mix(skyWhiteline, skyWhiteness, max(0, fogginess-0.8f)*5.f);
    sunColor = mix(mix(sunsetColor*(1+((10*clamp(sunHeight, 0.f, 0.1f))*(15*min(0.5f, abs(1-sunBrightness))))), skyColor*sunBrightness, sunSetness), vec3(sunBrightness), whiteness);
    sunColor = min(fromLinear(mix(vec3(1), vec3(1, 0.95f, 0.85f), sunSetness/4)), lighting.a*sunColor);
    if (!isSky && source.xz == sun.xz) {
        sunColor*=min(sunBrightnessMul, sunBrightnessMul <= 1.f ? 1.f : max(1.f, sunBrightnessMul-fogginess));
    }
    vec4 color = vec4(max(lighting.rgb, sunColor), thickness);
    return isSky ? color*gradient(lightPos.y, 72, 320, skyDensity, 1) : color;
}
vec4 powLighting(vec4 lighting) {
    return vec4(lighting.r, lighting.g, lighting.b, pow(lighting.a, 2));
}

bool shouldSelectBlock = true;
ivec3 hitBlock = ivec3(-1);
vec3 hitPos = vec3(0);
vec3 solidHitPos = vec3(0);
vec3 mapPos = vec3(0);
vec3 normal = vec3(0);
vec4 tint = vec4(1);
vec3 firstTintAddition = vec3(0);
bool underwater = false;
bool wasEverUnderwater = false;
bool hitCaustic = false;
bool isShadow = false;
vec3 lightSourcePos = vec3(0);
ivec3 lightSourceLODPos = ivec3(0);
ivec3 lightSourceLOD2Pos = ivec3(0);

vec3 ogRayPos = vec3(0);
vec3 prevPos = vec3(0);
vec3 lod2Pos = vec3(0);
vec3 lodPos = vec3(0);
ivec4 block = ivec4(0);
vec4 texColor = vec4(0);
vec4 lightFog = vec4(0);
bool hitSolidVoxel = false;
vec4 prevTintAddition = vec4(0);

void clearVars() {
    prevTintAddition = vec4(0);
    hitPos = vec3(0);
    solidHitPos = vec3(0);
    mapPos = vec3(0);
    normal = vec3(0);
    underwater = false;
    hitCaustic = false;
    tint = vec4(1);
    firstTintAddition = vec3(0);
    prevPos = vec3(0);
    lod2Pos = vec3(0);
    lodPos = vec3(0);
    block = ivec4(0);
    texColor = vec4(0);
    hitSolidVoxel = false;
}
bool isBlockLeaves(ivec2 block) {
    return block.y == 0 && (block.x == 17 || block.x == 21 || block.x == 27 || block.x == 36 || block.x == 39 || block.x == 42 || block.x == 45 || block.x == 48 || block.x == 51);
}
bool isLightSource(ivec2 block) {
    return block.x == 6 || block.x == 7 || block.x == 14 || block.x == 19 || block.x == 52 || block.x == 53;
}
bool isFullSemitransparentBlock(ivec2 block) {
    return block.x == 11 || block.x == 12 || block.x == 13;
}
bool isGlassSolid(vec3 blockPos, vec3 rayMapPos) {
    float samp = whiteNoise(((vec2(blockPos.x, blockPos.z)*128)+(rayMapPos.y*blockSize)+blockPos.y)+(vec2(rayMapPos.x, rayMapPos.z)*blockSize));
    if (samp > -0.004 && samp < -0.002 || samp > 0 && samp < 0.002) {
        return true;
    }
    return false;
}
vec4 lightFogLastCheck = vec4(0);
void updateLightFog(vec3 pos) {
    if (!isShadow) {
        lightFogLastCheck = (getLight(pos.x, pos.y, pos.z));
        if (lightFogLastCheck.r > 0.f || lightFogLastCheck.g > 0.f || lightFogLastCheck.b > 0.f) {
            lightFog = max(lightFog, getLightingColor(mapPos, lightFogLastCheck, false, 1, true)/2);
        }
    }
}

vec4 getVoxelAndBlock(vec3 pos) {
    vec3 rayMapPos = floor(pos);
    vec3 blockPos = (pos-rayMapPos)*blockSize;
    ivec2 block = getBlock(rayMapPos.x, rayMapPos.y, rayMapPos.z).xy;
    if (block.x <= 1) {
        return vec4(0.f);
    } else if (isBlockLeaves(block)) {
        return vec4(one);
    }
    return getVoxel(blockPos.x, blockPos.y, blockPos.z, rayMapPos.x, rayMapPos.y, rayMapPos.z, block.x, block.y);
}
vec4 getVoxelAndBlockWOLeavesOverride(vec3 pos) {
    vec3 rayMapPos = floor(pos);
    vec3 blockPos = (pos-rayMapPos)*blockSize;
    ivec2 block = getBlock(rayMapPos.x, rayMapPos.y, rayMapPos.z).xy;
    if (block.x <= 1) {
        return vec4(0.f);
    }
    return getVoxel(blockPos.x, blockPos.y, blockPos.z, rayMapPos.x, rayMapPos.y, rayMapPos.z, block.x, block.y);
}

vec3 ogDir = vec3(0);
vec3 rayDir = vec3(0);
vec3 roundDir(vec3 dir) {
    if (dir.x == 0.0f) {
        dir.x = 0.001f;
    }
    if (dir.y == 0.0f) {
        dir.y = 0.001f;
    }
    if (dir.z == 0.0f) {
        dir.z = 0.001f;
    }
    return dir;
}
vec3 tintNormal = vec3(0);
vec4 traceBlock(vec3 rayPos, vec3 iMask, float subChunkDist, float chunkDist) {
    rayPos *= 4;
    vec3 blockPos = floor(clamp(rayPos, vec3(0.0001), vec3(3.9999)));
    vec3 raySign = sign(rayDir);
    vec3 deltaDist = 1.0/rayDir;
    vec3 sideDist = ((blockPos - rayPos) + 0.5 + raySign * 0.5) * deltaDist;
    vec3 mask = iMask;

    vec3 mini = ((blockPos - rayPos) + 0.5 - 0.5 * vec3(raySign)) * deltaDist;
    float blockDist = max(mini.x, max(mini.y, mini.z));

    vec3 voxelRayPos = vec3(0.f);
    vec3 voxelPos = vec3(0.f);
    vec3 voxelSideDist = sideDist;
    vec3 voxelMask = mask;
    vec3 prevVoxelPos = vec3(0);

    bool steppingBlock = true;
    bool firstStep = true;
    bool isInsidePointLight = false;
    for (int i = 0; blockPos.x < 4.0 && blockPos.x >= 0.0 && blockPos.y < 4.0 && blockPos.y >= 0.0 && blockPos.z < 4.0 && blockPos.z >= 0.0 && i < (4*8)*3; i++) {
        vec4 tintAddition = vec4(0.f);
        if (steppingBlock) {
            if (firstStep) {
                firstStep = false;
                mapPos = (lod2Pos*16)+(lodPos*4)+blockPos;
                block = getBlock(mapPos.x, mapPos.y, mapPos.z);
            }
            updateLightFog(mapPos+0.5f);
            isInsidePointLight = ivec3(lightSourcePos) == ivec3(mapPos);
            if (isInsidePointLight || (block.x > 0 && !(block.x == 1 && underwater))) {
                steppingBlock = false;
                mini = ((blockPos-rayPos) + 0.5 - 0.5*vec3(raySign))*deltaDist;
                float blockDist = max(mini.x, max(mini.y, mini.z));
                vec3 intersect = rayPos + rayDir*blockDist;
                voxelRayPos = intersect - blockPos;
                if (blockPos == floor(rayPos)) { // Handle edge case where camera origin is inside of block
                    voxelRayPos = rayPos - blockPos;
                }
                voxelRayPos *= blockSize;

                voxelPos = floor(clamp(voxelRayPos, vec3(0.0001f), vec3(blockSize-0.0001f)));
                voxelSideDist = ((voxelPos - voxelRayPos) + 0.5 + raySign * 0.5) * deltaDist;
                voxelMask = mask;
                prevVoxelPos = voxelPos+(stepMask(voxelSideDist+(voxelMask*(-raySign)*deltaDist))*(-raySign));

                float rayLength = 0.f;
                vec3 voxelMini = ((voxelPos-voxelRayPos) + 0.5 - 0.5*vec3(raySign))*deltaDist;
                float voxelDist = max(voxelMini.x, max(voxelMini.y, voxelMini.z));
                if (voxelDist > 0.0f) {
                    rayLength += voxelDist/blockSize;
                }
                if (blockDist > 0.0f) {
                    rayLength += blockDist;
                }
                if (subChunkDist > 0.0f) {
                    rayLength += (subChunkDist*4);
                }
                if (chunkDist > 0.0f) {
                    rayLength += (chunkDist*16);
                }
                vec3 hitNormal = -voxelMask * raySign;
                vec3 realPos = (ogRayPos + rayDir * rayLength);
                prevPos = realPos + (hitNormal * 0.001f);
            } else {
                prevTintAddition = vec4(0);
            }
        } else if (voxelPos.x < blockSize && voxelPos.x >= 0.0 && voxelPos.y < blockSize && voxelPos.y >= 0.0 && voxelPos.z < blockSize && voxelPos.z >= 0.0) {
            vec3 offsetVoxelPos = voxelPos;
//            if (block.x == 4 && offsetVoxelPos.y > 2.0) {
//                bool windDir = timeOfDay > 0.f;
//                float windStr = noise(((vec2(mapPos.x, mapPos.z)/48) + (float(time) * 100)) * (16+(float(time)/(float(time)/32))))+0.5f;
//                if (windStr > 0.8) {
//                    offsetVoxelPos.x = offsetVoxelPos.x+((offsetVoxelPos.y > 5 ? 3 : (offsetVoxelPos.y > 4 ? 2 : 1)) * (windDir ? -1 : 1));
//                    if (block.y < 2) {
//                        offsetVoxelPos.z = offsetVoxelPos.z+(offsetVoxelPos.y > 4 ? 2 : 1);
//                    }
//                } else if (windStr > 0.4) {
//                    offsetVoxelPos.x = offsetVoxelPos.x+((offsetVoxelPos.y > 5 ? 3 : (offsetVoxelPos.y > 4 ? 2 : 1)) * (windDir ? -1 : 1));
//                    if (block.y < 2) {
//                        offsetVoxelPos.z = offsetVoxelPos.z+(offsetVoxelPos.y > 4 ? 1 : 0);
//                    }
//                } else if (windStr > -0.2) {
//                    offsetVoxelPos.x = offsetVoxelPos.x+((offsetVoxelPos.y > 4 ? 2 : 1) * (windDir ? -1 : 1));
//                    if (block.y < 2) {
//                        offsetVoxelPos.z = offsetVoxelPos.z+(offsetVoxelPos.y > 4 ? 1 : 0);
//                    }
//                } else if (windStr > -0.8) {
//                    offsetVoxelPos.x = offsetVoxelPos.x+((offsetVoxelPos.y > 4 ? 1 : 0) * (windDir ? -1 : 1));
//                }
//                offsetVoxelPos.xz = clamp(offsetVoxelPos.xz, 0, 7);
//            }
            vec4 baseColor = getVoxel(offsetVoxelPos.x, offsetVoxelPos.y, offsetVoxelPos.z, mapPos.x, mapPos.y, mapPos.z, block.x, block.y);
            vec4 voxelColor = baseColor;
            if (voxelColor.a > 0) {
                vec3 voxelHitPos = mapPos+(voxelPos/blockSize);
                if (shouldSelectBlock && block.x > 1) {
                    if (ivec2(gl_FragCoord.xy) == (upscale ? ivec2(res/vec2(4)) : ivec2(res/2))) {
                        shouldSelectBlock = false;
                        playerData[0] = voxelHitPos.x;
                        playerData[1] = voxelHitPos.y;
                        playerData[2] = voxelHitPos.z;
                        playerData[3] = (mapPos+(prevVoxelPos/blockSize)).x;
                        playerData[4] = (mapPos+(prevVoxelPos/blockSize)).y;
                        playerData[5] = (mapPos+(prevVoxelPos/blockSize)).z;
                    }
                }
                vec3 voxelMini = ((voxelPos-voxelRayPos) + 0.5 - 0.5*vec3(raySign))*deltaDist;
                float voxelDist = max(voxelMini.x, max(voxelMini.y, voxelMini.z));
                vec3 intersect = voxelRayPos + rayDir*voxelDist;
                vec3 uv3d = intersect - voxelPos;

                if (voxelPos == floor(voxelRayPos)) { // Handle edge case where camera origin is inside of block
                    uv3d = voxelRayPos - voxelPos;
                }

                normal = ivec3(voxelPos - prevVoxelPos);
                solidHitPos = (prevVoxelPos/blockSize)+floor(mapPos)+(uv3d/blockSize)-(normal/2);
                if (hitPos == vec3(0)) {
                    hitPos = solidHitPos;
                }
                if (hitBlock == ivec3(-1) && block.x > 1) { //dont detect water
                    hitBlock = ivec3(mapPos);
                }
                if (voxelColor.a < alphaMax) {
                    if (tintNormal == vec3(0)) {
                        tintNormal = normal;
                    }
                    if (block.x == 1) {
                        voxelColor = mix(voxelColor, mix(vec4(1.f, 0.6f, 0.f, 0.95f), voxelColor, clamp(distance(sun.y, 64)/256, 0.f, 1.f)), lightFogLastCheck.a);
                        bool topVoxel = voxelPos.y >= 7; //-(block.y/16)
                        if (!underwater && getVoxel(voxelPos.x, topVoxel ? 0 : voxelPos.y+1, voxelPos.z, mapPos.x, mapPos.y + (topVoxel ? 1 : 0), mapPos.z, block.x, block.y).a <= 0) {
                            float causticness = getCaustic(vec2(mapPos.x, mapPos.z)+(voxelPos.xz/blockSize)+voxelPos.y);
                            if (causticness > -0.033 && causticness < 0.033) {
                                hitCaustic = true;
                                return vec4(fromLinear(vec3(1)), 1);
                            } else {
                                voxelColor.rgb = mix(voxelColor.rgb, vec3(1), abs(1-abs(causticness))/3);
                            }
                        }
                        underwater = true;
                        wasEverUnderwater = true;
                        steppingBlock = true;
                    }
                    if (isFullSemitransparentBlock(block.xy)) {
                        steppingBlock = true;
                    }
                    if (prevTintAddition != voxelColor) {
                        float brightness = dot((tint.a < 1 ? -1 : 1) * normal, source+vec3(0, height, 0))*-0.0001f;
                        float tintMul = clamp(0.875f+brightness, 0.75f, 1.f);
                        prevTintAddition = voxelColor;
                        if (firstTintAddition == vec3(0)) {
                            firstTintAddition = voxelColor.rgb*tintMul;
                        }
                        vec3 shadeTintFactor = (isShadow ? vec3(1-voxelColor.r, 1-voxelColor.g, 1-voxelColor.b)*(tint.a == 1 ? 1.5f : 1.f) : vec3(1));
                        tint.rgb -= shadeTintFactor * abs(1-voxelColor.rgb)*tintMul*tint.rgb;
                        tint.a -= max(shadeTintFactor.r, max(shadeTintFactor.g, shadeTintFactor.b)) * voxelColor.a*tint.a;
                    }
                } else {
                    hitSolidVoxel = true;
                    texColor = baseColor;
                    return vec4(voxelColor.rgb, 1);
                }
            } else {
                prevTintAddition = vec4(0);
            }

            if (isInsidePointLight) {
                return vec4(1);
            }
            if (!steppingBlock) {
                voxelMask = stepMask(voxelSideDist);
                prevVoxelPos = voxelPos;
                voxelPos += voxelMask * raySign;
                voxelSideDist += voxelMask * raySign * deltaDist;
                float rayLength = 0.f;
                vec3 voxelMini = ((voxelPos-voxelRayPos) + 0.5 - 0.5*vec3(raySign))*deltaDist;
                float voxelDist = max(voxelMini.x, max(voxelMini.y, voxelMini.z));
                if (voxelDist > 0.0f) {
                    rayLength += voxelDist/blockSize;
                }
                if (blockDist > 0.0f) {
                    rayLength += blockDist;
                }
                if (subChunkDist > 0.0f) {
                    rayLength += (subChunkDist*4);
                }
                if (chunkDist > 0.0f) {
                    rayLength += (chunkDist*16);
                }
                vec3 hitNormal = -voxelMask * raySign;
                vec3 realPos = (ogRayPos + rayDir * rayLength);
                prevPos = realPos + (hitNormal * 0.001f);
            }
        } else {
            steppingBlock = true;
        }
        if (steppingBlock) {
            vec3 entered = vec3(0.f);
            if (block.x == 1) {
                vec3 intersect = rayPos + rayDir * blockDist;
                vec3 uv3d = intersect - blockPos;
                if (uv3d == floor(voxelRayPos)) { // Handle edge case where camera origin is inside of block
                    uv3d = rayPos - blockPos;
                }
                entered = floor(mapPos)+uv3d;
            }

            mask = stepMask(sideDist);
            blockPos += mask * raySign;
            sideDist += mask * raySign * deltaDist;
            mini = ((blockPos - rayPos) + 0.5 - 0.5 * vec3(raySign)) * deltaDist;
            blockDist = max(mini.x, max(mini.y, mini.z));
            mapPos = (lod2Pos*16)+(lodPos*4)+blockPos;
            block = getBlock(mapPos.x, mapPos.y, mapPos.z);

            if (entered != vec3(0)) {
                vec3 intersect = rayPos + rayDir * blockDist;
                vec3 uv3d = intersect - blockPos;
                if (uv3d == floor(voxelRayPos)) { // Handle edge case where camera origin is inside of block
                    uv3d = rayPos - blockPos;
                }
                vec3 exited = floor(mapPos)+uv3d;
                float dist = distance(entered, exited);
                if (underwater && isShadow) {
                    waterDepth -= dist/30f;
                }
            }
        }
    }

    return vec4(0);
}

vec3 lodSize = vec3(size/4, height/4, size/4);
vec4 traceLOD(vec3 rayPos, vec3 iMask, float chunkDist) {
    rayPos *= 4;
    lodPos = floor(clamp(rayPos, vec3(0.0001), vec3(3.9999)));
    vec3 raySign = sign(rayDir);
    vec3 deltaDist = 1.0/rayDir;
    vec3 sideDist = ((lodPos - rayPos) + 0.5 + raySign * 0.5) * deltaDist;
    vec3 mask = iMask;

    for (int i = 0; lodPos.x < 4.0 && lodPos.x >= 0.0 && lodPos.y < 4.0 && lodPos.y >= 0.0 && lodPos.z < 4.0 && lodPos.z >= 0.0 && i < 4*3; i++) {
        mapPos = (lod2Pos*16)+(lodPos*4);
//        if (distance(ogRayPos, mapPos)>lodSize.x+lodSize.x) {
//            return vec4(-1);
//        }
        int lod = ivec3(mapPos/4) == lightSourceLODPos ? 1 : texelFetch(blocks, ivec3(lodPos.z, lodPos.y, lodPos.x), 2).x;
        if (lod > 0) {
            vec3 uv3d = vec3(0);
            vec3 intersect = vec3(0);
            vec3 mini = ((lodPos-rayPos) + 0.5 - 0.5*vec3(raySign))*deltaDist;
            float lodDist = max(mini.x, max(mini.y, mini.z));
            intersect = rayPos + rayDir*lodDist;
            uv3d = intersect - lodPos;

            if (lodPos == floor(rayPos)) { // Handle edge case where camera origin is inside of block
                uv3d = rayPos - lodPos;
            }
            vec3 prevRayDir = rayDir;
            vec4 voxelColor = traceBlock(uv3d, mask, lodDist, chunkDist);
            if (voxelColor.a >= 1 || voxelColor.a <= -1) {
                return voxelColor;
            }
        }

        mask = stepMask(sideDist);
        lodPos += mask * raySign;
        sideDist += mask * raySign * deltaDist;
    }

    return vec4(0);
}

vec3 lod2Size = vec3(size/16, height/16, size/16);
float maxSteps = lod2Size.x+lod2Size.y;
vec4 raytrace(vec3 ogPos, vec3 newRayDir) {
    rayDir = roundDir(newRayDir);
    ogRayPos = ogPos;
    vec3 rayPos = ogPos/16;
    lod2Pos = floor(rayPos);
    vec3 raySign = sign(rayDir);
    vec3 deltaDist = 1.0/rayDir;
    vec3 sideDist = ((lod2Pos - rayPos) + 0.5 + raySign * 0.5) * deltaDist;
    vec3 mask = stepMask(sideDist);

    for (int i = 0; i < maxSteps; i++) {
        if (!inBounds(lod2Pos, lod2Size)) {
            break;
        }
        mapPos = lod2Pos*16;
        int lod = ivec3(mapPos/16) == lightSourceLOD2Pos ? 1 : texelFetch(blocks, ivec3(lod2Pos.z, lod2Pos.y, lod2Pos.x), 4).x;
        if (lod > 0) {
            vec3 uv3d = vec3(0);
            vec3 intersect = vec3(0);
            vec3 mini = ((lod2Pos-rayPos) + 0.5 - 0.5*vec3(raySign))*deltaDist;
            float lod2Dist = max(mini.x, max(mini.y, mini.z));
            intersect = rayPos + rayDir*lod2Dist;
            uv3d = intersect - lod2Pos;

            if (lod2Pos == floor(rayPos)) { // Handle edge case where camera origin is inside of block
                uv3d = rayPos - lod2Pos;
            }
            vec3 prevRayDir = rayDir;
            vec4 voxelColor = traceLOD(uv3d, mask, lod2Dist);
            if (voxelColor.a >= 1 || voxelColor.a <= -1) {
                voxelColor.rgb = fromLinear(voxelColor.rgb)*0.8;
                return voxelColor;
            }
        }

        mask = stepMask(sideDist);
        lod2Pos += mask * raySign;
        sideDist += mask * raySign * deltaDist;
    }

    return vec4(0);
}

bool isSolid(vec3 pos) {
    return getVoxelAndBlockWOLeavesOverride(pos).a >= alphaMax;
}

vec3 getSmoothedNormal(vec3 pos) {
    vec3 smoothedNorm = vec3(0.0);
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                if (!(x == 0 && y == 0 && z == 0)) { //dont count the voxel that was actually hit
                    vec3 offset = vec3(x, y, z)/2;
                    bool solid = isSolid(pos + offset);
                    if (solid) {
                        smoothedNorm += offset;
                    }
                }
            }
        }
    }
    return normalize(roundDir(smoothedNorm));
}

bool objectOutOfWorld = false;
vec3 lightPos = vec3(0);
float shadowFactor = 1.f;
vec3 vNorm = vec3(0);
vec4 getShadow(vec4 color, bool actuallyCastShadowRay, bool isTracedObject, float dist) {
    if (!shadowsEnabled) {
        actuallyCastShadowRay=false;
    }
    if (actuallyCastShadowRay) {
        shadowFactor = 1.f;
    }
//    float shadeNormOffFade = clamp(distance(ogPos, prevPos)/20, 1.f, 3.f);
//    float normalRounding = eigth*shadeNormOffFade;
//    vec3 subbed = vec3(dot(normal.x, ogDir.x), dot(normal.y, ogDir.y), dot(normal.z, ogDir.z));
//    bool xHighest = subbed.x > subbed.y && subbed.x > subbed.z;
//    bool yHighest = subbed.y > subbed.x && subbed.y > subbed.z;
//    bool zHighest = subbed.z > subbed.x && subbed.z > subbed.y;
//    vNorm = normal;
//    vec3 shadowPosOffset = vec3(0);
//    vec3 blockPos = ivec3(solidHitPos)+0.5f;
//    if (castsFullShadow(block.xy) && isTracedObject) {
//        vec3 avgNColor = vec3(0);
//        float neighborsSolid = 0.f;
//        bool wasY = false;
//        vec4 above = fromLinear(getVoxelAndBlockWOLeavesOverride((solidHitPos+(normal*0.75f))+vec3(0, normalRounding, 0)));
//        if (above.a < alphaMax) {
//            vNorm.y = -1;
//            shadowPosOffset.y = eigth;
//            wasY = true;
//        } else if (!yHighest) {
//            float brightness = max(above.r, max(above.g, above.b));
//            avgNColor.rgb += (above.rgb)*brightness;
//            neighborsSolid+=1*brightness;
//        }
//        vec4 below = fromLinear(getVoxelAndBlockWOLeavesOverride((solidHitPos+(normal*0.75f))-vec3(0, normalRounding, 0)));
//        if (below.a < alphaMax) {
//            vNorm.y = wasY ? 0 : 1;
//            shadowPosOffset.y = wasY ? 0 : -eigth;
//        } else if (!yHighest) {
//            float brightness = max(below.r, max(below.g, below.b));
//            avgNColor.rgb += (below.rgb)*brightness;
//            neighborsSolid+=1*brightness;
//        }
//        bool wasX = false;
//        vec4 east = fromLinear(getVoxelAndBlockWOLeavesOverride((solidHitPos+(normal*0.75f))+vec3(normalRounding, 0, 0)));
//        if (east.a < alphaMax) {
//            vNorm.x = -1;
//            shadowPosOffset.x = eigth;
//            wasX = true;
//        } else if (!xHighest) {
//            float brightness = max(east.r, max(east.g, east.b));
//            avgNColor.rgb += (east.rgb)*brightness;
//            neighborsSolid+=1*brightness;
//        }
//        vec4 west = fromLinear(getVoxelAndBlockWOLeavesOverride((solidHitPos+(normal*0.75f))-vec3(normalRounding, 0, 0)));
//        if (west.a < alphaMax) {
//            vNorm.x = wasX ? 0 : 1;
//            shadowPosOffset.x = wasX ? 0 : -eigth;
//        } else if (!xHighest) {
//            float brightness = max(west.r, max(west.g, west.b));
//            avgNColor.rgb += (west.rgb)*brightness;
//            neighborsSolid+=1*brightness;
//        }
//        if (!zHighest) {
//            vec4 north = fromLinear(getVoxelAndBlockWOLeavesOverride((solidHitPos+(normal*0.75f))+vec3(0, 0, normalRounding)));
//            if (north.a >= alphaMax) {
//                float brightness = max(north.r, max(north.g, north.b));
//                avgNColor.rgb += (north.rgb)*brightness;
//                neighborsSolid+=1*brightness;
//            }
//            vec4 south = fromLinear(getVoxelAndBlockWOLeavesOverride((solidHitPos+(normal*0.75f))-vec3(0, 0, normalRounding)));
//            if (south.a >= alphaMax) {
//                float brightness = max(south.r, max(south.g, south.b));
//                avgNColor.rgb += (south.rgb)*brightness;
//                neighborsSolid+=1*brightness;
//            }
//        }
//        if (neighborsSolid > 1) {
//            avgNColor /= neighborsSolid;
//            color.rgb = clamp(mix(color.rgb, avgNColor, clamp(min(dist/100, 1)-max(0, 4*(max(color.r, max(color.g, color.b))-0.5f)), 0, 1)), 0, 1);
//        }
//        if (texColor.a < 1 && texColor.a > alphaMax) {
//            vNorm *= 0;
//        }
//    }
    vec3 quarterBlockPos =  (floor((solidHitPos+(normal/2))*2)/2.f)+0.25f;
    vec3 vNorm = isTracedObject ? getSmoothedNormal(quarterBlockPos) : normal; //solidHitPos+(normal/2)
    vec3 shadowPos = underwater ? hitPos : prevPos;//(quarterBlockPos-(vNorm/2));
    if (actuallyCastShadowRay) {
        vec3 sunDir = vec3(normalize(source - (worldSize/2)));
        vec3 prevFirstTint = firstTintAddition;
        vec4 prevTint = tint;
        vec3 oldSolidHitPos = solidHitPos;
        vec3 oldHitPos = hitPos;
        vec3 oldNormal = normal;
        vec3 oldPrevPos = prevPos;
        vec4 oldTexColor = texColor;
        clearVars();
        isShadow = true;
        bool solidCaster = raytrace(shadowPos, sunDir).a > 0.0f;
        if (solidCaster) {
            if (waterDepth < 1.f) {
                waterDepth = 0.f;
            }
            shadowFactor = min(0.9f, mix(0.75f, 0.9f, min(1, distance(shadowPos, hitPos)/420))); //shadowFactor = mix(1.f, min(0.9f, mix(0.75f, 0.9f, min(1, distance(shadowPos, hitPos)/420))), waterDepth);
        }
        shadowFactor = max(0.75f, shadowFactor-(firstTintAddition != vec3(0) ? 0.125f : 0.f));
        isShadow = false;
        float prevTintMax = max(prevTint.r, max(prevTint.g, prevTint.b));
        tint.rgb = mix(tint.rgb/max(tint.r, max(tint.g, tint.b)), prevTint.rgb/prevTintMax, tint.a)*prevTintMax;
        tint.a = min(prevTint.a, tint.a);
        firstTintAddition = prevFirstTint;
        hitPos = oldHitPos;
        solidHitPos = oldSolidHitPos;
        normal = oldNormal;
        prevPos = oldPrevPos;
        texColor = oldTexColor;
    }
    float brightness = clamp(((dot(vNorm, objectOutOfWorld ? sun : (source+vec3(0, height, 0)))*-0.00015f)*waterDepth)+0.67f, shadowsEnabled ? 0.5f : 0.25f, 1.f);
    shadowFactor = mix(shadowFactor, brightness, shadowsEnabled ? 0.5 : 0.75f);
    return color;
}

float manhattanDistance(vec3 pos1, vec3 pos2) {
    vec3 distance = abs(pos1-pos2);
    return distance.x + distance.y + distance.z;
}

vec3 traceLight(vec3 lightSource, vec3 lightColor) {
    vec3 returnValue = vec3(0);
    vec3 shadowPos = mix((floor(prevPos*blockSize)+0.5f)/blockSize, prevPos, abs(normal));
    float blockLightBrightness = max(lightColor.r, max(lightColor.g, lightColor.b));
    float shadowLightDist = manhattanDistance(lightSource, shadowPos);
    if (shadowLightDist < blockLightBrightness) {
        lightSourcePos = lightSource;
        lightSourceLODPos = ivec3(lightSource/4);
        lightSourceLOD2Pos = ivec3(lightSource/16);
        vec3 lightDir = vec3(normalize(lightSource - shadowPos));
        vec3 prevFirstTint = firstTintAddition;
        vec4 prevTint = tint;
        vec3 oldSolidHitPos = solidHitPos;
        vec3 oldHitPos = hitPos;
        vec3 oldNormal = normal;
        vec3 oldPrevPos = prevPos;
        vec4 oldTexColor = texColor;
        clearVars();
        isShadow = true;
        raytrace(shadowPos, lightDir);
        if (ivec3(mapPos) == ivec3(lightSource)) {
            returnValue = abs(1-clamp(shadowLightDist/blockLightBrightness, 0, 1))*(lightColor/22.2222f);
            normal = oldNormal;
            if (oldTexColor.a < 1 && oldTexColor.a > alphaMax) {
                normal *= 0;
            }
            float brightness = clamp((dot(vNorm, lightSource)*-0.00015f)+0.67f, 0.5f, 1.f);
            returnValue.rgb *= mix(1, brightness, 0.5f);
        }
        isShadow = false;
//        float returnMax = max(returnValue.r, max(returnValue.g, returnValue.b));
//        returnValue.rgb = mix(tint.rgb/max(tint.r, max(tint.g, tint.b)), returnValue/returnMax, tint.a)*returnMax;
        tint = prevTint;
        firstTintAddition = prevFirstTint;
        hitPos = oldHitPos;
        solidHitPos = oldSolidHitPos;
        normal = oldNormal;
        prevPos = oldPrevPos;
        texColor = oldTexColor;
        lightSourcePos = vec3(0);
        lightSourceLODPos = ivec3(0);
        lightSourceLOD2Pos = ivec3(0);
    }
    return returnValue;
}

const float nearClip = 0.01f;
const float[16] xOffsets = float[16](0.0f, -0.25f, 0.25f, -0.375f, 0.125f, -0.125f, 0.375f, -0.4375f, 0.0625f, -0.1875f, 0.3125f, -0.3125f, 0.1875f, -0.0625f, 0.4375f, -0.46875f);
const float[16] yOffsets = float[16](0.0f, 0.166667f, -0.388889f, -0.055556f, 0.277778f, -0.277778f, 0.055556f, 0.388889f, -0.462963f, -0.12963f, 0.203704f, -0.351852f, -0.018519f, 0.314815f, -0.240741f, 0.092593f);

vec3 getDir(vec2 pos) {
    if (taa) {
        pos+=vec2(xOffsets[offsetIdx], yOffsets[offsetIdx]);
    }
    vec2 uv = ((pos/res) * 2.0) - 1.0;
    vec4 clipSpace = vec4((inverse(projection) * vec4(uv, 1.f, 1.f)).xyz, 0);
    return roundDir(normalize((inverse(view)*clipSpace).xyz));
}

bool skyChecks() {
    bool isSky = false;
    if (solidHitPos != vec3(0)) {
        isSky = false;
    }
    if (finalColor.a < alphaMax) {
        isSky = true;
    }
    if (isSky) {
        solidHitPos = mapPos;
    }
    return isSky;
}

void main() {
    vec2 pos = ivec2(gl_FragCoord.xy);
    if (upscale) {
        pos *= 2;
        bool xOdd = bool(int(gl_FragCoord.x) % 2 == 1);
        bool yOdd = bool(int(gl_FragCoord.y) % 2 == 1);
        bool isChecker = (xOdd && yOdd) || (!xOdd && !yOdd);
        if (reverseChecker) { isChecker = !isChecker; }
        if (isChecker) {
            pos += checkerStep;
        }
    }
    vec2 normalizedPos = pos/res; //used for sampling the rasterization results
    ogDir = getDir(pos);
    ogPos = invView[3].xyz;
    vec4 rasterColor = texture(raster_color, normalizedPos);
    vec4 rasterPos = texture(raster_pos, normalizedPos);
    source = sun.y > 0 ? sun : mun;
    source.y = max(source.y, 500);
    bool isSky = true;
    bool isLight = false;
    updateLightFog(ogPos);
    finalColor = raytrace(ogPos, ogDir);
    if (isLightSource(block.xy) && max(texColor.r, max(texColor.g, texColor.b)) > 0.9f) {
        finalColor.rgb *= 1.5f;
        isLight = true;
    }
    isSky = skyChecks();
    shouldSelectBlock = false;
    vec4 lighting = vec4(-1);
    lightPos = ogPos + ogDir * size;//this is only used if it's the sky
    bool isTracedObject = true;
    float rasterBrightness = max(rasterColor.r, max(rasterColor.g, rasterColor.b));
    if ((distance(ogPos, rasterPos.xyz) < distance(ogPos, hitPos+(normal/2)) || isSky) && rasterBrightness > 0.f) {
        isTracedObject = false;
        finalColor.rgb = fromLinear(rasterColor).rgb;
        finalColor.a = rasterColor.a;
        texColor = finalColor;
        normal = texture(raster_norm, normalizedPos).xyz;
        solidHitPos = rasterPos.xyz-(normal/2);
        prevPos = rasterPos.xyz-(normal*0.002f);
        if (!inBounds(prevPos, worldSize)) {
            objectOutOfWorld = true;
        }
        isSky = false;
        isLight = rasterBrightness >= 1.f;
    }
    //vec4 posCS = view*projection*vec4(dPos, 1);/(((posCS.z)+1)/2)/posCS.w
    vec3 dPos = ogPos + (rayDir*distance(ogPos, solidHitPos+(normal/2)));
    float depth = objectOutOfWorld || isSky ? 0.f : (nearClip/max(0.0001f, dot(dPos-ogPos, normalize(-invView[2].xyz))));
    finalNormal = vec4(normal, depth);
    if (inBounds(solidHitPos, worldSize)) {
        lighting = getLight(solidHitPos.x, solidHitPos.y, solidHitPos.z)*(shadowsEnabled ? vec4(0.93, 0.93, 0.93, 1) : vec4(1)); //flood fill
//        if (shadowsEnabled) {
//            lighting.rgb = max(lighting.rgb, traceLight(vec3(487.5f, 53.5f, 469.5f), vec3(40, 36, 26)));
//            lighting.rgb = max(lighting.rgb, traceLight(vec3(518.5f, 51.5f, 469.5f), vec3(0, 40, 20)));
//            lighting.rgb = max(lighting.rgb, traceLight(vec3(510.5f, 77.5f, 594.5f), vec3(0, 20, 40)));
//            lighting.rgb = max(lighting.rgb, traceLight(vec3(500.5f, 78.5f, 585.5f), vec3(40, 36, 26)));
////
////            lighting.rgb = max(lighting.rgb, traceLight(vec3(518.5f, 55.5f, 429.5f), vec3(35, 36, 40)));
////
////            for (int i = 0; i < 27; i++) {
////                lighting.a += getLight(i, i, i).a;
////            }
////            for (int i = 0; i <= 3; i++) {
////                lighting.rgb = max(lighting.rgb, traceLight(vec3(512.5f+(i*20), 72.5f, 512.5f), vec3(55, 57, i*20)));
////                lighting.a += getLight(i*5, i, i*10).a;
////                lighting.a += getLight(i*5, i, i*11).a;
////            }
////            for (int i = 0; i <= 3; i++) {
////                lighting.rgb = max(lighting.rgb, traceLight(vec3(512.5f+(i*20), 72.5f, 448.5f), vec3(55, i*20, 57)));
////                lighting.a += getLight(i, i*5, i*3).a;
////                lighting.a += getLight(i, i*5, i*4).a;
////            }
////            for (int i = 0; i <= 3; i++) {
////                lighting.rgb = max(lighting.rgb, traceLight(vec3(512.5f+(i*20), 72.5f, 576.5f), vec3(i*20, 57, 55)));
////                lighting.a += getLight(i, i*9, i*5).a;
////                lighting.a += getLight(i, i*9, i*6).a;
////            }
//        }
    } else {
        lighting = (vec4(0, 0, 0, 1));
    }
    if (!isSky) {
        lightPos = solidHitPos;
        vec4 shadowResult = getShadow(finalColor, !objectOutOfWorld, isTracedObject && !isSky && hitSolidVoxel, distance(ogPos, solidHitPos));
        if (!isLight) {
            finalColor = shadowResult;
        } else if (finalColor.a >= 10) {
            finalColor.a -= 10;
            shadowFactor = 0.5f;
        }
        //finalNormal = vec4(vNorm, depth);
    } else {
        //finalNormal = vec4(normal, depth);
    }
    float fogginess = hasAtmosphere ? clamp((clamp(sqrt(distance(ogPos, lightPos)/(size*0.66f))*gradient(lightPos.y, 63, 80, 1, 1+abs(noise(lightPos.xz)/3)), 0, 1)), 0.f, 1.f) : 0.f;
    if (finalColor.a < 0 && !isSky) { fogginess *= 0.5f; }
    lighting.a = mix(lighting.a*shadowFactor, (vec4(0, 0, 0, 1)).a, fogginess);
    lighting = powLighting(lighting);
    if (!isLight && !objectOutOfWorld) {
        lighting.a*=waterDepth;
        vec4 lightingColor = getLightingColor(lightPos, lighting, isSky, fogginess, false);
        finalColor.rgb *= lightingColor.rgb;
        finalColor.rgb = mix(finalColor.rgb, lightingColor.rgb, fogginess);
    }
    if (tint.a < 1) {
        source = sun.y > 0 ? sun : mun;
        source.y = max(source.y, 500);
        normal = tintNormal;
        float reflectivity = wasEverUnderwater ? clamp(distance(lightPos, ogPos)/128, 0, 1) : 0.f;
        lightPos = hitPos;
        shadowFactor = 1.f;
        fogginess = hasAtmosphere ? clamp((clamp(sqrt(distance(ogPos, lightPos)/(size*0.66f))*gradient(lightPos.y, 63, 80, 1, 1+abs(noise(lightPos.xz)/3)), 0, 1)), 0.f, 1.f) : 0.f;
        lighting = (getLight(lightPos.x, lightPos.y, lightPos.z));
        lighting.a = mix(lighting.a*shadowFactor, (vec4(0, 0, 0, 1)).a, fogginess);
        lighting = powLighting(lighting);
        vec4 lightingColor = getLightingColor(lightPos, lighting, false, fogginess, false);
        tint.rgb = mix(tint.rgb, firstTintAddition, 0.5f);
        tint.rgb *= lightingColor.rgb;
        tint.rgb = mix(tint.rgb, lightingColor.rgb, fogginess);
        finalColor.rgb = mix(finalColor.rgb, tint.rgb, mix(abs(1-tint.a), 1.f, reflectivity));
    }

    if ((chiselMode ? hitBlock == selected : ivec3(hitBlock/2)*2 == ivec3(selected/2)*2) && ui) {
        finalColor.rgb = mix(finalColor.rgb, vec3(0.7, 0.7, 1), 0.5f);
    }
    if (hasAtmosphere) {
        finalColor.rgb += max(vec3(0), mix(lightFog.rgb, vec3(0), 1.2f*max(finalColor.r, max(finalColor.g, finalColor.b))));
    }
    finalColor = vec4(toLinear(finalColor.rgb), depth);
}