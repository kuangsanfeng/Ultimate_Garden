#version 330

uniform sampler2D InSampler;
layout(std140) uniform ReplayBlur {
    vec4 Filter; // UV step X/Y, sigma in GUI pixels, reserved
};
in vec2 texCoord;
out vec4 fragColor;

void main() {
    float sigma = Filter.z;
    int radius = int(ceil(sigma * 3.0));
    float weightedGray = 0.0;
    float weights = 0.0;
    for (int offset = -radius; offset <= radius; ++offset) {
        float weight = sigma > 0.0 ? exp(-0.5 * float(offset * offset) / (sigma * sigma)) : 1.0;
        vec3 color = texture(InSampler, texCoord + Filter.xy * float(offset)).rgb;
        weightedGray += dot(color, vec3(0.2126, 0.7152, 0.0722)) * weight;
        weights += weight;
    }
    fragColor = vec4(vec3(weightedGray / weights), 1.0);
}
