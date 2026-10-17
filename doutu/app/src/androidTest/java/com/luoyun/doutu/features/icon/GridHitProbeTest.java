package com.luoyun.doutu.features.icon;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * 探针：把「微」宫格那一带的点击热区量出来。只打日志、不断言，用来确定
 * 「修改应用图标」那一格到底落在什么坐标上。
 */
@RunWith(AndroidJUnit4.class)
public class GridHitProbeTest {
    private static final String TAG = "GridProbe";
    private static final String MAIN = "com.luoyun.doutu.MainActivity";

    private static View content(Activity activity) {
        return ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
    }

    private static View findNative(View candidate) {
        if (candidate.getClass().getName().endsWith("MainActivity$NativeView")) return candidate;
        if (candidate instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) candidate;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findNative(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void tapDesign(View view, float designX, float designY) {
        float x = designX * view.getWidth() / 1080f;
        float y = designY * view.getHeight() / 2388f;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            long now = SystemClock.uptimeMillis();
            view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0));
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(450);
    }

    private static List<View> windowRoots(Activity activity) {
        List<View> roots = new ArrayList<>();
        View decor = activity.getWindow().getDecorView();
        roots.add(decor);
        try {
            Class<?> global = Class.forName("android.view.WindowManagerGlobal");
            java.lang.reflect.Method getInstance = global.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object instance = getInstance.invoke(null);
            java.lang.reflect.Field listField = global.getDeclaredField("mRoots");
            listField.setAccessible(true);
            List<?> viewRoots = (List<?>) listField.get(instance);
            if (viewRoots == null) return roots;
            for (Object viewRoot : viewRoots) {
                java.lang.reflect.Method getView = viewRoot.getClass().getDeclaredMethod("getView");
                getView.setAccessible(true);
                Object view = getView.invoke(viewRoot);
                if (view instanceof View && view != decor) roots.add((View) view);
            }
        } catch (Exception error) {
            Log.w(TAG, "windowRoots failed: " + error);
        }
        return roots;
    }

    private static void collectTexts(View node, List<String> texts) {
        if (node instanceof TextView) {
            CharSequence text = ((TextView) node).getText();
            if (text != null && text.length() > 0) texts.add(text.toString());
        }
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            for (int i = 0; i < group.getChildCount(); i++) collectTexts(group.getChildAt(i), texts);
        }
    }

    /** 只读浮在上面的那个窗口（弹窗）上的文字；没有弹窗就返回空。 */
    private static String dialogText(Activity activity) {
        List<String> texts = new ArrayList<>();
        for (View root : windowRoots(activity)) {
            if (root == activity.getWindow().getDecorView()) continue;
            collectTexts(root, texts);
        }
        return String.join(" | ", texts);
    }

    private static View findText(View node, String wanted) {
        if (node instanceof TextView) {
            CharSequence text = ((TextView) node).getText();
            if (text != null && text.toString().equals(wanted)) return node;
        }
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void dismissDialog(Activity activity) {
        for (View root : windowRoots(activity)) {
            if (root == activity.getWindow().getDecorView()) continue;
            View cancel = findText(root, "取消");
            if (cancel != null) {
                cancel.performClick();
                return;
            }
            View close = findText(root, "✕");
            if (close != null) {
                close.performClick();
                return;
            }
        }
    }

    /** 主壳现在在哪个服务页（0=微 1=支 2=抖 3=Q）。 */
    private static int tracePage(Activity activity) {
        View nativeView = findNative(content(activity));
        if (nativeView == null) return -1;
        try {
            java.lang.reflect.Field field = nativeView.getClass().getDeclaredField("servicePage");
            field.setAccessible(true);
            return (Integer) field.get(nativeView);
        } catch (Exception error) {
            Log.w(TAG, "servicePage unreadable: " + error);
            return -1;
        }
    }

    @Test
    public void probe() throws Exception {
        try (ActivityScenario<? extends Activity> scenario =
                     ActivityScenario.launch(Class.forName(MAIN).asSubclass(Activity.class))) {
            final View[] holder = new View[1];
            scenario.onActivity(activity -> holder[0] = findNative(content(activity)));
            View view = holder[0];
            Log.i(TAG, "native view " + view.getWidth() + "x" + view.getHeight());

            // 先切到 微 那一页（第 1 个分段：设计坐标 x 53..267、y 730..890，取正中那一点）
            tapDesign(view, 162f, 800f);
            final int[] page = new int[1];
            scenario.onActivity(activity -> page[0] = tracePage(activity));
            Log.i(TAG, "after 微 tap: servicePage=" + page[0] + " (0=微)");

            // 第 4 行：参考图量出来的图标中线 y=1691、标签中线 y=1724；
            // 列中线 173 / 416 / 659 / 904（交界 294 / 537 / 781）。
            for (float y : new float[]{1691f, 1724f}) {
                for (float x : new float[]{173f, 416f, 659f, 904f, 616f}) {
                    tapDesign(view, x, y);
                    final String[] text = new String[1];
                    final boolean[] dialog = new boolean[1];
                    scenario.onActivity(activity -> {
                        text[0] = dialogText(activity);
                        dialog[0] = !text[0].isEmpty();
                    });
                    Log.i(TAG, "tap(" + x + "," + y + ") dialog=" + dialog[0] + " [" + text[0] + "]");
                    if (dialog[0]) {
                        scenario.onActivity(GridHitProbeTest::dismissDialog);
                        SystemClock.sleep(400);
                    }
                }
            }
        }
    }
}
