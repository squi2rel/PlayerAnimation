package com.github.squi2rel.playerAnimation.mmd;

import com.github.squi2rel.playerAnimation.PlayerAnimation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MMDCameraData {
    public static final Vector3f FORWARD = new Vector3f(0, 0, -1);
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

    public void getCameraData(float rawFrame, Vector3f pos, Quaternionf rot) {
        float frame = (float) (rawFrame / (100.0 / 3));// TODO ?
        int k1 = findFrame(frame);
        int k2 = Math.min(k1 + 1, frames.length - 1);
        PlayerAnimation.LOGGER.info(" %f f %d %d".formatted(frame, k1, k2));
        CameraFrame f = frames[k1];
        if (k1 != k2) {
            float t0 = frames[k1].frameTime;
            float t = (frame - t0) / ((float) frames[k2].frameTime - t0);
            CameraFrame f2 = frames[k2];
            rot.set(f.rot).slerp(f2.rot, t).transform(FORWARD);
            pos.set(f.target).lerp(f2.target, t).fma(-f.distance, FORWARD);
        } else {
            rot.set(f.rot).transform(FORWARD);
            pos.set(f.target).fma(-f.distance, FORWARD);
        }
    }

    public int findFrame(float frame) {
        if (frame < frames[lastIndex].frameTime) {
            lastIndex = binarySearch(frame);
            return lastIndex;
        }

        while (lastIndex + 1 < frames.length && frames[lastIndex + 1].frameTime < frame) {
            lastIndex++;
        }

        return lastIndex;
    }

    private int binarySearch(float time) {
        int low = 0, high = frames.length - 1, result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (frames[mid].frameTime < time) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
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
