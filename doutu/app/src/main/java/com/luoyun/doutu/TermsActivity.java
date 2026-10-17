package com.luoyun.doutu;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 首次启动的《用户须知与免责声明》。
 *
 * 为什么单独做成一个 Activity 并放在启动器入口上（见 AndroidManifest 里四个 activity-alias）：
 * 条款必须**一打开就出现**，不能等主界面、网络或配置加载完 —— 所以这里不初始化任何 SDK、
 * 不读服务器配置，正文直接内置在代码里，只做「同意 / 拒绝」这一件事。
 *
 * 交互约束（产品要求）：
 *   1. 必须**滑到正文最底部**，才认定为「已阅读完」；
 *   2. 滑到底之后再**等待 5 秒**，「同意」才可点击；
 *   3. 点「拒绝」立即退出进程（不是返回，而是直接结束）。
 */
public class TermsActivity extends Activity {

    /** 桌面图标带来的落地页标记：MainActivity 读它决定进小支主页还是小微主页。 */
    public static final String EXTRA_ENTRY_ICON = "doutu_entry_icon";

    /** 条款版本：改一次正文就把它 +1，老用户会被重新要求确认一次。 */
    private static final String TERMS_VERSION = "2026-10-15-v1";
    private static final String PREFS = "doutu_terms";
    private static final String KEY_ACCEPTED = "accepted_version";
    /**
     * 已同意内容的 sha256。
     * 为什么不用版本号比对：版本号有两套命名空间 —— 客户端内置的是 "2026-10-15-v1"，
     * 服务端配置里的是 "1"。用户如果在内置文案还没被服务端版本替换前就点了同意，
     * 下次启动一比对版本号就「不一致」，会白问一遍。按内容哈希判就不会有这个问题。
     */
    private static final String KEY_ACCEPTED_HASH = "accepted_hash";
    /** 滑到底后还要等多久才允许点同意（毫秒） */
    private static final long WAIT_MS = 5000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ScrollView scroller;
    private TextView bodyText;
    private TextView hint;
    private Button agreeBtn;
    private boolean reachedBottom;
    private long bottomReachedAt;
    /** 当前展示的正文与版本：先用内置的，后台配置拉回来若不同就替换 */
    private String displayText = TERMS_TEXT;
    private String displayVersion = TERMS_VERSION;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateAgreeState();
            handler.postDelayed(this, 200);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        // 已同意过同一份正文：直接进主界面（不显示条款，也不闪一下）
        if (isAccepted(TERMS_TEXT)) {
            startMain();
            return;
        }

