package com.luoyun.doutu.features.wei.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.wei.model.Conversation;

import java.util.ArrayList;
import java.util.List;

/**
 * Native, canvas-drawn reconstruction of the supplied WeChat-style reference screens.
 * The reference screenshots are design guides only; no full-page screenshot is rendered here.
 * Layout is authored on a 360 x 792 dp canvas so the rows, spacing and type scale consistently.
 */
public final class WeiHomeScreen extends View {
    public interface Listener {
        void onTabSelected(int tab);

        void onConversationSelected(Conversation conversation);

        void onCreateConversationRequested();

        void onSearchRequested();

        void onAction(String action);
    }

    private static final float DESIGN_WIDTH = 360f;
    private static final float DESIGN_HEIGHT = 792f;
    private static final float TAB_TOP = 724f;
    private static final int BLACK = 0xff171717;
    private static final int TEXT = 0xff202020;
    private static final int LINE = 0xffeeeeee;
    private static final int SECTION = 0xffededed;
    private static final int GREEN = 0xff58be6a;
    private static final int BLUE_GRAY = 0xff637b99;

    private final int selectedTab;
    private final List<Conversation> conversations;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final OverScroller scroller;
    private final int touchSlop;
    private final int minimumFlingVelocity;
    private final Bitmap[] bitmaps = new Bitmap[40];
    private final Bitmap[][] tabBitmaps = new Bitmap[4][2];
    private final ArrayList<ContactRow> contactRows = new ArrayList<>();
    private VelocityTracker velocityTracker;
    private float downX;
    private float downY;
    private float lastY;
    private boolean dragging;
    private int scrollY;
    private int maxScroll;

    private static final int CHAT_DRIFT = 0;
    private static final int CHAT_PUBLIC = 1;
    private static final int CHAT_SERVICE = 2;
    private static final int CHAT_DOG = 3;
    private static final int CHAT_ARNING = 4;
    private static final int CHAT_GAMES = 5;
    private static final int CHAT_GROUP = 6;
    private static final int CHAT_ZHAO = 7;
    private static final int CHAT_GRID = 8;
    private static final int CONTACT_NEW = 9;
    private static final int CONTACT_CHAT = 10;
    private static final int CONTACT_GROUP = 11;
    private static final int CONTACT_TAG = 12;
    private static final int CONTACT_PUBLIC = 13;
    private static final int CONTACT_ENTERPRISE = 14;
    private static final int CONTACT_HOME = 15;
    private static final int CONTACT_SCHOOL = 16;
    private static final int CONTACT_SUGARCAT = 17;
    private static final int CONTACT_ANCUI = 18;
    private static final int CONTACT_ARNING = 19;
    private static final int DISCOVER_MOMENTS = 20;
    private static final int DISCOVER_VIDEO = 21;
    private static final int DISCOVER_LIVE = 22;
    private static final int DISCOVER_SCAN = 23;
    private static final int DISCOVER_LISTEN = 24;
    private static final int DISCOVER_LOOK = 25;
    private static final int DISCOVER_SEARCH = 26;
    private static final int DISCOVER_PEOPLE = 27;
    private static final int DISCOVER_GAMES = 28;
    private static final int DISCOVER_MINIPROGRAM = 29;
    private static final int ME_AVATAR = 30;
    private static final int ME_SERVICES = 31;
    private static final int ME_FAVORITES = 32;
    private static final int ME_MOMENTS = 33;
    private static final int ME_WORKS = 34;
    private static final int ME_SHOP = 35;
    private static final int ME_STICKERS = 36;
    private static final int ME_SETTINGS = 37;
    private static final int CONTACT_SERVICE = 38;
    private static final int CHAT_REDMi = 39;

    public WeiHomeScreen(Context context, int selectedTab, List<Conversation> conversations, Listener listener) {
        super(context);
        this.selectedTab = Math.max(0, Math.min(3, selectedTab));
        this.conversations = conversations == null ? new ArrayList<>() : conversations;
        this.listener = listener;
        setBackgroundColor(Color.WHITE);
        setClickable(true);
        setFocusable(true);
        setContentDescription(new String[]{"微信", "通讯录", "发现", "我"}[this.selectedTab] + "主页");
        scroller = new OverScroller(context);
        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        touchSlop = viewConfiguration.getScaledTouchSlop();
        minimumFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
        loadBitmaps();
        buildContacts();
        updateScrollRange();
    }

