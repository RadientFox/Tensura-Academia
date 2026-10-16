#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const vec3 SHADOW = vec3(0.04, 0.02, 0.16);
const vec3 TINT = vec3(0.24, 0.17, 0.66);
const vec3 EDGE = vec3(0.62, 0.55, 1.00);

float luma(vec2 uv) {
    return dot(texture(DiffuseSampler, uv).rgb, vec3(0.299, 0.587, 0.114));
}

vec3 seen(vec2 uv) {
    vec4 mask = texture(MaskSampler, uv);
    return mask.rgb * mask.a;
}

void main() {
    float center = luma(texCoord);
    float dx = luma(texCoord + vec2(oneTexel.x, 0.0)) - luma(texCoord - vec2(oneTexel.x, 0.0));
    float dy = luma(texCoord + vec2(0.0, oneTexel.y)) - luma(texCoord - vec2(0.0, oneTexel.y));
    float edge = clamp(length(vec2(dx, dy)) * 3.0, 0.0, 1.0);
    vec3 color = SHADOW + TINT * center + EDGE * edge * 0.7;

    vec4 mask = texture(MaskSampler, texCoord);
    vec3 glow = vec3(0.0);
    for (int i = 0; i < 12; i++) {
        float angle = float(i) * 0.5235988;
        vec2 direction = vec2(cos(angle), sin(angle)) * oneTexel;
        glow = max(glow, seen(texCoord + direction * 3.0) * 0.6);
        glow = max(glow, seen(texCoord + direction * 8.0) * 0.3);
    }

    color = mix(color, mask.rgb, mask.a * 0.8) + glow * (1.0 - mask.a);
    fragColor = vec4(color, 1.0);
}
