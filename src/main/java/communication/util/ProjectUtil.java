package communication.util;

import communication.mixin.GameRendererInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

public final class ProjectUtil {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private ProjectUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Vector2f project(double x, double y, double z) {
        Camera camera = MC.gameRenderer.getCamera();
        if (camera == null) {
            return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        }
        Vector3f result = new Vector3f((float) (x - camera.getPos().x), (float) (y - camera.getPos().y),
                (float) (z - camera.getPos().z));
        Quaternionf inverseRotation = new Quaternionf(camera.getRotation()).conjugate();
        result.rotate(inverseRotation);
        return project(result, ((GameRendererInvoker) MC.gameRenderer).invokeGetFov(camera,
                MC.getRenderTickCounter().getTickDelta(false), true));
    }

    private static Vector2f project(Vector3f position, double fov) {
        if (position.z >= 0.0f) {
            return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        }
        float aspect = (float) MC.getWindow().getFramebufferWidth() / (float) MC.getWindow().getFramebufferHeight();
        double halfHeight = (-position.z) * Math.tan(Math.toRadians(fov / 2.0d));
        double halfWidth = halfHeight * aspect;
        double ndcX = position.x / halfWidth;
        double ndcY = position.y / halfHeight;
        float screenX = (float) (MC.getWindow().getScaledWidth() / 2.0f + ndcX * (MC.getWindow().getScaledWidth() / 2.0f));
        float screenY = (float) (MC.getWindow().getScaledHeight() / 2.0f - ndcY * (MC.getWindow().getScaledHeight() / 2.0f));
        return new Vector2f(screenX, screenY);
    }

    public static boolean isOnScreen(Vector2f screen) {
        return screen.x() != Float.MAX_VALUE && screen.y() != Float.MAX_VALUE
                && screen.x() >= 0.0f && screen.y() >= 0.0f
                && screen.x() <= MC.getWindow().getScaledWidth()
                && screen.y() <= MC.getWindow().getScaledHeight();
    }
}
