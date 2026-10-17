package com.luoyun.doutu.features.order;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.R;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * 「淘宝订单详情」—— 娱乐页 → 实用功能 → 第 2 条。
 *
 * <p><b>这一页是按参考图逐像素复刻的。</b>参考图是一张 1380 宽的整屏截图，下面所有数字都是
 * 那张图上的原始像素（「参考像素」），运行时统一乘 {@code k = 屏幕宽 / 1380}；
 * y 用的是参考图的**整屏绝对坐标**（0 就是屏幕最上面一条），所以状态栏、底栏、卡片的位置
 * 和参考图一一对应。
 *
 * <p>每个数字都来自对参考图的测量（工具：{@code _ops/measure_order_ref.py}、
 * {@code _ops/probe_order_ref.py}、{@code _ops/elcomp.py}），字号还按「同一段文字在两张图里的
 * 墨迹宽度」校准过一遍，不是估的。图标全部从参考图上抠下来（{@code _ops/cut_order_icons.py}），
 * 白底按 alpha 反解成透明；商品图和六个推荐位图也是从截图里裁的。
 *
 * <p>文字按「墨迹中心」落位：{@code setIncludeFontPadding(false)} 之后墨迹中心约在
 * {@code top + 0.5×字号}，所以 {@code top = 中心 - 0.5×字号}。
 *
 * <p><b>页面上每一项都能点着改</b>，改完存进 {@link OrderStore}（跟着账号走），
 * 杀掉后台再进来还是改过的那一版。首次打开时收货人 / 手机号 / 地址 / 订单号 / 交易号 / 时间
 * 都是当场随机的，不沿用任何真实信息。
 *
 * <p>右上角那三个点在参考图上就是装饰：这里也只显示，不接任何功能。
 */
public final class TaobaoOrderActivity extends Activity {
    /** 参考图的宽度（设计坐标系）。 */
    private static final float DESIGN = 1380f;

    /* ---------------- 颜色（取自参考图上文字/色块最深处的像素） ---------------- */
    private static final int PAGE_BG = 0xFFF5F6F8;
    private static final int CARD = 0xFFFFFFFF;
    private static final int INK = 0xFF000000;
    private static final int GRAY = 0xFF727780;
    private static final int GREEN = 0xFF2F8F55;
    private static final int ORANGE = 0xFFFF5000;
    private static final int ORANGE_BTN = 0xFFFF5E05;
    private static final int HAIRLINE = 0xFFFAFAFA;
    private static final int BTN_GRAY = 0xFFF1F2F4;
    private static final int BTN_CREAM = 0xFFFFEBD3;
    private static final int CHIP_ORANGE = 0xFFFFF0E0;

    /* ---------------- 字号（参考像素） ---------------- */
    private static final float F_TITLE = 73f;
    private static final float F_SUBTITLE = 48f;
    private static final float F_ADDRESS = 56f;
    private static final float F_RECEIVER = 43f;
    private static final float F_SHOP = 56f;
    private static final float F_SHOP_NOTE = 44f;
    private static final float F_ENTER = 48f;
    private static final float F_GOOD = 45f;
    private static final float F_GOOD_PRICE = 48f;
    private static final float F_SPEC = 44f;
    private static final float F_REFUND = 49f;
    private static final float F_PAID_LABEL = 47f;
    private static final float F_PAID = 58f;
    private static final float F_PRICE_DETAIL = 45f;
    private static final float F_LABEL = 56f;
    private static final float F_LABEL_SMALL = 47f;
    private static final float F_ORANGE_VALUE = 59f;
    private static final float F_PAYABLE = 72f;
    private static final float F_SECTION = 55f;
    private static final float F_SECTION_SUB = 48f;
    private static final float F_ORDER_NO = 49f;
    private static final float F_COPY = 48f;
    private static final float F_KEY = 48f;
    private static final float F_VALUE = 52f;
    private static final float F_TIME = 42f;
    private static final float F_GUARANTEE = 49f;
    private static final float F_GUARANTEE_SUB = 45f;
    private static final float F_BTN = 47f;
    private static final float F_SERVICE = 36f;
    private static final float F_REC_TITLE = 56f;
    private static final float F_REC_PRICE = 52f;
    private static final float F_REC_TAG = 44f;
    private static final float F_REC_SOLD = 42f;
    /** 推荐位里的文字离图块左边缘的距离（量出来的）。 */
    private static final float REC_TEXT_INSET = 32f;

