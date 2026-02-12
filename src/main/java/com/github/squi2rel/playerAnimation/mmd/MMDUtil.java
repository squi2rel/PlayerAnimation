package com.github.squi2rel.playerAnimation.mmd;

import jp.nyatla.nymmd.MmdPmdModel_BasicClass;
import jp.nyatla.nymmd.core.PmdBone;
import jp.nyatla.nymmd.struct.pmd.PMD_Bone;
import jp.nyatla.nymmd.types.MmdMatrix;
import jp.nyatla.nymmd.types.MmdVector4;
import org.joml.Matrix3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.vivecraft.data.Pose;
import org.vivecraft.data.VrPlayerState;

import java.lang.reflect.Field;

public class MMDUtil {
    public static void update(VrPlayerState state, MMDModelData data) {
        apply(data.head(), state.hmd);
        if (data.rightElbow() != null) apply(data.rightElbow(), state.rightElbow);
        if (data.leftElbow() != null) apply(data.leftElbow(), state.leftElbow);
        if (data.rightHand() != null) apply(data.rightHand(), state.mainHand);
        if (data.leftHand() != null) apply(data.leftHand(), state.offHand);
        if (data.waist() != null) apply(data.waist(), state.waist);
        if (data.rightKnee() != null) apply(data.rightKnee(), state.rightKnee);
        if (data.leftKnee() != null) apply(data.leftKnee(), state.leftKnee);
        if (data.rightFoot() != null) apply(data.rightFoot(), state.rightFoot);
        if (data.leftFoot() != null) apply(data.leftFoot(), state.leftFoot);
    }

    public static void apply(PmdBone bone, Pose pose) {
        if (pose == null) throw new IllegalStateException();
        MmdMatrix mat = bone.m_matLocal;
        ((Vector3f) pose.position).set(-mat.m30 / 5, mat.m31 / 5, mat.m32 / 5);
        ((Quaternionf) pose.orientation).setFromUnnormalized(new Matrix3d(
                mat.m00, -mat.m01, -mat.m02,
                -mat.m10, mat.m11, mat.m12,
                -mat.m20, mat.m21, mat.m22
        ));
    }

    public static PmdBone boneHack(MmdPmdModel_BasicClass model) {
        PmdBone parent = model.getBoneByName("全ての親");
        PMD_Bone data = new PMD_Bone();
        data.nParentNo = -1;
        PmdBone bone = new PmdBone(data, new PmdBone[]{parent});
        try {
            Field field = PmdBone.class.getDeclaredField("_parent_bone");
            field.setAccessible(true);
            field.set(parent, bone);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return bone;
    }

    public static void rotateY(MmdVector4 rot, float deg) {
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(deg));
        rot.x = q.x;
        rot.y = q.y;
        rot.z = q.z;
        rot.w = q.w;
    }

    public static void offset(VrPlayerState state, float dx, float dy, float dz) {
        offset(state.hmd, dx, dy, dz);
        offset(state.mainHand, dx, dy, dz);
        offset(state.offHand, dx, dy, dz);
        offset(state.rightElbow, dx, dy, dz);
        offset(state.leftElbow, dx, dy, dz);
        offset(state.waist, dx, dy, dz);
        offset(state.rightKnee, dx, dy, dz);
        offset(state.leftKnee, dx, dy, dz);
        offset(state.rightFoot, dx, dy, dz);
        offset(state.leftFoot, dx, dy, dz);
    }

    public static void offset(Pose pose, float dx, float dy, float dz) {
        if (pose == null) throw new IllegalStateException();
        ((Vector3f) pose.position).add(dx, dy, dz);
    }

    public static Vector3f getCameraPosition(MMDCameraData.RawCameraFrame frame) {
        // TODO wrong?
        float rx = (float) Math.toRadians(frame.rotX);
        float ry = (float) Math.toRadians(frame.rotY);

        Vector3f forward = new Vector3f(
                (float) (Math.cos(rx) * Math.sin(ry)),
                (float) Math.sin(rx),
                (float) (Math.cos(rx) * Math.cos(ry))
        );

        return new Vector3f(frame.targetX, frame.targetY, frame.targetZ).sub(forward.mul(frame.distance));
    }
}
