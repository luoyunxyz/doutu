package com.luoyun.doutu.features.qq;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.InputStream;

/**
 * QQ「发红包」页 —— 照 歪点点 4.0.5.39 真机截图重画：
 * 浅灰页头（返回 / 发红包 / 黄色「发送」）+ 红包金额卡（￥ 0）+ 祝福语
 * + 发送人一行（头像 / 昵称 / 切换发送人）。
 *
 * <p>金额、祝福语、发送人都能点开改；「发送」校验金额后提示并退出。
 */
public final class QqRedPacketActivity extends Activity {
    private String amount = "0";
    private String blessing = "恭喜发财";
    private String sender = "与你隔离";
    private RedView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(0xfff7f7f7);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        view = new RedView();
        setContentView(view);
    }

    private final class RedView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect src = new Rect();
        private final RectF dst = new RectF();
        private final Bitmap avatar;

        RedView() {
            super(QqRedPacketActivity.this);
            Bitmap b = null;
            try (InputStream in = getContext().getAssets().open("qq_icons/rp_avatar.png")) {
                b = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) { }
            avatar = b;
            setBackgroundColor(0xfff7f7f7);
        }

        private void text(Canvas c, String s, float x, float baseline, float size, int color, boolean bold) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            c.drawText(s, x, baseline, p);
        }

        private float textWidth(String s, float size, boolean bold) {
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            p.setTextSize(size);
            return p.measureText(s);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.save();
            c.scale(getWidth() / 1080f, getHeight() / 2388f);
            c.drawColor(0xfff7f7f7);

            // 页头
            p.setColor(0xffededed);
            c.drawRect(0, 0, 1080, 283, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4.5f);
            p.setColor(0xff555555);
            c.drawLine(60, 190, 42, 212, p);
            c.drawLine(42, 212, 60, 234, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xff1a1a1a);
            p.setTextSize(42);
            p.setTypeface(Typeface.DEFAULT);
            c.drawText("发红包", 540 - p.measureText("发红包") / 2f, 231, p);
            // 发送按钮
            p.setColor(0xffffd84d);
            c.drawRoundRect(889, 143, 1017, 241, 12, 12, p);
            text(c, "发送", 953 - textWidth("发送", 33, false) / 2f, 205, 33, 0xff1a1a1a, false);

            // 金额卡
            p.setColor(Color.WHITE);
            c.drawRect(0, 283, 1080, 665, p);
            text(c, "红包金额", 96, 390, 33, 0xff333333, false);
            text(c, "￥", 96, 600, 100, 0xff1a1a1a, false);
            p.setColor(0xff999999);
            p.setTextSize(110);
            p.setTypeface(Typeface.DEFAULT);
            c.drawText(amount, 232, 600, p);

            // 祝福语
            p.setColor(0xfff7f7f7);
            c.drawRect(0, 665, 1080, 696, p);
            p.setColor(Color.WHITE);
            c.drawRect(0, 696, 1080, 997, p);
            text(c, blessing, 24, 790, 36, 0xff333333, false);

            // 发送人
            p.setColor(0xfff7f7f7);
            c.drawRect(0, 997, 1080, 1027, p);
            p.setColor(Color.WHITE);
            c.drawRect(0, 1027, 1080, 1208, p);
            if (avatar != null) {
                src.set(0, 0, avatar.getWidth(), avatar.getHeight());
                dst.set(45, 1060, 166, 1181);
                p.setFilterBitmap(true);
                c.drawBitmap(avatar, src, dst, p);
            }
            text(c, sender, 205, 1155, 36, 0xff333333, false);
            float w = textWidth("切换发送人", 36, false);
            text(c, "切换发送人", 1050 - w, 1155, 36, 0xff333333, false);

            // 下半区
            p.setColor(0xfff7f7f7);
            c.drawRect(0, 1208, 1080, 2388, p);
            c.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float x = e.getX() * 1080f / Math.max(1, getWidth());
            float y = e.getY() * 2388f / Math.max(1, getHeight());
            if (y < 283) {
                if (x > 850) send();
                else if (x < 100) finish();
                return true;
            }
            if (y > 283 && y < 665) {
                edit("红包金额", "0".equals(amount) ? "" : amount, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, value -> {
                    amount = value.trim().isEmpty() ? "0" : value.trim();
                    invalidate();
                });
                return true;
            }
            if (y > 696 && y < 997) {
                edit("祝福语", blessing, InputType.TYPE_CLASS_TEXT, value -> {
                    blessing = value.trim().isEmpty() ? "恭喜发财" : value.trim();
                    invalidate();
                });
                return true;
            }
            if (y > 1027 && y < 1208 && x > 700) {
                edit("发送人", sender, InputType.TYPE_CLASS_TEXT, value -> {
                    sender = value.trim().isEmpty() ? "与你隔离" : value.trim();
                    invalidate();
                });
            }
            return true;
        }

        private void send() {
            try {
                if (Double.parseDouble(amount) <= 0) {
                    Toast.makeText(QqRedPacketActivity.this, "请输入红包金额", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (Exception e) {
                Toast.makeText(QqRedPacketActivity.this, "请输入红包金额", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(QqRedPacketActivity.this, "红包已发送", Toast.LENGTH_SHORT).show();
            finish();
        }

        private void edit(String title, String current, int inputType, ValueSink sink) {
            EditText input = new EditText(QqRedPacketActivity.this);
            input.setInputType(inputType);
            input.setText(current);
            input.setSelection(input.getText().length());
            LinearLayout box = new LinearLayout(QqRedPacketActivity.this);
            box.setPadding(48, 24, 48, 0);
            box.addView(input);
            new AlertDialog.Builder(QqRedPacketActivity.this)
                    .setTitle(title)
                    .setView(box)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确定", (d, w) -> sink.apply(input.getText().toString()))
                    .show();
        }
    }

    private interface ValueSink {
        void apply(String value);
    }
}
