package com.github.squi2rel.playerAnimation.modules;

import com.github.squi2rel.playerAnimation.PlayerAnimation;

public abstract class BaseModule {
    public final PlayerAnimation plugin;

    public BaseModule(PlayerAnimation plugin) {
        this.plugin = plugin;
    }
}
