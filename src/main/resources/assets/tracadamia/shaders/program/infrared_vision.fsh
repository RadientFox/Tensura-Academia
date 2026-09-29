#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

vec3 thermal(float heat) {
    vec3 cold = vec3(0.30, 0.02, 0.40);
    vec3 warm = vec3(0.90, 0.12, 0.08);
    vec3 hot = vec3(1.00, 0.60, 0.08);
    vec3 core = vec3(1.00, 0.97, 0.70);
    if (heat < 0.33) {
        return mix(cold, warm, heat / 0.33);
    }
    if (heat < 0.66) {
        return mix(warm, hot, (heat - 0.33) / 0.33);
    }
    return mix(hot, core, (heat - 0.66) / 0.34);
}

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    float luma = dot(scene, vec3(0.299, 0.587, 0.114));
    vec3 color = vec3(luma * 0.45 + 0.02);

    float coverage = 0.0;
    for (int i = 0; i < 12; i++) {
        float angle = float(i) * 0.5235988;
        vec2 direction = vec2(cos(angle), sin(angle)) * oneTexel;
        coverage += texture(MaskSampler, texCoord + direction * 4.0).a;
        coverage += texture(MaskSampler, texCoord + direction * 10.0).a;
    }
    coverage /= 24.0;

    if (texture(MaskSampler, texCoord).a > 0.0) {
        color = thermal(0.3 + coverage * 0.7);
    } else {
        color += vec3(0.9, 0.25, 0.05) * coverage * 0.6;
    }

    fragColor = vec4(color, 1.0);
}
