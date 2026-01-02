package com.github.squi2rel.playerAnimation.audio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

public class AudioUtil {
    public static AudioPlayer create(AudioChannel channel, short[] audio) {
        VoicechatServerApi api = AudioPlayerPlugin.voicechatServerApi;
        channel.setCategory(AudioPlayerPlugin.category.getId());
        OpusEncoder encoder = api.createEncoder();
        return api.createAudioPlayer(channel, encoder, audio);
    }
}