    /* ---------------- 版式（参考像素，y 是参考图的整屏绝对坐标） ---------------- */
    private static final float STATUS_BAR = 152f;
    private static final float PAGE_LEFT = 30f, PAGE_RIGHT = 1350f;
    private static final float CARD_RADIUS = 14f;
    private static final float TOOLBAR_H = 207f;

    private static final float ADDRESS_TOP = 359f, ADDRESS_BOTTOM = 616f;
    private static final float SHOP_TOP = 648f, SHOP_BOTTOM = 869f;
    private static final float GOODS_TOP = 872f, GOODS_BOTTOM = 1541f;
    private static final float PRICE_TOP = 1544f, PRICE_BOTTOM = 1859f;
    private static final float ORDER_TOP = 1890f, ORDER_BOTTOM = 2818f;
    private static final float REC_TOP = 2884f;
    private static final float REC_SIZE = 650f;
    private static final float REC_PITCH = 945f;
    private static final float REC_MARGIN = 29f;
    private static final float REC_GUTTER = 22f;
    private static final float BOTTOM_BAR_TOP = 2752f;
    private static final float SCREEN_BOTTOM = 3036f;
    private static final float PAGE_BOTTOM = REC_TOP + 3 * REC_PITCH + 200f;

    private static final int REQUEST_IMAGE = 9001;

    private float k;
    private FrameLayout page;
    /** 订单信息那一块当前是展开的（参考图上就是展开的，箭头朝上）。 */
    private boolean orderExpanded = true;
    private final HashMap<Integer, Bitmap> bitmaps = new HashMap<>();

    public static void open(Activity activity) {
        activity.startActivity(new Intent(activity, TaobaoOrderActivity.class));
        activity.overridePendingTransition(0, 0);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        k = getResources().getDisplayMetrics().widthPixels / DESIGN;

        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(PAGE_BG));
        window.setStatusBarColor(PAGE_BG);
        window.setNavigationBarColor(CARD);

