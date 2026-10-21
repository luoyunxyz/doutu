package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 「群组设置」页 —— 按真机截图重画：
 * 成员区（群主 + 通讯录 + 自动获取）、查看真实成员图、群聊名称、群聊人数、5 个开关、
 * 设置聊天背景 / 清空聊天内容 / 设置群内未读消息。
 * 删除成员不做独立按钮：长按成员头像即可删除。
 */
public final class WxGroupSettingsActivity extends Activity {
    private static final String[] NAMES = {"北方佳人.", "明晨紫月", "淡紫姑娘！", "云边小卖部",
            "日落收藏家", "云朵收藏家", "青萍", "水星记", "星之笨比", "林克"};

    private final Random random = new Random();
    private final List<String> members = new ArrayList<>();
    private LinearLayout memberRow;
    private LinearLayout countRow;
    private LinearLayout nameRow;
    private String groupName = "再歪一点";
    private int displayCount = 100;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffffffff);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        // 顶栏
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(Color.WHITE);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(dp(48), dp(56)));
        TextView title = new TextView(this);
        title.setText("群组设置");
        title.setTextSize(16);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(14);
        done.setTextColor(0xff333333);
        done.setGravity(Gravity.CENTER);
        GradientDrawable dbg = new GradientDrawable();
        dbg.setColor(0xffffda43);
        dbg.setCornerRadius(dp(6));
        done.setBackground(dbg);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(58), dp(30));
        dlp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        dlp.rightMargin = dp(12);
        done.setOnClickListener(v -> finish());
        bar.addView(done, dlp);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);

        // 成员区
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        memberRow = new LinearLayout(this);
        memberRow.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(memberRow);
        body.addView(card);

        renderMembers();

        body.addView(row("查看真实成员图", "›"));
        body.addView(divider());
        nameRow = rowClickable("群聊名称", this::editName);
        body.addView(nameRow);
        body.addView(divider());
        countRow = rowClickable("群聊人数(显示人数)", this::editCount);
        body.addView(countRow);
        body.addView(divider());
        body.addView(toggle("置顶聊天"));
        body.addView(toggle("消息免打扰"));
        body.addView(toggle("使用听筒播放"));
        body.addView(toggle("显示企业图标"));
        body.addView(toggle("是否显示群昵称"));
        body.addView(divider());
        body.addView(row("设置聊天背景", "›"));
        body.addView(row("清空聊天内容", "›"));
        body.addView(row("设置群内未读消息", "›"));

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        updateCountText();
        updateNameText();
    }

    private void renderMembers() {
        memberRow.removeAllViews();
        memberRow.addView(memberCell("微笑似…", true));
        memberRow.addView(actionCell("📇", "通讯录", v -> pickContacts()));
        memberRow.addView(actionCell("👤+", "自动获取", v -> autoGenerate()));
        // 「删除成员」按钮已按需求去掉；删除方式改为长按成员头像
    }

    private void updateNameText() {
        if (nameRow == null) return;
        TextView value = (TextView) nameRow.getChildAt(1);
        value.setText(groupName);
    }

    private void updateCountText() {
        if (countRow == null) return;
        TextView value = (TextView) countRow.getChildAt(1);
        value.setText(String.valueOf(displayCount));
    }

    private LinearLayout memberCell(String name, boolean owner) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView avatar = new TextView(this);
        avatar.setText(name.substring(0, 1));
        avatar.setTextSize(14);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xff9aa7b0);
        bg.setCornerRadius(dp(6));
        avatar.setBackground(bg);
        box.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView label = new TextView(this);
        label.setText(name);
        label.setTextSize(10);
        label.setTextColor(0xff888888);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(6), 0, 0);
        box.addView(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT);
        box.setLayoutParams(lp);
        if (!owner) {
            avatar.setOnLongClickListener(v -> {
                memberRow.removeView(box);
                members.remove(name);
                Toast.makeText(this, "已删除成员：" + name, Toast.LENGTH_SHORT).show();
                return true;
            });
        }
        return box;
    }

    private LinearLayout actionCell(String icon, String label, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setOnClickListener(click);
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(20);
        iconView.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1), 0xffe0e0e0);
        bg.setCornerRadius(dp(6));
        iconView.setBackground(bg);
        box.addView(iconView, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(10);
        text.setTextColor(0xff888888);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(6), 0, 0);
        box.addView(text);
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void autoGenerate() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("生成多少个成员");
        new AlertDialog.Builder(this)
                .setTitle("自动获取")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String text = input.getText().toString().trim();
                    int n = text.isEmpty() ? 0 : Integer.parseInt(text);
                    for (int i = 0; i < n; i++) {
                        String name = NAMES[random.nextInt(NAMES.length)];
                        members.add(name);
                        memberRow.addView(memberCell(name, false));
                    }
                    Toast.makeText(this, "已生成 " + n + " 个成员", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickContacts() {
        final String[] pool = NAMES;
        boolean[] checked = new boolean[pool.length];
        new AlertDialog.Builder(this)
                .setTitle("从通讯录选择")
                .setMultiChoiceItems(pool, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("确定", (d, w) -> {
                    for (int i = 0; i < pool.length; i++) {
                        if (!checked[i]) continue;
                        members.add(pool[i]);
                        memberRow.addView(memberCell(pool[i], false));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void editName() {
        EditText input = new EditText(this);
        input.setText(groupName);
        new AlertDialog.Builder(this)
                .setTitle("群聊名称")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    groupName = input.getText().toString().trim();
                    updateNameText();
                    Toast.makeText(this, "群名已改为：" + groupName, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void editCount() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(displayCount));
        new AlertDialog.Builder(this)
                .setTitle("群聊人数")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    try {
                        displayCount = Integer.parseInt(input.getText().toString().trim());
                    } catch (Exception ignored) { }
                    updateCountText();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private View row(String name, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(15);
        n.setTextColor(0xff333333);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(0xff999999);
        row.addView(v);
        return row;
    }

    private LinearLayout rowClickable(String name, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(15);
        n.setTextColor(0xff333333);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText("");
        v.setTextSize(14);
        v.setTextColor(0xff333333);
        row.addView(v);
        row.setOnClickListener(x -> action.run());
        return row;
    }

    private View toggle(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView n = new TextView(this);
        n.setText(label);
        n.setTextSize(15);
        n.setTextColor(0xff333333);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final boolean[] on = {false};
        final FrameLayout sw = new FrameLayout(this);
        final View track = new View(this);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(0xffe0e0e0);
        tbg.setCornerRadius(dp(14));
        track.setBackground(tbg);
        sw.addView(track, new FrameLayout.LayoutParams(dp(46), dp(28)));
        final View knob = new View(this);
        GradientDrawable kbg = new GradientDrawable();
        kbg.setColor(Color.WHITE);
        kbg.setShape(GradientDrawable.OVAL);
        knob.setBackground(kbg);
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(dp(24), dp(24));
        klp.leftMargin = dp(2);
        klp.topMargin = dp(2);
        sw.addView(knob, klp);
        sw.setOnClickListener(v -> {
            on[0] = !on[0];
            GradientDrawable g = new GradientDrawable();
            g.setColor(on[0] ? 0xffffda43 : 0xffe0e0e0);
            g.setCornerRadius(dp(14));
            track.setBackground(g);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) knob.getLayoutParams();
            lp.leftMargin = dp(on[0] ? 20 : 2);
            knob.setLayoutParams(lp);
        });
        row.addView(sw);
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFFEEEEEE);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
