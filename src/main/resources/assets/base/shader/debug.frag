uniform layout(binding = 0) sampler3D texture;

uniform vec4 color;
uniform int tex;
uniform ivec2 atlasOffset;
uniform vec3 sun;
uniform vec3 mun;
uniform bool alwaysUpfront;
uniform bool instanced;
layout(std430, binding = 2) buffer colorsSSBO
{
    vec4[] colors;
};
layout(std430, binding = 3) buffer atlasOffsetsSSBO
{
    ivec2[] atlasOffsets;
};

in flat int instance;
in vec3 pos;
in vec3 norm;
in vec3 wPos;

layout (location = 0) out vec4 fragColor;
layout (location = 1) out vec4 rasterPos;
layout (location = 2) out vec4 rasterNorm;

void main() {
    float depth = alwaysUpfront ? 1.f : gl_FragCoord.z;
    rasterPos = vec4(wPos, depth);
    rasterNorm = vec4(norm*-1, 0);
    vec4 theColor = color;
    if (instanced) {
        theColor = colors[instance];
    }
    if (tex <= 0) {
        fragColor = theColor;
    } else {
        ivec2 offset = instanced ? atlasOffsets[instance] : atlasOffset;
        vec4 guiColor = texelFetch(texture, ivec3(offset.x+(pos.x*16), offset.y+(abs(1-pos.y)*16), 0), 0)*theColor;
        if (guiColor.a > 0) {
            fragColor = guiColor;
        } else {
            discard;
        }
    }
}