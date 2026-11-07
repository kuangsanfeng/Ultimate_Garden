#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in float LineWidth;

out vec4 vertexColor;

void main() {
    // Use the exact current world uniforms, including view bobbing and FOV effects.
    vec4 cameraTarget = ModelViewMat * vec4(Position, 1.0);
    vec4 target = ProjMat * cameraTarget;
    vertexColor = Color;
    gl_Position = vec4(2.0, 2.0, 0.5, 1.0);
    if (any(isnan(cameraTarget)) || any(isinf(cameraTarget)) ||
        any(isnan(target)) || any(isinf(target)) ||
        any(lessThanEqual(ScreenSize, vec2(0.0)))) {
        return;
    }

    vec2 end;
    if (target.w > 0.00001) {
        // Preserve the normal projection for targets in front of the render camera.
        end = target.xy / target.w;
    } else {
        // Rear/side targets use camera-local lateral direction, never negative-w division.
        float magnitude = max(abs(cameraTarget.x), max(abs(cameraTarget.y), abs(cameraTarget.z)));
        if (magnitude < 0.000001) {
            return;
        }
        vec3 localDirection = cameraTarget.xyz / magnitude;
        if (length(localDirection.xy) <= 0.001 * length(localDirection)) {
            // A small rear-axis cone has no stable left/right direction; always point down.
            end = vec2(0.0, -1.0);
        } else {
            end = localDirection.xy * vec2(ProjMat[0][0], ProjMat[1][1]);
            float edge = max(abs(end.x), abs(end.y));
            if (edge < 0.000001) {
                return;
            }
            end /= edge;
        }
    }
    if (any(isnan(end)) || any(isinf(end))) {
        return;
    }
    // Preserve off-screen direction and clip to the viewport edge.
    end /= max(1.0, max(abs(end.x), abs(end.y)));
    vec2 pixels = end * ScreenSize;
    float lengthSquared = dot(pixels, pixels);
    if (lengthSquared < 0.000001) {
        return;
    }
    vec2 direction = pixels * inversesqrt(lengthSquared);
    vec2 offset = vec2(-direction.y, direction.x) * LineWidth / ScreenSize;

    // LINES duplicates each endpoint: vertices 0/1 start, vertices 2/3 end.
    vec2 point = (gl_VertexID & 2) == 0 ? vec2(0.0) : end;
    point += (gl_VertexID & 1) == 0 ? offset : -offset;
    // A fixed clip depth works with both OpenGL and zero-to-one depth ranges.
    gl_Position = vec4(point, 0.5, 1.0);
}
