package com.github.squi2rel.playerAnimation.audio;

import com.github.squi2rel.playerAnimation.PlayerAnimation;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;

public class AudioPlayerPlugin implements VoicechatPlugin {
    public static VoicechatApi voicechatApi;
    public static VoicechatServerApi voicechatServerApi;
    public static VolumeCategory category;

    @Override
    public String getPluginId() {
        return PlayerAnimation.PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        voicechatApi = api;
        voicechatServerApi = (VoicechatServerApi) api;
    }

    public static void setup() {
        if (category != null) return;
        category = voicechatServerApi.volumeCategoryBuilder()
                .setId("mmd_audio")
                .setName("MMD audio")
                .setDescription("MMD audio")
                .build();
        voicechatServerApi.registerVolumeCategory(category);
    }

    public static void cleanup() {
        if (category != null) {
            voicechatServerApi.unregisterVolumeCategory(category);
            category = null;
        }
    }
}
