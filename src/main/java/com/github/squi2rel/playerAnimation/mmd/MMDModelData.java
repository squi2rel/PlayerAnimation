package com.github.squi2rel.playerAnimation.mmd;

import jp.nyatla.nymmd.MmdPmdModel_BasicClass;
import jp.nyatla.nymmd.MmdVmdMotion_BasicClass;
import jp.nyatla.nymmd.core.PmdBone;
import org.jetbrains.annotations.Nullable;

public record MMDModelData(
        MmdPmdModel_BasicClass pmd, MmdVmdMotion_BasicClass vmd,
        PmdBone head,
        @Nullable PmdBone rightElbow, @Nullable PmdBone leftElbow,
        @Nullable PmdBone rightHand, @Nullable PmdBone leftHand,
        @Nullable PmdBone waist,
        @Nullable PmdBone rightKnee, @Nullable PmdBone leftKnee,
        @Nullable PmdBone rightFoot, @Nullable PmdBone leftFoot
) {
    public static MMDModelData create(MmdPmdModel_BasicClass pmd, MmdVmdMotion_BasicClass vmd) {
        return new MMDModelData(pmd, vmd,
                pmd.getBoneByName("頭"),
                pmd.getBoneByName("右ひじ"), pmd.getBoneByName("左ひじ"),
                pmd.getBoneByName("右手首"), pmd.getBoneByName("左手首"),
                pmd.getBoneByName("下半身"),
                pmd.getBoneByName("右ひざ"), pmd.getBoneByName("左ひざ"),
                pmd.getBoneByName("右足首"), pmd.getBoneByName("左足首")
        );
    }
}