        FrameLayout screen = new FrameLayout(this);
        FrameLayout.LayoutParams screenLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, v(SCREEN_BOTTOM - STATUS_BAR));
        screenLp.topMargin = v(STATUS_BAR);
        screen.addView(toolbar(), rect(0, 0, DESIGN, TOOLBAR_H));

        ScrollView scroller = new ScrollView(this);
        scroller.setClipToPadding(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setBackgroundColor(PAGE_BG);
        page = new FrameLayout(this);
        scroller.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.addView(scroller, rect(0, TOOLBAR_H, DESIGN, BOTTOM_BAR_TOP - TOOLBAR_H));
        screen.addView(bottomBar(), rect(0, BOTTOM_BAR_TOP - STATUS_BAR, DESIGN,
                SCREEN_BOTTOM - BOTTOM_BAR_TOP));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(PAGE_BG);
        root.addView(screen, screenLp);
        setContentView(root);
        render();
    }

    /**
     * 铺满整屏（状态栏和底部手势条都压在页面上面）——和参考图一样。
     *
     * <p>这件事只能在窗口拿到 DecorView 之后做：在 {@code onCreate} 里调
     * {@code getInsetsController()} 会在这个 ROM 上抛 NPE（DecorView 还是 null）。
     */
    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!focus) return;
        Window window = getWindow();
        if (window == null || window.getDecorView() == null) return;
        window.setStatusBarColor(PAGE_BG);
        window.setNavigationBarColor(CARD);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    /* ------------------------------------------------------------------ 单位与摆放 */

    private int v(float ref) {
        return Math.round(ref * k);
    }

    private FrameLayout.LayoutParams rect(float left, float top, float width, float height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(v(width), v(height));
        lp.leftMargin = v(left);
        lp.topMargin = v(top);
        return lp;
    }

    /** 放进滚动区：y 传参考图的整屏绝对坐标，这里减掉滚动区顶部。 */
    private void placeAt(View view, float left, float top, float width, float height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(v(width), v(height));
        lp.leftMargin = v(left);
        lp.topMargin = v(top - ADDRESS_TOP);
        page.addView(view, lp);
    }

    private View block(float left, float top, float width, float height, int color,
                       float radius) {
        View view = new View(this);
        view.setBackground(radius > 0 ? round(color, v(radius)) : new ColorDrawable(color));
        placeAt(view, left, top, width, height);
        return view;
    }

    private void render() {
        page.removeAllViews();
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, v(PAGE_BOTTOM - ADDRESS_TOP)));
        addressCard();
        shopRow();
        goodsRow();
        priceBlock();
        orderCard();
        recommendation();
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private TextView label(String value, float font, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, v(font));
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        text.setLineSpacing(0f, 1f);
        if (bold) text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    /** 按墨迹中心放一行字（左边界固定在 left）。 */
    private TextView text(String value, float left, float center, float font, int color,
                          boolean bold) {
        TextView view = label(value, font, color, bold);
        placeAt(view, left, center - font * 0.5f,
                ViewGroup.LayoutParams.WRAP_CONTENT, font * 1.4f);
        return view;
    }

    /** 右对齐的一行字。 */
    private TextView textRight(String value, float right, float center, float font,
                               int color, boolean bold) {
        TextView view = label(value, font, color, bold);
        view.setGravity(Gravity.END);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                v(780f), v(font * 1.4f));
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.rightMargin = Math.round((DESIGN - right) * k);
        lp.topMargin = v(center - font * 0.5f - ADDRESS_TOP);
        page.addView(view, lp);
        return view;
    }

    private void tap(View view, Runnable action) {
        view.setClickable(true);
        view.setOnClickListener(x -> action.run());
        TypedValue out = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true)) {
            view.setBackgroundResource(out.resourceId);
        }
    }

    private ImageView icon(int resId, float left, float top, float width, float height) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        view.setImageBitmap(bitmap(resId));
        placeAt(view, left, top, width, height);
        return view;
    }

    private Bitmap bitmap(int resId) {
        Bitmap cached = bitmaps.get(resId);
        if (cached != null) return cached;
        Bitmap decoded = BitmapFactory.decodeResource(getResources(), resId);
        if (decoded != null) bitmaps.put(resId, decoded);
        return decoded;
    }

    /* ------------------------------------------------------------------ 顶部标题栏（参考图 y 152..359） */

    private View toolbar() {
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(PAGE_BG);

        ImageView back = new ImageView(this);
        back.setScaleType(ImageView.ScaleType.FIT_XY);
        back.setImageBitmap(bitmap(R.drawable.order_ic_back));
        back.setOnClickListener(x -> finish());
        bar.addView(back, rect(53, 74, 38, 62));

        // 标题：可以点着改；下面那一行「其他」按产品要求去掉了，标题顺势往下挪
        TextView title = label(OrderStore.get(OrderStore.TITLE), F_TITLE, INK, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(v(24), 0, v(24), 0);
        tap(title, () -> edit("标题", OrderStore.TITLE, false));
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, v(F_TITLE * 1.4f));
        titleLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        titleLp.topMargin = v(59);
        bar.addView(title, titleLp);

        // 右上角三个点：参考图上就是个装饰，这里不接任何点击
        ImageView dots = new ImageView(this);
        dots.setScaleType(ImageView.ScaleType.FIT_XY);
        dots.setImageBitmap(bitmap(R.drawable.order_ic_dots));
        bar.addView(dots, rect(1251, 94, 80, 24));
        return bar;
    }

    /* ------------------------------------------------------------------ 地址卡 359..616 */

    private void addressCard() {
        block(PAGE_LEFT, ADDRESS_TOP, PAGE_RIGHT - PAGE_LEFT,
                ADDRESS_BOTTOM - ADDRESS_TOP, CARD, CARD_RADIUS);
        icon(R.drawable.order_ic_pin, 74, 423, 57, 61);

        TextView address = text(OrderStore.get(OrderStore.ADDRESS), 164, 454, F_ADDRESS, INK, true);
        tap(address, () -> edit("收货地址（短）", OrderStore.ADDRESS, false));

        TextView receiver = text(OrderStore.get(OrderStore.NAME) + " " + OrderStore.maskedPhone(),
                161, 538, F_RECEIVER, GRAY, false);
        tap(receiver, this::editReceiver);
    }

    private void editReceiver() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText nameField = field(OrderStore.get(OrderStore.NAME), false);
        EditText phoneField = field("86-" + OrderStore.get(OrderStore.PHONE), false);
        form.addView(caption("收货人"), matchParams());
        form.addView(nameField, matchParams());
        form.addView(caption("手机号"), matchParams());
        form.addView(phoneField, matchParams());
        new AlertDialog.Builder(this)
                .setTitle("收货人")
                .setView(pad(form))
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String phone = text(phoneField).replace("86-", "").replace(" ", "");
                    LinkedHashMap<String, String> changed = new LinkedHashMap<>();
                    changed.put(OrderStore.NAME, text(nameField));
                    if (!phone.isEmpty()) changed.put(OrderStore.PHONE, phone);
                    OrderStore.setAll(changed);
                    render();
                })
                .show();
    }

    /* ------------------------------------------------------------------ 店铺行 648..869 */

    private void shopRow() {
        block(PAGE_LEFT, SHOP_TOP, PAGE_RIGHT - PAGE_LEFT, SHOP_BOTTOM - SHOP_TOP, CARD, 0);
        icon(R.drawable.order_shop_logo, 56, 697, 130, 130);

        TextView name = text(OrderStore.get(OrderStore.SHOP), 222, 730, F_SHOP, INK, true);
        tap(name, () -> edit("店铺名称", OrderStore.SHOP, false));

        TextView note = text(OrderStore.get(OrderStore.SHOP_NOTE), 223, 805, F_SHOP_NOTE, GRAY, false);
        tap(note, () -> edit("店铺说明", OrderStore.SHOP_NOTE, false));

        text("进店逛逛", 1085, 762, F_ENTER, GRAY, false);
        icon(R.drawable.order_ic_arrow_r, 1286, 743, 27, 39);
        block(PAGE_LEFT + 30, 870, PAGE_RIGHT - PAGE_LEFT - 60, 2, HAIRLINE, 0);
    }

    /* ------------------------------------------------------------------ 商品行 872..1541 */

    private void goodsRow() {
        block(PAGE_LEFT, GOODS_TOP, PAGE_RIGHT - PAGE_LEFT, GOODS_BOTTOM - GOODS_TOP, CARD, 0);

        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap custom = customImage();
        photo.setImageBitmap(custom != null ? custom : bitmap(R.drawable.order_main));
        placeAt(photo, 61, 917, 292, 296);
        tap(photo, this::pickImage);

        TextView title = text(OrderStore.get(OrderStore.GOOD_TITLE), 429, 945, F_GOOD, INK, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setMaxWidth(v(665f));
        tap(title, () -> edit("商品标题", OrderStore.GOOD_TITLE, false));

        icon(R.drawable.order_ic_caret_down, 1114, 931, 40, 24);

        TextView goodPrice = textRight("¥" + OrderStore.get(OrderStore.GOOD_PRICE), 1317, 949,
                F_GOOD_PRICE, INK, true);
        tap(goodPrice, () -> edit("商品原价", OrderStore.GOOD_PRICE, true));

        TextView spec = text(OrderStore.get(OrderStore.GOOD_SPEC), 401, 1017, F_SPEC, GRAY, false);
        tap(spec, () -> edit("商品规格", OrderStore.GOOD_SPEC, false));

        TextView count = textRight("x" + OrderStore.get(OrderStore.GOOD_COUNT), 1313, 1016,
                F_SPEC, GRAY, false);
        tap(count, () -> edit("购买数量", OrderStore.GOOD_COUNT, true));

        TextView refund = text(OrderStore.get(OrderStore.REFUND_NOTE), 400, 1080, F_REFUND,
                GREEN, false);
        tap(refund, () -> edit("退款说明", OrderStore.REFUND_NOTE, false));
        icon(R.drawable.order_ic_arrow_r, 931, 1062, 22, 32);

        // 实付价 ¥9699 价格明细 › —— 用一条横向流，字号不同也不会挤在一起
        LinearLayout paidRow = new LinearLayout(this);
        paidRow.setOrientation(LinearLayout.HORIZONTAL);
        paidRow.setGravity(Gravity.CENTER_VERTICAL);
        paidRow.addView(label("实付价", F_PAID_LABEL, INK, false), flowParams(0));
        TextView paid = label("¥" + OrderStore.get(OrderStore.PAID_PRICE), F_PAID, INK, true);
        tap(paid, () -> edit("实付价", OrderStore.PAID_PRICE, true));
        paidRow.addView(paid, flowParams(4));
        paidRow.addView(label("价格明细", F_PRICE_DETAIL, GRAY, false), flowParams(18));
        ImageView detailArrow = new ImageView(this);
        detailArrow.setScaleType(ImageView.ScaleType.FIT_XY);
        detailArrow.setImageBitmap(bitmap(R.drawable.order_ic_arrow_r));
        paidRow.addView(detailArrow, new LinearLayout.LayoutParams(v(22), v(32)));
        placeAt(paidRow, 400, 1180 - 40, 700, 80);

        TextView addCart = label("加入购物车", F_BTN, ORANGE, false);
        addCart.setGravity(Gravity.CENTER);
        addCart.setBackground(round(BTN_CREAM, v(10f)));
        addCart.setOnClickListener(x -> toast("已加入购物车"));
        placeAt(addCart, 1026, 1232, 295, 112);

        TextView totalLabel = text("商品总价", 62, 1459, F_LABEL, INK, false);
        tap(totalLabel, () -> edit("商品总价", OrderStore.TOTAL_PRICE, true));
        TextView totalValue = textRight("¥" + OrderStore.get(OrderStore.TOTAL_PRICE), 1317,
                1464, F_LABEL, INK, true);
        tap(totalValue, () -> edit("商品总价", OrderStore.TOTAL_PRICE, true));

        block(PAGE_LEFT + 30, 1542, PAGE_RIGHT - PAGE_LEFT - 60, 2, HAIRLINE, 0);
    }

    /* ------------------------------------------------------------------ 金额 1544..1859 */

    private void priceBlock() {
        block(PAGE_LEFT, PRICE_TOP, PAGE_RIGHT - PAGE_LEFT, PRICE_BOTTOM - PRICE_TOP, CARD, 0);
        icon(R.drawable.order_ic_coupon, 46, 1589, 83, 70);

        TextView platform = text("平台优惠", 143, 1626, F_LABEL_SMALL, INK, false);
        tap(platform, () -> edit("平台优惠金额", OrderStore.DISCOUNT, true));
        TextView coupon = text("购物券", 352, 1625, F_LABEL_SMALL, INK, false);
        tap(coupon, () -> edit("平台优惠金额", OrderStore.DISCOUNT, true));
        TextView discount = textRight("-¥" + OrderStore.get(OrderStore.DISCOUNT), 1268, 1628,
                F_ORANGE_VALUE, ORANGE, true);
        tap(discount, () -> edit("平台优惠金额", OrderStore.DISCOUNT, true));
        icon(R.drawable.order_ic_arrow_r, 1286, 1612, 22, 32);

        TextView payableLabel = text("应付款", 62, 1781, F_LABEL, INK, true);
        tap(payableLabel, () -> edit("应付款", OrderStore.PAYABLE, true));
        TextView saved = text("共减¥" + OrderStore.get(OrderStore.DISCOUNT), 253, 1788,
                F_LABEL, ORANGE, false);
        tap(saved, () -> edit("平台优惠金额", OrderStore.DISCOUNT, true));

        TextView payable = textRight("¥" + OrderStore.get(OrderStore.PAYABLE), 1261, 1775,
                F_PAYABLE, INK, true);
        tap(payable, () -> edit("应付款", OrderStore.PAYABLE, true));
        icon(R.drawable.order_ic_caret_up, 1279, 1764, 41, 26);
    }

    /* ------------------------------------------------------------------ 订单信息卡 1890..2818 */

    private void orderCard() {
        block(PAGE_LEFT, ORDER_TOP, PAGE_RIGHT - PAGE_LEFT, ORDER_BOTTOM - ORDER_TOP,
                CARD, CARD_RADIUS);

        TextView head = text("订单信息", 63, 1962, F_SECTION, INK, true);
        head.setClickable(false);
        TextView count = text("共5项", 293, 1961, F_SECTION_SUB, GRAY, false);
        count.setClickable(false);
        // 参考图上这一页是展开的，箭头朝上
        icon(orderExpanded ? R.drawable.order_ic_caret_up : R.drawable.order_ic_caret_down,
                432, orderExpanded ? 1956 : 1958, 33, 24);
        // 「订单信息 共5项 ⌃」这一带点一下收起 / 展开（和原包一样）
        View toggle = new View(this);
        toggle.setOnClickListener(x -> {
            orderExpanded = !orderExpanded;
            render();
        });
        placeAt(toggle, 40, 1930, 460, 70);

        TextView orderNo = text(OrderStore.get(OrderStore.ORDER_NO), 665, 1963, F_ORDER_NO,
                GRAY, false);
        tap(orderNo, () -> edit("订单号", OrderStore.ORDER_NO, false));

        TextView copy = text("复制", 1228, 1963, F_COPY, INK, false);
        tap(copy, () -> {
            ClipboardManager manager =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) manager.setPrimaryClip(
                    ClipData.newPlainText("订单号", OrderStore.get(OrderStore.ORDER_NO)));
            toast("订单号已复制");
        });

        // 收货信息：值有两行，从 2061 起；标签的墨迹在 2090..2119
        if (orderExpanded) {
            orderLine("收货信息", OrderStore.receiverLine(), 63, 2105, 2061, OrderStore.FULL_ADDRESS, true);
            icon(R.drawable.order_ic_info, 261, 2089, 51, 34);
            orderLine("发票", OrderStore.get(OrderStore.INVOICE), 62, 2261, 2261, OrderStore.INVOICE, false);
            orderLine("支付宝交易号", OrderStore.get(OrderStore.TRADE_NO), 62, 2381, 2381,
                    OrderStore.TRADE_NO, false);
            orderLine("创建时间", OrderStore.get(OrderStore.CREATE_TIME), 62, 2500, 2501,
                    OrderStore.CREATE_TIME, false);
            orderLine("成交时间", OrderStore.get(OrderStore.DEAL_TIME), 62, 2620, 2620,
                    OrderStore.DEAL_TIME, false);

            block(PAGE_LEFT + 30, 2693, PAGE_RIGHT - PAGE_LEFT - 60, 2, HAIRLINE, 0);
        }

        TextView guarantee = text("订单保障", 61, 2762, F_GUARANTEE, INK, true);
        guarantee.setClickable(false);
        TextView chip = label("共" + OrderStore.get(OrderStore.GUARANTEE_COUNT) + "项",
                F_GUARANTEE_SUB, ORANGE, false);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(round(CHIP_ORANGE, v(6f)));
        chip.setOnClickListener(x -> edit("保障项数", OrderStore.GUARANTEE_COUNT, true));
        placeAt(chip, 268, 2730, 116, 64);
        TextView basis = text(OrderStore.get(OrderStore.GUARANTEE), 489, 2762,
                F_GUARANTEE_SUB, GRAY, false);
        tap(basis, () -> edit("保障依据", OrderStore.GUARANTEE, false));
        icon(R.drawable.order_ic_arrow_r, 1286, 2746, 22, 32);
    }

    /**
     * 订单信息里的一行：左边灰标签（墨迹中心 {@code keyCenter}），右边深色值
     * （右对齐到 1321，墨迹顶 {@code valueTop}，可以是两行）。收货信息那一行改的是整条地址。
     */
    private void orderLine(String name, String value, float left, float keyCenter,
                           float valueTop, String key, boolean address) {
        text(name, left, keyCenter, F_KEY, INK, false);

        final String title = address ? "收货信息（整条）" : ("修改" + name);
        final String hint = address ? "省 市 区 镇 详细地址" : "";
        float font = address ? F_VALUE : F_KEY;
        TextView view = label(value, font, GRAY, false);
        view.setGravity(Gravity.END);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                v(860f), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.rightMargin = Math.round((DESIGN - 1321f) * k);
        lp.topMargin = v(valueTop - 0.05f * font - ADDRESS_TOP);
        page.addView(view, lp);
        tap(view, () -> edit(title, key, false, hint));
    }

    /* ------------------------------------------------------------------ 推荐位 */

    private void recommendation() {
        for (int rowIndex = 0; rowIndex < OrderStore.REC_COUNT / 2; rowIndex++) {
            float top = REC_TOP + rowIndex * REC_PITCH;
            for (int column = 0; column < 2; column++) {
                recommendItem(rowIndex * 2 + column,
                        REC_MARGIN + column * (REC_SIZE + REC_GUTTER), top);
            }
        }
    }

    private void recommendItem(int index, float left, float top) {
        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setImageBitmap(bitmap(recommendImage(index)));
        placeAt(photo, left, top, REC_SIZE, REC_SIZE);
        tap(photo, () -> editRecommend(index));

        TextView title = label(OrderStore.recTitle(index), F_REC_TITLE, INK, false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        placeAt(title, left + REC_TEXT_INSET, top + REC_SIZE + 31,
                REC_SIZE - REC_TEXT_INSET, 80);
        tap(title, () -> editRecommend(index));

        // 一行：¥ 价格 标签 销量（参考图里这四段就在同一行）
        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView yuan = label("¥", F_REC_TAG, ORANGE, true);
        TextView price = label(OrderStore.recPrice(index), F_REC_PRICE, ORANGE, true);
        TextView tag = label(OrderStore.recTag(index), F_REC_TAG, GRAY, false);
        TextView sold = label(OrderStore.recSold(index), F_REC_SOLD, GRAY, false);
        priceRow.addView(yuan, flowParams(0));
        priceRow.addView(price, flowParams(0));
        priceRow.addView(tag, flowParams(16));
        priceRow.addView(sold, flowParams(14));
        placeAt(priceRow, left + REC_TEXT_INSET, top + REC_SIZE + 104,
                REC_SIZE - REC_TEXT_INSET, 90);
        tap(priceRow, () -> editRecommend(index));
    }

    private static int recommendImage(int index) {
        switch (index) {
            case 0: return R.drawable.order_rec_1;
            case 1: return R.drawable.order_rec_2;
            case 2: return R.drawable.order_rec_3;
            case 3: return R.drawable.order_rec_4;
            case 4: return R.drawable.order_rec_5;
            default: return R.drawable.order_rec_6;
        }
    }

    private void editRecommend(int index) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText title = field(OrderStore.recTitle(index), false);
        EditText price = field(OrderStore.recPrice(index), true);
        EditText tag = field(OrderStore.recTag(index), false);
        EditText sold = field(OrderStore.recSold(index), false);
        form.addView(caption("商品标题"), matchParams());
        form.addView(title, matchParams());
        form.addView(caption("价格（不带 ¥）"), matchParams());
        form.addView(price, matchParams());
        form.addView(caption("标签"), matchParams());
        form.addView(tag, matchParams());
        form.addView(caption("销量"), matchParams());
        form.addView(sold, matchParams());
        new AlertDialog.Builder(this)
                .setTitle("推荐位 " + (index + 1))
                .setView(pad(form))
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    LinkedHashMap<String, String> changed = new LinkedHashMap<>();
                    changed.put(String.format(Locale.US, "rec%d_title", index), text(title));
                    changed.put(String.format(Locale.US, "rec%d_price", index), text(price));
                    changed.put(String.format(Locale.US, "rec%d_tag", index), text(tag));
                    changed.put(String.format(Locale.US, "rec%d_sold", index), text(sold));
                    OrderStore.setAll(changed);
                    render();
                })
                .show();
    }

    /* ------------------------------------------------------------------ 底栏 2752..3036
     *
     * 版式照用户后来给的那张「交易成功」截图：客服、更多两个图标入口，加三个按钮
     * 闲鱼转卖 / 加入购物车 / 再买一单。按产品要求这一条**只是展示，五个都不接点击**。 */

    private View bottomBar() {
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(CARD);

        icon(bar, R.drawable.order_ic_service, 52, 32, 72, 72);
        tabLabel(bar, "客服", 88);
        icon(bar, R.drawable.order_ic_more, 174, 32, 72, 72);
        tabLabel(bar, "更多", 210);

        pill(bar, "闲鱼转卖", 327, false);
        pill(bar, "加入购物车", 673, false);
        pill(bar, "再买一单", 1019, true);
        return bar;
    }

    private void icon(FrameLayout bar, int resId, float left, float top, float width,
                      float height) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        view.setImageBitmap(bitmap(resId));
        bar.addView(view, rect(left, top, width, height));
    }

    /** 底栏里图标下面那行小字（居中在 centerX 上）。 */
    private void tabLabel(FrameLayout bar, String value, float centerX) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, v(F_SERVICE));
        view.setTextColor(0xFF939499);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER);
        bar.addView(view, rect(centerX - 80f, 108, 160, 60));
    }

    /** 底栏里的按钮；{@code primary} 是那个实心橙的。 */
    private void pill(FrameLayout bar, String value, float left, boolean primary) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, v(F_BTN));
        view.setTextColor(primary ? Color.WHITE : INK);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(primary ? ORANGE_BTN : BTN_GRAY, v(10f)));
        bar.addView(view, rect(left, 33, 316, 127));
    }

    /* ------------------------------------------------------------------ 输入框 */

    private void edit(String title, String key, boolean numberOnly) {
        edit(title, key, numberOnly, "");
    }

    private void edit(String title, String key, boolean numberOnly, String hint) {
        final EditText input = field(OrderStore.get(key), numberOnly);
        if (!hint.isEmpty()) input.setHint(hint);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(pad(input))
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    OrderStore.set(key, text(input));
                    render();
                })
                .show();
    }

    private EditText field(String value, boolean numberOnly) {
        EditText input = new EditText(this);
        input.setText(value == null ? "" : value);
        input.setTextSize(15f);
        input.setMaxLines(3);
        input.setInputType(numberOnly
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_TEXT);
        input.setSelection(input.getText().length());
        return input;
    }

    private static String text(EditText field) {
        return field.getText().toString().trim();
    }

    private TextView caption(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(12f);
        view.setTextColor(GRAY);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 横排里一个「按内容宽 + 左间距」的子项。 */
    private LinearLayout.LayoutParams flowParams(int leftMarginRef) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = v(leftMarginRef);
        return lp;
    }

    private View pad(View view) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(6), dp(18), 0);
        box.addView(view, matchParams());
        return box;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /* ------------------------------------------------------------------ 商品图 */

    private Bitmap customImage() {
        if (!OrderStore.hasCustomImage()) return null;
        java.io.File file = OrderStore.imageFile();
        return file == null ? null : BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    private void pickImage() {
        new AlertDialog.Builder(this)
                .setTitle("商品图片")
                .setItems(new String[]{"从相册选择", "恢复参考图"}, (dialog, which) -> {
                    if (which == 1) {
                        OrderStore.clearImage();
                        render();
                        return;
                    }
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    try {
                        startActivityForResult(intent, REQUEST_IMAGE);
                    } catch (Exception e) {
                        toast("打不开相册");
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_IMAGE || result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            if (bitmap == null) {
                toast("这张图读不出来");
                return;
            }
            OrderStore.saveImage(bitmap);
            render();
        } catch (Exception e) {
            toast("这张图读不出来");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Bitmap bitmap : bitmaps.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        bitmaps.clear();
    }
}