        buildUi();
        // 后台可能改过条款：内置文案先顶上（保证「一打开就有」），
        // 配置拉回来更快就用服务端版本替换，不需要用户等网络。
        loadServerTerms();
        // 布局完成后再判一次：正文短到一屏放得下时直接算「已读完」，但仍然要等满 5 秒
        scroller.getViewTreeObserver().addOnGlobalLayoutListener(this::checkBottom);
        handler.post(tick);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int sp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics()));
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF5F6FA);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(18), dp(20), dp(14));
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("用户须知与免责声明");
        title.setTextSize(19f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF23232F);
        column.addView(title);

        TextView sub = new TextView(this);
        sub.setText("请完整阅读以下全部条款后再决定是否使用本软件");
        sub.setTextSize(12f);
        sub.setTextColor(0xFF8A8A9A);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(6);
        column.addView(sub, subLp);

        // 正文：可滚动，滑到底才算读完
        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(true);
        GradientDrawable card = new GradientDrawable();
        card.setColor(Color.WHITE);
        card.setCornerRadius(dp(14));
        scroller.setBackground(card);
        scroller.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = dp(18);
        column.addView(scroller, scrollLp);

        TextView body = new TextView(this);
        body.setText(displayText);
        body.setTextSize(14f);
        body.setTextColor(0xFF44444F);
        body.setLineSpacing(dp(6), 1f);
        // 注意：这里**不能**给 body 设 ScrollingMovementMethod —— 它会抢走滚动手势，
        // 而且会让内容一进来就跳到最底部，导致「必须滑到底」的约束形同虚设。
        scroller.addView(body);
        bodyText = body;

        // 已经同意的用户不受影响：只有没滑到底 / 没等够 5 秒时按钮才置灰
        hint = new TextView(this);
        hint.setTextSize(13f);
        hint.setTextColor(0xFFB4551F);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(10);
        column.addView(hint, hintLp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(buttons);

        Button reject = new Button(this);
        reject.setText("拒绝");
        reject.setTextSize(16f);
        reject.setAllCaps(false);
        reject.setTextColor(0xFF6B6B7B);
        GradientDrawable rejectBg = new GradientDrawable();
        rejectBg.setColor(0xFFE8E8EF);
        rejectBg.setCornerRadius(dp(24));
        reject.setBackground(rejectBg);
        LinearLayout.LayoutParams rejectLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        rejectLp.rightMargin = dp(10);
        buttons.addView(reject, rejectLp);
        reject.setOnClickListener(v -> exitNow());

        agreeBtn = new Button(this);
        agreeBtn.setText("同意");
        agreeBtn.setTextSize(16f);
        agreeBtn.setAllCaps(false);
        agreeBtn.setTextColor(Color.WHITE);
        buttons.addView(agreeBtn, new LinearLayout.LayoutParams(0, dp(48), 1f));
        agreeBtn.setOnClickListener(v -> {
            if (!canAgree()) return;          // 双保险：视觉置灰之外再挡一次
            prefs().edit()
                    .putString(KEY_ACCEPTED, displayVersion)
                    .putString(KEY_ACCEPTED_HASH, sha256(displayText))
                    .apply();
            startMain();
        });

        setContentView(root);
        updateAgreeState();

        // 滚动监听：滑到底立刻记时间，之后开始 5 秒倒计时
        scroller.setOnScrollChangeListener((v, x, y, ox, oy) -> checkBottom());
    }

    /** 是否已滑到正文末尾（留 8px 容差，避免浮点/圆角导致差一两个像素永远判不到底）。 */
    private void checkBottom() {
        if (reachedBottom || scroller == null || scroller.getChildCount() == 0) return;
        View child = scroller.getChildAt(0);
        // 首帧 post 时还没量过尺寸（高度为 0），此时 diff 会算成 0 被误判成「已到底」，
        // 所以必须等真正完成布局（两边高度都大于 0）再判。
        if (scroller.getHeight() <= 0 || child.getHeight() <= 0) return;
        int diff = child.getBottom() - (scroller.getHeight() + scroller.getScrollY());
        if (diff <= dp(8)) {
            reachedBottom = true;
            bottomReachedAt = SystemClock.elapsedRealtime();
        }
        updateAgreeState();
    }

    private boolean canAgree() {
        return reachedBottom && SystemClock.elapsedRealtime() - bottomReachedAt >= WAIT_MS;
    }

    private void updateAgreeState() {
        if (agreeBtn == null || hint == null) return;
        boolean ok = canAgree();
        agreeBtn.setEnabled(ok);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ok ? 0xFF6F6AE8 : 0xFFC9C6E8);
        bg.setCornerRadius(dp(24));
        agreeBtn.setBackground(bg);

        if (ok) {
            hint.setText("已阅读完毕，可以点击「同意」");
            hint.setTextColor(0xFF2E7D32);
        } else if (!reachedBottom) {
            hint.setText("请滑动阅读至最底部");
            hint.setTextColor(0xFFB4551F);
        } else {
            long left = (WAIT_MS - (SystemClock.elapsedRealtime() - bottomReachedAt) + 999) / 1000;
            hint.setText("已阅读完毕，请再等待 " + Math.max(1, left) + " 秒");
            hint.setTextColor(0xFFB4551F);
        }
    }

    /**
     * 拉公开配置里的《用户须知与免责声明》。
     * 成功且版本/内容与当前展示的不一致时替换正文，并**重置阅读判定**（这是一份新文档，
     * 必须重新滑到底并重新计时），否则用户可能拿着旧文档的阅读进度去同意新条款。
     */
    private void loadServerTerms() {
        com.luoyun.doutu.account.DoutuApi.config(this, result -> {
            if (!result.ok() || result.data == null) return;
            org.json.JSONObject terms = result.data.optJSONObject("terms");
            if (terms == null) return;
            String text = terms.optString("text", "").trim();
            int serverVersion = terms.optInt("version", 0);
            if (text.isEmpty() || serverVersion <= 0) return;
            String version = String.valueOf(serverVersion);
            // 用户已经同意/已读到底了就别中途换文档
            if (reachedBottom || canAgree()) return;
            if (text.equals(displayText)) {
                displayVersion = version;
                return;
            }
            if (isAccepted(text)) {
                // 服务端这份正文用户已经同意过（只是版本号写法不同）：直接放行
                displayText = text;
                displayVersion = version;
                prefs().edit().putString(KEY_ACCEPTED, version).apply();
                startMain();
                return;
            }
            displayText = text;
            displayVersion = version;
            if (bodyText != null) bodyText.setText(text);
            // 换了正文 → 重新要求滑到底 + 重新计时
            reachedBottom = false;
            bottomReachedAt = 0L;
            if (scroller != null) scroller.scrollTo(0, 0);
            updateAgreeState();
        });
    }

    /** 这份正文用户是否已经同意过（按内容哈希判断，与版本号写法无关）。 */
    private boolean isAccepted(String text) {
        String saved = prefs().getString(KEY_ACCEPTED_HASH, "");
        return !saved.isEmpty() && saved.equals(sha256(text == null ? "" : text));
    }

    /** 正文的 sha256（十六进制）。 */
    private static String sha256(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void startMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(EXTRA_ENTRY_ICON, entryTarget());
        // CLEAR_TOP + SINGLE_TOP：从桌面反复点图标时复用已经在跑的主页（走 onNewIntent），
        // 不会每点一次就压一层新的主页 / 目标页，返回栈不会越点越长。
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * 桌面图标决定落地页（第 7 条）：
     *   支付宝 / 支付宝（香港）图标 → 小支主页（"zhi"）
     *   微信图标                   → 小微主页（"wei"）
     *   豆图图标以及其它一切入口     → 应用主页（"home"）
     *
     * <p>四个 {@code activity-alias} 都指向这个 Activity，从桌面点进来时
     * {@code getIntent().getComponent()} 拿到的就是那个别名的全名，所以这里认得出是哪一款图标。
     * 认不出来的（比如被系统设置里直接启动、或者别名被改过名）按默认款处理 —— 进应用主页。
     */
    private String entryTarget() {
        try {
            android.content.ComponentName component = getIntent() == null ? null : getIntent().getComponent();
            String name = component == null ? "" : component.getClassName();
            if (name.endsWith(".Icon.alipay") || name.endsWith(".Icon.hkalipay")) return "zhi";
            if (name.endsWith(".Icon.wechat")) return "wei";
        } catch (Exception ignored) {
            // 认不出来就走默认
        }
        return "home";
    }

    /** 拒绝：直接结束整个进程（产品要求「立刻闪退」）。 */
    private void exitNow() {
        handler.removeCallbacksAndMessages(null);
        finishAffinity();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    @Override
    public void onBackPressed() {
        // 未同意前不允许用返回键绕过条款
        if (isAccepted(displayText)) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** 正文内置在代码里：不读网络、不读配置，保证「一打开就有」。 */
    private static final String TERMS_TEXT =
            "欢迎使用本软件。在您开始使用之前，请务必认真、完整地阅读以下全部条款。本条款是您与本软件开发者之间就使用本软件所订立的协议，自您点击「同意」之时起生效，对双方均具有约束力。您点击「同意」或继续使用本软件，即视为您已完整阅读、充分理解并自愿接受本条款的全部内容；如您不同意本条款的任何内容，请点击「拒绝」并立即停止使用本软件。\n\n"
          + "一、软件性质与用途\n"
          + "1. 本软件仅为技术演示、功能模拟与学习交流之用，不构成任何形式的商业服务承诺，亦不对处理结果作出任何准确性、完整性或可用性保证。\n"
          + "2. 本软件不存储、不传播、不再分发任何第三方平台的内容，仅按用户本人的指令，对其自行提供的链接或素材在本地设备上进行处理。\n"
          + "3. 本软件与任何第三方平台均无隶属、代理、合作或授权关系，相关名称、标识的权利归各自权利人所有。\n\n"
          + "二、用户责任与后果自担\n"
          + "1. 您通过本软件进行的一切操作、处理、下载、保存、使用、传播等行为，均为您本人独立意愿的体现，并由您本人独立决定、独立实施、独立承担全部后果。\n"
          + "2. 您承诺所处理的素材已取得合法授权或归您本人所有，且您的使用行为符合您所在国家或地区的法律法规、公序良俗以及相关平台的服务条款。\n"
          + "3. 因使用本软件所产生的一切结果、后果、损失、纠纷、索赔、行政处罚及其他法律责任，无论直接或间接，均由您本人承担，与本软件及其开发者无关。\n\n"
          + "三、免责范围\n"
          + "1. 本软件的开发者、运营者、公司及其员工、管理人员、关联方，均不对您使用本软件所产生的任何直接或间接后果承担任何责任，包括但不限于：素材侵权、版权纠纷、账号封禁或限流、数据丢失、设备损坏、预期利益损失、第三方索赔、行政或刑事处罚等。\n"
          + "2. 本软件按「现状」提供，不对可用性、连续性、准确性、及时性、适用性作出任何明示或默示的保证；因网络故障、设备异常、第三方平台接口变更、政策调整或不可抗力导致的服务中断、处理失败或数据异常，开发者不承担任何责任。\n"
          + "3. 您不得将本软件用于任何违反法律法规、侵犯他人合法权益或损害公共利益的用途；由此产生的一切后果由您自行承担，开发者保留在发现违法使用时停止提供服务并配合有关机关调查的权利。\n"
          + "4. 您因使用本软件而作出的任何判断与决定，均由您自行负责；本软件不构成任何形式的法律、财务或其他专业建议。\n\n"
          + "四、条款的变更、效力与争议解决\n"
          + "1. 本条款可能因法律法规调整或软件功能变化而更新，更新后将在软件内公示；公示后继续使用即视为接受更新后的条款。\n"
          + "2. 本条款的解释、效力与履行适用中华人民共和国法律（不含冲突规范）。因本条款产生的争议，双方应首先友好协商；协商不成的，提交开发者所在地有管辖权的人民法院解决。\n"
          + "3. 本条款部分内容如被认定无效或不可执行，不影响其余条款的效力；该部分应以最接近原意且合法有效的方式予以替代。\n\n"
          + "请您再次确认：您已完整阅读并理解上述全部内容，在知晓并自愿承担使用本软件的全部风险与后果的前提下，继续使用本软件。";

    /** 供其它界面（如「我 → 关于」）复用同一份正文与版本号。 */
    public static String text() {
        return TERMS_TEXT;
    }

    public static String version() {
        return TERMS_VERSION;
    }
}
