#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const vec3 DIM_GREEN = vec3(0.08, 0.30, 0.10);
const vec3 BRIGHT_GREEN = vec3(0.40, 1.00, 0.45);

vec3 entityColor(float strength, float luma) {
    float quirked = smoothstep(0.45, 0.75, strength);
    return mix(DIM_GREEN, BRIGHT_GREEN, quirked) * (0.35 + luma * 1.1) * (0.6 + strength * 0.4);
}

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    vec4 mask = texture(MaskSampler, texCoord);
    float luma = dot(scene, vec3(0.299, 0.587, 0.114));

    vec3 color = vec3(luma * 0.5 + 0.02);

    if (mask.a > 0.0) {
        color = entityColor(mask.g, luma);
    } else {
        float glow = 0.0;
        for (int i = 0; i < 12; i++) {
            float angle = float(i) * 0.5235988;
            vec2 direction = vec2(cos(angle), sin(angle)) * oneTexel;
            glow = max(glow, texture(MaskSampler, texCoord + direction * 3.0).g * 0.55);
            glow = max(glow, texture(MaskSampler, texCoord + direction * 7.0).g * 0.25);
        }

        color += BRIGHT_GREEN * glow * glow;
    }

    fragColor = vec4(color, 1.0);
}
