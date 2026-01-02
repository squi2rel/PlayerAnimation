package com.github.squi2rel.playerAnimation.mmd;

import jp.nyatla.nymmd.MmdException;

import java.util.List;
import java.util.function.Consumer;

public class MMDThread extends Thread {
    public int targetFps = 30;

    public final MMDPlayer[] players;
    public Consumer<Float> updateCallback;
    public Runnable stopCallback;

    public MMDThread(List<MMDPlayer> players) {
        this.players = players.toArray(new MMDPlayer[0]);
    }

    public void setUpdateCallback(Consumer<Float> updateCallback) {
        this.updateCallback = updateCallback;
    }

    public void setStopCallback(Runnable stopCallback) {
        this.stopCallback = stopCallback;
    }

    @Override
    public void run() {
        float timeLength = players[0].getTimeLength();
        long targetFrameTimeNs = 1_000_000_000 / targetFps;
        long startTime = System.nanoTime();

        try {
            for (MMDPlayer player : players) player.updateMotion(0);
            while (!Thread.currentThread().isInterrupted()) {
                long frameStart = System.nanoTime();

                float elapsedMs = (frameStart - startTime) / 1_000_000f;
                if (elapsedMs >= timeLength) break;
                for (MMDPlayer player : players) player.updateMotion(elapsedMs);
                updateCallback.accept(elapsedMs);

                long frameEnd = System.nanoTime();
                long frameDuration = frameEnd - frameStart;
                long sleepTimeNs = targetFrameTimeNs - frameDuration;
                if (sleepTimeNs > 0) {
                    try {
                        Thread.sleep(sleepTimeNs / 1_000_000, (int) (sleepTimeNs % 1_000_000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (MmdException e) {
            throw new RuntimeException(e);
        } finally {
            stopCallback.run();
        }
    }

    public static MMDThread create(List<MMDPlayer> players) {
        MMDThread t = new MMDThread(players);
        t.setName("MMDPlayer-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    }
}
