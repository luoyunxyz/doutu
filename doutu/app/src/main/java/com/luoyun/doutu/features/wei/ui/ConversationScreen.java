package com.luoyun.doutu.features.wei.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.wei.model.Conversation;

/** Native, interactive reconstruction of the supplied WeChat conversation screen. */
public final class ConversationScreen extends LinearLayout {
    public interface Listener {
        void onSend(String message);
        void onBack();
    }

    private static final int CHAT_BG = 0xffededed;
    private static final int COMPOSER_BG = 0xfff7f7f7;
    private static final int TEXT_COLOR = 0xff191919;
    // The supplied reference uses the lighter WeChat green shown in the message bubbles.
    private static final int SENT_GREEN = 0xffaaeA7a;

    private final Conversation conversation;
    private final Listener listener;
    private final LinearLayout messages;
    private final ScrollView messageScroll;
    private final EditText composer;
    private final IconView fieldAction;

    public ConversationScreen(Context context, Conversation conversation, Listener listener) {
        super(context);
        this.conversation = conversation;
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(CHAT_BG);
        setFocusableInTouchMode(true);

        addView(createHeader(context), new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));

        messageScroll = new ScrollView(context);
        messageScroll.setFillViewport(false);
        messageScroll.setClipToPadding(false);
        messageScroll.setVerticalScrollBarEnabled(true);
        messageScroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        messageScroll.setBackgroundColor(CHAT_BG);
        messages = new LinearLayout(context);
        messages.setOrientation(VERTICAL);
        // The reference opens near the bottom of a longer thread.  A small hidden
        // top buffer lets fullScroll() reproduce that initial viewport while still
        // keeping the list fully native and user-scrollable.
        messages.setPadding(dp(9), dp(13), dp(9), dp(22));
        messageScroll.addView(messages, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(messageScroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout composerBar = new LinearLayout(context);
        composerBar.setOrientation(HORIZONTAL);
        composerBar.setGravity(Gravity.CENTER_VERTICAL);
        composerBar.setPadding(dp(5), dp(6), dp(5), dp(6));
        composerBar.setBackgroundColor(COMPOSER_BG);

        IconView voiceButton = new IconView(context, IconView.VOICE);
        voiceButton.setContentDescription("切换语音输入");
        LayoutParams voiceParams = new LayoutParams(dp(34), dp(38));
        voiceParams.rightMargin = dp(5);
        composerBar.addView(voiceButton, voiceParams);
        FrameLayout inputFrame = new FrameLayout(context);
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(Color.WHITE);
        inputBackground.setCornerRadius(dp(7));
        inputFrame.setBackground(inputBackground);
        composer = new EditText(context);
        composer.setSingleLine(true);
        composer.setTextSize(16);
        composer.setTextColor(TEXT_COLOR);
        composer.setHintTextColor(0xffa4a4a4);
        composer.setHint("");
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        composer.setPadding(dp(10), 0, dp(3), 0);
        composer.setBackgroundColor(Color.TRANSPARENT);
        composer.setSelectAllOnFocus(false);
        voiceButton.setOnClickListener(v -> {
            boolean voiceMode = "按住 说话".contentEquals(composer.getHint());
            composer.setHint(voiceMode ? "" : "按住 说话");
            composer.requestFocus();
        });
        FrameLayout.LayoutParams editParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        editParams.rightMargin = dp(36);
        inputFrame.addView(composer, editParams);
        fieldAction = new IconView(context, IconView.MIC);
        fieldAction.setContentDescription("发送消息");
        FrameLayout.LayoutParams micParams = new FrameLayout.LayoutParams(dp(34), LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        inputFrame.addView(fieldAction, micParams);
        fieldAction.setOnClickListener(v -> {
            if (composer.length() > 0) sendCurrentMessage();
            else {
                composer.requestFocus();
                InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.showSoftInput(composer, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        composer.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                fieldAction.setIcon(s.length() == 0 ? IconView.MIC : IconView.SEND);
                fieldAction.setContentDescription(s.length() == 0 ? "语音输入" : "发送消息");
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        composer.setOnEditorActionListener((view, actionId, event) -> {
            boolean send = actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP);
            if (send) {
                sendCurrentMessage();
                return true;
            }
            return false;
        });
        composerBar.addView(inputFrame, new LayoutParams(0, dp(38), 1));

        IconView emojiButton = new IconView(context, IconView.EMOJI);
        emojiButton.setContentDescription("表情");
        LayoutParams emojiParams = new LayoutParams(dp(38), dp(40));
        emojiParams.leftMargin = dp(4);
        composerBar.addView(emojiButton, emojiParams);
        emojiButton.setOnClickListener(v -> {
            int cursor = Math.max(0, composer.getSelectionStart());
            composer.getText().insert(cursor, "😊");
        });

        IconView addButton = new IconView(context, IconView.ADD);
        addButton.setContentDescription("更多功能");
        LayoutParams addParams = new LayoutParams(dp(38), dp(40));
        addParams.leftMargin = dp(2);
        composerBar.addView(addButton, addParams);
        addButton.setOnClickListener(v -> new AlertDialog.Builder(context)
                .setItems(new String[]{"照片", "拍摄", "位置"}, (dialog, which) ->
                        Toast.makeText(context, new String[]{"选择照片", "打开相机", "发送位置"}[which], Toast.LENGTH_SHORT).show())
                .show());

        addView(composerBar, new LayoutParams(LayoutParams.MATCH_PARENT, dp(50)));
        refreshMessages();
    }

    private View createHeader(Context context) {
        FrameLayout header = new FrameLayout(context);
        header.setBackgroundColor(CHAT_BG);
        IconView back = new IconView(context, IconView.BACK);
        back.setContentDescription("返回");
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(52), LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(back, backParams);
        back.setOnClickListener(v -> {
            if (listener != null) listener.onBack();
        });

        TextView title = new TextView(context);
        title.setText(conversation.getTitle());
        title.setTextColor(TEXT_COLOR);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        titleParams.leftMargin = dp(56);
        titleParams.rightMargin = dp(56);
        header.addView(title, titleParams);

        IconView more = new IconView(context, IconView.MORE);
        more.setContentDescription("更多聊天信息");
        FrameLayout.LayoutParams moreParams = new FrameLayout.LayoutParams(dp(52), LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(more, moreParams);
        more.setOnClickListener(v -> Toast.makeText(context, "聊天信息", Toast.LENGTH_SHORT).show());
        View divider = new View(context);
        divider.setBackgroundColor(0xffe7e7e7);
        FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(0.5f), Gravity.BOTTOM);
        header.addView(divider, dividerParams);
        return header;
    }

    private void sendCurrentMessage() {
        String value = composer.getText().toString().trim();
        if (value.isEmpty()) return;
        if (listener != null) listener.onSend(value);
        composer.setText("");
        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    public void refreshMessages() {
        messages.removeAllViews();
        for (Conversation.Message message : conversation.getMessages()) addMessageRow(message);
        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessageRow(Conversation.Message message) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL | (message.isSentByMe() ? Gravity.RIGHT : Gravity.LEFT));

        ImageView avatar = new ImageView(getContext());
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int avatarId = message.isSentByMe() ? R.drawable.chat_avatar_me : conversation.getAvatarResourceId();
        avatar.setImageResource(avatarId);
        GradientDrawable avatarShape = new GradientDrawable();
        avatarShape.setColor(Color.WHITE);
        avatarShape.setCornerRadius(dp(5));
        avatar.setBackground(avatarShape);
        avatar.setClipToOutline(true);
        avatar.setContentDescription(message.isSentByMe() ? "我的头像" : conversation.getTitle() + "的头像");

        TextView bubble = new TextView(getContext());
        bubble.setText(message.getText());
        bubble.setTextSize(16);
        bubble.setTextColor(TEXT_COLOR);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setIncludeFontPadding(true);
         bubble.setPadding(dp(11), dp(6), dp(11), dp(6));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.66f));
        bubble.setMinHeight(dp(34));
        bubble.setBackground(new BubbleDrawable(message.isSentByMe() ? SENT_GREEN : Color.WHITE,
                message.isSentByMe(), dp(7), dp(4), dp(6)));

        // Keep the avatar tile fixed per message identity.  At 480dpi, 36dp
        // produces the approximately 110px visible tile in the reference.
        LayoutParams avatarParams = new LayoutParams(dp(36), dp(36));
        LayoutParams bubbleParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (message.isSentByMe()) {
            bubbleParams.rightMargin = dp(7);
            avatarParams.leftMargin = dp(3);
            row.addView(bubble, bubbleParams);
            row.addView(avatar, avatarParams);
        } else {
            avatarParams.rightMargin = dp(3);
            bubbleParams.leftMargin = 0;
            bubbleParams.rightMargin = dp(35);
            row.addView(avatar, avatarParams);
            row.addView(bubble, bubbleParams);
        }
        LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        // The smaller avatar is paired with the reference's wider inter-row gap;
        // the resulting row pitch remains 47dp on the test device.
        rowParams.bottomMargin = dp(11);
        messages.addView(row, rowParams);
    }

    private int dp(float value) {
        return WeiPalette.dp(getContext(), value);
    }

    /** A compact WeChat-like bubble with the triangular tail drawn in the same fill. */
    private static final class BubbleDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tail = new Path();
        private final int color;
        private final boolean sent;
        private final float tailWidth;
        private final float tailHeight;
        private final float radius;

        BubbleDrawable(int color, boolean sent, float tailWidth, float tailHeight, float radius) {
            this.color = color;
            this.sent = sent;
            this.tailWidth = tailWidth;
            this.tailHeight = tailHeight;
            this.radius = radius;
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float centerY = bounds.centerY();
            float inset = Math.min(tailWidth, bounds.width() * 0.2f);
            RectF body = new RectF(bounds);
            if (sent) body.right -= inset;
            else body.left += inset;
            canvas.drawRoundRect(body, radius, radius, paint);
            tail.reset();
            if (sent) {
                tail.moveTo(body.right - radius * 0.2f, centerY - tailHeight);
                tail.quadTo(bounds.right - inset * 0.15f, centerY - tailHeight * 0.5f, bounds.right, centerY);
                tail.quadTo(bounds.right - inset * 0.15f, centerY + tailHeight * 0.5f, body.right - radius * 0.2f, centerY + tailHeight);
            } else {
                tail.moveTo(body.left + radius * 0.2f, centerY - tailHeight);
                tail.quadTo(bounds.left + inset * 0.15f, centerY - tailHeight * 0.5f, bounds.left, centerY);
                tail.quadTo(bounds.left + inset * 0.15f, centerY + tailHeight * 0.5f, body.left + radius * 0.2f, centerY + tailHeight);
            }
            tail.close();
            canvas.drawPath(tail, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** Small vector icons keep the composer and title bar crisp at every display density. */
    private static final class IconView extends View {
        static final int BACK = 0, MORE = 1, VOICE = 2, MIC = 3, EMOJI = 4, ADD = 5, SEND = 6;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int icon;

        IconView(Context context, int icon) {
            super(context);
            this.icon = icon;
            setClickable(true);
            setFocusable(true);
        }

        void setIcon(int icon) {
            this.icon = icon;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = getResources().getDisplayMetrics().density;
            canvas.save();
            canvas.translate((getWidth() - 32 * scale) / 2f, (getHeight() - 32 * scale) / 2f);
            canvas.scale(scale, scale);
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            if (icon == BACK) {
                Path p = new Path();
                p.moveTo(20, 5); p.lineTo(9, 16); p.lineTo(20, 27);
                canvas.drawPath(p, paint);
            } else if (icon == MORE) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(9, 16, 1.8f, paint);
                canvas.drawCircle(16, 16, 1.8f, paint);
                canvas.drawCircle(23, 16, 1.8f, paint);
            } else if (icon == VOICE) {
                canvas.drawCircle(16, 16, 12, paint);
                Path speaker = new Path();
                speaker.moveTo(8, 14); speaker.lineTo(11, 14); speaker.lineTo(15, 11); speaker.lineTo(15, 21); speaker.lineTo(11, 18); speaker.lineTo(8, 18); speaker.close();
                canvas.drawPath(speaker, paint);
                canvas.drawArc(13, 10, 23, 22, -65, 130, false, paint);
                canvas.drawArc(12, 7, 27, 25, -65, 130, false, paint);
            } else if (icon == MIC) {
                RectF capsule = new RectF(12, 5, 20, 19);
                canvas.drawRoundRect(capsule, 4, 4, paint);
                Path mic = new Path();
                mic.moveTo(8, 15); mic.lineTo(8, 17);
                mic.quadTo(8, 25, 16, 25);
                mic.quadTo(24, 25, 24, 17);
                mic.lineTo(24, 15);
                canvas.drawPath(mic, paint);
                canvas.drawLine(16, 25, 16, 29, paint);
                canvas.drawLine(12, 29, 20, 29, paint);
            } else if (icon == EMOJI) {
                canvas.drawCircle(16, 16, 12, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12, 13, 1.3f, paint);
                canvas.drawCircle(20, 13, 1.3f, paint);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawArc(11, 12, 21, 21, 15, 150, false, paint);
            } else if (icon == ADD) {
                canvas.drawCircle(16, 16, 12, paint);
                canvas.drawLine(10, 16, 22, 16, paint);
                canvas.drawLine(16, 10, 16, 22, paint);
            } else if (icon == SEND) {
                paint.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(5, 15); p.lineTo(27, 5); p.lineTo(20, 27); p.lineTo(15, 19); p.close();
                canvas.drawPath(p, paint);
            }
            canvas.restore();
        }
    }
}
