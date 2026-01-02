package com.github.squi2rel.playerAnimation.mmd;

import org.jetbrains.annotations.Nullable;

public record MMDResource(MMDModelData[] models, @Nullable MMDCameraData camera, @Nullable short[] audio) {
}
