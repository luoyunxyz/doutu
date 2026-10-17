package com.luoyun.doutu.features.sound;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 「模拟收款声音」—— 娱乐页那一格进来的页面（原包路由 `/account_sound`，显示名「支付宝到账声音」）。
 *
 * <p>做的事就是原包那一件：把用户给的金额拆成一串音素 mp3（数字/十/百/千/万/亿/点 + 「元」），
 * 前面加上到账提示音、后面按风格接小支或小微的收尾音，顺序播出来。页面上的开关也照原包的字符串来：
 * 小支/小微收款、随机收款方、播报一次 / 循环播报 / 无限收款模式、随机金额区间
 * （10-100 / 100-1000 / 1000-10000）、延时播放（2/5/10 秒）、倍速（0.5~1.5）。
 *
 * <p>版式上做了一轮整理（2026-10-16）：金额输入和「播报」并成一行，模块收进卡片、标题压在卡片里，
 * 上面常驻一行「这次会播报什么」，让用户不用试听就知道会响成什么样。
 *
 * <p>全部本地：不联网、不写服务器、不动任何账户余额 —— 它只是把包里那 20 个音频按顺序放一遍。
 */
public final class AccountSoundActivity extends Activity {
    private static final int BRAND = 0xFF6F6AE8;
    private static final int BRAND_SOFT = 0xFFEDEBFF;
    private static final int INK = 0xFF23232F;
    private static final int SUB = 0xFF8A8A9A;
    private static final int CARD = 0xFFFFFFFF;
    private static final int LINE = 0xFFEDEDF3;
    private static final int CHIP_BG = 0xFFF4F4F9;
    private static final int DANGER = 0xFFE24C4C;

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f};
    private static final int[] DELAYS = {0, 2, 5, 10};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<TextView> styleChips = new ArrayList<>();
    private final List<LinearLayout> modeCards = new ArrayList<>();
    private final List<TextView> modeLabels = new ArrayList<>();
    private final List<LinearLayout> rangeRows = new ArrayList<>();
    private final List<TextView> delayChips = new ArrayList<>();
    private final List<TextView> speedChips = new ArrayList<>();

    private AccountSoundPlayer player;
    private EditText amountField;
    private TextView errorLine;
    private TextView statusLine;
    private TextView summaryLine;
    private TextView playButton;
    private TextView stopLoop;

    private boolean looping;
    private boolean infinite;
    private int dotPhase;
    private Runnable dotTicker;

    public static void open(Activity activity) {
        activity.startActivity(new Intent(activity, AccountSoundActivity.class));
        activity.overridePendingTransition(0, 0);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        player = new AccountSoundPlayer(this);
        buildUi();
        bindState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLooping(false);
        handler.removeCallbacksAndMessages(null);
        player.release();
    }

    /* ------------------------------ 小工具 ------------------------------ */

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private static GradientDrawable stroked(int color, float radius, int stroke, int width) {
        GradientDrawable shape = round(color, radius);
        shape.setStroke(width, stroke);
        return shape;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = margin;
        return lp;
    }

    /** 一张卡片：左上角一个标题，下面放内容 —— 比「标题 + 卡片」两段式省一半纵向空间。 */
    private LinearLayout card(String title, String hint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(CARD, dp(14)));
        box.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(head);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(14f);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(INK);
        head.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (hint != null && !hint.isEmpty()) {
            TextView tail = new TextView(this);
            tail.setText(hint);
            tail.setTextSize(12f);
            tail.setTextColor(SUB);
            head.addView(tail);
        }
        return box;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f)));
        lp.topMargin = dp(10);
        view.setLayoutParams(lp);
        return view;
    }

    private TextView chip(String text, View.OnClickListener click) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(13f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(9), dp(14), dp(9));
        chip.setOnClickListener(click);
        chip.setTextColor(INK);
        chip.setBackground(stroked(CHIP_BG, dp(18), LINE, dp(1f)));
        return chip;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        return lp;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /* ------------------------------ 界面 ------------------------------ */

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF4F5F9);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        root.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(14), dp(12), dp(14), dp(26));
        scroller.addView(column);

        // 标题栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(bar);
        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(16f);
        back.setTextColor(INK);
        back.setPadding(0, dp(6), dp(8), dp(6));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = new TextView(this);
        title.setText("支付宝到账声音");
        title.setTextSize(19f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(INK);
        bar.addView(title);

        // 这次会播报什么（常驻的一行摘要）
        summaryLine = new TextView(this);
        summaryLine.setTextSize(13f);
        summaryLine.setTextColor(BRAND);
        summaryLine.setBackground(round(BRAND_SOFT, dp(10)));
        summaryLine.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams summaryLp = match();
        summaryLp.topMargin = dp(10);
        column.addView(summaryLine, summaryLp);

        // ① 收款风格
        LinearLayout styleCard = card("收款风格", null);
        LinearLayout.LayoutParams styleLp = match();
        styleLp.topMargin = dp(12);
        column.addView(styleCard, styleLp);
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        styleRow.setPadding(0, dp(10), 0, 0);
        styleCard.addView(styleRow);
        styleChips.add(chip("小支收款", v -> {
            SoundStore.style(SoundStore.STYLE_ZHI);
            bindState();
        }));
        styleChips.add(chip("小微收款", v -> {
            SoundStore.style(SoundStore.STYLE_WEI);
            bindState();
        }));
        for (View chip : styleChips) styleRow.addView(chip, chipParams());
        TextView randomStyleChip = chip("随机小支/小微", v -> {
            SoundStore.randomStyle(!SoundStore.randomStyle());
            bindState();
        });
        styleChips.add(randomStyleChip);
        styleRow.addView(randomStyleChip);
        TextView styleHint = new TextView(this);
        styleHint.setText("随机收款方：将会随机播报小支或小微的收款");
        styleHint.setTextSize(12f);
        styleHint.setTextColor(SUB);
        LinearLayout.LayoutParams styleHintLp = match();
        styleHintLp.topMargin = dp(8);
        styleCard.addView(styleHint, styleHintLp);

        // ② 播报金额
        LinearLayout amountCard = card("播报金额", null);
        LinearLayout.LayoutParams amountLp = match();
        amountLp.topMargin = dp(12);
        column.addView(amountCard, amountLp);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams inputLp = match();
        inputLp.topMargin = dp(10);
        amountCard.addView(inputRow, inputLp);

        amountField = new EditText(this);
        amountField.setSingleLine(true);
        amountField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountField.setTextSize(19f);
        amountField.setTextColor(INK);
        amountField.setHintTextColor(0xFFB4B4C0);
        amountField.setBackground(round(0xFFF4F4F9, dp(10)));
        amountField.setPadding(dp(14), dp(11), dp(14), dp(11));
        // 不给它 MovementMethod：这个框只收数字，用不着方向键挪光标，而系统那套 ArrowKeyMovementMethod
        // 在「Layout 还没跟着新文本重建」的窗口期按到「移到行尾」会在框架里抛
        // IndexOutOfBoundsException（setSpan (1...1) ends beyond length 0）——真机上按下 MOVE_END 崩过一次。
        amountField.setMovementMethod(null);
        amountField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (SoundStore.range() >= 0) return;         // 随机额度时这栏是被锁的
                SoundStore.amount(s.toString().trim());
                refreshSummary();
            }
        });
        inputRow.addView(amountField, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        playButton = new TextView(this);
        playButton.setText("开始播报");
        playButton.setTextSize(15f);
        playButton.setGravity(Gravity.CENTER);
        playButton.setTextColor(Color.WHITE);
        playButton.setBackground(round(BRAND, dp(10)));
        playButton.setOnClickListener(v -> onMainButton());
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(96), dp(48));
        playLp.leftMargin = dp(10);
        inputRow.addView(playButton, playLp);

        TextView amountHint = new TextView(this);
        amountHint.setText("输入「金额」可快捷播报，装逼必备~");
        amountHint.setTextSize(12f);
        amountHint.setTextColor(SUB);
        LinearLayout.LayoutParams amountHintLp = match();
        amountHintLp.topMargin = dp(8);
        amountCard.addView(amountHint, amountHintLp);

        errorLine = new TextView(this);
        errorLine.setTextSize(12f);
        errorLine.setTextColor(DANGER);
        errorLine.setVisibility(View.GONE);
        amountCard.addView(errorLine);

        // 三个模式：做成并排的小方块
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeLp = match();
        modeLp.topMargin = dp(12);
        amountCard.addView(modeRow, modeLp);
        modeRow.addView(modeCard(R.drawable.sound_once, "播报一次", SoundStore.MODE_ONCE), weighted());
        modeRow.addView(modeCard(R.drawable.sound_loop, "循环播报", SoundStore.MODE_LOOP), weighted());
        modeRow.addView(modeCard(R.drawable.sound_infinite, "无限收款", SoundStore.MODE_INFINITE), weighted());

        statusLine = new TextView(this);
        statusLine.setTextSize(13f);
        statusLine.setTextColor(BRAND);
        LinearLayout.LayoutParams statusLp = match();
        statusLp.topMargin = dp(10);
        amountCard.addView(statusLine, statusLp);

        stopLoop = new TextView(this);
        stopLoop.setText("取消循环播放");
        stopLoop.setTextSize(13f);
        stopLoop.setGravity(Gravity.CENTER);
        stopLoop.setTextColor(DANGER);
        stopLoop.setBackground(stroked(0xFFFFF5F5, dp(10), DANGER, dp(1f)));
        stopLoop.setPadding(dp(12), dp(10), dp(12), dp(10));
        stopLoop.setOnClickListener(v -> stopLooping(true));
        LinearLayout.LayoutParams stopLp = match();
        stopLp.topMargin = dp(10);
        amountCard.addView(stopLoop, stopLp);

        // ③ 随机金额
        LinearLayout rangeCard = card("随机金额", "不定额时按上面输入的金额播");
        LinearLayout.LayoutParams rangeLp = match();
        rangeLp.topMargin = dp(12);
        column.addView(rangeCard, rangeLp);
        rangeRows.add(rangeRow(rangeCard, 0, "不定额（自己输）", "请输入金额", -1, true));
        rangeRows.add(rangeRow(rangeCard, 0, "随机￥10-￥100之间", "随机播报金额区间10-100元", 0, false));
        rangeRows.add(rangeRow(rangeCard, 0, "随机￥100-￥1000之间", "随机播报金额区间100-1000元", 1, false));
        rangeRows.add(rangeRow(rangeCard, 0, "随机￥1000-￥10000之间", "随机播报金额区间1000-10000元", 2, false));

        // ④ 延时 + 倍速 并排
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bottomLp = match();
        bottomLp.topMargin = dp(12);
        column.addView(bottomRow, bottomLp);

        LinearLayout delayCard = card("延时播放", null);
        bottomRow.addView(delayCard, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout delayWrap = new LinearLayout(this);
        delayWrap.setOrientation(LinearLayout.HORIZONTAL);
        delayWrap.setPadding(0, dp(10), 0, 0);
        delayCard.addView(delayWrap);
        for (int seconds : DELAYS) {
            final int value = seconds;
            TextView chip = chip(seconds == 0 ? "立即" : seconds + "秒", v -> {
                SoundStore.delay(value);
                bindState();
            });
            delayChips.add(chip);
        }
        // 四个小标签一行放不下就分两行
        LinearLayout delayLine = new LinearLayout(this);
        delayLine.setOrientation(LinearLayout.HORIZONTAL);
        delayWrap.addView(delayLine);
        for (int i = 0; i < delayChips.size(); i++) {
            delayLine.addView(delayChips.get(i), i % 2 == 0 ? chipParams() : smallChipParams());
            if (i % 2 == 1 && i != delayChips.size() - 1) {
                delayLine = new LinearLayout(this);
                delayLine.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                nextLp.topMargin = dp(8);
                delayCard.addView(delayLine, nextLp);
            }
        }
        TextView delayHint = new TextView(this);
        delayHint.setText("选了延时，到点才响");
        delayHint.setTextSize(11f);
        delayHint.setTextColor(SUB);
        LinearLayout.LayoutParams delayHintLp = match();
        delayHintLp.topMargin = dp(8);
        delayCard.addView(delayHint, delayHintLp);

        LinearLayout speedCard = card("播报倍速", null);
        LinearLayout.LayoutParams speedCardLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        speedCardLp.leftMargin = dp(10);
        bottomRow.addView(speedCard, speedCardLp);
        LinearLayout speedWrap = new LinearLayout(this);
        speedWrap.setOrientation(LinearLayout.HORIZONTAL);
        speedWrap.setPadding(0, dp(10), 0, 0);
        speedCard.addView(speedWrap);
        for (float value : SPEEDS) {
            TextView chip = chip(speedLabel(value).replace("倍速", ""), v -> {
                SoundStore.speed(value);
                bindState();
            });
            speedChips.add(chip);
        }
        LinearLayout speedLine1 = new LinearLayout(this);
        speedLine1.setOrientation(LinearLayout.HORIZONTAL);
        speedWrap.addView(speedLine1);
        // 两个一行：卡片就半屏宽，三个「0.5 / 0.75 / 1.0」并排会被挤成一条
        for (int i = 0; i < speedChips.size(); i++) {
            speedLine1.addView(speedChips.get(i), chipParams());
            if (i % 2 == 1 && i != speedChips.size() - 1) {
                speedLine1 = new LinearLayout(this);
                speedLine1.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                nextLp.topMargin = dp(8);
                speedCard.addView(speedLine1, nextLp);
            }
        }
        TextView speedHint = new TextView(this);
        speedHint.setTextSize(11f);
        speedHint.setTextColor(SUB);
        LinearLayout.LayoutParams speedHintLp = match();
        speedHintLp.topMargin = dp(8);
        speedCard.addView(speedHint, speedHintLp);
        speedHintLine = speedHint;

        TextView footer = new TextView(this);
        footer.setText("调整好手机音量开始装逼吧~");
        footer.setTextSize(12f);
        footer.setTextColor(SUB);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = match();
        footerLp.topMargin = dp(16);
        column.addView(footer, footerLp);

        setContentView(root);
    }

    private TextView speedHintLine;

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(8);
        return lp;
    }

    private LinearLayout.LayoutParams smallChipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(4);
        return lp;
    }

    /** 模式选项：图标在上、文字在下的小方块。 */
    private LinearLayout modeCard(int icon, String label, int mode) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(11), dp(4), dp(11));

        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        box.addView(image, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(12f);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.topMargin = dp(6);
        box.addView(text, textLp);

        box.setOnClickListener(v -> {
            // 正在循环的时候换档：先把循环停下来，免得「播报一次」亮着声音还在循环
            if (looping || infinite) stopLooping(false);
            SoundStore.mode(mode);
            bindState();
        });
        modeCards.add(box);
        modeLabels.add(text);
        return box;
    }

    /** 随机金额的一行：左边文案、右边一个勾。 */
    private LinearLayout rangeRow(LinearLayout parent, int index, String title, String subtitle,
                                  int range, boolean first) {
        if (!first) parent.addView(divider());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(0, dp(11), 0, dp(11));
        box.setOnClickListener(v -> {
            SoundStore.range(range);
            bindState();
        });
        box.setTag(range);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        box.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView head = new TextView(this);
        head.setText(title);
        head.setTextSize(14f);
        head.setTextColor(INK);
        text.addView(head);
        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextSize(11f);
        sub.setTextColor(SUB);
        text.addView(sub);

        TextView mark = new TextView(this);
        mark.setTag("mark");
        mark.setTextSize(15f);
        mark.setTextColor(BRAND);
        box.addView(mark);

        parent.addView(box);
        return box;
    }

    /* ------------------------------ 状态同步 ------------------------------ */

    private void bindState() {
        // 风格
        boolean randomStyle = SoundStore.randomStyle();
        paintChip(styleChips.get(0), !randomStyle && SoundStore.style() == SoundStore.STYLE_ZHI);
        paintChip(styleChips.get(1), !randomStyle && SoundStore.style() == SoundStore.STYLE_WEI);
        paintChip(styleChips.get(2), randomStyle);

        // 模式
        int mode = SoundStore.mode();
        for (int i = 0; i < modeCards.size(); i++) {
            boolean on = i == mode;
            modeCards.get(i).setBackground(on ? round(BRAND, dp(10)) : round(0xFFF4F4F9, dp(10)));
            modeLabels.get(i).setTextColor(on ? Color.WHITE : INK);
        }

        // 随机区间
        for (LinearLayout row : rangeRows) {
            int value = (Integer) row.getTag();
            TextView mark = row.findViewWithTag("mark");
            if (mark != null) mark.setText(value == SoundStore.range() ? "✓" : "");
        }

        // 延时
        for (int i = 0; i < delayChips.size(); i++) {
            paintChip(delayChips.get(i), DELAYS[i] == SoundStore.delay());
        }
        // 倍速
        for (int i = 0; i < speedChips.size(); i++) {
            paintChip(speedChips.get(i), SPEEDS[i] == SoundStore.speed());
        }
        if (speedHintLine != null) {
            speedHintLine.setText("当前播报倍速是：" + speedLabel(SoundStore.speed()));
        }

        boolean manual = SoundStore.range() < 0;
        // 只在输入框没被编辑时回填：用户在打字的时候 setText 会让 EditText 的 Layout 和内容对不上，
        // 接着按方向键/移到行尾就会在系统里抛 IndexOutOfBoundsException（「setSpan (1...1) ends
        // beyond length 0」）—— 真机上按下 MOVE_END 崩过一次。
        if (manual && !amountField.hasFocus()) {
            String stored = SoundStore.amount();
            if (!amountField.getText().toString().equals(stored)) {
                amountField.setText(stored);
                amountField.setSelection(amountField.getText().length());
            }
        }
        amountField.setEnabled(manual);
        amountField.setAlpha(manual ? 1f : 0.5f);
        amountField.setHint(manual ? "请输入金额" : "金额由随机区间决定");

        boolean busy = looping || infinite;
        playButton.setText(busy ? "取消循环" : "开始播报");
        stopLoop.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (!busy) {
            stopDotTicker();
            statusLine.setText("设置成功，播报一下看看吧");
        } else {
            startDotTicker();
        }
        errorLine.setVisibility(View.GONE);
        refreshSummary();
    }

    private void paintChip(TextView chip, boolean on) {
        chip.setTextColor(on ? Color.WHITE : INK);
        chip.setBackground(on ? round(BRAND, dp(18)) : stroked(CHIP_BG, dp(18), LINE, dp(1f)));
    }

    /** 顶上那行摘要：这次会以小支还是小微、播多少钱、多快、什么时候响。 */
    private void refreshSummary() {
        StringBuilder text = new StringBuilder();
        if (SoundStore.randomStyle()) {
            text.append("随机小支/小微");
        } else {
            text.append(SoundStore.style() == SoundStore.STYLE_WEI ? "小微收款" : "小支收款");
        }
        text.append(" · ");
        int[] bounds = SoundStore.rangeBounds(SoundStore.range());
        if (bounds != null) {
            text.append("随机 ").append(bounds[0]).append('-').append(bounds[1]).append(" 元");
        } else {
            String amount = amountField == null ? SoundStore.amount() : amountField.getText().toString().trim();
            text.append(amount.isEmpty() ? "金额待输入" : amount + " 元");
        }
        text.append(" · ").append(speedLabel(SoundStore.speed()));
        if (SoundStore.delay() > 0) text.append(" · 延时 ").append(SoundStore.delay()).append(" 秒");
        if (infinite) text.append(" · 无限收款模式");
        else if (looping) text.append(" · 循环播报");
        summaryLine.setText(text.toString());
    }

    private String speedLabel(float value) {
        String text = String.format(Locale.US, "%.2f", value);
        if (text.matches("\\d+\\.\\d0")) text = text.substring(0, text.length() - 1);
        return text + "倍速";
    }

    /* ------------------------------ 播放 ------------------------------ */

    private void onMainButton() {
        if (looping || infinite) {
            stopLooping(true);
            return;
        }
        int mode = SoundStore.mode();
        if (mode == SoundStore.MODE_ONCE) {
            playOnce();
            return;
        }
        looping = mode == SoundStore.MODE_LOOP;
        infinite = mode == SoundStore.MODE_INFINITE;
        errorLine.setVisibility(View.GONE);
        playButton.setText("取消循环");
        stopLoop.setVisibility(View.VISIBLE);
        statusLine.setText(infinite ? "无限收款模式已开启" : "即将开始循环播报");
        refreshSummary();
        startDotTicker();
        // 「延时」在这里生效：等那几秒再开始第一轮
        handler.postDelayed(this::playRound, Math.max(0, SoundStore.delay()) * 1000L);
    }

    private void playOnce() {
        String amount = nextAmount(false);
        boolean wechat = nextStyle();
        errorLine.setVisibility(View.GONE);
        player.play(amount, wechat, SoundStore.speed(), new AccountSoundPlayer.Listener() {
            @Override
            public void onFinished() {
                statusLine.setText("设置成功，播报一下看看吧");
            }

            @Override
            public void onError(String message) {
                errorLine.setText(message);
                errorLine.setVisibility(View.VISIBLE);
                toast(message);
            }
        });
    }

    /** 循环里的每一轮：延时只在第一轮之前算，之后按原包的 5 秒间隔。 */
    private void playRound() {
        if (!looping && !infinite) return;
        String amount = nextAmount(infinite);
        boolean wechat = nextStyle();
        player.play(amount, wechat, SoundStore.speed(), new AccountSoundPlayer.Listener() {
            @Override
            public void onFinished() {
                if (!looping && !infinite) return;
                handler.postDelayed(AccountSoundActivity.this::playRound, SoundStore.LOOP_GAP_MS);
            }

            @Override
            public void onError(String message) {
                errorLine.setText(message);
                errorLine.setVisibility(View.VISIBLE);
                stopLooping(false);
            }
        });
    }

    /** 这一轮播多少钱：随机区间优先，无限收款模式每次换一档。 */
    private String nextAmount(boolean randomAlways) {
        int[] bounds = SoundStore.rangeBounds(SoundStore.range());
        if (bounds == null && randomAlways) bounds = SoundStore.rangeBounds(random.nextInt(3));
        if (bounds != null) {
            return String.valueOf(bounds[0] + random.nextInt(Math.max(1, bounds[1] - bounds[0] + 1)));
        }
        return amountField.getText().toString().trim();
    }

    private boolean nextStyle() {
        return SoundStore.randomStyle() ? random.nextBoolean() : SoundStore.style() == SoundStore.STYLE_WEI;
    }

    private void stopLooping(boolean tell) {
        boolean was = looping || infinite;
        looping = false;
        infinite = false;
        handler.removeCallbacksAndMessages(null);
        stopDotTicker();
        player.stop();
        if (was) {
            statusLine.setText("已暂停循环播报");
            if (tell) toast("已取消循环播放");
        }
        playButton.setText("开始播报");
        stopLoop.setVisibility(View.GONE);
        refreshSummary();
    }

    /* ------------------------------ 状态行的小点点 ------------------------------ */

    private void startDotTicker() {
        if (dotTicker != null) return;
        dotTicker = new Runnable() {
            @Override
            public void run() {
                if (!looping && !infinite) return;
                dotPhase = (dotPhase + 1) % 4;
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotPhase; i++) dots.append('.');
                statusLine.setText((infinite ? "无限收款模式已开启" : "正在循环播报中，间隔5秒响一次") + dots);
                handler.postDelayed(this, 400);
            }
        };
        handler.post(dotTicker);
    }

    private void stopDotTicker() {
        if (dotTicker != null) {
            handler.removeCallbacks(dotTicker);
            dotTicker = null;
        }
    }
}
