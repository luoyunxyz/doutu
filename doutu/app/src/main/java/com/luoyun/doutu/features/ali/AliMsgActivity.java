package com.luoyun.doutu.features.ali;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.io.InputStream;

/**
 * 支付宝「消息」页 + 「添加公众号」弹窗 —— 按真机截图重画：
 * 顶部 消息 / 搜索 / 通讯录 / +，全部会话卡（消息盒子行），底部五格导航；
 * 打开时弹出添加公众号表单（常用图标 / 头像 / 从相册获取、名称、消息内容、
 * 免打扰、消息置顶）。
 */
public final class AliMsgActivity extends Activity {
    private static final int BG = 0xffededed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(16), dp(10));
        TextView title = new TextView(this);
        title.setText("消息");
        title.setTextSize(24);
        title.setTextColor(0xff161722);
        title.getPaint().setFakeBoldText(true);
        bar.addView(title);
        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xffe3e5ea);
        sbg.setCornerRadius(dp(100));
        search.setBackground(sbg);
        search.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        slp.leftMargin = dp(12);
        slp.rightMargin = dp(10);
        TextView sIcon = new TextView(this);
        sIcon.setText("⌕");
        sIcon.setTextSize(16);
        sIcon.setTextColor(0xff8a8b90);
        search.addView(sIcon);
        TextView sHint = new TextView(this);
        sHint.setText("搜索好友等");
        sHint.setTextSize(14);
        sHint.setTextColor(0xff8a8b90);
        LinearLayout.LayoutParams shlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shlp.leftMargin = dp(6);
        search.addView(sHint, shlp);
        bar.addView(search, slp);
        TextView contacts = new TextView(this);
        contacts.setText("☰");
        contacts.setTextSize(20);
        contacts.setTextColor(0xff333333);
        bar.addView(contacts);
        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(22);
        plus.setTextColor(0xff333333);
        plus.setGravity(Gravity.CENTER);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(Color.TRANSPARENT);
        pbg.setStroke(dp(1), 0xff333333);
        pbg.setShape(GradientDrawable.OVAL);
        plus.setBackground(pbg);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(30), dp(30));
        plp.leftMargin = dp(12);
        bar.addView(plus, plp);
        page.addView(bar);

        // 全部会话卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Color.WHITE);
        cbg.setCornerRadius(dp(14));
        card.setBackground(cbg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(12);
        clp.rightMargin = dp(12);
        clp.topMargin = dp(6);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(10));
        TextView all = new TextView(this);
        all.setText("全部会话");
        all.setTextSize(15);
        all.setTextColor(0xff333333);
        header.addView(all, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String[] glyphs = {"@", "◧", "¥", "🎁"};
        for (String g : glyphs) {
            TextView tv = new TextView(this);
            tv.setText(g);
            tv.setTextSize(14);
            tv.setTextColor(0xff8a8b90);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.leftMargin = dp(14);
            header.addView(tv, glp);
        }
        card.addView(header);
        View line = new View(this);
        line.setBackgroundColor(0xfff0f0f0);
        card.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(14));
        TextView bell = new TextView(this);
        bell.setText("🔔");
        bell.setTextSize(18);
        bell.setGravity(Gravity.CENTER);
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(0xff1e7cf5);
        bbg.setShape(GradientDrawable.OVAL);
        bell.setBackground(bbg);
        row.addView(bell, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mlp.leftMargin = dp(12);
        TextView name = new TextView(this);
        name.setText("消息盒子");
        name.setTextSize(16);
        name.setTextColor(0xff161722);
        mid.addView(name);
        TextView sub = new TextView(this);
        sub.setText("蚂蚁财富 | 你的总资产收益已更新");
        sub.setTextSize(13);
        sub.setTextColor(0xff8a8b90);
        mid.addView(sub);
        row.addView(mid, mlp);
        TextView time = new TextView(this);
        time.setText("18:01");
        time.setTextSize(12);
        time.setTextColor(0xff9a9b9f);
        row.addView(time);
        card.addView(row);
        page.addView(card, clp);

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 底部五格
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Color.WHITE);
        nav.setPadding(0, dp(6), 0, dp(10));
        String[] labels = {"首页", "理财", "视频", "消息", "我的"};
        String[] glyph = {"支", "¥", "▷", "✉", "人"};
        for (int i = 0; i < 5; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            TextView ic = new TextView(this);
            ic.setText(glyph[i]);
            ic.setTextSize(16);
            ic.setGravity(Gravity.CENTER);
            ic.setTextColor(i == 3 ? Color.WHITE : 0xff2a4d8f);
            GradientDrawable ibg = new GradientDrawable();
            ibg.setColor(i == 3 ? 0xff1e7cf5 : Color.WHITE);
            ibg.setShape(GradientDrawable.OVAL);
            ibg.setStroke(dp(1), 0xff2a4d8f);
            ic.setBackground(ibg);
            item.addView(ic, new LinearLayout.LayoutParams(dp(30), dp(30)));
            TextView lb = new TextView(this);
            lb.setText(labels[i]);
            lb.setTextSize(11);
            lb.setTextColor(i == 3 ? 0xff1e7cf5 : 0xff666666);
            lb.setPadding(0, dp(2), 0, 0);
            item.addView(lb);
            nav.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.gravity = Gravity.BOTTOM;
        root.addView(nav, nlp);

        setContentView(root);
        contentPost();
    }

    private void contentPost() {
        getWindow().getDecorView().post(this::showGzhDialog);
    }

    /** 添加公众号表单。 */
    private void showGzhDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        box.setBackground(bg);
        box.setPadding(0, dp(16), 0, 0);

        LinearLayout icons = new LinearLayout(this);
        icons.setGravity(Gravity.TOP);
        icons.setPadding(dp(26), 0, dp(26), 0);
        icons.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));
        icons.addView(iconColumn("★", 46, "常用图标"), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ImageView avatar = new ImageView(this);
        Bitmap av = asset("qq_icons/qe_avatar.png");
        if (av != null) avatar.setImageBitmap(av);
        icons.addView(avatar, new LinearLayout.LayoutParams(dp(78), dp(78)));
        icons.addView(iconColumnAsset("wx_icons/gzh_photo.png", 46, "从相册获取"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        box.addView(icons);

        final EditText name = field(box, "名称:", "请输入公众号名称", dp(14));
        final EditText content = field(box, "消息内容:", "请输入消息内容", dp(6));

        box.addView(switchRow("免打扰", dp(6)));
        box.addView(switchRow("消息置顶", dp(6)));

        View line = new View(this);
        line.setBackgroundColor(0xfff0f0f0);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        llp.topMargin = dp(18);
        box.addView(line, llp);

        LinearLayout footer = new LinearLayout(this);
        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(15);
        cancel.setTextColor(0xff888888);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> dialog.dismiss());
        footer.addView(cancel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView ok = new TextView(this);
        ok.setText("确定");
        ok.setTextSize(15);
        ok.setTextColor(Color.BLACK);
        ok.setGravity(Gravity.CENTER);
        ok.setOnClickListener(v -> dialog.dismiss());
        footer.addView(ok, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        box.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        dialog.setContentView(box);
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.setDimAmount(0.35f);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.width = dp(292);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            win.setAttributes(lp);
        }
        dialog.show();
    }

    private EditText field(LinearLayout parent, String label, String hint, int topMargin) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(4));
        bg.setStroke(dp(1), 0xffd9d9d9);
        box.setBackground(bg);
        box.setPadding(dp(10), 0, dp(8), 0);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        blp.leftMargin = dp(30);
        blp.rightMargin = dp(30);
        blp.topMargin = topMargin;
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setTextColor(0xff333333);
        box.addView(tv);
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setBackground(null);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        ilp.leftMargin = dp(8);
        box.addView(input, ilp);
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
        parent.addView(box, blp);
        return input;
    }

    private LinearLayout switchRow(String text, int topMargin) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(4));
        bg.setStroke(dp(1), 0xffd9d9d9);
        row.setBackground(bg);
        row.setPadding(dp(10), 0, dp(8), 0);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(0xff333333);
        row.addView(tv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        Switch sw = new Switch(this);
        sw.setScaleX(0.75f);
        sw.setScaleY(0.75f);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(0xffffffff));
        sw.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0xffffda43, 0xffe5e5e5}));
        row.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        blp.leftMargin = dp(30);
        blp.rightMargin = dp(30);
        blp.topMargin = topMargin;
        row.setLayoutParams(blp);
        return row;
    }

    private View iconColumn(String glyph, int size, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView ic = new TextView(this);
        ic.setText(glyph);
        ic.setTextSize(24);
        ic.setTextColor(0xff333333);
        ic.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xfff0f0f0);
        bg.setShape(GradientDrawable.OVAL);
        ic.setBackground(bg);
        col.addView(ic, new LinearLayout.LayoutParams(dp(size), dp(size)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setTextColor(0xff333333);
        tv.setPadding(0, dp(6), 0, 0);
        col.addView(tv);
        return col;
    }

    private View iconColumnAsset(String path, int size, String label) {
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
