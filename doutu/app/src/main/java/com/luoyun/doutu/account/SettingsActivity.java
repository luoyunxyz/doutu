package com.luoyun.doutu.account;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

/**
 * 账号设置页（登录后点「我」页用户行进入）。
 *
 * 版式照参考稿：返回箭头 + 居中标题「设置」→ 一张白卡里四行
 * （头像 / 用户名 / 手机号 / 会员状态）→ 底部一颗描边胶囊「退出登录」。
 * 手机号按需求**只做一行**，且中间四位打码。
 */
public final class SettingsActivity extends Activity {

    private static final int INK = 0xFF1B1F27;
    private static final int SUB = 0xFF8A8F9A;
    private static final int LINE = 0xFFEFF1F6;
    private static final int BRAND = 0xFF2F6FED;
    private static final int CARD_BG = 0xFFF6F8FE;

    private LinearLayout card;
    private AvatarView avatarView;
    private TextView usernameValue;
    private TextView phoneValue;
    private TextView memberValue;

    private static final int REQ_PICK_AVATAR = 1001;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(buildUi());
        refresh();
        refreshFromServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /* ------------------------------ 界面 ------------------------------ */

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(CARD_BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(26));
        // 高度撑满视口：配合下面那个 weight=1 的占位，把「退出登录」压到页面最下方
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 标题栏：返回箭头 + 居中「设置」
        FrameLayout header = new FrameLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        root.addView(header);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(38);
        back.setTextColor(INK);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, 0, dp(6), dp(6));
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(56), dp(56));
        backParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        header.addView(back, backParams);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        header.addView(title, titleParams);

        // 白卡
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(18));
        card.setBackground(cardBg);
        card.setElevation(dp(6));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(12);
        card.setLayoutParams(cardParams);
        root.addView(card);

        // 头像（值在右侧，按参考稿）：固定圆形，点它换图、长按恢复默认
        // 2026-10-17: 点它打开的是「随机头像 4×4 + 换一批 + 从本地上传」那张面板（与我页点头像一致）
        avatarView = new AvatarView(this);
        LinearLayout avatarRow = row("头像", avatarView, true, dp(56), dp(56));
        avatarRow.setOnClickListener(v -> AvatarUpload.pick(this, this::refresh));
        avatarRow.setOnLongClickListener(v -> {
            if (Session.avatarUrl().isEmpty()) {
                toast("当前就是默认头像，点一下可以上传");
                return true;
            }
            new AlertDialog.Builder(this)
                    .setTitle("恢复默认头像")
                    .setMessage("会删掉已上传的头像，回到用户名首字。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("恢复", (d, w) -> DoutuApi.removeAvatar(this, r -> {
                        if (!r.ok()) {
                            toast(r.msg == null || r.msg.isEmpty() ? "操作失败" : r.msg);
                            return;
                        }
                        Session.updateUser(r.user());
                        AvatarStore.clear();
                        refresh();
                        AvatarStore.load(this, this::refresh);
                        toast("已恢复默认头像");
                    }))
                    .show();
            return true;
        });

        // 用户名
        usernameValue = valueText();
        LinearLayout nameRow = row("用户名", usernameValue, true, 0, 0);
        nameRow.setOnClickListener(v -> renameDialog());

        // 手机号（按需求只保留一行，且打码）
        phoneValue = valueText();
        LinearLayout phoneRow = row("手机号", phoneValue, true, 0, 0);
        phoneRow.setOnClickListener(v -> toast("手机号不可自助修改，请联系客服"));

        // 会员状态：行首一枚小金冠，值紧跟标签（参考稿就是这个排法）
        LinearLayout memberRow = new LinearLayout(this);
        memberRow.setOrientation(LinearLayout.HORIZONTAL);
        memberRow.setGravity(Gravity.CENTER_VERTICAL);
        memberRow.setPadding(dp(18), dp(18), dp(16), dp(18));
        memberRow.setClickable(true);

        CrownIcon crown = new CrownIcon(this);
        LinearLayout.LayoutParams crownParams = new LinearLayout.LayoutParams(dp(24), dp(21));
        crownParams.rightMargin = dp(10);
        memberRow.addView(crown, crownParams);

        TextView memberLabel = new TextView(this);
        memberLabel.setText("会员状态");
        memberLabel.setTextSize(16);
        memberLabel.setTextColor(INK);
        memberRow.addView(memberLabel);

        memberValue = valueText();
        memberValue.setGravity(Gravity.START);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueParams.leftMargin = dp(10);
        memberRow.addView(memberValue, valueParams);

        memberRow.addView(chevron());
        memberRow.setOnClickListener(v -> memberDialog());

        // 上一行与这一行之间的分隔线
        View memberDivider = new View(this);
        memberDivider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams mdParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        mdParams.leftMargin = dp(18);
        card.addView(memberDivider, mdParams);
        card.addView(memberRow);

        // 占位：把「退出登录」顶到页面最下方（内容比屏幕高时它会收缩为 0，不影响滚动）
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部：描边胶囊「退出登录」
        TextView logout = new TextView(this);
        logout.setText("退出登录");
        logout.setTextSize(17);
        logout.setTypeface(Typeface.DEFAULT_BOLD);
        logout.setTextColor(0xFF2B3A55);
        logout.setGravity(Gravity.CENTER);
        GradientDrawable logoutBg = new GradientDrawable();
        logoutBg.setColor(Color.WHITE);
        logoutBg.setCornerRadius(dp(30));
        logoutBg.setStroke(dp(1.6f), 0xFF9FB3D9);
        logout.setBackground(logoutBg);
        logout.setPadding(0, dp(16), 0, dp(16));
        LinearLayout.LayoutParams logoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logoutParams.topMargin = dp(28);
        logout.setLayoutParams(logoutParams);
        logout.setOnClickListener(v -> confirmLogout());
        root.addView(logout);

        return scroll;
    }

    /**
     * 一行设置项：左标签 + 右侧值（可带箭头）。
     * fixedW/fixedH > 0 时给值固定尺寸（头像用），否则值占满剩余宽度并右对齐。
     */
    private LinearLayout row(String label, View value, boolean withChevron, int fixedW, int fixedH) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(16), dp(16), dp(16));
        row.setClickable(true);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(16);
        labelView.setTextColor(INK);
        row.addView(labelView);

        if (fixedW > 0 && fixedH > 0) {
            // 固定尺寸的值（头像）：用一个占位 View 把它顶到右边。
            // 注意不能给值本身设 weight —— weight 会覆盖固定宽度，圆形会被拉成椭圆。
            View spacer = new View(this);
            row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
            row.addView(value, new LinearLayout.LayoutParams(fixedW, fixedH));
        } else {
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (value instanceof TextView) {
                ((TextView) value).setGravity(Gravity.END);
            }
            value.setPadding(0, 0, dp(8), 0);
            row.addView(value, valueParams);
        }

        if (withChevron) row.addView(chevron());
        if (card.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(LINE);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dividerParams.leftMargin = dp(18);
            card.addView(divider, dividerParams);
        }
        card.addView(row);
        return row;
    }

    private TextView valueText() {
        TextView tv = new TextView(this);
        tv.setTextSize(16);
        tv.setTextColor(0xFF6B7280);
        tv.setMaxLines(1);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return tv;
    }

    private TextView chevron() {
        TextView tv = new TextView(this);
        tv.setText("›");
        tv.setTextSize(22);
        tv.setTextColor(0xFFB5BCC8);
        tv.setPadding(dp(4), 0, 0, 0);
        return tv;
    }

    /* ------------------------------ 头像上传 ------------------------------ */

    /** 打开系统图片选择器（不引第三方库）。 */
    private void pickAvatar() {
        Intent pick = new Intent(Intent.ACTION_PICK);
        pick.setType("image/*");
        try {
            startActivityForResult(pick, REQ_PICK_AVATAR);
        } catch (Exception e) {
            Intent get = new Intent(Intent.ACTION_GET_CONTENT);
            get.setType("image/*");
            try {
                startActivityForResult(Intent.createChooser(get, "选择头像"), REQ_PICK_AVATAR);
            } catch (Exception e2) {
                toast("这台设备没有可用的图片选择器");
            }
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        // 换头像面板里「从本地上传」的相册结果先交给它（它自己拿着回调）
        if (AvatarUpload.deliver(this, request, result, data)) {
            refresh();
            return;
        }
        if (request != REQ_PICK_AVATAR || result != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        toast("正在处理图片…");
        new Thread(() -> {
            final String base64 = readAndEncode(uri);
            runOnUiThread(() -> {
                if (base64 == null) {
                    toast("读取图片失败，换一张试试");
                    return;
                }
                DoutuApi.uploadAvatar(this, base64, r -> {
                    if (!r.ok()) {
                        toast(r.msg == null || r.msg.isEmpty() ? "上传失败" : r.msg);
                        return;
                    }
                    Session.updateUser(r.user());
                    AvatarStore.clear();
                    AvatarStore.load(this, this::refresh);
                    toast("头像已更新");
                });
            });
        }).start();
    }

    /**
     * 读图 → 压到最长边 320px → JPEG base64。
     * 压缩是必须的：服务端请求体上限 2MB、头像上限 1MB，原图动辄几 MB。
     */
    private String readAndEncode(android.net.Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (maxSide / sample > 640) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap decoded;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(in, null, opts);
            }
            if (decoded == null) return null;

            // 缩到最长边 320
            int w = decoded.getWidth(), h = decoded.getHeight();
            float scale = 320f / Math.max(w, h);
            Bitmap target = decoded;
            if (scale < 1f) {
                target = Bitmap.createScaledBitmap(decoded, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
                if (target != decoded) decoded.recycle();
            }

            byte[] bytes = compress(target, 88);
            if (bytes.length > 700 * 1024) bytes = compress(target, 60);
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            android.util.Log.w("SettingsActivity", "读取头像失败 → " + e);
            return null;
        }
    }

    private byte[] compress(Bitmap bitmap, int quality) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }

    /* ------------------------------ 状态 ------------------------------ */

    private void refresh() {
        avatarView.setInitial(firstChar(Session.displayName()));
        avatarView.setBitmap(AvatarStore.bitmap());
        usernameValue.setText(Session.username().isEmpty() ? Session.displayName() : Session.username());
        phoneValue.setText(maskPhone(Session.phone()));
        memberValue.setText(Session.isActivated()
                ? "VIP 会员 · 有效期至 " + (Session.expireAt().isEmpty() ? "-" : Session.expireAt().substring(0, 10).replace('-', '.'))
                : "未开通会员 · 带水印");
        memberValue.setTextColor(Session.isActivated() ? 0xFFB8860B : 0xFF6B7280);
    }

    /** 拉一次最新状态，回来后刷新这页（会员状态可能在后台被改过）。 */
    private void refreshFromServer() {
        if (!Session.isLoggedIn()) return;
        DoutuApi.status(this, result -> {
            if (result.code == 2005) {
                Session.clear();
                refresh();
                return;
            }
            if (result.data == null) return;
            Session.updateUser(result.user());
            refresh();
        });
    }

    /** 138****8826 这种打码形式。 */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone == null || phone.isEmpty() ? "-" : phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String firstChar(String text) {
        if (text == null || text.isEmpty()) return "登";
        return text.substring(0, 1);
    }

    /* ------------------------------ 交互 ------------------------------ */

    private void renameDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(Session.username());
        input.setSelection(input.getText().length());
        input.setHint("2~20 位，中文/字母/数字/下划线");
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(16), dp(8), dp(16), 0);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("修改用户名")
                .setMessage("用户名同时也是登录账号，改完可立即用新用户名登录，手机号不受影响。")
                .setView(wrap)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String name = input.getText().toString().trim();
                    String err = LoginActivity.checkUsername(name);
                    if (err != null) {
                        toast(err);
                        return;
                    }
                    DoutuApi.changeUsername(this, name, result -> {
                        if (!result.ok()) {
                            toast(result.msg == null || result.msg.isEmpty() ? "修改失败" : result.msg);
                            return;
                        }
                        Session.setUsername(name);
                        Session.updateUser(result.user());
                        refresh();
                        toast("用户名已修改为 " + name);
                    });
                })
                .show();
    }

    private void memberDialog() {
        String title = Session.isActivated() ? "会员状态" : "未开通会员";
        StringBuilder sb = new StringBuilder();
        sb.append("用户名：").append(Session.username().isEmpty() ? Session.displayName() : Session.username()).append('\n');
        sb.append("手机号：").append(maskPhone(Session.phone())).append('\n');
        sb.append("状态：").append(Session.statusText()).append('\n');
        if (Session.isActivated()) {
            sb.append("到期：").append(Session.expireAt().isEmpty() ? "-" : Session.expireAt());
        } else {
            sb.append('\n').append("未开通会员也可以正常使用，只是界面与成品会带水印。\n");
            sb.append("开通后水印会自动消失，请联系客服在后台为你开通。");
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(sb.toString())
                .setNegativeButton("关闭", null)
                .setPositiveButton("刷新状态", (d, w) -> refreshFromServer())
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("退出后仍然可以继续使用，只是不再记录你的使用额度。")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", (d, w) -> {
                    // 交给 DoutuApi.logout：它先用当前 token 撤销服务端会话，成功后清本地。
                    // （不能在这里先 Session.clear()，否则请求会带着空 token 出去，服务端撤销不掉。）
                    DoutuApi.logout(this, r -> {
                        toast("已退出登录");
                        finish();
                    });
                })
                .show();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static void open(Context context) {
        Intent intent = new Intent(context, SettingsActivity.class);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** 会员状态前面那枚小金冠（纯 Path，不依赖 emoji 字体）。 */
    private static final class CrownIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CrownIcon(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            Path crown = new Path();
            float left = w * 0.06f, right = w * 0.94f, top = h * 0.10f, base = h * 0.72f;
            crown.moveTo(left, base);
            crown.lineTo(left, top + h * 0.16f);
            crown.lineTo(w * 0.34f, top + h * 0.46f);
            crown.lineTo(w * 0.5f, top);
            crown.lineTo(w * 0.66f, top + h * 0.46f);
            crown.lineTo(right, top + h * 0.16f);
            crown.lineTo(right, base);
            crown.close();
            paint.setColor(0xFFE8B33C);
            canvas.drawPath(crown, paint);
            canvas.drawRect(left, h * 0.80f, right, h * 0.94f, paint);
        }
    }
}
