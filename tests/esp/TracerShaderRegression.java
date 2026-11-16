
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Runs the real tracer shader in a hidden GL context and reads its clip-space output. */
public final class TracerShaderRegression {
    private static int checks, program, feedback, globals, transforms, projection;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Tracer GPU check " + checks); }
    private static String resource(String path) throws Exception {
        try (var stream = TracerShaderRegression.class.getResourceAsStream("/" + path)) {
            if (stream == null) throw new IllegalStateException("Missing shader resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private static String source() throws Exception {
        String source = resource("assets/ultimate_garden/shaders/core/esp_tracer.vsh");
        Matcher matcher = Pattern.compile("#moj_import <minecraft:([^>]+)>").matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(result, Matcher.quoteReplacement(
                resource("assets/minecraft/shaders/include/" + matcher.group(1)).replaceAll("(?m)^#version.*$", "")));
        matcher.appendTail(result); return result.toString();
    }
    private static int uniformBuffer(String name, int binding) {
        int index = glGetUniformBlockIndex(program, name);
        check(index != GL_INVALID_INDEX);
        glUniformBlockBinding(program, index, binding);
        int buffer = glGenBuffers(); glBindBuffer(GL_UNIFORM_BUFFER, buffer);
        glBufferData(GL_UNIFORM_BUFFER, glGetActiveUniformBlocki(program, index, GL_UNIFORM_BLOCK_DATA_SIZE), GL_DYNAMIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, binding, buffer); return buffer;
    }
    private static void matrix(int buffer, Matrix4f value) {
        glBindBuffer(GL_UNIFORM_BUFFER, buffer); glBufferSubData(GL_UNIFORM_BUFFER, 0, value.get(new float[16]));
    }
    private static float[] draw(Matrix4f p, Matrix4f view, Vector3f target, int width, int height) {
        matrix(projection, p); matrix(transforms, view);
        int index = glGetUniformIndices(program, "ScreenSize");
        int offset = glGetActiveUniformsi(program, index, GL_UNIFORM_OFFSET);
        glBindBuffer(GL_UNIFORM_BUFFER, globals); glBufferSubData(GL_UNIFORM_BUFFER, offset, new float[]{width,height});
        glVertexAttrib3f(glGetAttribLocation(program, "Position"), target.x, target.y, target.z);
        glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, feedback);
        glBeginTransformFeedback(GL_POINTS); glDrawArrays(GL_POINTS, 0, 4); glEndTransformFeedback();
        float[] result = new float[16]; glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, feedback); glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER, 0, result);
        return result;
    }
    private static void verify(Matrix4f p, Matrix4f view, Vector3f target, int width, int height) {
        float[] actual = draw(p, view, target, width, height);
        for (float v : actual) check(Float.isFinite(v));
        Vector4f expected = new Matrix4f(p).mul(view).transform(new Vector4f(target, 1));
        float x = expected.x / expected.w, y = expected.y / expected.w;
        float clip = Math.max(1, Math.max(Math.abs(x), Math.abs(y))); x /= clip; y /= clip;
        check(Math.abs((actual[0] + actual[4]) * 0.5f) < 1e-6);
        check(Math.abs((actual[1] + actual[5]) * 0.5f) < 1e-6);
        check(Math.abs((actual[8] + actual[12]) * 0.5f - x) < 2e-5);
        check(Math.abs((actual[9] + actual[13]) * 0.5f - y) < 2e-5);
        double dx = (actual[0] - actual[4]) * width / 2, dy = (actual[1] - actual[5]) * height / 2;
        check(Math.abs(Math.hypot(dx,dy) - 3) < 1e-4);
    }
    private static void edge(Matrix4f p, Matrix4f view, Vector3f target, float x, float y) {
        float[] actual = draw(p, view, target, 1920, 1080);
        for (float v : actual) check(Float.isFinite(v));
        check(Math.abs((actual[0] + actual[4]) * 0.5f) < 1e-6);
        check(Math.abs((actual[1] + actual[5]) * 0.5f) < 1e-6);
        check(Math.abs((actual[8] + actual[12]) * 0.5f - x) < 2e-5);
        check(Math.abs((actual[9] + actual[13]) * 0.5f - y) < 2e-5);
        double dx = (actual[0] - actual[4]) * 960, dy = (actual[1] - actual[5]) * 540;
        check(Math.abs(Math.hypot(dx,dy) - 3) < 1e-4);
    }
    public static void main(String[] args) throws Exception {
        if (!glfwInit()) throw new IllegalStateException("GLFW unavailable; GPU test was not run");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(64, 64, "Tracer GPU regression", 0, 0);
        if (window == 0) { glfwTerminate(); throw new IllegalStateException("Cannot create hidden OpenGL context"); }
        int shader = 0, vao = 0;
        try {
            glfwMakeContextCurrent(window); GL.createCapabilities();
            shader = glCreateShader(GL_VERTEX_SHADER); glShaderSource(shader, source()); glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) throw new AssertionError(glGetShaderInfoLog(shader));
            program = glCreateProgram(); glAttachShader(program, shader);
            glTransformFeedbackVaryings(program, new CharSequence[]{"gl_Position"}, GL_INTERLEAVED_ATTRIBS); glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) throw new AssertionError(glGetProgramInfoLog(program));
            glUseProgram(program); vao = glGenVertexArrays(); glBindVertexArray(vao);
            glVertexAttrib4f(glGetAttribLocation(program, "Color"), 1,1,1,1);
            glVertexAttrib1f(glGetAttribLocation(program, "LineWidth"), 3);
            projection = uniformBuffer("Projection", 0); transforms = uniformBuffer("DynamicTransforms", 1); globals = uniformBuffer("Globals", 2);
            feedback = glGenBuffers(); glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, feedback); glBufferData(GL_TRANSFORM_FEEDBACK_BUFFER, 64, GL_DYNAMIC_READ);
            glEnable(GL_RASTERIZER_DISCARD);
            Matrix4f base = new Matrix4f().perspective((float)Math.toRadians(70), 16f/9, 0.05f, 1000);
            edge(base, new Matrix4f(), new Vector3f(-2,0,5), -1,0);
            edge(base, new Matrix4f(), new Vector3f(2,0,5), 1,0);
            edge(base, new Matrix4f(), new Vector3f(0,0,5), 0,-1);
            edge(base, new Matrix4f(), new Vector3f(0,2,5), 0,1);
            edge(base, new Matrix4f(), new Vector3f(0,-2,5), 0,-1);
            // Crossing the camera plane retains the same lateral direction.
            for (float z : new float[]{-0.0001f, 0, 0.0001f}) {
                edge(base, new Matrix4f(), new Vector3f(-5,0,z), -1,0);
                edge(base, new Matrix4f(), new Vector3f(5,0,z), 1,0);
            }
            for (float x : new float[]{-0.0001f, 0, 0.0001f}) edge(base, new Matrix4f(), new Vector3f(x,0,5), 0,-1);
            // The same world positions swap camera-local sides in third-person front view.
            Matrix4f frontView = new Matrix4f().rotateY((float)Math.PI);
            edge(base, frontView, new Vector3f(-2,0,-5), 1,0);
            edge(base, frontView, new Vector3f(2,0,-5), -1,0);
            edge(base, frontView, new Vector3f(0,0,-5), 0,-1);
            for (float yaw : new float[]{179.999f,180,-179.999f})
                edge(base, new Matrix4f().rotateY((float)Math.toRadians(yaw)), new Vector3f(0,0,-5), 0,-1);
            for (int frame = 0; frame < 60; frame++) {
                float angle = frame * 0.1f;
                Matrix4f view = new Matrix4f().rotateY(angle).rotateX((float)Math.sin(angle) * 0.5f);
                Matrix4f p = new Matrix4f().perspective((float)Math.toRadians(50 + frame), frame % 2 == 0 ? 16f/9 : 4f/3, 0.05f, 1000, frame % 2 == 0);
                // Bobbing affects forward projection, not the rear camera-direction rule.
                p.translate((float)Math.sin(angle) * 0.05f, 0.02f, 0);
                Vector3f relative = new Matrix4f(view).invert().transformPosition(new Vector3f(frame % 2 == 0 ? -2 : 2,0,5));
                edge(p, view, relative, frame % 2 == 0 ? -1 : 1, 0);
            }
            Matrix4f bobbed = new Matrix4f(base).translate(0.05f, 0.02f, 0);
            Vector3f oldStart = new Matrix4f(base).invert().transformProject(new Vector3f(0,0,0.5f));
            Vector3f oldScreen = new Matrix4f(bobbed).scale(255f/256).transformProject(oldStart);
            double drift = Math.hypot(oldScreen.x * 960, oldScreen.y * 540);
            check(drift > 10);
            for (int frame = 0; frame < 120; frame++) {
                float t = frame * 0.05f;
                int width = frame % 2 == 0 ? 1920 : 1280, height = frame % 3 == 0 ? 1200 : 720;
                Matrix4f p = new Matrix4f().perspective((float)Math.toRadians(70 + 20 * Math.sin(t)), (float)width/height, 0.05f, 1000, frame % 2 == 0);
                p.translate((float)Math.sin(t) * 0.05f, (float)Math.cos(t) * 0.02f, 0).rotateZ((float)Math.sin(t) * 0.02f);
                Matrix4f view = new Matrix4f().rotateX((float)Math.cos(t) * 0.1f).rotateY((float)Math.sin(t) * 0.5f);
                Vector3f target = new Vector3f(2 - t * 0.1f, 1 + (float)Math.sin(t) * 0.2f, -5 - (frame % 2 == 0 ? 0 : 4));
                verify(p, view, target, width, height);
            }
            verify(base, new Matrix4f(), new Vector3f(1000, 1, -5), 1920,1080);
            for (Vector3f hidden : new Vector3f[]{new Vector3f(Float.NaN,0,-5), new Vector3f(Float.POSITIVE_INFINITY,0,-5), new Vector3f(0,0,0), new Vector3f(0,0,-5)}) {
                float[] result = draw(base, new Matrix4f(), hidden, 1920,1080);
                for (int i=0; i<4; i++) check(result[i*4] == 2 && result[i*4+1] == 2 && Float.isFinite(result[i*4+2]));
            }
            check(glGetError() == GL_NO_ERROR);
            System.out.printf(java.util.Locale.ROOT, "PASS: %d real tracer GPU checks; old-chain viewport-center drift reproduced: %.2f px; fixed start <0.001 px. GPU: %s%n", checks, drift, glGetString(GL_RENDERER));
        } finally {
            if (feedback != 0) glDeleteBuffers(feedback); if (globals != 0) glDeleteBuffers(globals);
            if (transforms != 0) glDeleteBuffers(transforms); if (projection != 0) glDeleteBuffers(projection);
            if (vao != 0) glDeleteVertexArrays(vao); if (program != 0) glDeleteProgram(program); if (shader != 0) glDeleteShader(shader);
            glfwMakeContextCurrent(0); glfwDestroyWindow(window); glfwTerminate();
        }
    }
}
