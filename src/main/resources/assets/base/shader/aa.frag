const float[16] xOffsets = float[16](0.0f, -0.25f, 0.25f, -0.375f, 0.125f, -0.125f, 0.375f, -0.4375f, 0.0625f, -0.1875f, 0.3125f, -0.3125f, 0.1875f, -0.0625f, 0.4375f, -0.46875f);
const float[16] yOffsets = float[16](0.0f, 0.166667f, -0.388889f, -0.055556f, 0.277778f, -0.277778f, 0.055556f, 0.388889f, -0.462963f, -0.12963f, 0.203704f, -0.351852f, -0.018519f, 0.314815f, -0.240741f, 0.092593f);
const float nearClip = 0.01f;
const int kernelSize = 64;
const float aoRadius = 0.5f;
const float bias = 0.000001f;
const float aoDarkness = 0.5/kernelSize;

uniform mat4 projection;
uniform mat4 prevProj;
uniform mat4 view;
uniform mat4 prevView;
uniform ivec2 res;
uniform bool taa;
uniform bool upscale;
uniform int offsetIdx;
uniform int offsetIdxOld;

uniform layout(binding = 0) sampler2D in_color_old;
uniform layout(binding = 1) sampler2D in_color;
uniform layout(binding = 2) sampler2D in_normal;

layout(std430, binding = 4) buffer kernelSSBO
{
    vec3[] kernelData;
};

in vec4 gl_FragCoord;

out vec4 fragColor;

vec2 worldToScreenPos(vec3 worldPos) {
    vec4 projectionVec = projection * view * vec4(worldPos, 1.0f);
    projectionVec.xyz /= projectionVec.w;
    projectionVec.xy = projectionVec.xy * 0.5f + 0.5f;
    return projectionVec.xy;
}
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
vec3 getDir() {
    vec2 uv = ((gl_FragCoord.xy / res) * 2.0) - 1.0;
    vec4 clipSpace = vec4((inverse(projection) * vec4(uv, 1.f, 1.f)).xyz, 0);
    return roundDir(normalize((inverse(view)*clipSpace).xyz));
}
vec3 getPosFromDepth(float depth) {
    vec2 ndc = ((gl_FragCoord.xy/res)*2.f)-1.f;
    vec4 clip = vec4(ndc, depth, 1.f);
    vec4 viewPos = inverse(projection)*clip;
    viewPos /= viewPos.w;
    vec4 worldPos = inverse(view)*viewPos;
    return worldPos.xyz;
}
vec3 getPrevWorldPosFromDepth(float depth) {
    vec2 ndc = ((gl_FragCoord.xy/res)*2.f)-1.f;
    vec4 clip = vec4(ndc, depth, 1.f);
    vec4 viewPos = inverse(projection)*clip;
    viewPos /= viewPos.w;
    vec4 worldPos = inverse(view)*viewPos;
    return worldPos.xyz;
}
vec2 reprojectPrev(vec3 worldPos) {
    vec4 projectionVec = prevProj * prevView * vec4(worldPos, 1.0f);
    float xOff = xOffsets[offsetIdxOld]/res.x;
    float yOff = yOffsets[offsetIdxOld]/res.y;
    projectionVec.xy += vec2(xOff, yOff)*projectionVec.w;
    projectionVec.xyz /= projectionVec.w;
    projectionVec.xy = projectionVec.xy * 0.5f + 0.5f;
    return projectionVec.xy;
}

void main() {
    vec2 texCoords = gl_FragCoord.xy/res;
    vec4 currentColor = texture(in_color, texCoords);

    float fragDepth = currentColor.w;
    vec3 pos = getPosFromDepth(fragDepth);
//    if (fragDepth > 0.f) {
//
//        currentColor = vec4(pos, 1);
//        currentColor.a = 1.f;
//        vec3 norm = texture(in_normal, texCoords).xyz*-1; //normalize(vec3(0, 1, 0));//
//        if (norm == vec3(0.f)) {
//            currentColor.a = 1.f;
//        } else {
//            //norm = normalize(norm*mat3(view));
//            //currentColor.rgb = vec3(1);
//            vec3 randDir = vec3(1.f);//0.5f, 1.f, 0.8f);
//            vec3 tangent  = normalize((randDir - norm) * dot(randDir, norm));
//            vec3 bitangent = cross(norm, tangent);
//            mat3 TBN = mat3(bitangent, tangent, norm);
//            float ao = 1.0;
//            for (int i = 0; i < kernelSize; ++i){
//                vec3 samplePos = TBN * kernelData[i];// from tangent to view-space
//                samplePos = pos + (samplePos * aoRadius)+(norm/100);
//                vec2 scrPos = worldToScreenPos(samplePos);
//                vec4 samp = texture(in_color, scrPos);
//                float rangeCheck = smoothstep(0.0f, 1.0f, aoRadius / abs(fragDepth - samp.w));
//                ao -= (samp.w >= fragDepth ? (aoDarkness*rangeCheck) : 0.0f);
//            }
//            currentColor.a = pow(ao, 10.0);
//            //currentColor = vec4(texture(in_color, worldToScreenPos(pos + ((TBN * vec3(-0.3, 0.7, 0.02))*aoRadius) + (norm/100))).w*1000, 0, fragDepth*1000, 1);
//            //currentColor = vec4((((TBN * vec3(-0.3, 0.7, 0.02))*aoRadius) + (norm/100)), 1);
//            //currentColor = vec4(worldToScreenPos(pos + ((TBN * vec3(-0.3, 0.7, 0.02))*aoRadius) + (norm/100)), 0, 1);
//            //currentColor = vec4((worldToScreenPos(pos + ((TBN * vec3(-0.3, 0.7, 0.02))*aoRadius) + (norm/100))-texCoords)*500, 0, 1);
//        }
//    } else {
//        currentColor.a = 1.f;
//    }

    if (taa) {
        vec4 oldColorUnjittered = texture(in_color_old, texCoords);
        vec2 reprojected = reprojectPrev(pos);
        vec4 oldColor = (reprojected.x >= 0.f && reprojected.x < 1.f && reprojected.y >= 0.f && reprojected.y < 1.f) ? texture(in_color_old, reprojected) : currentColor;
        float velocity = distance((reprojected*res), gl_FragCoord.xy);
        int radius = 2;
        vec3 boxMin = vec3(1);
        vec3 boxMax = vec3(0);
        for (int x = int(gl_FragCoord.x-radius); x < gl_FragCoord.x+radius; x++) {
            for (int y = int(gl_FragCoord.y-radius); y < gl_FragCoord.y+radius; y++) {
                vec3 nearColor = texelFetch(in_color, ivec2(x, y), 0).rgb;
                boxMin = min(boxMin, nearColor);
                boxMax = max(boxMax, nearColor);
            }
        }
        oldColor.rgb = mix(oldColor.rgb, clamp(oldColor.rgb, boxMin, boxMax), clamp((velocity-0.6f)*2, 0, 1));

        vec3 comparedColors = abs(currentColor.rgb-oldColor.rgb);
        float brightDif = clamp(max(comparedColors.r, max(comparedColors.g, comparedColors.b))*6.66f, 0.f, 1.f);
        fragColor = mix(currentColor, oldColor, mix(0.95f, 0.85f, brightDif));
        //fragColor.rgb = mix(currentColor.rgb, oldColor.rgb, 0.99);
        fragColor.a = fragDepth;
    } else {
        fragColor = currentColor;
    }
}