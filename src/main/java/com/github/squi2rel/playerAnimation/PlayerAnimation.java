package com.github.squi2rel.playerAnimation;

import com.github.squi2rel.playerAnimation.audio.AudioPlayerPlugin;
import com.github.squi2rel.playerAnimation.mmd.MMDCameraData;
import com.github.squi2rel.playerAnimation.mmd.MMDCameraReader;
import com.github.squi2rel.playerAnimation.modules.MMDPlayModule;
import com.github.squi2rel.playerAnimation.modules.VRLinkModule;
import com.github.squi2rel.playerAnimation.packet.PacketUtil;
import com.github.squi2rel.playerAnimation.vivecraft.ViveUtil;
import de.maxhenkel.voicechat.api.VoicechatApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

public final class PlayerAnimation extends JavaPlugin {
    public static final String PLUGIN_ID = "playeranimation";
    public static Logger LOGGER;
    public static PlayerAnimation plugin;

    public static MMDPlayModule mmdPlayModule;
    public static VRLinkModule vrLinkModule;

    public static AudioPlayerPlugin audioPlayerPlugin;

    @Override
    public void onEnable() {
        // Plugin startup logic
        plugin = this;
        LOGGER = getLogger();

        Bukkit.getScheduler().runTask(this, this::setup);
        Bukkit.getScheduler().runTaskTimer(this, this::loop, 0, 1);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (mmdPlayModule.player != null) mmdPlayModule.player.interrupt();
        Bukkit.getScheduler().cancelTasks(this);
        PacketUtil.protocolManager.removePacketListeners(this);
        AudioPlayerPlugin.cleanup();
        getServer().getServicesManager().unregister(audioPlayerPlugin);
    }

    public void setup() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            audioPlayerPlugin = new AudioPlayerPlugin();
            try {
                audioPlayerPlugin.initialize((VoicechatApi) Class.forName("de.maxhenkel.voicechat.plugins.impl.VoicechatServerApiImpl").getDeclaredMethod("instance").invoke(null));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, 20);

        mmdPlayModule = new MMDPlayModule(this);
        vrLinkModule = new VRLinkModule(this);
    }

    public void loop() {
        ViveUtil.update();
    }

    public static void main(String[] args) throws IOException {
        try (InputStream is = new FileInputStream("F:\\work\\《人间万朵红》MMD配布\\人间万朵红-Camera数据-配布用-by To_E.vmd")) {
            int l = MMDCameraReader.readCameraLength(is);
            if (l != -1) {
                MMDCameraData data = MMDCameraReader.readBody(is, l);
                System.out.println(data);
            }
        }
    }
}
