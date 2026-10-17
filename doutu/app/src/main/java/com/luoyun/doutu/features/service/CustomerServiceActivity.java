package com.luoyun.doutu.features.service;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.R;
import com.luoyun.doutu.account.DoutuApi;
import com.luoyun.doutu.account.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 「我 → 人工客服」。
 *
 * <p>一页三样东西，全部由后台「全局配置 → 客服」分组下发，改完刷新即可生效，
 * 不用发版：
 *
 * <ul>
 *   <li>二维码上方那行字 —— {@code cs_notice}（默认「开通会员、不会使用等问题均可询问客服」）</li>
 *   <li>中间的二维码图片 —— {@code cs_qr_image}（http(s) 地址；留空时显示占位提示）</li>
 *   <li>二维码下方那行手机号 / 微信号 —— {@code cs_contact}，<b>点一下复制到剪贴板</b></li>
 * </ul>
 *
 * <p>进页面先用上次落盘的那一份立刻画出来（不闪空白），同时后台再拉一次
 * {@code /api/v1/config}，拿到新值就刷新；拉不到也不影响已经显示的内容。
 */
public final class CustomerServiceActivity extends Activity {
    private static final int PAGE_BG = 0xFFF2F3F7;
    private static final int CARD = 0xFFFFFFFF;
    private static final int INK = 0xFF1B1B20;
    private static final int GRAY = 0xFF8A8F99;
    private static final int BRAND = 0xFF4A7BF7;
    private static final int PLACEHOLDER = 0xFFF4F5F8;

    private static final int QR_SIZE_DP = 232;
    private static final int QR_MAX_PX = 720;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    /** 拉回来之前先用这一份画（来自上次落盘）。 */
    private String notice = "";
    private String contact = "";
    private String qrUrl = "";

    public static Intent intentFor(Context context) {
        return new Intent(context, CustomerServiceActivity.class);
    }

    public static void open(Activity activity) {
        activity.startActivity(intentFor(activity));
        activity.overridePendingTransition(0, 0);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawable(new ColorDrawable(PAGE_BG));
        getWindow().setStatusBarColor(PAGE_BG);

        notice = Session.csNotice();
        contact = Session.csContact();
        qrUrl = Session.csQrImage();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAGE_BG);
        root.addView(toolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        ScrollView scroller = new ScrollView(this);
        scroller.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        content.setPadding(pad, dp(28), pad, dp(32));
        scroller.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        render();
        refresh();
    }

    /* ------------------------------------------------------------------ 取配置 */

    /** 拉一次最新配置：拿到了就覆盖三样东西重画。 */
    private void refresh() {
        DoutuApi.config(this, result -> {
            if (!result.ok() || result.data == null) return;
            Session.applyConfig(result.data);
            String nextNotice = Session.csNotice();
            String nextContact = Session.csContact();
            String nextQr = Session.csQrImage();
            boolean changed = !nextNotice.equals(notice) || !nextContact.equals(contact)
                    || !nextQr.equals(qrUrl);
            if (!changed) return;
            notice = nextNotice;
            contact = nextContact;
            qrUrl = nextQr;
            render();
        });
    }

    /* ------------------------------------------------------------------ 界面 */

    private View toolbar() {
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(CARD);

        ImageView back = new ImageView(this);
        back.setScaleType(ImageView.ScaleType.FIT_XY);
        back.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.order_ic_back));
        back.setPadding(dp(9), dp(14), dp(9), dp(14));
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(44), dp(52));
        bar.addView(back, backLp);

        TextView title = new TextView(this);
        title.setText("人工客服");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        View line = new View(this);
        line.setBackgroundColor(0xFFEDEEF3);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(CARD);
        wrap.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        wrap.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f))));
        return wrap;
    }

    private void render() {
        content.removeAllViews();

        // 二维码上方那行字（后台「客服页顶部文案」）
        TextView note = new TextView(this);
        note.setText(notice == null || notice.trim().isEmpty()
                ? "开通会员、不会使用等问题均可询问客服" : notice);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        note.setTextColor(INK);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(dp(4), 1f);
        content.addView(note, matchWrap());

        content.addView(space(dp(22)));

        // 中间的二维码（尺寸由外层给，卡片自己不加进 content）
        View qr = qrCard();
        content.addView(qr, new LinearLayout.LayoutParams(dp(QR_SIZE_DP), dp(QR_SIZE_DP)));

        content.addView(space(dp(22)));

        // 二维码下方那行联系方式，点一下复制
        content.addView(contactCard());
    }

    /** 白底圆角卡片里的二维码；地址没配或还没下载完时显示占位。 */
    private View qrCard() {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(CARD);
        shape.setCornerRadius(dp(14));
        card.setBackground(shape);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView placeholder = new TextView(this);
        placeholder.setText(qrUrl == null || qrUrl.trim().isEmpty()
                ? "二维码待后台配置" : "二维码加载中…");
        placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        placeholder.setTextColor(GRAY);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setBackgroundColor(PLACEHOLDER);
        card.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (qrUrl != null && !qrUrl.trim().isEmpty()) loadQr(qrUrl.trim(), image, placeholder);
        return card;
    }

    /** 二维码下方那行：手机号 / 微信号，点一下复制。 */
    private View contactCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(CARD);
        shape.setCornerRadius(dp(12));
        box.setBackground(shape);
        int padH = dp(18), padV = dp(12);
        box.setPadding(padH, padV, padH, padV);

        boolean has = contact != null && !contact.trim().isEmpty();
        TextView value = new TextView(this);
        value.setText(has ? contact.trim() : "客服联系方式待后台配置");
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextColor(has ? INK : GRAY);
        value.setGravity(Gravity.CENTER);
        box.addView(value, matchWrap());

        TextView hint = new TextView(this);
        hint.setText(has ? "点击复制" : "");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        hint.setTextColor(BRAND);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(5), 0, 0);
        box.addView(hint, matchWrap());

        if (has) {
            final String text = contact.trim();
            // 监听挂在整张卡片上，两个子 TextView 上也各挂一份：不管用户点到哪一行都能复制
            final TextView hintView = hint;
            View.OnClickListener listener = v -> {
                copy(text);
                // 复制完顺手把提示改成「已复制 ✓」，用户一眼就能确认
                hintView.setText("已复制 ✓");
            };
            box.setClickable(true);
            box.setOnClickListener(listener);
            value.setOnClickListener(listener);
            hint.setOnClickListener(listener);
        }
        return box;
    }

    private void copy(String text) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("客服", text));
        Toast.makeText(this, "已复制：" + text, Toast.LENGTH_SHORT).show();
    }

    /* ------------------------------------------------------------------ 二维码下载 */

    /** 后台配的是 http(s) 地址，这里直接下下来解码；失败就把占位改成提示。 */
    private void loadQr(final String url, final ImageView target, final TextView placeholder) {
        new Thread(() -> {
            Bitmap bitmap = download(url);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (bitmap == null) {
                    placeholder.setText("二维码加载失败");
                    placeholder.setVisibility(View.VISIBLE);
                    return;
                }
                target.setImageBitmap(bitmap);
                placeholder.setVisibility(View.GONE);
            });
        }, "cs-qr").start();
    }

    private static Bitmap download(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "doutu-demo");
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > 8 * 1024 * 1024) break;
                }
                byte[] bytes = out.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                int longest = Math.max(bounds.outWidth, bounds.outHeight);
                while (longest / (options.inSampleSize * 2) >= QR_MAX_PX) options.inSampleSize *= 2;
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /* ------------------------------------------------------------------ 小工具 */

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private View space(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, height));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