    private void loadBitmaps() {
        int[] ids = {
                R.drawable.chat_avatar_drift, R.drawable.chat_avatar_public, R.drawable.chat_avatar_service,
                R.drawable.avatar_dog, R.drawable.chat_avatar_arning, R.drawable.chat_avatar_games,
                R.drawable.chat_avatar_group, R.drawable.chat_avatar_zhao, R.drawable.chat_avatar_grid,
                R.drawable.contact_new, R.drawable.contact_chat, R.drawable.contact_group,
                R.drawable.contact_tag, R.drawable.contact_public, R.drawable.contact_avatar_enterprise,
                R.drawable.contact_avatar_home, R.drawable.contact_avatar_school,
                R.drawable.contact_avatar_sugarcat, R.drawable.contact_avatar_ancui,
                R.drawable.contact_avatar_arning, R.drawable.discover_moments,
                R.drawable.discover_video, R.drawable.discover_live, R.drawable.discover_scan,
                R.drawable.discover_listen, R.drawable.discover_look, R.drawable.discover_search,
                R.drawable.discover_people, R.drawable.discover_games, R.drawable.discover_miniprogram,
                R.drawable.me_profile_avatar, R.drawable.me_services, R.drawable.me_favorites,
                R.drawable.me_moments, R.drawable.me_works, R.drawable.me_shop,
                R.drawable.me_stickers, R.drawable.me_settings, R.drawable.contact_service,
                R.drawable.chat_avatar_redmi
        };
        for (int i = 0; i < ids.length; i++)
            bitmaps[i] = BitmapFactory.decodeResource(getResources(), ids[i]);

        int[][] tabIds = {
                {R.drawable.wei_tab_chats_unselected, R.drawable.wei_tab_chats_selected},
                {R.drawable.wei_tab_contacts_unselected, R.drawable.wei_tab_contacts_selected},
                {R.drawable.wei_tab_discover_unselected, R.drawable.wei_tab_discover_selected},
                {R.drawable.wei_tab_me_unselected, R.drawable.wei_tab_me_selected}
        };
        for (int tab = 0; tab < tabIds.length; tab++) {
            for (int state = 0; state < tabIds[tab].length; state++) {
                tabBitmaps[tab][state] = BitmapFactory.decodeResource(getResources(), tabIds[tab][state]);
            }
        }
    }

    private void buildContacts() {
        contactRows.clear();
        contactRows.add(new ContactRow("企业微信联系人", CONTACT_ENTERPRISE));
        contactRows.add(new ContactRow("千羽 雪生", CONTACT_HOME));
        contactRows.add(new ContactRow("学校通知", CONTACT_SCHOOL));
        contactRows.add(new ContactRow("爱吃软糖喵", CONTACT_SUGARCAT));
        contactRows.add(new ContactRow("安翠", CONTACT_ANCUI));
        contactRows.add(new ContactRow("阿宁", CONTACT_ARNING));
        contactRows.add(new ContactRow("赵海洋", CHAT_ZHAO));
        contactRows.add(new ContactRow("黄俊熙", CHAT_DOG));
        contactRows.add(new ContactRow("漂学社", CHAT_DRIFT));
        contactRows.add(new ContactRow("程序员客栈133群", CHAT_GROUP));
        contactRows.add(new ContactRow("微信游戏", CHAT_GAMES));
        contactRows.add(new ContactRow("公众号", CHAT_PUBLIC));
        contactRows.add(new ContactRow("服务号", CHAT_SERVICE));
    }

    private void updateScrollRange() {
        float contentEnd;
        if (selectedTab == 0) contentEnd = 125f + Math.max(10, conversations.size()) * 65f;
        else if (selectedTab == 1) contentEnd = 597f + Math.max(0, contactRows.size() - 3) * 50f;
        else if (selectedTab == 2) contentEnd = 630f;
        else contentEnd = 576f;
        // Reserve a small extra tail so every tab visibly responds to vertical swipes.
        maxScroll = Math.max(24, Math.round(contentEnd - TAB_TOP));
        scrollY = clamp(scrollY, 0, maxScroll);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth() / DESIGN_WIDTH;
        float sy = getHeight() / DESIGN_HEIGHT;
        canvas.save();
        canvas.scale(sx, sy);
        if (selectedTab == 3) drawMePage(canvas);
        else {
            canvas.drawColor(SECTION);
            canvas.save();
            canvas.clipRect(0, 0, DESIGN_WIDTH, TAB_TOP);
            canvas.translate(0, -scrollY);
            if (selectedTab == 0) drawChats(canvas);
            else if (selectedTab == 1) drawContacts(canvas);
            else drawDiscover(canvas);
            canvas.restore();
            // The app header is drawn above the content and replaces the system
            // status/notification strip while the home activity is immersive.
            drawPageHeader(canvas);
        }
        drawBottomTabs(canvas);
        canvas.restore();
    }

