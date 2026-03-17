package com.github.squi2rel.playerAnimation.mmd;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MMDCameraData {
    private static final float FRAME_TIME_MS = 100.0f / 3.0f;
    private static final Vector3f CAMERA_OFFSET = new Vector3f(0, 0, 1);

    private final CameraFrame[] frames;
    private int lastIndex;

    public MMDCameraData(RawCameraFrame[] raws) {
        frames = new CameraFrame[raws.length];
        for (int i = 0; i < raws.length; i++) {
            RawCameraFrame raw = raws[i];
            CameraFrame frame = new CameraFrame();
            frame.frameTime = raw.frameId;
            frame.distance = raw.distance;
            frame.target = new Vector3f(raw.targetX, raw.targetY, raw.targetZ);
            frame.rot = new Quaternionf().rotationXYZ(raw.rotX, raw.rotY, raw.rotZ);
            frames[i] = frame;
        }
    }

    public void sample(float elapsedMs, Vector3f eyePos, Vector3f lookDir) {
        float frame = elapsedMs / FRAME_TIME_MS;
        int k1 = findFrame(frame);
        int k2 = Math.min(k1 + 1, frames.length - 1);
        CameraFrame from = frames[k1];

        float distance = from.distance;
        lookDir.set(from.target);

        Quaternionf rotation = new Quaternionf(from.rot);
        if (k1 != k2) {
            CameraFrame to = frames[k2];
            float t0 = from.frameTime;
            float t1 = to.frameTime;
            float t = t1 == t0 ? 0.0f : (frame - t0) / (t1 - t0);

            distance = lerp(from.distance, to.distance, t);
            lookDir.lerp(to.target, t);
            rotation.slerp(to.rot, t);
        }

        eyePos.set(CAMERA_OFFSET);
        rotation.transform(eyePos);
        eyePos.mul(distance).add(lookDir);

        lookDir.sub(eyePos);
        if (lookDir.lengthSquared() > 1.0e-8f) {
            lookDir.normalize();
        } else {
            lookDir.set(0, 0, -1);
            rotation.transform(lookDir);
        }
    }

    public int findFrame(float frame) {
        if (frame <= frames[0].frameTime) {
            lastIndex = 0;
            return 0;
        }
        if (frame >= frames[frames.length - 1].frameTime) {
            lastIndex = frames.length - 1;
            return lastIndex;
        }
        if (frame < frames[lastIndex].frameTime) {
            lastIndex = binarySearch(frame);
            return lastIndex;
        }

        while (lastIndex + 1 < frames.length && frames[lastIndex + 1].frameTime <= frame) {
            lastIndex++;
        }

        return lastIndex;
    }

    private int binarySearch(float time) {
        int low = 0;
        int high = frames.length - 1;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (frames[mid].frameTime <= time) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static class RawCameraFrame {
        int frameId;
        float distance;
        float targetX, targetY, targetZ;
        float rotX, rotY, rotZ;
        byte[] interpolation;
        int fov;
        boolean perspective;
    }

    public static class CameraFrame {
        int frameTime;
        float distance;
        Vector3f target;
        Quaternionf rot;
    }
}
