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
 * 「邀请码重置密码」—— 登录页「忘记密码？联系客服协助重置」下面那一行的去向。
 *
 * <p>一个邀请码只能注册一个账号，注册时就把邀请码和账号钉在一起了
 * （`invite_code.used_by / used_phone`），所以「注册时的手机号 + 邀请码」凑齐
 * 就足以证明是号主本人，不需要短信验证码。这一步两步：
 *
 * <ol>
 *   <li><b>核验</b>：手机号 + 邀请码 → 服务端核对通过后发一张 10 分钟的一次性票据；</li>
 *   <li><b>改密</b>：输入两次新密码 → 确认修改。服务端换掉密码哈希，并把旧会话全部吊销，
 *       所以<b>旧密码立刻作废</b>，用新密码重新登录即可。</li>
 * </ol>
 *
 * 票据只在内存里拿着，退出这个页面就作废；改密成功后自动关页回到登录页。
 */
public final class ResetByInviteActivity extends Activity {
    private static final int BRAND = 0xFF4A7BF7;
    private static final int BRAND_2 = 0xFF6F8CFF;
    private static final int INK = 0xFF1B1B22;
    private static final int SUB = 0xFF8A8F9A;
    private static final int FIELD_BG = 0xFFF4F6FA;

    private LinearLayout stepOne;
    private LinearLayout stepTwo;
    private EditText phoneField;
    private EditText inviteField;
    private EditText passwordField;
    private EditText confirmField;
    private TextView errorLine;
    private TextView stepTwoHint;
    private TextView stepOneButton;
    private TextView stepTwoButton;

    /** 核验通过后服务端给的一次性票据；没有它就进不了第二步。 */
    private String resetToken = "";
    private boolean busy;

    public static Intent intentFor(Context context) {
        return new Intent(context, ResetByInviteActivity.class);
    }