    private void drawPageHeader(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffededed);
        canvas.drawRect(0, 0, DESIGN_WIDTH, 81, paint);
        String title = selectedTab == 0 ? "微信" : selectedTab == 1 ? "通讯录" : "发现";
        // The original capture includes a system status bar above the toolbar.
        // With that bar hidden in this app, keep the toolbar controls at the
        // same visual position as the reference while leaving content at y=81.
        text(canvas, title, 180, 66, 17f, BLACK, true, Paint.Align.CENTER);
        drawHeaderSearch(canvas, 302, 59.5f);
        drawHeaderPlus(canvas, 337, 59.5f);
        paint.setColor(0xffdddddd);
        canvas.drawRect(0, 80.7f, DESIGN_WIDTH, 81, paint);
    }

    private void drawHeaderSearch(Canvas canvas, float cx, float cy) {
        paint.setColor(0xff171717);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f);
        canvas.drawCircle(cx - 1.5f, cy - 1.5f, 7.2f, paint);
        canvas.drawLine(cx + 4, cy + 4, cx + 9, cy + 9, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHeaderPlus(Canvas canvas, float cx, float cy) {
        paint.setColor(0xff171717);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.35f);
        canvas.drawCircle(cx, cy, 8.2f, paint);
        canvas.drawLine(cx - 4, cy, cx + 4, cy, paint);
        canvas.drawLine(cx, cy - 4, cx, cy + 4, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawChats(Canvas canvas) {
        paint.setColor(0xfff1f1f1);
        canvas.drawRect(0, 81, DESIGN_WIDTH, 125, paint);
        drawMonitor(canvas, 11, 95, 0xff888888);
        text(canvas, "Windows 微信已登录", 68, 108.5f, 14.2f, 0xff777777, false, Paint.Align.LEFT);
        paint.setColor(0xffdadada);
        canvas.drawRect(0, 124.7f, DESIGN_WIDTH, 125, paint);
        for (int i = 0; i < conversations.size(); i++) drawChatRow(canvas, i, conversations.get(i));
    }

    private void drawMonitor(Canvas canvas, float x, float y, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f);
        canvas.drawRect(x, y, x + 17, y + 11.5f, paint);
        canvas.drawLine(x + 8.5f, y + 11.5f, x + 8.5f, y + 15, paint);
        canvas.drawLine(x + 4, y + 15, x + 13, y + 15, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawChatRow(Canvas canvas, int index, Conversation item) {
        float top = 125 + index * 65f;
        float bottom = top + 65;
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, top, DESIGN_WIDTH, bottom, paint);
        int avatar = chatAvatarFor(item);
        drawBitmap(canvas, avatar, 14.5f, top + 9, 44, 44);
        int unreadColor = unreadBadgeFor(item);
        if (unreadColor != Color.TRANSPARENT)
            drawChatUnreadBadge(canvas, 57.5f, top + 12.5f, unreadColor);
        String title = item.getTitle();
        int titleColor = hasBlueTitle(item) ? BLUE_GRAY : TEXT;
        drawTextClipped(canvas, title, 69, top + 29.5f, 17.2f, titleColor, false, Paint.Align.LEFT, 228);
        drawTextClipped(canvas, item.getUpdatedAt(), 344, top + 24, 12, 0xffb2b2b2, false, Paint.Align.RIGHT, 52);
        String preview = item.getPreview();
        if ("conversation-3".equals(item.getId())) {
            // Notification preview belongs to the service conversation identity, not
            // its current list position (sending any chat can reorder these rows).
            drawTextClipped(canvas, "[联学车等6条通知]", 69, top + 50, 13.6f, 0xffe35b61, false, Paint.Align.LEFT, 139);
            drawTextClipped(canvas, "鱼皮编程导航：又跌爆了。。。", 209, top + 50, 13.6f, 0xffaaaaaa, false, Paint.Align.LEFT, 116);
        } else {
            drawTextClipped(canvas, preview, 69, top + 50, 13.5f, 0xffaaaaaa, false, Paint.Align.LEFT, 250);
        }
        if (isMuted(item)) drawMutedBell(canvas, 330, top + 46);
        paint.setColor(LINE);
        canvas.drawRect(68.5f, bottom - .22f, DESIGN_WIDTH, bottom, paint);
    }

    /**
     * Row decoration follows the conversation identity when sending reorders the inbox.
     */
    private int unreadBadgeFor(Conversation item) {
        switch (item.getId()) {
            case "conversation-2":
                return 0xff3e84e6;
            case "conversation-3":
                return 0xff4facf9;
            case "conversation-6":
                return Color.WHITE;
            case "conversation-7":
                return 0xfff5f5f5;
            default:
                return Color.TRANSPARENT;
        }
    }

    private boolean hasBlueTitle(Conversation item) {
        return "conversation-1".equals(item.getId()) || "conversation-6".equals(item.getId());
    }

    private boolean isMuted(Conversation item) {
        return "conversation-1".equals(item.getId())
                || "conversation-6".equals(item.getId())
                || "conversation-7".equals(item.getId());
    }

    private int chatAvatarFor(Conversation conversation) {
        int avatarId = conversation.getAvatarResourceId();
        if (avatarId == R.drawable.chat_avatar_redmi) return CHAT_REDMi;
        if (avatarId == R.drawable.chat_avatar_drift) return CHAT_DRIFT;
        if (avatarId == R.drawable.chat_avatar_public) return CHAT_PUBLIC;
        if (avatarId == R.drawable.chat_avatar_service) return CHAT_SERVICE;
        if (avatarId == R.drawable.avatar_dog) return CHAT_DOG;
        if (avatarId == R.drawable.chat_avatar_arning) return CHAT_ARNING;
        if (avatarId == R.drawable.chat_avatar_games) return CHAT_GAMES;
        if (avatarId == R.drawable.chat_avatar_group) return CHAT_GROUP;
        if (avatarId == R.drawable.chat_avatar_zhao) return CHAT_ZHAO;
        if (avatarId == R.drawable.chat_avatar_grid) return CHAT_GRID;
        return CHAT_ARNING;
    }

    private void drawChatUnreadBadge(Canvas canvas, float cx, float cy, int avatarCornerColor) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(avatarCornerColor);
        canvas.drawCircle(cx, cy, 6.3f, paint);
        paint.setColor(0xfff04b53);
        canvas.drawCircle(cx, cy, 4.55f, paint);
    }

    private void drawMutedBell(Canvas canvas, float x, float y) {
        paint.setColor(0xffc7c7c7);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.8f);
        Path bell = new Path();
        bell.moveTo(x, y + 8);
        bell.quadTo(x + 1, y + 6, x + 1, y + 3);
        bell.quadTo(x + 1, y, x + 4, y);
        bell.quadTo(x + 7, y, x + 7, y + 3);
        bell.quadTo(x + 7, y + 6, x + 8, y + 8);
        canvas.drawPath(bell, paint);
        canvas.drawLine(x - 1, y + 8.5f, x + 9, y + 8.5f, paint);
        canvas.drawLine(x + 1, y - 1, x + 9, y + 10, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawContacts(Canvas canvas) {
        String[] labels = {"新的朋友", "仅聊天的朋友", "群聊", "标签", "公众号", "服务号"};
        int[] icons = {CONTACT_NEW, CONTACT_CHAT, CONTACT_GROUP, CONTACT_TAG, CONTACT_PUBLIC, CONTACT_SERVICE};
        for (int i = 0; i < labels.length; i++)
            drawContactRow(canvas, labels[i], icons[i], 89 + i * 50.5f, 50.5f, false);
        drawSectionLabel(canvas, "我的企业及企业联系人", 384, 21);
        drawContactRow(canvas, contactRows.get(0).name, contactRows.get(0).bitmap, 405, 50, false);
        drawContactRow(canvas, contactRows.get(1).name, contactRows.get(1).bitmap, 455, 50, false);
        drawContactRow(canvas, contactRows.get(2).name, contactRows.get(2).bitmap, 505, 50, false);
        paint.setColor(SECTION);
        canvas.drawRect(0, 555, DESIGN_WIDTH, 575, paint);
        drawSectionLabel(canvas, "A", 575, 22);
        for (int i = 3; i < contactRows.size(); i++) {
            ContactRow row = contactRows.get(i);
            drawContactRow(canvas, row.name, row.bitmap, 597 + (i - 3) * 50f, 50, false);
        }
        drawAlphabetIndex(canvas);
    }

    private void drawContactRow(Canvas canvas, String label, int bitmap, float top, float height, boolean ignored) {
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, top, DESIGN_WIDTH, top + height, paint);
        drawBitmap(canvas, bitmap, 14.5f, top + (height - 36.25f) * .5f, 36.25f, 36.25f);
        text(canvas, label, 64.5f, top + height * .63f, 17.1f, TEXT, false, Paint.Align.LEFT);
        paint.setColor(LINE);
        canvas.drawRect(64.5f, top + height - .2f, DESIGN_WIDTH, top + height, paint);
    }

    private void drawSectionLabel(Canvas canvas, String label, float top, float height) {
        paint.setColor(SECTION);
        canvas.drawRect(0, top, DESIGN_WIDTH, top + height, paint);
        text(canvas, label, 14.5f, top + height * .72f, 11.5f, 0xff858585, false, Paint.Align.LEFT);
    }

    private void drawAlphabetIndex(Canvas canvas) {
        String alphabet = "↑☆ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
        float start = 174.5f;
        float step = 16.6f;
        for (int i = 0; i < alphabet.length(); i++) {
            text(canvas, String.valueOf(alphabet.charAt(i)), 350.5f, start + i * step, 9.5f, 0xff333333, false, Paint.Align.CENTER);
        }
    }

    private void drawDiscover(Canvas canvas) {
        drawDiscoverRow(canvas, "朋友圈", DISCOVER_MOMENTS, 89, 51, "朋友圈");
        sectionGap(canvas, 140, 8);
        drawDiscoverRow(canvas, "视频号", DISCOVER_VIDEO, 148, 50, "视频号");
        drawDiscoverRow(canvas, "直播", DISCOVER_LIVE, 198, 50, "直播");
        sectionGap(canvas, 248, 8);
        drawDiscoverRow(canvas, "扫一扫", DISCOVER_SCAN, 256, 50, "扫一扫");
        drawDiscoverRow(canvas, "听一听", DISCOVER_LISTEN, 306, 50, "听一听");
        sectionGap(canvas, 356, 8);
        drawDiscoverRow(canvas, "看一看", DISCOVER_LOOK, 364, 50, "看一看");
        drawDiscoverRow(canvas, "搜一搜", DISCOVER_SEARCH, 414, 50, "搜一搜");
        sectionGap(canvas, 464, 8);
        drawDiscoverRow(canvas, "附近的人", DISCOVER_PEOPLE, 472, 50, "附近的人");
        sectionGap(canvas, 522, 8);
        drawDiscoverRow(canvas, "游戏", DISCOVER_GAMES, 530, 50, "游戏");
        sectionGap(canvas, 580, 8);
        drawDiscoverRow(canvas, "小程序", DISCOVER_MINIPROGRAM, 588, 50, "小程序");
        paint.setColor(SECTION);
        canvas.drawRect(0, 630, DESIGN_WIDTH, TAB_TOP + scrollY, paint);
    }

    private void sectionGap(Canvas canvas, float top, float height) {
        paint.setColor(SECTION);
        canvas.drawRect(0, top, DESIGN_WIDTH, top + height, paint);
    }

    private void drawDiscoverRow(Canvas canvas, String label, int bitmap, float top, float height, String action) {
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, top, DESIGN_WIDTH, top + height, paint);
        drawBitmap(canvas, bitmap, 16, top + (height - 19f) * .5f, 19, 19);
        text(canvas, label, 51, top + height * .64f, 17.2f, TEXT, false, Paint.Align.LEFT);
        drawChevron(canvas, 339, top + height * .5f, 0xffaaaaaa);
    }

    private void drawMePage(Canvas canvas) {
        // The status bar is hidden on this screen; the reference area above the
        // profile card is white, not a leftover gray status-bar spacer.
        canvas.drawColor(Color.WHITE);
        canvas.save();
        canvas.clipRect(0, 27, DESIGN_WIDTH, TAB_TOP);
        canvas.translate(0, -scrollY);
        drawProfile(canvas);
        drawSectionBreak(canvas, 202, 8);
        drawMeRow(canvas, "服务", ME_SERVICES, 210, 50, false);
        drawSectionBreak(canvas, 260, 8);
        drawMeRow(canvas, "收藏", ME_FAVORITES, 268, 50, false);
        drawMeRow(canvas, "朋友圈", ME_MOMENTS, 318, 50, false);
        drawMeRow(canvas, "作品", ME_WORKS, 368, 50, false);
        drawShopRow(canvas, 418, 50);
        drawMeRow(canvas, "表情", ME_STICKERS, 468, 50, false);
        drawSectionBreak(canvas, 518, 8);
        drawMeRow(canvas, "设置", ME_SETTINGS, 526, 50, false);
        paint.setColor(SECTION);
        canvas.drawRect(0, 576, DESIGN_WIDTH, TAB_TOP + scrollY, paint);
        canvas.restore();
    }

    private void drawProfile(Canvas canvas) {
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, 27, DESIGN_WIDTH, 202, paint);
        drawBitmap(canvas, ME_AVATAR, 22, 87, 58, 58);
        text(canvas, "落陨", 93, 106, 22f, TEXT, true, Paint.Align.LEFT);
        text(canvas, "微信号： luoyun_xyz", 93, 137, 15f, 0xff777777, false, Paint.Align.LEFT);
        drawQrIcon(canvas, 328, 92, 16, 16, 0xff728398);
        drawChevron(canvas, 339, 134, 0xffaaaaaa);
        drawPill(canvas, 93, 157, 52, 22, "+ 状态", false);
        drawPill(canvas, 153, 157, 72, 22, "1个朋友", true);
        paint.setColor(0xfff04b55);
        canvas.drawCircle(211, 168, 3.3f, paint);
    }

    private void drawPill(Canvas canvas, float left, float top, float width, float height, String label, boolean friend) {
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(left, top, left + width, top + height, 12, 12, paint);
        paint.setColor(0xffc8c8c8);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.55f);
        canvas.drawRoundRect(left, top, left + width, top + height, 12, 12, paint);
        paint.setStyle(Paint.Style.FILL);
        float textLeft = left + 8;
        text(canvas, label, textLeft, top + 15.1f, 12.5f, 0xff858585, false, Paint.Align.LEFT);
    }

    private void drawQrIcon(Canvas canvas, float left, float top, float width, float height, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.05f);
        canvas.drawRect(left, top, left + 5, top + 5, paint);
        canvas.drawRect(left + 10, top, left + 15, top + 5, paint);
        canvas.drawRect(left, top + 10, left + 5, top + 15, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(left + 2, top + 2, left + 3, top + 3, paint);
        canvas.drawRect(left + 12, top + 2, left + 13, top + 3, paint);
        canvas.drawRect(left + 2, top + 12, left + 3, top + 13, paint);
        canvas.drawRect(left + 8, top + 8, left + 10, top + 10, paint);
        canvas.drawRect(left + 12, top + 10, left + 15, top + 12, paint);
        canvas.drawRect(left + 9, top + 13, left + 11, top + 15, paint);
    }

    private void drawSectionBreak(Canvas canvas, float top, float height) {
        paint.setColor(SECTION);
        canvas.drawRect(0, top, DESIGN_WIDTH, top + height, paint);
    }

    private void drawMeRow(Canvas canvas, String label, int bitmap, float top, float height, boolean ignored) {
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, top, DESIGN_WIDTH, top + height, paint);
        drawBitmap(canvas, bitmap, 15, top + 15.5f, 19, 19);
        text(canvas, label, 51, top + 31.5f, 17.3f, TEXT, false, Paint.Align.LEFT);
        drawChevron(canvas, 339, top + 25, 0xffaaaaaa);
        paint.setColor(LINE);
        canvas.drawRect(51, top + height - .2f, DESIGN_WIDTH, top + height, paint);
    }

    private void drawShopRow(Canvas canvas, float top, float height) {
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, top, DESIGN_WIDTH, top + height, paint);
        drawBitmap(canvas, ME_SHOP, 15, top + 15.5f, 19, 19);
        text(canvas, "小店与卡包", 51, top + 31.5f, 17.1f, TEXT, false, Paint.Align.LEFT);
        text(canvas, "[热卖1万+]治愈垂耳", 289, top + 20, 10.7f, 0xff888888, false, Paint.Align.RIGHT);
        text(canvas, "兔宝宝毛绒公仔", 289, top + 34, 10.7f, 0xff888888, false, Paint.Align.RIGHT);
        paint.setColor(0xffed3345);
        canvas.drawCircle(324, top + 10, 3.4f, paint);
        paint.setColor(0xffe95a67);
        canvas.drawCircle(310, top + 28, 13, paint);
        text(canvas, "cha", 310, top + 27, 3.6f, Color.WHITE, true, Paint.Align.CENTER);
        text(canvas, "cheer", 310, top + 31, 3.1f, Color.WHITE, true, Paint.Align.CENTER);
        drawChevron(canvas, 339, top + 25, 0xffaaaaaa);
        paint.setColor(LINE);
        canvas.drawRect(51, top + height - .2f, DESIGN_WIDTH, top + height, paint);
    }

    private void drawChevron(Canvas canvas, float cx, float cy, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.15f);
        Path p = new Path();
        p.moveTo(cx - 2.4f, cy - 5);
        p.lineTo(cx + 2.4f, cy);
        p.lineTo(cx - 2.4f, cy + 5);
        canvas.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);
    }

        private void drawBottomTabs(Canvas canvas) {
        paint.setColor(0xfff8f8f8);
        canvas.drawRect(0, TAB_TOP, DESIGN_WIDTH, DESIGN_HEIGHT, paint);
        paint.setColor(0xffe8e8e8);
        canvas.drawRect(0, TAB_TOP, DESIGN_WIDTH, TAB_TOP + .35f, paint);
        String[] labels = {"微信", "通讯录", "发现", "我"};
        for (int i = 0; i < 4; i++) {
            float cx = 45 + i * 90;
            int color = selectedTab == i ? GREEN : BLACK;
            Bitmap icon = tabBitmaps[i][selectedTab == i ? 1 : 0];
            if (icon != null && !icon.isRecycled()) {
                // The source crops are 120 x 110 px from a 4x reference capture,
                // so render them at their original 30 x 27.5 dp footprint.
                canvas.drawBitmap(icon, null, new RectF(cx - 15, 727.5f, cx + 15, 755f), paint);
            }
            // Match the unread indicator visible on Discover in the supplied
            // Me-tab reference; keep it outside the compass glyph's center.
            if (selectedTab == 3 && i == 2) {
                paint.setColor(0xfff04b55);
                canvas.drawCircle(cx + 12, 729, 3.4f, paint);
            }
            text(canvas, labels[i], cx, 766.5f, 12.1f, color, selectedTab == i, Paint.Align.CENTER);
        }
        paint.setColor(0xffb8b8b8);
        canvas.drawRoundRect(108, 782, 252, 786, 2.2f, 2.2f, paint);
    }

    private void drawBitmap(Canvas canvas, int index, float left, float top, float width, float height) {
        Bitmap bitmap = bitmaps[index];
        if (bitmap == null || bitmap.isRecycled()) return;
        paint.setAlpha(255);
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + width, top + height), paint);
    }

    private void drawTextClipped(Canvas canvas, String source, float x, float baseline, float size,
                                 int color, boolean bold, Paint.Align align, float maxWidth) {
        String value = source == null ? "" : source;
        paint.setTypeface(bold ? Typeface.create("sans-serif", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(size);
        float width = paint.measureText(value);
        if (width > maxWidth) {
            String ellipsis = "…";
            while (!value.isEmpty() && paint.measureText(value + ellipsis) > maxWidth)
                value = value.substring(0, value.length() - 1);
            value += ellipsis;
        }
        text(canvas, value, x, baseline, size, color, bold, align);
    }

    private void text(Canvas canvas, String value, float x, float baseline, float size,
                      int color, boolean bold, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        paint.setStrokeWidth(1);
        canvas.drawText(value == null ? "" : value, x, baseline, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX() * DESIGN_WIDTH / Math.max(1, getWidth());
        float y = event.getY() * DESIGN_HEIGHT / Math.max(1, getHeight());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.abortAnimation();
                downX = x;
                downY = y;
                lastY = y;
                dragging = false;
                if (velocityTracker != null) velocityTracker.recycle();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) velocityTracker.addMovement(event);
                float dragSlop = touchSlop * .65f * DESIGN_HEIGHT / Math.max(1, getHeight());
                if (!dragging && isScrollablePoint(downY)
                        && (Math.abs(y - downY) > dragSlop))
                    dragging = true;
                if (dragging) {
                    int delta = Math.round(lastY - y);
                    scrollY = clamp(scrollY + delta, 0, maxScroll);
                    lastY = y;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                    if (dragging) {
                        velocityTracker.computeCurrentVelocity(1000);
                        float velocityPx = velocityTracker.getYVelocity();
                        float velocityDp = -velocityPx * DESIGN_HEIGHT / Math.max(1, getHeight());
                        if (Math.abs(velocityPx) >= minimumFlingVelocity && maxScroll > 0) {
                            scroller.fling(0, scrollY, 0, Math.round(velocityDp), 0, 0, 0, maxScroll);
                            invalidate();
                        }
                    }
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                if (!dragging && Math.hypot(x - downX, y - downY) < touchSlop * DESIGN_HEIGHT / Math.max(1, getHeight())) {
                    handleTap(x, y);
                }
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                scroller.abortAnimation();
                dragging = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return true;
            default:
                return true;
        }
    }

    private boolean isScrollablePoint(float y) {
        // The header is part of the app canvas (system status/notification bar is
        // hidden), so a swipe may begin anywhere above the fixed bottom tabs.
        return y >= 0 && y < TAB_TOP;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = clamp(scroller.getCurrY(), 0, maxScroll);
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void handleTap(float x, float y) {
        if (y >= TAB_TOP && y < 772) {
            if (listener != null) listener.onTabSelected(clamp((int) (x / 90f), 0, 3));
            return;
        }
        if (selectedTab != 3 && y >= 27 && y < 81) {
            if (x >= 321) {
                if (listener != null) listener.onCreateConversationRequested();
            } else if (x >= 280 && x < 321) {
                if (listener != null) listener.onSearchRequested();
            }
            return;
        }
        if (selectedTab == 3) {
            float contentY = y + scrollY;
            if (contentY >= 27 && contentY < 202) notifyAction("个人资料");
            else if (contentY >= 210 && contentY < 260) notifyAction("服务");
            else if (contentY >= 268 && contentY < 318) notifyAction("收藏");
            else if (contentY >= 318 && contentY < 368) notifyAction("朋友圈");
            else if (contentY >= 368 && contentY < 418) notifyAction("作品");
            else if (contentY >= 418 && contentY < 468) notifyAction("小店与卡包");
            else if (contentY >= 468 && contentY < 518) notifyAction("表情");
            else if (contentY >= 526 && contentY < 576) notifyAction("设置");
            return;
        }
        if (y < 27 || y >= TAB_TOP) return;
        float contentY = y + scrollY;
        if (selectedTab == 0) {
            if (contentY >= 81 && contentY < 125) {
                notifyAction("Windows 微信已登录");
                return;
            }
            int index = (int) ((contentY - 125) / 65f);
            if (index >= 0 && index < conversations.size() && listener != null)
                listener.onConversationSelected(conversations.get(index));
        } else if (selectedTab == 1) {
            if (x >= 339 && contentY >= 170 && contentY < 672) {
                int index = clamp(Math.round((contentY - 174) / 16.6f), 0, 27);
                char letter = index < 2 ? '↑' : index < 28 ? (char) ('A' + index - 2) : '#';
                if (letter >= 'A' && letter <= 'Z') {
                    int row = Math.min(contactRows.size() - 1, Math.max(3, letter - 'A' + 3));
                    scrollY = clamp(597 + (row - 3) * 50 - 405, 0, maxScroll);
                    invalidate();
                }
                notifyAction("联系人索引 " + letter);
                return;
            }
            if (contentY >= 81 && contentY < 384) {
                String[] categories = {"新的朋友", "仅聊天的朋友", "群聊", "标签", "公众号", "服务号"};
                int index = clamp((int) ((contentY - 81) / 50.5f), 0, categories.length - 1);
                notifyAction(categories[index]);
            } else if (contentY >= 405 && contentY < 555) {
                int row = (int) ((contentY - 405) / 50f);
                notifyAction(contactRows.get(row).name);
            } else if (contentY >= 597) {
                int row = 3 + (int) ((contentY - 597) / 50f);
                if (row >= 0 && row < contactRows.size()) notifyAction(contactRows.get(row).name);
            }
        } else {
            String action = discoverAction(contentY);
            if (action != null) notifyAction(action);
        }
    }

    private String discoverAction(float y) {
        if (y >= 81 && y < 132) return "朋友圈";
        if (y >= 140 && y < 190) return "视频号";
        if (y >= 190 && y < 240) return "直播";
        if (y >= 248 && y < 298) return "扫一扫";
        if (y >= 298 && y < 348) return "听一听";
        if (y >= 356 && y < 406) return "看一看";
        if (y >= 406 && y < 456) return "搜一搜";
        if (y >= 464 && y < 514) return "附近的人";
        if (y >= 522 && y < 572) return "游戏";
        if (y >= 580 && y < 630) return "小程序";
        return null;
    }

    private void notifyAction(String action) {
        if (listener != null) listener.onAction(action);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ContactRow {
        final String name;
        final int bitmap;

        ContactRow(String name, int bitmap) {
            this.name = name;
            this.bitmap = bitmap;
        }
    }
}
