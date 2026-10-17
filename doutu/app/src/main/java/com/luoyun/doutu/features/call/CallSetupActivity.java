package com.luoyun.doutu.features.call;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 「模拟来电」的设置页：点了不直接开始，先在这里把这场来电安排明白，到点了它才响。
 *
 * <p>四件事，对应四个区块：
 *   1. **来电人** —— 「微」模块通讯录里的联系人横着排一排，点一个就连名字带头像一起选上；
 *   2. **头像 + 名称** —— 头像可以单独从相册换一张，名字想叫什么就改什么；
 *   3. **铃声** —— 用页面内置那首，或者从手机里挑一个音频文件（挑完会复制一份到应用目录，
 *      因为网页读不到 {@code content://}，只能通过 {@code /__call_assets/} 这条自建路由给它）；
 *   4. **什么时候响** —— 立刻 / 多少分钟之后 / 指定钟点，后两种交给系统闹钟
 *      （{@link CallScheduler}）。
 *
 * <p>整场来电的界面还是「微」模块里那一页：这里只把「谁打来的、用什么头像、放哪首歌」交过去，
 * 所以看起来跟原来那场模拟来电完全一样。
 */
public final class CallSetupActivity extends Activity {
    private static final int REQUEST_AVATAR = 0x2c21;
    private static final int REQUEST_RINGTONE = 0x2c22;

    private static final int BRAND = 0xFF6F6AE8;
    private static final int INK = 0xFF23232F;
    private static final int SUB = 0xFF8A8A9A;
    private static final int CARD = 0xFFFFFFFF;
    private static final int LINE = 0xFFE7E7F0;

    private LinearLayout contactRow;
    private LinearLayout ringBox;
    private LinearLayout whenBox;
    private ImageView avatarPreview;
    private EditText nameField;
    private TextView ringtoneLabel;
    private TextView delayField;
    private TextView atField;
    private View pendingBar;
    private TextView pendingText;
    private Button startButton;

    private MediaPlayer preview;
    private boolean previewing;

    public static void open(Activity activity) {
        activity.startActivity(new Intent(activity, CallSetupActivity.class));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        buildUi();
        reloadContacts(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPendingBar();
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        super.onDestroy();
    }

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

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF5F6FA);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        root.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroller.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 标题栏 ───────────────────────────────────────────────────────
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(bar);

        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(16f);
        back.setTextColor(INK);
        back.setPadding(0, dp(6), dp(10), dp(6));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText("模拟来电");
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(INK);
        bar.addView(title);

        TextView hint = new TextView(this);
        hint.setText("设好来电人和时间，到点了它会自己响起来。");
        hint.setTextSize(13f);
        hint.setTextColor(SUB);
        LinearLayout.LayoutParams hintLp = wrap();
        hintLp.topMargin = dp(4);
        column.addView(hint, hintLp);

        // ── 待触发的来电（有才显示） ──────────────────────────────────────
        pendingBar = new LinearLayout(this);
        ((LinearLayout) pendingBar).setOrientation(LinearLayout.HORIZONTAL);
        ((LinearLayout) pendingBar).setGravity(Gravity.CENTER_VERTICAL);
        pendingBar.setBackground(round(0xFFFFF4E5, dp(12)));
        pendingBar.setPadding(dp(14), dp(10), dp(10), dp(10));
        pendingText = new TextView(this);
        pendingText.setTextSize(13f);
        pendingText.setTextColor(0xFF9A6A16);
        ((LinearLayout) pendingBar).addView(pendingText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(13f);
        cancel.setTextColor(BRAND);
        cancel.setPadding(dp(10), dp(4), dp(6), dp(4));
        cancel.setOnClickListener(v -> {
            CallScheduler.cancel(this);
            refreshPendingBar();
            toast("已取消待响的来电");
        });
        ((LinearLayout) pendingBar).addView(cancel);
        LinearLayout.LayoutParams pendingLp = match();
        pendingLp.topMargin = dp(12);
        column.addView(pendingBar, pendingLp);

        // ── 1. 来电人 ───────────────────────────────────────────────────
        column.addView(sectionTitle("来电人（从通讯录选择）"), topMargin(dp(16)));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        contactRow = new LinearLayout(this);
        contactRow.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(contactRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = match();
        scrollLp.topMargin = dp(8);
        column.addView(scroll, scrollLp);

        // ── 2. 头像 + 名称 ──────────────────────────────────────────────
        column.addView(sectionTitle("头像与名称"), topMargin(dp(18)));

        LinearLayout profile = new LinearLayout(this);
        profile.setOrientation(LinearLayout.HORIZONTAL);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setBackground(round(CARD, dp(14)));
        profile.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams profileLp = match();
        profileLp.topMargin = dp(8);
        column.addView(profile, profileLp);

        avatarPreview = new ImageView(this);
        avatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarPreview.setBackground(round(0xFFEDEDF3, dp(16)));
        avatarPreview.setOnClickListener(v -> pickAvatar());
        profile.addView(avatarPreview, new LinearLayout.LayoutParams(dp(72), dp(72)));

        LinearLayout profileText = new LinearLayout(this);
        profileText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = dp(14);
        profile.addView(profileText, textLp);

        nameField = new EditText(this);
        nameField.setSingleLine(true);
        nameField.setInputType(InputType.TYPE_CLASS_TEXT);
        nameField.setHint("来电人名称");
        nameField.setTextSize(17f);
        nameField.setTextColor(INK);
        nameField.setBackground(null);
        profileText.addView(nameField);

        TextView avatarHint = new TextView(this);
        avatarHint.setText("点头像可以从相册换一张");
        avatarHint.setTextSize(12f);
        avatarHint.setTextColor(SUB);
        profileText.addView(avatarHint);

        // ── 3. 铃声 ─────────────────────────────────────────────────────
        column.addView(sectionTitle("来电铃声"), topMargin(dp(18)));

        LinearLayout ring = new LinearLayout(this);
        ring.setOrientation(LinearLayout.VERTICAL);
        ring.setBackground(round(CARD, dp(14)));
        ring.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams ringLp = match();
        ringLp.topMargin = dp(8);
        column.addView(ring, ringLp);
        ringBox = ring;

        ring.addView(optionRow("默认铃声（应用内置）", "使用内置铃声", v -> {
            CallStore.useDefaultRingtone();
            refreshRingtone();
        }));
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        ring.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f))));

        LinearLayout local = new LinearLayout(this);
        local.setOrientation(LinearLayout.HORIZONTAL);
        local.setGravity(Gravity.CENTER_VERTICAL);
        local.setPadding(0, dp(12), 0, dp(12));
        prepareCustomRow(local, "本地音频");

        LinearLayout localText = new LinearLayout(this);
        localText.setOrientation(LinearLayout.VERTICAL);
        local.addView(localText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView localTitle = new TextView(this);
        localTitle.setText("本地音频");
        localTitle.setTextSize(15f);
        localTitle.setTextColor(INK);
        localText.addView(localTitle);
        ringtoneLabel = new TextView(this);
        ringtoneLabel.setTextSize(12f);
        ringtoneLabel.setTextColor(SUB);
        localText.addView(ringtoneLabel);

        Button pick = new Button(this);
        pick.setText("选择");
        pick.setAllCaps(false);
        pick.setTextSize(13f);
        pick.setTextColor(BRAND);
        pick.setBackground(stroked(CARD, dp(20), BRAND, dp(1.2f)));
        pick.setOnClickListener(v -> pickRingtone());
        local.addView(pick, new LinearLayout.LayoutParams(dp(64), dp(36)));

        Button listen = new Button(this);
        listen.setText("试听");
        listen.setAllCaps(false);
        listen.setTextSize(13f);
        listen.setTextColor(INK);
        listen.setBackground(stroked(CARD, dp(20), LINE, dp(1.2f)));
        listen.setOnClickListener(v -> togglePreview());
        LinearLayout.LayoutParams listenLp = new LinearLayout.LayoutParams(dp(64), dp(36));
        listenLp.leftMargin = dp(8);
        local.addView(listen, listenLp);

        ring.addView(local);

        // ── 4. 什么时候响 ───────────────────────────────────────────────
        column.addView(sectionTitle("什么时候响"), topMargin(dp(18)));

        LinearLayout when = new LinearLayout(this);
        when.setOrientation(LinearLayout.VERTICAL);
        when.setBackground(round(CARD, dp(14)));
        when.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams whenLp = match();
        whenLp.topMargin = dp(8);
        column.addView(when, whenLp);
        whenBox = when;

        when.addView(optionRow("立即", "现在就打过来", v -> {
            CallStore.time(CallStore.MODE_NOW, CallStore.delayMinutes(),
                    CallStore.atHour(), CallStore.atMinute());
            syncTimeRow();
        }));
        when.addView(rowDivider());

        LinearLayout delayRow = new LinearLayout(this);
        delayRow.setOrientation(LinearLayout.HORIZONTAL);
        delayRow.setGravity(Gravity.CENTER_VERTICAL);
        delayRow.setPadding(0, dp(10), 0, dp(10));
        prepareCustomRow(delayRow, "多少分钟之后");
        TextView delayTitle = new TextView(this);
        delayTitle.setText("多少分钟之后");
        delayTitle.setTextSize(15f);
        delayTitle.setTextColor(INK);
        delayRow.addView(delayTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        delayField = new EditText(this);
        delayField.setSingleLine(true);
        delayField.setInputType(InputType.TYPE_CLASS_NUMBER);
        delayField.setTextSize(15f);
        delayField.setGravity(Gravity.END);
        delayField.setBackground(null);
        // 在这里打字/点进来就等于选了「多少分钟之后」这一档
        delayField.setOnClickListener(v -> selectDelay());
        delayField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) selectDelay();
        });
        delayRow.addView(delayField, new LinearLayout.LayoutParams(dp(56),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView delayUnit = new TextView(this);
        delayUnit.setText("分钟后响");
        delayUnit.setTextSize(13f);
        delayUnit.setTextColor(SUB);
        delayRow.addView(delayUnit);
        when.addView(delayRow);
        when.addView(rowDivider());

        LinearLayout atRow = new LinearLayout(this);
        atRow.setOrientation(LinearLayout.HORIZONTAL);
        atRow.setGravity(Gravity.CENTER_VERTICAL);
        atRow.setPadding(0, dp(10), 0, dp(10));
        prepareCustomRow(atRow, "指定时间");
        TextView atTitle = new TextView(this);
        atTitle.setText("指定时间");
        atTitle.setTextSize(15f);
        atTitle.setTextColor(INK);
        atRow.addView(atTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        atField = new TextView(this);
        atField.setTextSize(15f);
        atField.setTextColor(BRAND);
        atField.setPadding(dp(10), dp(6), dp(10), dp(6));
        atField.setBackground(stroked(CARD, dp(18), BRAND, dp(1.2f)));
        atField.setOnClickListener(v -> pickTime());
        atRow.addView(atField);
        when.addView(atRow);

        // ── 开始 ────────────────────────────────────────────────────────
        startButton = new Button(this);
        startButton.setText("开始来电");
        startButton.setAllCaps(false);
        startButton.setTextSize(17f);
        startButton.setTextColor(Color.WHITE);
        startButton.setBackground(round(BRAND, dp(24)));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        startLp.topMargin = dp(22);
        column.addView(startButton, startLp);
        startButton.setOnClickListener(v -> start());

        setContentView(root);
        // 上一次设过什么就先显示什么
        nameField.setText(CallStore.name());
        refreshAvatar();
        refreshRingtone();
        syncTimeRow();
        refreshPendingBar();
    }

    /* ------------------------------ 小工具 ------------------------------ */

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = margin;
        return lp;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14f);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(INK);
        return view;
    }

    private View rowDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f))));
        return divider;
    }

    /** 一整行可点的选项：标题 + 说明 + 右边一个「已选」标记。 */
    private View optionRow(String title, String subtitle, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setTag(title);
        row.setOnClickListener(click);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        text.setTag("text");
        TextView head = new TextView(this);
        head.setText(title);
        head.setTextSize(15f);
        head.setTextColor(INK);
        text.addView(head);
        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextSize(12f);
        sub.setTextColor(SUB);
        text.addView(sub);

        TextView mark = new TextView(this);
        mark.setTag("mark");
        mark.setTextSize(15f);
        mark.setTextColor(BRAND);
        row.addView(mark);
        return row;
    }

    /** 当前选中的那一行画个勾，其余清掉。 */
    private void markOption(ViewGroup container, String title) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child.getTag() == null) continue;
            View mark = child.findViewWithTag("mark");
            if (mark instanceof TextView) {
                ((TextView) mark).setText(title.equals(child.getTag()) ? "✓" : "");
            }
        }
    }

    /**
     * 给「自己搭的那种行」（本地音频 / 多少分钟之后 / 指定时间）也配上一个勾。
     *
     * <p>这几行里有输入框或按钮，没法用 {@link #optionRow} 生成，但要让 {@link #markOption}
     * 认得出它们：所以补一个 tag 和一个带 mark 标签的勾（放在最左边，不和右边的输入框抢位置）。
     */
    private TextView prepareCustomRow(LinearLayout row, String title) {
        row.setTag(title);
        TextView mark = new TextView(this);
        mark.setTag("mark");
        mark.setTextSize(15f);
        mark.setTextColor(BRAND);
        mark.setMinWidth(dp(20));
        row.addView(mark, 0);
        return mark;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private TextView chip(String text, View.OnClickListener click) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(13f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setOnClickListener(click);
        return chip;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        return lp;
    }

    /* ------------------------------ 来电人 ------------------------------ */

    private void reloadContacts(boolean force) {
        final List<CallContact> list = CallContacts.cached(this);
        fillContacts(list);
        if (!force) {
            // 页面里那份是真的通讯录，问一次、有就换掉兜底名单
            CallContacts.refresh(this, fromPage -> {
                if (!fromPage) return;
                fillContacts(CallContacts.cached(CallSetupActivity.this));
            });
        }
    }

    private void fillContacts(List<CallContact> list) {
        contactRow.removeAllViews();
        String current = CallStore.name();
        for (CallContact contact : list) {
            boolean chosen = contact.name.equals(current);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(dp(6), dp(4), dp(6), dp(4));

            ImageView avatar = new ImageView(this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setTag(contact.avatarUrl);
            avatar.setBackground(stroked(chosen ? BRAND : CARD, dp(28), chosen ? BRAND : LINE, dp(2)));
            item.addView(avatar, new LinearLayout.LayoutParams(dp(56), dp(56)));
            CallImages.into(this, contact.avatarUrl, 0, avatar);

            TextView label = new TextView(this);
            label.setText(contact.name);
            label.setTextSize(12f);
            label.setTextColor(chosen ? BRAND : INK);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams labelLp = wrap();
            labelLp.topMargin = dp(6);
            labelLp.width = dp(64);
            item.addView(label, labelLp);

            item.setOnClickListener(v -> {
                CallStore.pickContact(contact);
                nameField.setText(contact.name);
                stopPreview();
                refreshAvatar();
                fillContacts(CallContacts.cached(this));
            });

            LinearLayout.LayoutParams itemLp = wrap();
            itemLp.rightMargin = dp(4);
            contactRow.addView(item, itemLp);
        }
    }

    private void refreshAvatar() {
        String file = CallStore.avatarFile();
        if (!file.isEmpty()) {
            java.io.File target = CallStore.assetFile(file);
            Bitmap bitmap = target != null && target.isFile()
                    ? BitmapFactory.decodeFile(target.getAbsolutePath()) : null;
            if (bitmap != null) {
                avatarPreview.setTag(null);
                avatarPreview.setImageBitmap(bitmap);
                return;
            }
        }
        String url = CallStore.avatarUrl();
        avatarPreview.setTag(url);
        avatarPreview.setImageDrawable(null);
        if (!url.isEmpty()) CallImages.into(this, url, 0, avatarPreview);
    }

    private void pickAvatar() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_AVATAR);
        } catch (Exception e) {
            toast("打不开相册");
        }
    }

    /* ------------------------------ 铃声 ------------------------------ */

    private void refreshRingtone() {
        String label = CallStore.ringtoneLabel();
        ringtoneLabel.setText(label.isEmpty() ? "未选择，使用内置铃声" : label);
        markOption(ringBox, label.isEmpty() ? "默认铃声（应用内置）" : "本地音频");
    }

    /** 点/进延时那一格：把时间档位切到「多少分钟之后」。 */
    private void selectDelay() {
        CallStore.time(CallStore.MODE_DELAY, readDelay(), CallStore.atHour(), CallStore.atMinute());
        markOption(whenBox, "多少分钟之后");
    }

    private void pickRingtone() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        try {
            startActivityForResult(intent, REQUEST_RINGTONE);
        } catch (Exception e) {
            toast("打不开文件选择器");
        }
    }

    private void togglePreview() {
        if (previewing) {
            stopPreview();
            return;
        }
        java.io.File file = CallStore.assetFile(CallStore.ringtoneFile());
        try {
            preview = new MediaPlayer();
            if (file != null && file.isFile()) {
                preview.setDataSource(file.getAbsolutePath());
            } else {
                android.content.res.AssetFileDescriptor fd =
                        getAssets().openFd("fake_world/assets/audio/call.mp3");
                preview.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                fd.close();
            }
            preview.setLooping(false);
            preview.setOnCompletionListener(mp -> stopPreview());
            preview.prepare();
            preview.start();
            previewing = true;
            toast("试听中…再点一次停止");
        } catch (Exception e) {
            stopPreview();
            toast("这段音频放不出来");
        }
    }

    private void stopPreview() {
        previewing = false;
        if (preview == null) return;
        try {
            preview.stop();
        } catch (Exception ignored) {
            // 还没真正开始放
        }
        preview.release();
        preview = null;
    }

    /* ------------------------------ 时间 ------------------------------ */

    private void syncTimeRow() {
        delayField.setText(String.valueOf(CallStore.delayMinutes()));
        int hour = CallStore.atHour();
        int minute = CallStore.atMinute();
        atField.setText(hour < 0 ? "选择时间" : String.format(Locale.US, "%02d:%02d", hour, minute));
        String title = CallStore.timeMode() == CallStore.MODE_DELAY ? "多少分钟之后"
                : CallStore.timeMode() == CallStore.MODE_AT ? "指定时间" : "立即";
        markOption(whenBox, title);
    }

    private void pickTime() {
        Calendar now = Calendar.getInstance();
        int hour = CallStore.atHour() >= 0 ? CallStore.atHour() : now.get(Calendar.HOUR_OF_DAY);
        int minute = CallStore.atHour() >= 0 ? CallStore.atMinute() : now.get(Calendar.MINUTE);
        new TimePickerDialog(this, (view, h, m) -> {
            CallStore.time(CallStore.MODE_AT, readDelay(), h, m);
            syncTimeRow();
        }, hour, minute, true).show();
    }

    private int readDelay() {
        try {
            int value = Integer.parseInt(delayField.getText().toString().trim());
            return value < 1 ? 1 : value;
        } catch (Exception e) {
            return 1;
        }
    }

    /* ------------------------------ 开始 / 待触发 ------------------------------ */

    private void refreshPendingBar() {
        long at = CallScheduler.pendingAt(this);
        if (at <= 0L) {
            pendingBar.setVisibility(View.GONE);
            return;
        }
        pendingBar.setVisibility(View.VISIBLE);
        pendingText.setText("已安排："
                + new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(at)) + " 来电");
    }

    private void start() {
        String name = nameField.getText().toString().trim();
        if (name.isEmpty()) {
            toast("给来电人起个名字");
            return;
        }
        CallStore.rename(name);

        // 三个时间档位里，用户最后动的是哪一个就以哪个为准
        stopPreview();
        int mode = CallStore.timeMode();
        if (mode == CallStore.MODE_NOW) {
            CallStore.time(CallStore.MODE_NOW, readDelay(), CallStore.atHour(), CallStore.atMinute());
            stopPreview();
            CallScheduler.startNow(this, CallScheduler.payload());
            finish();
            return;
        }
        if (mode == CallStore.MODE_DELAY) {
            int minutes = readDelay();
            CallStore.time(CallStore.MODE_DELAY, minutes, CallStore.atHour(), CallStore.atMinute());
            schedule(System.currentTimeMillis() + minutes * 60_000L, minutes + " 分钟后");
            return;
        }
        int hour = CallStore.atHour();
        if (hour < 0) {
            toast("先选一个时间");
            return;
        }
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, CallStore.atMinute());
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (target.getTimeInMillis() <= System.currentTimeMillis()) target.add(Calendar.DAY_OF_MONTH, 1);
        CallStore.time(CallStore.MODE_AT, readDelay(), hour, CallStore.atMinute());
        schedule(target.getTimeInMillis(), String.format(Locale.US, "%02d:%02d", hour, CallStore.atMinute()));
    }

    private void schedule(long triggerAt, String label) {
        stopPreview();
        CallScheduler.scheduleAt(this, triggerAt, CallScheduler.payload());
        refreshPendingBar();
        askNotificationPermission();
        new AlertDialog.Builder(this)
                .setTitle("已安排好")
                .setMessage(label + "来电，届时会自动响起。\n\n"
                        + "这期间可以退出应用或锁屏，到点会自己弹出来。\n\n"
                        + "个别手机会限制应用在后台弹界面，所以到点还会挂一条通知兜底 —— "
                        + "如果界面没自己弹出来，点那条「模拟来电」通知一样能接。")
                .setPositiveButton("好的", (d, w) -> {
                    // 提示得先弹：这个页面已经 finish 之后再 show 对话框是弹不出来的
                    if (!offerBackgroundSettings()) finish();
                })
                .setNegativeButton("留在这里", (d, w) -> {
                    refreshPendingBar();
                    offerBackgroundSettings();
                })
                .show();
    }

    /**
     * 让用户把豆图从「电池优化 / 后台限制」里放出来。
     *
     * <p>真机上踩过的坑：应用退到后台后，系统（尤其 vivo/OPPO/小米这类 ROM）会连闹钟广播一起吃掉 ——
     * dumpsys 里闹钟没了，接收器一行日志都没有，界面自然也不响。这是系统级开关，代码绕不过去，
     * 只能把用户领到正确的那个设置页，并且提示国产 ROM 还有「自启动」「后台弹出界面」这两个额外开关。
     *
     * @return 有没有弹出这个提示（弹了就由它负责关掉本页）
     */
    private boolean offerBackgroundSettings() {
        android.os.PowerManager power =
                (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (power == null || power.isIgnoringBatteryOptimizations(getPackageName())) return false;
        new AlertDialog.Builder(this)
                .setTitle("还想更稳一点吗")
                .setMessage("系统会在你不打开豆图的时候限制它，那样到点可能弹不出来（实测有些手机连闹钟"
                        + "都会被吃掉）。\n\n"
                        + "· 点「去设置」把豆图的电池限制改成「不限制 / 允许后台运行」\n"
                        + "· 如果是 vivo / OPPO / 小米，再到手机的「自启动」和「后台弹出界面」里给豆图放行\n\n"
                        + "都开着，到点就能稳稳地自己弹出来。")
                .setPositiveButton("去设置", (d, w) -> {
                    openBatterySettings();
                    finish();
                })
                .setNegativeButton("以后再说", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
        return true;
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                toast("打不开系统设置");
            }
        }
    }

    /** Android 13 起通知要用户点头，兜底那条通知才有意义，所以顺手问一次。 */
    private void askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0x2c30);
    }

    /* ------------------------------ 选完文件回来 ------------------------------ */

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == REQUEST_AVATAR) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (CallStore.saveAvatar(in)) {
                    refreshAvatar();
                    fillContacts(CallContacts.cached(this));
                    toast("头像已更换");
                } else {
                    toast("这张图片读不出来");
                }
            } catch (Exception e) {
                toast("这张图片读不出来");
            }
            return;
        }
        if (request == REQUEST_RINGTONE) {
            String extension = extensionOf(uri);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (CallStore.saveRingtone(in, displayName(uri), extension)) {
                    refreshRingtone();
                    toast("铃声已设置");
                } else {
                    toast("这个文件读不出来");
                }
            } catch (Exception e) {
                toast("这个文件读不出来");
            }
        }
    }

    /** 选中的文件叫什么（文件选择器给的就是最后一段）。 */
    private String displayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) return value.trim();
                }
            }
        } catch (Exception ignored) {
            // 下面还有一条按 URI 猜的路
        }
        String last = uri.getLastPathSegment();
        if (last == null) return "";
        // DocumentsUI 的 URI 末段是文档 id（msf:1000001520 这种），不是文件名：别当成名字显示
        if (last.contains(":")) {
            int colon = last.lastIndexOf(':');
            String tail = last.substring(colon + 1);
            return tail.matches("[0-9]+") ? "" : tail;
        }
        int slash = last.lastIndexOf('/');
        return slash >= 0 ? last.substring(slash + 1) : last;
    }

    private String extensionOf(Uri uri) {
        String name = displayName(uri);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.US);
            if (ext.matches("[a-z0-9]{1,5}")) return ext;
        }
        // 名字里没有扩展名就按 MIME 猜一个，WebView 那边要靠它决定 audio/* 的类型
        String mime = getContentResolver().getType(uri);
        if (mime != null) {
            if (mime.contains("mpeg")) return "mp3";
            if (mime.contains("mp4") || mime.contains("m4a")) return "m4a";
            if (mime.contains("aac")) return "aac";
            if (mime.contains("ogg")) return "ogg";
            if (mime.contains("wav") || mime.contains("x-wav")) return "wav";
            if (mime.contains("flac")) return "flac";
        }
        return "mp3";
    }
}
