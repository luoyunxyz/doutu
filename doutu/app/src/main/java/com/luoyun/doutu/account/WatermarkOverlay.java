package com.luoyun.doutu.account;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * 未激活会员时铺满整页的斜向水印。
 *
 * 实现要点：
 *  - 挂在 {@code android.R.id.content} 的最上层，因此对**所有**页面生效
 *    （包括各个子 Activity，由 DoutuApp 的 ActivityLifecycleCallbacks 统一挂载）；
 *  - {@link #onTouchEvent} 恒返回 false，触摸事件会继续往下派发给真正的页面，
 *    水印只挡视线、不挡操作；
 *  - 描边 + 填充两层绘制，白底和深色底上都看得清；
 *  - 文案来自服务端配置（sys_config.watermark_overlay_text），默认「开通会员去除水印」。
 */
public final class WatermarkOverlay extends View {

    private static final Object TAG = new Object();

    private static final float ANGLE = -30f;      // 斜向角度
    private static final int FILL_COLOR = 0x5AFFFFFF;   // 白色半透明填充
    private static final int STROKE_COLOR = 0x55000000;  // 深色描边，保证浅底可见

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text = "开通会员去除水印";

    public WatermarkOverlay(Context context) {
        super(context);
        fill.setColor(FILL_COLOR);
        fill.setTypeface(Typeface.DEFAULT_BOLD);
        fill.setTextAlign(Paint.Align.LEFT);
        stroke.setColor(STROKE_COLOR);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setTypeface(Typeface.DEFAULT_BOLD);
        stroke.setTextAlign(Paint.Align.LEFT);
        setClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        text = Session.overlayText();
    }

    /**
     * 按当前会话状态挂载或移除水印。每次 Activity 可见时调用一次即可。
     */
    public static void attachIfNeeded(Activity activity) {
        if (activity == null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        // 登录/注册页不盖水印：那里还没有账号，盖住只会让人看不清表单。
        // 「邀请码重置密码」同理 —— 用户正因为登不上才来这一步，更不该被水印糊住两个输入框。
        boolean skip = activity instanceof LoginActivity
                || activity instanceof ResetByInviteActivity;
        View existing = content.findViewWithTag(TAG);
        if (skip || Session.isActivated()) {
            if (existing != null) content.removeView(existing);
            return;
        }
        if (existing instanceof WatermarkOverlay) {
            ((WatermarkOverlay) existing).text = Session.overlayText();
            existing.invalidate();
            return;
        }
        WatermarkOverlay overlay = new WatermarkOverlay(activity);
        overlay.setTag(TAG);
        content.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 不吃事件：让点按落到下面的真实页面上
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float size = Math.max(28f, w * 0.055f);
        fill.setTextSize(size);
        stroke.setTextSize(size);
        stroke.setStrokeWidth(Math.max(1.6f, size * 0.045f));

        float textWidth = fill.measureText(text);
        float stepX = textWidth + size * 1.6f;
        float stepY = size * 3.2f;

        // 旋转后要覆盖整块画布：按对角线长度铺网格
        float diagonal = (float) Math.sqrt((double) (w * w + h * h));
        int rows = (int) (diagonal / stepY) + 2;
        int cols = (int) (diagonal / stepX) + 2;

        canvas.save();
        canvas.rotate(ANGLE, w / 2f, h / 2f);
        float startX = w / 2f - cols * stepX / 2f;
        float startY = h / 2f - rows * stepY / 2f;
        for (int r = 0; r < rows; r++) {
            // 隔行错开半格，看起来更像均匀铺满的水印层
            float offset = (r % 2 == 0) ? 0f : stepX / 2f;
            for (int c = 0; c < cols; c++) {
                float x = startX + c * stepX + offset;
                float y = startY + r * stepY;
                canvas.drawText(text, x, y, stroke);
                canvas.drawText(text, x, y, fill);
            }
        }
        canvas.restore();
    }

    /** 便于调试：当前遮罩文案。 */
    public String overlayText() {
        return text;
    }

    /** 水印层的填充色（供外部判断是否已挂载）。 */
    public static int overlayFillColor() {
        return FILL_COLOR;
    }
}
