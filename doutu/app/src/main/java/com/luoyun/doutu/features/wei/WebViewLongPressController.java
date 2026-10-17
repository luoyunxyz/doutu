package com.luoyun.doutu.features.wei;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;

import java.util.Locale;

/**
 * Observes WebView touch events without consuming them and bridges one stable
 * long-press recognizer to the bundled fake-world page.
 *
 * The WebView keeps ownership of scrolling. This class only watches DOWN/MOVE/
 * UP and cancels after a generous movement threshold, so a real finger can
 * jitter while a deliberate vertical swipe still cancels the pending press.
 */
final class WebViewLongPressController implements View.OnTouchListener, View.OnLongClickListener {
    private static final long HOLD_MS = 1500L;
    private static final int MIN_MOVE_DP = 28;

    private final WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private final int moveThresholdPx;
    private Runnable trigger;
    private float downX;
    private float downY;
    private boolean tracking;
    private boolean fired;

    WebViewLongPressController(WebView webView) {
        this.webView = webView;
        density = webView.getResources().getDisplayMetrics().density;
        int touchSlop = ViewConfiguration.get(webView.getContext()).getScaledTouchSlop();
        int generousSlop = Math.round(MIN_MOVE_DP * density);
        moveThresholdPx = Math.max(generousSlop, touchSlop * 4);
    }

    void attach() {
        // The page owns the menu. Disable Chromium's text-selection/context callout
        // so it cannot compete with the 1.5 second page menu.
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);
        webView.setOnLongClickListener(this);
        webView.setOnTouchListener(this);
    }

    void detach() {
        cancelPending(false);
        webView.setOnTouchListener(null);
        webView.setOnLongClickListener(null);
    }

    @Override
    public boolean onLongClick(View v) {
        // Keep the Android/WebView native long-click consumed if a WebView build
        // still asks for it. The custom timer below is the only menu owner.
        return true;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                begin(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                if (tracking && movedTooFar(event.getX(), event.getY())) {
                    cancelPending(true);
                }
                break;
            case MotionEvent.ACTION_UP:
                finishTouch("up");
                break;
            case MotionEvent.ACTION_CANCEL:
                // Chromium may emit CANCEL when its internal recognizer takes over
                // around the native long-click deadline. If the finger has not
                // moved beyond our scroll threshold, keep the timer alive. A real
                // scroll already passed through ACTION_MOVE and was cancelled.
                if (tracking && fired) {
                    finishTouch("up");
                }
                break;
            default:
                break;
        }
        // Never consume the event: WebView/Chromium must retain vertical scrolling
        // and its normal click/navigation behavior.
        return false;
    }

    private void begin(float x, float y) {
        cancelPending(false);
        downX = x;
        downY = y;
        tracking = true;
        fired = false;
        sendTouch("down", x, y);
        trigger = () -> {
            if (!tracking || webView.getWindowToken() == null) return;
            fired = true;
            tracking = false;
            trigger = null;
            sendOpenContext();
        };
        handler.postDelayed(trigger, HOLD_MS);
    }

    private boolean movedTooFar(float x, float y) {
        float dx = x - downX;
        float dy = y - downY;
        return dx * dx + dy * dy > (float) moveThresholdPx * moveThresholdPx;
    }

    private void finishTouch(String action) {
        boolean wasTracking = tracking;
        tracking = false;
        if (trigger != null) {
            handler.removeCallbacks(trigger);
            trigger = null;
        }
        if (wasTracking || fired) {
            sendTouch(action, downX, downY);
        }
        fired = false;
    }

    private void cancelPending(boolean notifyPage) {
        boolean wasTracking = tracking;
        tracking = false;
        fired = false;
        if (trigger != null) {
            handler.removeCallbacks(trigger);
            trigger = null;
        }
        if (notifyPage && wasTracking) {
            sendTouch("cancel", downX, downY);
        }
    }

    private void sendOpenContext() {
        int width = webView.getWidth();
        int height = webView.getHeight();
        if (width <= 0 || height <= 0) return;
        String script = String.format(Locale.US,
                "window.__fwNativeOpenChatContextAt && window.__fwNativeOpenChatContextAt(%s,%s,%d,%d)",
                Float.toString(downX), Float.toString(downY), width, height);
        webView.evaluateJavascript(script, null);
    }

    private void sendTouch(String action, float x, float y) {
        int width = webView.getWidth();
        int height = webView.getHeight();
        if (width <= 0 || height <= 0) return;
        String script = String.format(Locale.US,
                "window.__fwNativeChatTouch && window.__fwNativeChatTouch('%s',%s,%s,%d,%d)",
                action, Float.toString(x), Float.toString(y), width, height);
        webView.evaluateJavascript(script, null);
    }
}
