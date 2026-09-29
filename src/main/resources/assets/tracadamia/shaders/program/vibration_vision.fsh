#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const vec3 SENSED = vec3(0.92, 0.95, 1.00);

float luma(vec2 uv) {
    return dot(texture(DiffuseSampler, uv).rgb, vec3(0.299, 0.587, 0.114));
}

float sensed(vec2 uv) {
    vec4 mask = texture(MaskSampler, uv);
    return mask.r * mask.a;
}

void main() {
    float center = luma(texCoord);
    float dx = luma(texCoord + vec2(oneTexel.x, 0.0)) - luma(texCoord - vec2(oneTexel.x, 0.0));
    float dy = luma(texCoord + vec2(0.0, oneTexel.y)) - luma(texCoord - vec2(0.0, oneTexel.y));
    float edge = clamp(length(vec2(dx, dy)) * 2.5, 0.0, 1.0);
    vec3 color = vec3(0.07, 0.075, 0.09) + vec3(center * 0.18) + vec3(edge * 0.22);

    float strength = sensed(texCoord);
    float glow = 0.0;
    for (int i = 0; i < 12; i++) {
        float angle = float(i) * 0.5235988;
        vec2 direction = vec2(cos(angle), sin(angle)) * oneTexel;
        glow = max(glow, sensed(texCoord + direction * 3.0) * 0.6);
        glow = max(glow, sensed(texCoord + direction * 8.0) * 0.3);
    }

    color = mix(color, SENSED, strength) + SENSED * glow * glow * (1.0 - strength);
    fragColor = vec4(color, 1.0);
}
