package com.luoyun.doutu.account;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 真实登录 / 注册页（「我」页顶部头像与名字是入口）。
 *
 * 版式：渐变头图 + 悬浮白卡 + 分段控件 + 圆角输入行 + 渐变主按钮。
 * 两个 Tab：
 *   登录 —— 手机号**或用户名** + 密码
 *   注册 —— 用户名 + 邀请码 + 手机号 + 密码 + 确认密码（一个邀请码只能创建一个账号）
 *
 * 页面用代码搭建，不引布局资源，保持项目「零依赖」的风格。
 */
public final class LoginActivity extends Activity {

    private static final int BRAND = 0xFF2F6FED;
    private static final int BRAND_2 = 0xFF6E8BFF;
    private static final int INK = 0xFF1B1F27;
    private static final int SUB = 0xFF8A8F9A;
    private static final int FIELD_BG = 0xFFF4F6FA;

    private boolean registerMode;

    private EditText usernameField;
    private EditText inviteField;
    private EditText phoneField;
    private EditText passwordField;
    private EditText confirmField;
    private TextView submitButton;
    private TextView loginTab;
    private TextView registerTab;
    private TextView hintView;
    private TextView legalView;
    private LinearLayout usernameRow;
    private LinearLayout inviteRow;
    private LinearLayout phoneRow;
    private LinearLayout confirmRow;
    private TextView loginExtra;
    private TextView inviteReset;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(buildUi());
        applyMode();
        refreshConfig();
    }

    /* ------------------------------ 界面 ------------------------------ */

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        GradientDrawable page = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFE9EFFF, 0xFFFDFDFF});
        scroll.setBackground(page);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(34), dp(20), dp(28));
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 渐变头图 ──────────────────────────────────────────────
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BRAND, BRAND_2, 0xFF8A6BFF});
        heroBg.setCornerRadius(dp(26));
        hero.setBackground(heroBg);
        hero.setPadding(dp(26), dp(26), dp(26), dp(30));
        root.addView(hero);

        TextView brand = new TextView(this);
        // 未登录时点「我」页用户行进来的就是这个页面，标题直接说清是干什么的
        brand.setText("注册 / 登录");
        brand.setTextSize(27);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setTextColor(Color.WHITE);
        brand.setOnLongClickListener(v -> {
            // 正式包没有这个入口（只有调试包能改地址）
            if (Session.isDevBuild()) editBaseUrl();
            return true;
        });
        hero.addView(brand);

        TextView slogan = new TextView(this);
        slogan.setText("一键解析 · 服务端合成 · 支持批量");
        slogan.setTextSize(13);
        slogan.setTextColor(0xCCFFFFFF);
        slogan.setPadding(0, dp(6), 0, 0);
        hero.addView(slogan);

        TextView badge = new TextView(this);
        badge.setText("未开通会员也能用，只是成品带水印");
        badge.setTextSize(12);
        badge.setTextColor(Color.WHITE);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x33FFFFFF);
        badgeBg.setCornerRadius(dp(20));
        badge.setBackground(badgeBg);
        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.topMargin = dp(16);
        badge.setLayoutParams(badgeParams);
        hero.addView(badge);

        // ── 悬浮白卡 ──────────────────────────────────────────────
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(24));
        card.setBackground(cardBg);
        card.setPadding(dp(22), dp(20), dp(22), dp(24));
        card.setElevation(dp(10));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = -dp(30);   // 压在头图上，做出层次
        card.setLayoutParams(cardParams);
        root.addView(card);

        // 分段控件
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable tabsBg = new GradientDrawable();
        tabsBg.setColor(0xFFF1F3F8);
        tabsBg.setCornerRadius(dp(14));
        tabs.setBackground(tabsBg);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        card.addView(tabs);

        loginTab = segment("登录", tabs);
        registerTab = segment("注册新账号", tabs);

        // 字段
        usernameRow = fieldRow(card, "用户名", "2~20 位，中文/字母/数字/下划线",
                InputType.TYPE_CLASS_TEXT, true);
        usernameField = (EditText) usernameRow.getChildAt(1);
        inviteRow = fieldRow(card, "邀请码", "12 位字母数字，一个邀请码只能注册一个账号",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true);
        inviteField = (EditText) inviteRow.getChildAt(1);
        phoneRow = fieldRow(card, "手机号", "11 位手机号", InputType.TYPE_CLASS_PHONE, true);
        phoneField = (EditText) phoneRow.getChildAt(1);
        passwordField = (EditText) fieldRow(card, "密码", "至少 6 位",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, true).getChildAt(1);
        confirmRow = fieldRow(card, "确认密码", "再输入一次密码",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, true);
        confirmField = (EditText) confirmRow.getChildAt(1);

        // 主按钮
        submitButton = new TextView(this);
        submitButton.setText("登 录");
        submitButton.setTextSize(17);
        submitButton.setTypeface(Typeface.DEFAULT_BOLD);
        submitButton.setTextColor(Color.WHITE);
        submitButton.setGravity(Gravity.CENTER);
        GradientDrawable btn = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BRAND, 0xFF5B84FF});
        btn.setCornerRadius(dp(14));
        submitButton.setBackground(btn);
        submitButton.setPadding(0, dp(16), 0, dp(16));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(24);
        submitButton.setLayoutParams(buttonParams);
        submitButton.setOnClickListener(v -> submit());
        card.addView(submitButton);

        hintView = new TextView(this);
        hintView.setTextSize(12.5f);
        hintView.setTextColor(SUB);
        hintView.setGravity(Gravity.CENTER);
        hintView.setLineSpacing(0, 1.3f);
        hintView.setPadding(0, dp(14), 0, 0);
        card.addView(hintView);

        loginExtra = new TextView(this);
        loginExtra.setText("忘记密码？请联系客服协助重置");
        loginExtra.setTextSize(12.5f);
        loginExtra.setTextColor(BRAND);
        loginExtra.setGravity(Gravity.CENTER);
        loginExtra.setPadding(0, dp(12), 0, 0);
        card.addView(loginExtra);

        // 忘记密码下面再给一条自助的路：一个邀请码只注册一个账号，账号和邀请码是绑定的，
        // 所以「注册手机号 + 邀请码」就能证明是号主本人，不用等客服。
        inviteReset = new TextView(this);
        inviteReset.setText("邀请码重置密码");
        inviteReset.setTextSize(12.5f);
        inviteReset.setTextColor(BRAND);
        inviteReset.setTypeface(Typeface.DEFAULT_BOLD);
        inviteReset.setGravity(Gravity.CENTER);
        inviteReset.setPadding(0, dp(8), 0, dp(4));
        inviteReset.setOnClickListener(v -> ResetByInviteActivity.open(this));
        card.addView(inviteReset);

        // 版权承诺（合规要求：首次使用与登录页展示）
        legalView = new TextView(this);
        legalView.setTextSize(11);
        legalView.setTextColor(0xFF9AA1AC);
        legalView.setLineSpacing(0, 1.35f);
        legalView.setPadding(0, dp(18), 0, 0);
        legalView.setText("本软件仅用于处理您自己创作或拥有版权的素材，请勿用于去除他人作品的水印或二次搬运。");
        root.addView(legalView);

        // 这一页不再显示服务端地址（产品要求：注册/登录页下面不要挂域名）。
        // 调试包仍然需要看到当前连的是哪个后端 —— 那就在标题上长按弹出来，
        // 正式包连这个入口都没有（Session.isDevBuild() 兜住）。
        if (Session.isDevBuild()) {
            TextView server = new TextView(this);
            server.setTextSize(10.5f);
            server.setTextColor(0xFFB4BAC5);
            server.setGravity(Gravity.CENTER);
            server.setPadding(0, dp(10), 0, 0);
            server.setText("调试包 · 长按标题可改服务端地址");
            server.setOnClickListener(v -> editBaseUrl());
            root.addView(server);
        }

        return scroll;
    }

    private TextView segment(String label, LinearLayout parent) {
        final TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(11), 0, dp(11));
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(tv);
        tv.setOnClickListener(v -> {
            registerMode = "注册新账号".equals(tv.getText().toString());
            applyMode();
        });
        return tv;
    }

    private LinearLayout fieldRow(LinearLayout parent, String label, String hint, int inputType, boolean withIcon) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(14), 0, 0);

        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextSize(12.5f);
        caption.setTextColor(SUB);
        caption.setPadding(dp(4), 0, 0, dp(6));
        row.addView(caption);

        EditText field = new EditText(this);
        field.setHint(hint);
        field.setHintTextColor(0xFFB9BEC8);
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setTextColor(INK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(FIELD_BG);
        bg.setCornerRadius(dp(12));
        field.setBackground(bg);
        field.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.addView(field);

        parent.addView(row);
        return row;
    }

    private void applyMode() {
        boolean reg = registerMode;
        // 登录 Tab：账号（手机号或用户名）+ 密码，两行就够
        // 注册 Tab：用户名 + 邀请码 + 手机号 + 密码 + 确认密码
        usernameRow.setVisibility(View.VISIBLE);
        ((TextView) usernameRow.getChildAt(0)).setText(reg ? "用户名" : "手机号 / 用户名");
        usernameField.setHint(reg ? "2~20 位，中文/字母/数字/下划线" : "手机号或用户名都行");
        inviteRow.setVisibility(reg ? View.VISIBLE : View.GONE);
        phoneRow.setVisibility(reg ? View.VISIBLE : View.GONE);
        confirmRow.setVisibility(reg ? View.VISIBLE : View.GONE);
        submitButton.setText(reg ? "注 册" : "登 录");
        loginExtra.setVisibility(reg ? View.GONE : View.VISIBLE);
        inviteReset.setVisibility(reg ? View.GONE : View.VISIBLE);
        hintView.setText(reg
                ? "注册需要邀请码（一个邀请码只能创建一个账号）。\n注册后即可使用，未开通会员时成品与界面会带水印。"
                : "还没有账号？点上面的「注册新账号」，用邀请码创建。");
        styleSegment(loginTab, !reg);
        styleSegment(registerTab, reg);
    }

    private void styleSegment(TextView tv, boolean active) {
        tv.setTextColor(active ? Color.WHITE : 0xFF6B7280);
        tv.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        if (active) {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{BRAND, BRAND_2});
            bg.setCornerRadius(dp(11));
            tv.setBackground(bg);
        } else {
            tv.setBackground(null);
        }
    }

    /* ------------------------------ 提交 ------------------------------ */

    private void submit() {
        String account = usernameField.getText().toString().trim();
        String password = passwordField.getText().toString();
        if (account.isEmpty()) {
            usernameField.setError(registerMode ? "请输入用户名" : "请输入手机号或用户名");
            return;
        }
        if (password.length() < 6) {
            passwordField.setError("密码至少 6 位");
            return;
        }

        if (!registerMode) {
            setBusy(true);
            DoutuApi.login(this, account, password, this::onAuthResult);
            return;
        }

        // 注册：先做本地校验，省一次来回
        String nameError = checkUsername(account);
        if (nameError != null) {
            usernameField.setError(nameError);
            return;
        }
        String invite = inviteField.getText().toString().trim().replace("-", "");
        String phone = phoneField.getText().toString().trim();
        String confirm = confirmField.getText().toString();
        if (invite.isEmpty()) {
            inviteField.setError("请输入邀请码");
            return;
        }
        if (invite.length() != 12) {
            inviteField.setError("邀请码是 12 位字母数字");
            return;
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            phoneField.setError("请输入正确的手机号");
            return;
        }
        if (!password.equals(confirm)) {
            confirmField.setError("两次输入的密码不一致");
            return;
        }
        setBusy(true);
        DoutuApi.register(this, account, invite, phone, password, confirm, this::onAuthResult);
    }

    /** 与后端 validateUsername 保持一致的本地校验；通过返回 null。 */
    public static String checkUsername(String name) {
        if (name.length() < 2 || name.length() > 20) return "用户名长度需为 2~20 位";
        if (!name.matches("^[\\u4e00-\\u9fa5A-Za-z0-9_-]+$")) return "只能包含中文、字母、数字、下划线或短横线";
        if (name.matches("^1[3-9]\\d{9}$")) return "用户名不能是手机号，请换一个";
        return null;
    }

    private void onAuthResult(DoutuApi.Result result) {
        setBusy(false);
        if (!result.ok()) {
            toast(result.msg == null || result.msg.isEmpty() ? "请求失败" : result.msg);
            return;
        }
        String token = result.data.optString("token", "");
        String signKey = result.data.optString("sign_key", "");
        String phone = phoneField.getText().toString().trim();
        if (!registerMode && phone.isEmpty()) phone = result.data.optJSONObject("user") != null
                ? result.data.optJSONObject("user").optString("phone", "") : "";
        Session.save(phone, token, signKey);
        Session.updateUser(result.user());
        toast(result.msg == null || result.msg.isEmpty() ? "成功" : result.msg);
        setResult(RESULT_OK);
        finish();
    }

    private void setBusy(boolean busy) {
        submitButton.setEnabled(!busy);
        submitButton.setAlpha(busy ? 0.6f : 1f);
        submitButton.setText(busy ? "请稍候…" : (registerMode ? "注 册" : "登 录"));
    }

    /** 拉一次公开配置：水印文案、未开通提示、合规文案、客服信息与首页横幅公告。 */
    private void refreshConfig() {
        DoutuApi.config(this, result -> {
            if (!result.ok() || result.data == null) return;
            Session.setOverlayText(result.data.optString("watermark_overlay_text", null));
            Session.setActivationNotice(result.data.optString("activation_notice", null));
            // 首页横幅（公告 type=3）与轮播节奏都从这里下发；解析失败不影响其它字段
            Session.applyConfig(result.data);
            String legal = result.data.optString("legal_notice", "");
            if (!legal.isEmpty()) legalView.setText(legal);
        });
    }

    /**
     * 调试包专用：临时改服务端地址（换机器联调时用）。
     * **正式包里根本没有入口**——地址是编译期注入的域名，客户端不给改。
     */
    private void editBaseUrl() {
        if (!Session.isDevBuild()) return;
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(Session.baseUrl());
        new android.app.AlertDialog.Builder(this)
                .setTitle("服务端地址（仅调试包）")
                .setMessage("正式包只能用编译期写死的域名，这里改的只对本机调试生效。")
                .setView(field)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    Session.setBaseUrl(field.getText().toString());
                    toast("已保存，重新打开页面生效");
                })
                .show();
    }

    /* ------------------------------ 工具 ------------------------------ */

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /** 供其它页面拉起登录页。 */
    public static void open(Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        // 登录页是可选入口：返回就是回到应用，不需要拦住
        super.onBackPressed();
    }
}
