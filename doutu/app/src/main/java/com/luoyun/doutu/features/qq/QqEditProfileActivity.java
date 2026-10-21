package com.luoyun.doutu.features.qq;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * QQ「编辑个人资料」页（工具页「在线状态」入口）—— 照 歪点点 4.0.5.39 真机截图重画：
 * 返回 / 编辑个人资料 / 完成、头像行（自动获取 / 头像 / 从相册获取）、
 * 昵称与 QQ 号输入框、「Q我吧 ›」、其他用户 → 新建QQ账户。
 */
public final class QqEditProfileActivity extends Activity {
    private EditText name;
    private EditText qq;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.WHITE);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(build());
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // 标题栏
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView title = new TextView(this);
        title.setText("编辑个人资料");
        title.setTextSize(18);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(16);
        done.setTextColor(0xff333333);
        done.setGravity(Gravity.CENTER);
        done.setPadding(0, 0, dp(12), 0);
        done.setOnClickListener(v -> {
            getSharedPreferences("doutu_tools", 0).edit()
                    .putString("qq_name", name.getText().toString().trim())
                    .putString("qq_number", qq.getText().toString().trim())
                    .putString("qq_status", status.getText().toString())
                    .apply();
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
        bar.addView(done, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(bar);

        // 头像行
        LinearLayout icons = new LinearLayout(this);
        icons.setGravity(Gravity.TOP);
        icons.setPadding(dp(24), dp(10), dp(24), 0);
        icons.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));
        icons.addView(iconCell("qq_icons/qe_auto.png", 30, "自动获取"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ImageView avatar = new ImageView(this);
        avatar.setImageBitmap(asset("qq_icons/qe_avatar.png"));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(34), dp(38));
        alp.topMargin = dp(4);
        icons.addView(avatar, alp);
        icons.addView(iconCell("qq_icons/qe_photo.png", 30, "从相册获取"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(icons);

        // 昵称 / QQ 号 / 状态
        name = field(root, "微笑似年华々", 15);
        qq = field(root, "123456", 25);
        LinearLayout statusRow = rowBox(root, 28);
        status = new TextView(this);
        status.setText("Q我吧");
        status.setTextSize(15);
        status.setTextColor(0xff333333);
        statusRow.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView chev = new TextView(this);
        chev.setText("›");
        chev.setTextSize(20);
        chev.setTextColor(0xff999999);
        statusRow.addView(chev);
        statusRow.setOnClickListener(v -> pickStatus());

        // 其他用户
        TextView other = new TextView(this);
        other.setText("其他用户");
        other.setTextSize(15);
        other.setTextColor(0xff333333);
        other.setGravity(Gravity.CENTER_VERTICAL);
        other.setPadding(dp(12), 0, 0, 0);
        other.setBackgroundColor(0xfff7f7f7);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        olp.topMargin = dp(14);
        root.addView(other, olp);

        // 新建QQ账户
        LinearLayout add = new LinearLayout(this);
        add.setGravity(Gravity.CENTER);
        add.setPadding(0, dp(14), 0, 0);
        ImageView plus = new ImageView(this);
        plus.setImageBitmap(circlePlus());
        add.addView(plus, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView addText = new TextView(this);
        addText.setText("新建QQ账户");
        addText.setTextSize(15);
        addText.setTextColor(0xff333333);
        addText.setPadding(dp(8), 0, 0, 0);
        add.addView(addText);
        add.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("新建QQ账户")
                .setMessage("输入一个新的 QQ 昵称即可创建。")
                .setPositiveButton("知道了", null)
                .show());
        root.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        return root;
    }

    private View iconCell(String path, int size, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView img = new ImageView(this);
        img.setImageBitmap(asset(path));
        col.addView(img, new LinearLayout.LayoutParams(dp(size), dp(size)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setTextColor(0xff333333);
        tv.setPadding(0, dp(6), 0, 0);
        col.addView(tv);
        return col;
    }

    private EditText field(LinearLayout root, String value, int topMargin) {
        LinearLayout box = rowBox(root, topMargin);
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextSize(15);
        input.setTextColor(0xff333333);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setPadding(0, 0, 0, 0);
        box.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        // 清空按钮
        TextView clear = new TextView(this);
        clear.setText("✕");
        clear.setTextSize(10);
        clear.setTextColor(Color.WHITE);
        clear.setGravity(Gravity.CENTER);
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(0xffc9c9c9);
        cg.setShape(GradientDrawable.OVAL);
        clear.setBackground(cg);
        clear.setOnClickListener(v -> input.setText(""));
        box.addView(clear, new LinearLayout.LayoutParams(dp(16), dp(16)));
        return input;
    }

    private LinearLayout rowBox(LinearLayout root, int topMargin) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), 0, dp(10), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(6));
        g.setStroke(dp(1), 0xffd9d9d9);
        box.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(190), dp(38));
        lp.leftMargin = dp(80);
        lp.topMargin = dp(topMargin);
        box.setLayoutParams(lp);
        root.addView(box);
        return box;
    }

    private void pickStatus() {
        final String[] options = {"Q我吧", "在线", "忙碌", "离开", "隐身"};
        new AlertDialog.Builder(this)
                .setTitle("状态")
                .setItems(options, (d, which) -> status.setText(options[which]))
                .show();
    }

    private Bitmap circlePlus() {
        int s = 88;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xffffd84d);
        c.drawCircle(s / 2f, s / 2f, s / 2f - 2, p);
        p.setColor(0xff333333);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(4);
        c.drawCircle(s / 2f, s / 2f, s / 2f - 2, p);
        p.setStyle(android.graphics.Paint.Style.FILL);
        p.setStrokeWidth(6);
        c.drawLine(s / 2f - 16, s / 2f, s / 2f + 16, s / 2f, p);
        c.drawLine(s / 2f, s / 2f - 16, s / 2f, s / 2f + 16, p);
        return b;
    }

    private Bitmap asset(String path) {
        try (InputStream in = getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
