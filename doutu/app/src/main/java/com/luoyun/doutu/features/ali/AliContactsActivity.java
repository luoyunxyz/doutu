package com.luoyun.doutu.features.ali;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
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
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 支付宝「通讯录」—— 按真机 activity_ali_contacts.xml + item_ali_contact(_header) 重画：
 * 灰底 44dp 顶栏（返回 / 通讯录 / 添加朋友），搜索框，白色圆角卡（新的朋友…专属客服），
 * 下面按首字母分组的联系人 + 右侧字母索引。
 */
public final class AliContactsActivity extends Activity {
    private static final int BG = 0xfff5f5f5;
    private LinearLayout list;

    private static final String[] TOP_NAMES = {
            "新的朋友", "群聊", "生活号", "最近转账联系人", "智能服务", "标签", "专属客服"
    };
    private static final String[] TOP_ICONS = {
            "new_friend.png", "group.png", "shh.png", "early.png", "smart.png", "tag.png", "kefu.png"
    };

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
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        ImageView back = new ImageView(this);
        back.setImageBitmap(asset("ali_icons/back.png"));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = dp(9);
        bar.addView(back, blp);
        TextView title = new TextView(this);
        title.setText("通讯录");
        title.setTextSize(18);
        title.setTextColor(0xff333333);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(12);
        bar.addView(title, tlp);
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView add = new TextView(this);
        add.setText("添加朋友");
        add.setTextSize(16);
        add.setTextColor(0xff333333);
        add.setPadding(dp(10), 0, dp(16), 0);
        add.setOnClickListener(v -> showAddDialog());
        bar.addView(add);
        page.addView(bar);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(page);

        // 右侧字母索引
        TextView index = new TextView(this);
        index.setText("A\nB\nC\nD\nE\nF\nG\nH\nI\nJ\nK\nL\nM\nN\nO\nP\nQ\nR\nS\nT\nU\nV\nW\nX\nY\nZ");
        index.setTextSize(9);
        index.setTextColor(0xff666666);
        index.setGravity(Gravity.CENTER);
        index.setLineSpacing(dp(2), 1f);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(13), ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        ilp.rightMargin = dp(5);
        root.addView(index, ilp);

