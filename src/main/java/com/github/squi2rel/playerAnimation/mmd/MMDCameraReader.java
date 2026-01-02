package com.github.squi2rel.playerAnimation.mmd;

import com.google.common.io.LittleEndianDataInputStream;

import java.io.IOException;
import java.io.InputStream;

public class MMDCameraReader {

    public static int readCameraLength(InputStream stream) {
        try {
            LittleEndianDataInputStream dis = new LittleEndianDataInputStream(stream);
            dis.skipBytes(50);
            int l = dis.readInt();
            if (l != 0) return -1;
            l = dis.readInt();
            if (l != 0) return -1;
            l = dis.readInt();
            return l == 0 ? -1 : l;
        } catch (IOException e) {
            return -1;
        }
    }

    public static MMDCameraData readBody(InputStream stream, int l) throws IOException {
        LittleEndianDataInputStream dis = new LittleEndianDataInputStream(stream);
        MMDCameraData.RawCameraFrame[] frames = new MMDCameraData.RawCameraFrame[l];
        for (int i = 0; i < l; i++) {
            MMDCameraData.RawCameraFrame frame = new MMDCameraData.RawCameraFrame();
            frame.frameId = dis.readInt();
            frame.distance = dis.readFloat();
            frame.targetX = dis.readFloat();
            frame.targetY = dis.readFloat();
            frame.targetZ = dis.readFloat();
            frame.rotX = dis.readFloat();
            frame.rotY = dis.readFloat();
            frame.rotZ = dis.readFloat();
            frame.interpolation = new byte[24];
            dis.readFully(frame.interpolation);
            frame.fov = dis.readInt();
            frame.perspective = dis.readBoolean();
            frames[i] = frame;
        }
        return new MMDCameraData(frames);
    }
}
