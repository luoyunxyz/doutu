package com.luoyun.doutu.account;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.luoyun.doutu.features.zhi.AvatarChooser;

import java.io.ByteArrayOutputStream;

/**
 * 「我」页换头像（2026-10-17）。
 *
 * <p>两条路，同一个落点：
 * <ul>
 *   <li><b>随机头像</b>：{@link AvatarChooser} 的四宫格（每格来自随机头像接口
 *       {@code https://api.lvxiaodong.com/api/head}），点一下即可选中；</li>
 *   <li><b>本地上传</b>：同一张面板底部的「从本地上传」走系统相册。</li>
 * </ul>
 * 无论哪条路拿到的都是一张 {@link Bitmap}，这里负责压到 320px 以内、转 base64、
 * 调 {@code POST /api/v1/auth/avatar} 上传（走私有协议），成功后刷新本地缓存并重画。
 *
 * <p>为什么统一压到 320px：服务端单张头像上限 1MB，而 320px 的 JPEG 只有几十 KB ——
 * 既远离上限，也让隧道帧保持很小（帧越大越容易撞到请求体上限）。
 */
public final class AvatarUpload {
    /** 最长边上限：够圆头像显示，又能把体积压到几十 KB。 */
    private static final int MAX_EDGE = 320;
    private static final int QUALITY = 88;

    private AvatarUpload() {
    }

    /** 我页头像被点时调用：打开随机头像 / 本地相册那张面板。 */
    public static void pick(Activity activity, Runnable onDone) {
        if (!Session.isLoggedIn()) {
            Toast(activity, "登录后才能更换头像");
            return;
        }
        AvatarChooser.show(activity, "更换头像", bitmap -> {
            if (bitmap == null) return;
            apply(activity, bitmap, onDone);
        });
    }

    /** 把选中的图上传成账号头像。 */
    public static void apply(Activity activity, Bitmap bitmap, Runnable onDone) {
        final String base64;
        try {
            base64 = encode(bitmap);
        } catch (Exception e) {
            Toast(activity, "这张图片处理失败，换一张试试");
            return;
        }
        if (base64 == null || base64.isEmpty()) {
            Toast(activity, "这张图片处理失败，换一张试试");
            return;
        }
        Toast(activity, "正在上传头像…");
        DoutuApi.uploadAvatar(activity, base64, r -> {
            if (!r.ok()) {
                Toast(activity, r.msg == null || r.msg.isEmpty() ? "头像上传失败" : r.msg);
                return;
            }
            Session.updateUser(r.user());
            AvatarStore.clear();
            AvatarStore.load(activity, onDone);
            Toast(activity, "头像已更新");
            if (onDone != null) onDone.run();
        });
    }

    /** 等比缩到 {@link #MAX_EDGE} 以内的 JPEG，再转 base64（服务端只认 data URL 里的图片字节）。 */
    private static String encode(Bitmap source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        Bitmap scaled = source;
        if (longest > MAX_EDGE) {
            float ratio = MAX_EDGE / (float) longest;
            scaled = Bitmap.createScaledBitmap(source,
                    Math.max(1, Math.round(source.getWidth() * ratio)),
                    Math.max(1, Math.round(source.getHeight() * ratio)), true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out);
        if (scaled != source) scaled.recycle();
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /** 相册结果转交给面板（宿主 Activity 的 onActivityResult 里调）。 */
    public static boolean deliver(Activity activity, int request, int result, android.content.Intent data) {
        return AvatarChooser.deliver(activity, request, result, data);
    }

    /** 从相册选的图可能很大，先按最长边 1024 采样解码，避免 OOM。 */
    public static Bitmap decode(Activity activity, android.net.Uri uri) {
        try (java.io.InputStream probe = activity.getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(probe, null, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / (options.inSampleSize * 2) >= 1024) options.inSampleSize *= 2;
            try (java.io.InputStream in = activity.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void Toast(Activity activity, String text) {
        android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_SHORT).show();
    }
}