    public static void open(Activity activity) {
        activity.startActivity(intentFor(activity));
        activity.overridePendingTransition(0, 0);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(buildUi());
        showStep(1);
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
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 顶部返回 + 标题
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(15);
        back.setTextColor(BRAND);
        back.setPadding(0, dp(6), dp(12), dp(6));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        root.addView(bar);

        // 渐变小头图
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BRAND, BRAND_2});
        heroBg.setCornerRadius(dp(22));
        hero.setBackground(heroBg);
        hero.setPadding(dp(22), dp(20), dp(22), dp(22));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroParams.topMargin = dp(10);
        hero.setLayoutParams(heroParams);
        root.addView(hero);

        TextView title = new TextView(this);
        title.setText("邀请码重置密码");
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        hero.addView(title);

        TextView slogan = new TextView(this);
        slogan.setText("用注册时的手机号 + 邀请码验证身份，一个邀请码只对应一个账号");
        slogan.setTextSize(12.5f);
        slogan.setTextColor(0xCCFFFFFF);
        slogan.setLineSpacing(dp(3), 1f);
        slogan.setPadding(0, dp(8), 0, 0);
        hero.addView(slogan);

        // 白卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(22));
        card.setBackground(cardBg);
        card.setPadding(dp(20), dp(6), dp(20), dp(22));
        card.setElevation(dp(8));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = -dp(18);
        card.setLayoutParams(cardParams);
        root.addView(card);

        // ── 第一步：手机号 + 邀请码 ──
        stepOne = new LinearLayout(this);
        stepOne.setOrientation(LinearLayout.VERTICAL);
        card.addView(stepOne);

        phoneField = field(stepOne, "注册时的手机号", "11 位手机号", InputType.TYPE_CLASS_PHONE);
        inviteField = field(stepOne, "邀请码",
                "12 位字母数字，注册时用的那一个",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        stepOneButton = primaryButton(stepOne, "找回密码");
        stepOneButton.setOnClickListener(v -> verify());

        // ── 第二步：两次新密码 ──
        stepTwo = new LinearLayout(this);
        stepTwo.setOrientation(LinearLayout.VERTICAL);
        card.addView(stepTwo);

        stepTwoHint = new TextView(this);
        stepTwoHint.setTextSize(12.5f);
        stepTwoHint.setTextColor(SUB);
        stepTwoHint.setLineSpacing(dp(3), 1f);
        stepTwoHint.setPadding(dp(4), dp(14), dp(4), 0);
        stepTwo.addView(stepTwoHint);

        passwordField = field(stepTwo, "新密码", "至少 6 位",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmField = field(stepTwo, "确认新密码", "再输入一次新密码",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        stepTwoButton = primaryButton(stepTwo, "确认修改");
        stepTwoButton.setOnClickListener(v -> submitReset());

        TextView backToStepOne = new TextView(this);
        backToStepOne.setText("← 手机号 / 邀请码填错了，重新填写");
        backToStepOne.setTextSize(12.5f);
        backToStepOne.setTextColor(BRAND);
        backToStepOne.setGravity(Gravity.CENTER);
        backToStepOne.setPadding(0, dp(16), 0, 0);
        backToStepOne.setOnClickListener(v -> showStep(1));
        stepTwo.addView(backToStepOne);

        // 公共错误行
        errorLine = new TextView(this);
        errorLine.setTextSize(12.5f);
        errorLine.setTextColor(0xFFE24C4C);
        errorLine.setGravity(Gravity.CENTER);
        errorLine.setLineSpacing(dp(3), 1f);
        errorLine.setPadding(dp(4), dp(14), dp(4), 0);
        errorLine.setVisibility(View.GONE);
        card.addView(errorLine);

        return scroll;
    }

    private EditText field(LinearLayout parent, String label, String hint, int inputType) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(16), 0, 0);

        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextSize(12.5f);
        caption.setTextColor(SUB);
        caption.setPadding(dp(4), 0, 0, dp(6));
        row.addView(caption);

        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(0xFFB9BEC8);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setTextColor(INK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(FIELD_BG);
        bg.setCornerRadius(dp(12));
        input.setBackground(bg);
        input.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.addView(input);

        parent.addView(row);
        return input;
    }

    private TextView primaryButton(LinearLayout parent, String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(17);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BRAND, BRAND_2});
        bg.setCornerRadius(dp(14));
        button.setBackground(bg);
        button.setPadding(0, dp(16), 0, dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(24);
        button.setLayoutParams(params);
        parent.addView(button);
        return button;
    }

    /** 切换两步；换步时把上一步的错误清掉。 */
    private void showStep(int step) {
        errorLine.setVisibility(View.GONE);
        boolean first = step == 1;
        stepOne.setVisibility(first ? View.VISIBLE : View.GONE);
        stepTwo.setVisibility(first ? View.GONE : View.VISIBLE);
        if (!first) {
            stepTwoHint.setText("手机号和邀请码已核验通过，请设置新密码。\n改完之后旧密码立即作废，请用新密码登录。");
            passwordField.setText("");
            confirmField.setText("");
            passwordField.requestFocus();
        }
    }

    /* ------------------------------ 交互 ------------------------------ */

    private void verify() {
        if (busy) return;
        String phone = phoneField.getText().toString().trim();
        String code = inviteField.getText().toString().trim().toUpperCase();
        if (!phone.matches("\\d{6,20}")) {
            returnError("请输入注册时填写的手机号");
            return;
        }
        if (code.replaceAll("[^0-9A-Z]", "").length() != 12) {
            returnError("邀请码是 12 位字母数字，请核对后重试");
            return;
        }

        setBusy(true, stepOneButton, "核验中…");
        DoutuApi.verifyInviteForReset(this, phone, code, result -> {
            setBusy(false, stepOneButton, "找回密码");
            if (!result.ok()) returnError(friendly(result));
            resetToken = result.data == null ? "" : result.data.optString("reset_token", "");
            if (resetToken.isEmpty()) returnError("核验没通过，请稍后重试");
            showStep(2);
        });
    }

    private void submitReset() {
        if (busy) return;
        String password = passwordField.getText().toString();
        String confirm = confirmField.getText().toString();
        if (password.length() < 6) {
            returnError("密码至少 6 位");
            return;
        }
        if (!password.equals(confirm)) {
            returnError("两次输入的密码不一致");
            return;
        }
        if (resetToken.isEmpty()) {
            returnError("核验已失效，请重新填写手机号和邀请码");
            showStep(1);
            return;
        }

        setBusy(true, stepTwoButton, "提交中…");
        DoutuApi.resetPasswordByInvite(this, resetToken, password, confirm, result -> {
            setBusy(false, stepTwoButton, "确认修改");
            if (!result.ok()) {
                returnError(friendly(result));
                // 票据过期 / 用过了：退回第一步重新核验
                if (String.valueOf(result.msg).contains("过期")) {
                    resetToken = "";
                    showStep(1);
                }
                return;
            }
            resetToken = "";
            hideError();
            Toast.makeText(this, "密码已重置，请用新密码登录", Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private void setBusy(boolean value, TextView button, String label) {
        busy = value;
        button.setEnabled(!value);
        button.setAlpha(value ? 0.6f : 1f);
        button.setText(label);
    }

    private void returnError(String message) {
        errorLine.setText(message);
        errorLine.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorLine.setVisibility(View.GONE);
    }

    /** 服务端的提示已经说得挺清楚（手机号对不上 / 邀请码不存在…），优先直接用它。 */
    private static String friendly(DoutuApi.Result result) {
        String message = result == null || result.msg == null ? "" : String.valueOf(result.msg).trim();
        if (message.isEmpty() || "null".equals(message)) return "操作失败，请稍后重试";
        return message;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
