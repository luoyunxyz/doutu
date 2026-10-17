package com.luoyun.doutu.features.shutdown;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 「模拟没电关机」—— 娱乐页 → 更多玩法 → 社恐神器 → 第 2 条（原包路由 `/shutdown`，显示名「模拟关机」）。
 *
 * <p>这一页是**照着逆向量化结论写的，不是照着原包截图抄的** —— 原包那个页面组件 `Pcb`
 * （class id 3823）在反编译产物里没有类声明，widget 树拿不到（见
 * `00_资料包/娱乐页_模拟没电关机/README.md` 第 5 节与 `05_反汇编/08_为什么拿不到页面代码.txt`）。
 * 能确证的是这些，所以实现就落在这些上面：
 *
 * <ul>
 *   <li><b>零资源</b>：整个功能没有图片 / 音频 / 视频 / 额外文案（全量资源扫描 0 命中），
 *       所以它必然是**纯代码绘制的全屏画面** —— 这里画的是纯黑底 + 中间那个没电的电池。</li>
 *   <li><b>零数据库、零网络</b>：不读写任何表，也没有请求。</li>
 *   <li><b>旁证</b>：原包打包了 `wakelock_plus`（保持屏幕常亮），且全包只有一处
 *       `SystemChrome.setEnabledSystemUIMode(SystemUiMode.manual)`（归属未确认）——
 *       一个指向「屏幕别自己灭」，一个指向「把系统栏藏起来」，正是「假装手机没电」需要的样子，
 *       两条都按上。</li>
 * </ul>
 *
 * <p>中间那个电池是**画出来的**，不是图片：资料包的 Remix 子集字体里 118 个字形全渲染看过，
 * 只有 `U+F116`「实心电池 + 闪电」（那是娱乐页那一格的入口图标，语义是充电），
 * 没有「没电」那一个；本项目 `res/drawable-nodpi` 与 `assets/` 里也没有电池素材。
 * 所以这里用和娱乐页同一套画法（Path/Canvas）画一个**空电池轮廓**：圆角矩形 + 右侧正极小凸起，
 * 里头什么都不填 —— 没电就该是空的。它慢慢一明一暗地闪（1.5 秒一个来回）。
 *
 * <p>出去的办法：点一下屏幕，或者按返回。原包里它是一条普通路由，返回键本来就能退。
 * 进来头两秒会在底部压一行很暗的提示，然后自动消失。
 */
public final class ShutdownActivity extends Activity {
    /** 底部那行提示显示多久（毫秒）。 */
    private static final long HINT_MS = 2400;
    /** 电池明暗一个来回的时长（毫秒）——慢一点才像「快没电了」，闪太快像通知。 */
    private static final long BLINK_MS = 1500;

    public static void open(Activity activity) {
        activity.startActivity(new Intent(activity, ShutdownActivity.class));
        // 和别的内页一致：跳过系统转场，切进来就是一块黑
        activity.overridePendingTransition(0, 0);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 「没电了」的样子：屏幕不许自己灭（原包带 wakelock_plus）
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // 中间：没电的电池，慢慢明暗闪
        BatteryIconView battery = new BatteryIconView(this);
        int iconWidth = Math.round(150 * getResources().getDisplayMetrics().density);
        int iconHeight = Math.round(72 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams batteryLp = new FrameLayout.LayoutParams(iconWidth, iconHeight);
        batteryLp.gravity = Gravity.CENTER;
        root.addView(battery, batteryLp);

        ObjectAnimator blink = ObjectAnimator.ofFloat(battery, "alpha", 0.10f, 0.80f);
        blink.setDuration(BLINK_MS);
        blink.setRepeatMode(ValueAnimator.REVERSE);
        blink.setRepeatCount(ValueAnimator.INFINITE);
        blink.setInterpolator(new AccelerateDecelerateInterpolator());
        blink.start();

        TextView hint = new TextView(this);
        hint.setText("点一下屏幕回到豆图");
        hint.setTextSize(13f);
        hint.setTextColor(0xFF3A3A3A);          // 很暗：不影响「黑屏」的观感
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hintLp.bottomMargin = Math.round(56 * getResources().getDisplayMetrics().density);
        root.addView(hint, hintLp);

        root.setOnClickListener(v -> finish());
        setContentView(root);

        hideSystemBars();
        // 两秒后把提示也收掉，剩下的就是「一块黑屏 + 一颗在闪的没电电池」
        root.postDelayed(() -> hint.setVisibility(View.GONE), HINT_MS);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 用户从边缘划出系统栏之后会掉焦点，回来时再藏一次（immersive sticky 的那套手工版）
        if (hasFocus) hideSystemBars();
    }

    /** 把状态栏和导航栏都藏起来：手机「关机」的时候不该还挂着电量和时间。 */
    private void hideSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            return;
        }
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    /**
     * 没电的电池：空心的圆角矩形（电池身）+ 右边一个小凸起（正极）。
     *
     * <p>画法和娱乐页那些图标一致（同一套 Canvas/Path 路数），所以不需要任何图片资源，
     * 也不会在别的分辨率上糊。里面什么都不填 —— 空的就是「没电」。
     */
    private static final class BatteryIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BatteryIconView(Context context) {
            super(context);
            paint.setColor(Color.WHITE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float stroke = h * 0.10f;
            paint.setStrokeWidth(stroke);

            // 电池头的宽度：身体右边留出这么多
            float nubW = h * 0.16f;
            float bodyLeft = stroke * 0.5f;
            float bodyRight = w - nubW - h * 0.10f;
            float top = stroke * 0.5f;
            float bottom = h - stroke * 0.5f;
            float radius = Math.min(h * 0.30f, (bottom - top) * 0.38f);

            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(new RectF(bodyLeft, top, bodyRight, bottom), radius, radius, paint);

            // 正极：右边那个小圆角块，竖直居中
            paint.setStyle(Paint.Style.FILL);
            float nubTop = h * 0.34f;
            float nubBottom = h * 0.66f;
            float nubRadius = nubW * 0.45f;
            canvas.drawRoundRect(new RectF(bodyRight + h * 0.05f, nubTop, bodyRight + nubW, nubBottom),
                    nubRadius, nubRadius, paint);
        }
    }
}
