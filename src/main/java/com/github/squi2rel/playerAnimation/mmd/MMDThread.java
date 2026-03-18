package com.github.squi2rel.playerAnimation.mmd;

import jp.nyatla.nymmd.MmdException;

import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.locks.LockSupport;

public class MMDThread extends Thread {
    private static final long PARK_MARGIN_NS = 2_000_000L;
    private static final long SPIN_MARGIN_NS = 100_000L;

    public int targetFps = 60;

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
        long nextFrameTimeNs = startTime;

        try {
            for (MMDPlayer player : players) player.updateMotion(0);
            while (!Thread.currentThread().isInterrupted()) {
                nextFrameTimeNs += targetFrameTimeNs;
                waitUntil(nextFrameTimeNs);
                long frameTime = System.nanoTime();

                float elapsedMs = (frameTime - startTime) / 1_000_000f;
                if (elapsedMs >= timeLength) break;
                for (MMDPlayer player : players) player.updateMotion(elapsedMs);
                updateCallback.accept(elapsedMs);

                long now = System.nanoTime();
                if (now - nextFrameTimeNs > targetFrameTimeNs) {
                    nextFrameTimeNs = now;
                }
            }
        } catch (MmdException e) {
            throw new RuntimeException(e);
        } finally {
            stopCallback.run();
        }
    }

    private static void waitUntil(long deadlineNs) {
        while (!Thread.currentThread().isInterrupted()) {
            long remaining = deadlineNs - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            if (remaining > PARK_MARGIN_NS) {
                LockSupport.parkNanos(remaining - PARK_MARGIN_NS);
                continue;
            }
            if (remaining > SPIN_MARGIN_NS) {
                Thread.yield();
                continue;
            }
            Thread.onSpinWait();
        }
    }

    public static MMDThread create(List<MMDPlayer> players) {
        MMDThread t = new MMDThread(players);
        t.setName("MMDPlayer-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    }
}
