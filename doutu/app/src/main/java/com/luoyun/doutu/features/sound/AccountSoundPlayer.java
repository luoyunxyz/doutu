package com.luoyun.doutu.features.sound;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@link SoundScript} 拼出来的那条音素串依次放出来。
 *
 * <p>原包用的是 `audioplayers` 插件顺序播放 + `setPlaybackRate` 倍速（资料包 §4.2）；
 * 这里就用一个片段一个 {@link MediaPlayer}，播完一个接下一个 —— 片段都只有零点几秒，
 * 顺序播放的接缝听不出来。倍速走 {@link PlaybackParams}（API 23 起可用）。
 *
 * <p>播放器只管「放完一条」，循环、随机、延时这些节奏由页面自己安排（见 AccountSoundActivity），
 * 这样两边各管一件事，出错也好定位。
 */
public final class AccountSoundPlayer {
    private static final String TAG = "DoutuSound";

    /** 播放过程中的回调；都在主线程。 */
    public interface Listener {
        /** 这一条播完了（循环模式会接着排下一条）。 */
        void onFinished();

        /** 放不出来时给用户看的一句话。 */
        void onError(String message);
    }

    private final Context context;
    private final List<MediaPlayer> players = new ArrayList<>();
    private Listener listener;
    private float speed = 1f;
    private boolean released = true;

    public AccountSoundPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isPlaying() {
        return !released;
    }

    /**
     * 播一条。
     *
     * @param amount 金额原文
     * @param wechat true = 小微风格收尾
     * @param speed  倍速（0.5 / 0.75 / 1 / 1.25 / 1.5）
     */
    public void play(String amount, boolean wechat, float speed, Listener callback) {
        stop();
        this.listener = callback;
        this.speed = speed <= 0f ? 1f : speed;
        SoundScript.Result script = SoundScript.build(amount, wechat);
        if (!script.ok) {
            if (callback != null) callback.onError(script.error);
            return;
        }
        released = false;
        Log.e(TAG, "播报序列（" + (wechat ? "小微" : "小支") + "）：" + script.clips);
        try {
            for (String clip : script.clips) {
                players.add(prepare(clip));
            }
        } catch (Exception e) {
            Log.e(TAG, "收款声音：音频准备失败", e);
            stop();
            if (callback != null) callback.onError("播放失败: " + e.getMessage());
            return;
        }
        // 一段接一段地放。这里**必须**用 setNextMediaPlayer 交接，不能用「上一段放完的回调里再开下一段」——
        // 那个回调要等播放器把缓冲排空、再把消息投回主线程，实测每个字之间会多出上百毫秒的停顿，
        // 听上去一个字一顿。交给播放器自己交接就是无缝的，数字连起来才像一句播报。
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            for (MediaPlayer player : players) {
                player.setOnInfoListener((mp, what, extra) -> {
                    if (what == MediaPlayer.MEDIA_INFO_STARTED_AS_NEXT && speed != 1f) {
                        try {
                            mp.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1f));
                        } catch (Exception ignored) {
                            // 这一段就按原速放
                        }
                    }
                    return false;
                });
            }
        }
        for (int i = 0; i < players.size(); i++) {
            if (i + 1 < players.size()) {
                try {
                    players.get(i).setNextMediaPlayer(players.get(i + 1));
                } catch (Exception e) {
                    Log.e(TAG, "收款声音：第 " + i + " 段交接失败，改回逐段播放", e);
                    fallbackChain(i);
                    return;
                }
            }
        }
        players.get(players.size() - 1).setOnCompletionListener(mp -> finishSequence());
        startClip(0);
    }

    /** 交接不上时的退路：还是用「放完再开下一段」的老办法，慢一点但能放。 */
    private void fallbackChain(int from) {
        for (int i = from; i < players.size(); i++) {
            final int index = i;
            players.get(i).setOnCompletionListener(mp -> startClip(index + 1));
        }
        startClip(0);
    }

    private void finishSequence() {
        released = true;
        Listener callback = listener;
        for (MediaPlayer player : players) {
            try {
                player.release();
            } catch (Exception ignored) {
                // 忽略
            }
        }
        players.clear();
        if (callback != null) callback.onFinished();
    }

    /** 现在放的是哪几个片段（调试/自检用）。 */
    public static List<String> clipsOf(String amount, boolean wechat) {
        SoundScript.Result script = SoundScript.build(amount, wechat);
        return script.ok ? script.clips : new ArrayList<>();
    }

    public void stop() {
        for (MediaPlayer player : players) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (Exception ignored) {
                // 已经结束了
            }
            try {
                player.release();
            } catch (Exception ignored) {
                // 同上
            }
        }
        players.clear();
        released = true;
    }

    /** 释放，页面销毁时调。 */
    public void release() {
        stop();
        listener = null;
    }

    private void startClip(int index) {
        if (released) return;
        if (index >= players.size()) {
            finishSequence();
            return;
        }
        MediaPlayer player = players.get(index);
        try {
            player.start();
            // 先 start 再设速率：setPlaybackParams 在「已开始」的播放器上只改速率，不会重新起播。
            // 后面几段是播放器自己交接的，速率在那个 onInfo 回调里补上。
            if (speed != 1f) {
                player.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1f));
            }
        } catch (Exception e) {
            Log.e(TAG, "收款声音：第 " + index + " 段播放失败", e);
            released = true;
            Listener callback = listener;
            if (callback != null) callback.onError("播放失败: " + e.getMessage());
        }
    }

    private MediaPlayer prepare(String clip) throws Exception {
        MediaPlayer player = new MediaPlayer();
        try (AssetFileDescriptor fd = context.getAssets().openFd("fake_world/assets/" + SoundScript.path(clip))) {
            player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            player.prepare();
        } catch (Exception e) {
            player.release();
            throw e;
        }
        return player;
    }
}
