package com.github.squi2rel.playerAnimation.mmd;

import jp.nyatla.nymmd.MmdException;
import jp.nyatla.nymmd.MmdPmdModel_BasicClass;
import jp.nyatla.nymmd.MmdVmdMotion_BasicClass;
import jp.nyatla.nymmd.core.PmdBone;
import jp.nyatla.nymmd.core.PmdFace;
import jp.nyatla.nymmd.core.PmdIK;
import jp.nyatla.nymmd.types.FaceData;
import jp.nyatla.nymmd.types.MmdVector3;
import jp.nyatla.nymmd.types.MotionData;

/**
 * @see jp.nyatla.nymmd.MmdMotionPlayer
 */
public class MMDPlayer {
    protected MmdPmdModel_BasicClass _ref_pmd_model;
    protected MmdVmdMotion_BasicClass _ref_vmd_motion;
    private PmdBone[] m_ppBoneList;
    private PmdFace[] m_ppFaceList;

    public MMDPlayer() {
    }

    public void setPmd(MmdPmdModel_BasicClass i_pmd_model) {
        this._ref_pmd_model = i_pmd_model;
        //PMD/VMDが揃った？
        if (this._ref_vmd_motion != null) {
            makeBoneFaceList();
        }
    }

    public void setVmd(MmdVmdMotion_BasicClass i_vmd_model) {
        this._ref_vmd_motion = i_vmd_model;
        // 操作対象ボーンのポインタを設定する
        MotionData[] pMotionDataList = i_vmd_model.refMotionDataArray();
        this.m_ppBoneList = new PmdBone[pMotionDataList.length];
        // 操作対象表情のポインタを設定する
        FaceData[] pFaceDataList = i_vmd_model.refFaceDataArray();
        this.m_ppFaceList = new PmdFace[pFaceDataList.length];
        //PMD/VMDが揃った？
        if (this._ref_pmd_model != null) {
            makeBoneFaceList();
        }
    }

    private void makeBoneFaceList() {
        MmdPmdModel_BasicClass pmd_model = this._ref_pmd_model;
        MmdVmdMotion_BasicClass vmd_model = this._ref_vmd_motion;

        // 操作対象ボーンのポインタを設定する
        MotionData[] pMotionDataList = vmd_model.refMotionDataArray();
        this.m_ppBoneList = new PmdBone[pMotionDataList.length];
        for (int i = 0; i < pMotionDataList.length; i++) {
            this.m_ppBoneList[i] = pmd_model.getBoneByName(pMotionDataList[i].szBoneName);
        }
        // 操作対象表情のポインタを設定する
        FaceData[] pFaceDataList = vmd_model.refFaceDataArray();
        this.m_ppFaceList = new PmdFace[pFaceDataList.length];
        for (int i = 0; i < pFaceDataList.length; i++) {
            this.m_ppFaceList[i] = pmd_model.getFaceByName(pFaceDataList[i].szFaceName);
        }
    }

    /**
     * VMDの再生時間長を返します。
     *
     * @return ms単位の再生時間
     */
    public float getTimeLength() {
        return (float) (this._ref_vmd_motion.getMaxFrame() * (100.0 / 3));
    }

    /**
     * 指定した時刻のモーションに更新します。
     *
     * @param i_position_in_msec モーションの先頭からの時刻をms単位で指定します。
     * @throws MmdException
     */
    public void updateMotion(float i_position_in_msec) throws MmdException {
        final PmdIK[] ik_array = this._ref_pmd_model.getIKArray();
        final PmdBone[] bone_array = this._ref_pmd_model.getBoneArray();
        assert i_position_in_msec >= 0;
        //描画するフレームを計算する。
        float frame = (float) (i_position_in_msec / (100.0 / 3));
        //範囲外を除外
        if (frame > this._ref_vmd_motion.getMaxFrame()) {
            frame = this._ref_vmd_motion.getMaxFrame();
        }
        this.updateFace(frame);

        // モーション更新
        this.updateBone(frame);

        // ボーン行列の更新
        for (PmdBone bone : bone_array) {
            bone.updateMatrix();
        }

        // IKの更新
        for (PmdIK pmdIK : ik_array) {
            pmdIK.update();
        }
    }

    private void updateBone(float i_frame) {
        //---------------------------------------------------------
        // 指定フレームのデータでボーンを動かす
        final PmdBone[] ppBone = this.m_ppBoneList;

        MotionData[] pMotionDataList = _ref_vmd_motion.refMotionDataArray();
        for (int i = 0; i < pMotionDataList.length; i++) {
            if (ppBone[i] == null) {
                continue;
            }
            pMotionDataList[i].getMotionPosRot(i_frame, ppBone[i]);
//			ppBone[i].m_vec3Position.setValue(vec3Position);
            //	 補間あり
            //				Vector3Lerp( &((*pBone)->m_vec3Position), &((*pBone)->m_vec3Position), &vec3Position, fLerpValue );
            //				QuaternionSlerp( &((*pBone)->m_vec4Rotate), &((*pBone)->m_vec4Rotate), &vec4Rotate, fLerpValue );
        }
    }

    /**
     * 指定フレームのデータで表情を変形する
     *
     * @param i_frame
     * @throws MmdException
     */
    private void updateFace(float i_frame) throws MmdException {
        final MmdVector3[] position_array = this._ref_pmd_model.getPositionArray();
        PmdFace[] ppFace = this.m_ppFaceList;
        FaceData[] pFaceDataList = _ref_vmd_motion.refFaceDataArray();
        for (int i = 0; i < pFaceDataList.length; i++) {
            final float fFaceRate = getFaceRate(pFaceDataList[i], i_frame);
            if (ppFace[i] == null) {
                continue;
            }
            if (fFaceRate == 1.0f) {
                ppFace[i].setFace(position_array);
            } else if (0.001f < fFaceRate) {
                ppFace[i].blendFace(position_array, fFaceRate);
            }
        }
    }

    private float getFaceRate(FaceData pFaceData, float fFrame) {
        int i;
        int ulNumKeyFrame = pFaceData.ulNumKeyFrames;

        // 最終フレームを過ぎていた場合
        if (fFrame > pFaceData.pKeyFrames[ulNumKeyFrame - 1].fFrameNo) {
            fFrame = pFaceData.pKeyFrames[ulNumKeyFrame - 1].fFrameNo;
        }

        // 現在の時間がどのキー近辺にあるか
        for (i = 0; i < ulNumKeyFrame; i++) {
            if (fFrame <= pFaceData.pKeyFrames[i].fFrameNo) {
                break;
            }
        }

        // 前後のキーを設定
        int lKey0 = i - 1;
        int lKey1 = i;

        if (lKey0 <= 0) {
            lKey0 = 0;
        }
        if (i == ulNumKeyFrame) {
            lKey1 = ulNumKeyFrame - 1;
        }

        // 前後のキーの時間
        float fTime0 = pFaceData.pKeyFrames[lKey0].fFrameNo;
        float fTime1 = pFaceData.pKeyFrames[lKey1].fFrameNo;

        // 前後のキーの間でどの位置にいるか
        float fLerpValue;
        if (lKey0 != lKey1) {
            fLerpValue = (fFrame - fTime0) / (fTime1 - fTime0);
            return (pFaceData.pKeyFrames[lKey0].fRate * (1.0f - fLerpValue)) + (pFaceData.pKeyFrames[lKey1].fRate * fLerpValue);
        } else {
            return pFaceData.pKeyFrames[lKey0].fRate;
        }
    }

    public void load(MMDModelData data) {
        setPmd(data.pmd());
        setVmd(data.vmd());
    }
}