        setContentView(root);
        render();
    }

    private void render() {
        list.removeAllViews();

        // 搜索
        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Color.WHITE);
        sbg.setCornerRadius(dp(100));
        search.setBackground(sbg);
        search.setPadding(dp(15), 0, 0, 0);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        slp.leftMargin = dp(8);
        slp.rightMargin = dp(8);
        slp.topMargin = dp(9);
        ImageView icon = new ImageView(this);
        icon.setImageBitmap(asset("ali_icons/search.png"));
        search.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView hint = new TextView(this);
        hint.setText("搜索");
        hint.setTextSize(15);
        hint.setTextColor(0xff9d9d9d);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = dp(6);
        search.addView(hint, hlp);
        list.addView(search, slp);

        // 顶部白色圆角卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Color.WHITE);
        cbg.setCornerRadius(dp(12));
        card.setBackground(cbg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(8);
        clp.rightMargin = dp(8);
        clp.topMargin = dp(10);
        for (int i = 0; i < TOP_NAMES.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, 0, 0);
            ImageView ic = new ImageView(this);
            ic.setImageBitmap(asset("ali_icons/" + TOP_ICONS[i]));
            row.addView(ic, new LinearLayout.LayoutParams(dp(38), dp(38)));
            TextView name = new TextView(this);
            name.setText(TOP_NAMES[i]);
            name.setTextSize(16);
            name.setTextColor(0xff333333);
            name.getPaint().setFakeBoldText(true);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = dp(12);
            row.addView(name, nlp);
            card.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        }
        list.addView(card, clp);

        // 联系人分组
        Bitmap avatar = asset("ali_icons/avatar.png");
        Bitmap circle = avatar == null ? null : circle(avatar);
        for (Map.Entry<String, List<String>> e : AliContactStore.grouped(this).entrySet()) {
            TextView letter = new TextView(this);
            letter.setText(e.getKey());
            letter.setTextSize(12);
            letter.setTextColor(0xff999999);
            letter.setGravity(Gravity.CENTER_VERTICAL);
            letter.setPadding(dp(16), 0, 0, 0);
            list.addView(letter, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(25)));
            for (String name : e.getValue()) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                GradientDrawable rbg = new GradientDrawable();
                rbg.setColor(Color.WHITE);
                rbg.setCornerRadius(dp(12));
                row.setBackground(rbg);
                row.setPadding(dp(16), 0, 0, 0);
                ImageView iv = new ImageView(this);
                if (circle != null) iv.setImageBitmap(circle);
                row.addView(iv, new LinearLayout.LayoutParams(dp(38), dp(38)));
                TextView tv = new TextView(this);
                tv.setText(name);
                tv.setTextSize(14);
                tv.setTextColor(0xff333333);
                tv.getPaint().setFakeBoldText(true);
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                nlp.leftMargin = dp(12);
                row.addView(tv, nlp);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
                rlp.leftMargin = dp(8);
                rlp.rightMargin = dp(8);
                list.addView(row, rlp);
            }
        }
    }

    private void showAddDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        box.setBackground(bg);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView title = new TextView(this);
        title.setText("添加朋友");
        title.setTextSize(16);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        LinearLayout field = new LinearLayout(this);
        field.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable fbg = new GradientDrawable();
        fbg.setColor(0xfff7f7f7);
        fbg.setCornerRadius(dp(6));
        field.setBackground(fbg);
        field.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        flp.leftMargin = dp(30);
        flp.rightMargin = dp(30);
        flp.topMargin = dp(10);
        TextView label = new TextView(this);
        label.setText("昵称:");
        label.setTextSize(13);
        label.setTextColor(0xff333333);
        field.addView(label);
        final EditText input = new EditText(this);
        input.setHint("请输入昵称");
        input.setTextSize(13);
        input.setSingleLine(true);
        input.setBackground(null);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        ilp.leftMargin = dp(10);
        field.addView(input, ilp);
        box.addView(field, flp);

        LinearLayout stranger = new LinearLayout(this);
        stranger.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable stbg = new GradientDrawable();
        stbg.setColor(0xfff7f7f7);
        stbg.setCornerRadius(dp(6));
        stranger.setBackground(stbg);
        stranger.setPadding(dp(10), 0, dp(10), 0);
        TextView stLabel = new TextView(this);
        stLabel.setText("陌生人");
        stLabel.setTextSize(13);
        stLabel.setTextColor(0xff333333);
        stranger.addView(stLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        Switch sw = new Switch(this);
        sw.setScaleX(0.75f);
        sw.setScaleY(0.75f);
        stranger.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        stlp.leftMargin = dp(30);
        stlp.rightMargin = dp(30);
        stlp.topMargin = dp(15);
        box.addView(stranger, stlp);

        View line = new View(this);
        line.setBackgroundColor(0xfff5f5f5);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        llp.topMargin = dp(20);
        box.addView(line, llp);

        LinearLayout footer = new LinearLayout(this);
        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(13);
        cancel.setTextColor(0xff888888);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> dialog.dismiss());
        footer.addView(cancel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ok = new TextView(this);
        ok.setText("确定");
        ok.setTextSize(13);
        ok.setTextColor(Color.BLACK);
        ok.setGravity(Gravity.CENTER);
        ok.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = "朋友";
            AliContactStore.add(AliContactsActivity.this, name);
            render();
            dialog.dismiss();
        });
        footer.addView(ok, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        dialog.setContentView(box);
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.setDimAmount(0.35f);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.width = dp(300);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            win.setAttributes(lp);
        }
        dialog.show();
    }

    private Bitmap circle(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        p.setShader(shader);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
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
