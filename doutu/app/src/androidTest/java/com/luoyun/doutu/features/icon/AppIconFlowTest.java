package com.luoyun.doutu.features.icon;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 走一遍「微 → 修改应用图标」这条线：宫格那一格点得到、弹窗拉得起来、四行的图和名字对得上、
 * 确认之后组件状态真的换过去了。截图落在
 * /sdcard/Android/data/com.luoyun.doutu/files/icon-test/ 下，用来回头看弹窗长什么样。
 *
 * <p>放在 features.icon 包里，因为 {@link AppIcon} 是包内可见的。
 */
@RunWith(AndroidJUnit4.class)
public class AppIconFlowTest {
    private static final String TAG = "AppIconFlow";
    /** 微模块主壳 Activity，按名字启动（它自己不是 exported，只能靠 instrumentation 起）。 */
    private static final String MAIN = "com.luoyun.doutu.MainActivity";
    /** 顶上那排分段控件的「微」：设计坐标里在 x 53..267、y 730..890 那一带。 */
    private static final float WEI_TAB_X = 162f;
    private static final float WEI_TAB_Y = 800f;
    /**
     * 微宫格「修改应用图标」那一格：第 4 行第 2 格（x 295..540）。宫格四行的分界照参考图量出来
     * 是 900 / 1170 / 1390 / 1610 / 1830，所以第 4 行取 1610..1830 中间那一带的 y。
     */
    private static final float ICON_CELL_X = 417f;
    /** 紧挨着右边那一格（x 540..782）写的是「抖音去水印」：现在不做功能，点了不该有反应。 */
    private static final float WATERMARK_CELL_X = 660f;
    /** 宫格第 4 行那一带的 y。 */
    private static final float ROW4_Y = 1720f;

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

    /** 把设计坐标换成屏幕像素，再走一次真实的触摸分发。 */
    private static void tapDesign(final View view, final float designX, final float designY) {
        final float x = designX * view.getWidth() / 1080f;
        final float y = designY * view.getHeight() / 2388f;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            long now = SystemClock.uptimeMillis();
            view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0));
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(700);
    }

    private static void shot(String name) {
        try {
            Bitmap bmp = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().takeScreenshot();
            File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getExternalFilesDir("icon-test");
            if (dir == null || (!dir.exists() && !dir.mkdirs())) {
                Log.w(TAG, "no screenshot dir");
                return;
            }
            File out = new File(dir, name + ".png");
            try (FileOutputStream stream = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
            Log.i(TAG, "screenshot -> " + out.getAbsolutePath());
        } catch (Exception error) {
            Log.w(TAG, "screenshot failed", error);
        }
    }

    /**
     * 当前 Activity 底下所有窗口的根视图。Dialog 是独立窗口，不在 Activity 的视图树里，
     * 得从 WindowManagerGlobal 那份 ViewRootImpl 列表里取。
     */
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

    /** 当前应用里所有窗口的文字，按窗口逐个读。 */
    private static List<String> allWindowTexts(Activity activity) {
        List<String> texts = new ArrayList<>();
        for (View root : windowRoots(activity)) collectTexts(root, texts);
        return texts;
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

    /** 在某个窗口的视图树里找写着这段文字的 TextView。 */
    private static TextView findText(View node, String wanted) {
        if (node instanceof TextView) {
            CharSequence text = ((TextView) node).getText();
            if (text != null && text.toString().equals(wanted)) return (TextView) node;
        }
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 点写着这段文字的那一行：往上找到最近的可点祖先，performClick。 */
    private static boolean clickRowContaining(final Activity activity, final String label) {
        final boolean[] clicked = new boolean[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (View root : windowRoots(activity)) {
                TextView node = findText(root, label);
                if (node == null) continue;
                View target = node;
                while (target != null && !target.isClickable()) {
                    target = target.getParent() instanceof View ? (View) target.getParent() : null;
                }
                if (target != null) {
                    Log.i(TAG, "clicking row for " + label + " via " + target.getClass().getName());
                    target.performClick();
                    clicked[0] = true;
                }
                break;
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(900);
        return clicked[0];
    }

    /** 这一款应用图标现在是不是唯一启用的那个。 */
    private static String componentState(Activity activity) {
        PackageManager manager = activity.getPackageManager();
        StringBuilder state = new StringBuilder();
        for (AppIcon icon : AppIcon.values()) {
            ComponentName name = new ComponentName(activity.getPackageName(), icon.aliasName());
            int setting = manager.getComponentEnabledSetting(name);
            state.append(icon.key).append('=')
                    .append(setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ? "on"
                            : setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ? "off" : "default")
                    .append(' ');
        }
        return state.toString().trim();
    }

    @Test
    public void gridCellOpensSheetAndSwitchingApplies() throws Exception {
        final String[] sheetTexts = new String[1];
        final String[] state = new String[1];
        final boolean[] clicked = new boolean[2];

        try (ActivityScenario<? extends Activity> scenario =
                     ActivityScenario.launch(Class.forName(MAIN).asSubclass(Activity.class))) {
            final View[] holder = new View[1];
            scenario.onActivity(activity -> holder[0] = findNative(content(activity)));
            View view = holder[0];
            assertTrue("主壳里的 NativeView 找不到", view != null);
            Log.i(TAG, "native view " + view.getWidth() + "x" + view.getHeight());
            shot("01-shell");

            // 支 是默认页，先切到 微
            tapDesign(view, WEI_TAB_X, WEI_TAB_Y);
            shot("02-after-wei-tab");

            // 右边那一格是「抖音去水印」：这一格现在不做功能，点下去不能把修改应用图标那层拉起来
            tapDesign(view, WATERMARK_CELL_X, ROW4_Y);
            scenario.onActivity(activity ->
                    sheetTexts[0] = String.join(" | ", allWindowTexts(activity)));
            Log.i(TAG, "after 抖音去水印 tap, window texts: " + sheetTexts[0]);
            shot("03-after-watermark-tap");
            assertTrue("「抖音去水印」那一格现在不做功能，点了却弹出了修改应用图标：" + sheetTexts[0],
                    !sheetTexts[0].contains("修改应用图标") && !sheetTexts[0].contains("点一个图标"));

            // 微宫格：修改应用图标
            tapDesign(view, ICON_CELL_X, ROW4_Y);
            scenario.onActivity(activity ->
                    sheetTexts[0] = String.join(" | ", allWindowTexts(activity)));
            Log.i(TAG, "after cell tap, window texts: " + sheetTexts[0]);
            shot("04-after-cell-tap");

            assertTrue("点了宫格那一格之后没有拉起弹窗，当前窗口文字：" + sheetTexts[0],
                    sheetTexts[0].contains("修改应用图标") && sheetTexts[0].contains("点一个图标"));

            // 弹窗第一行是支付宝（alipay.png）
            scenario.onActivity(activity -> clicked[0] = clickRowContaining(activity, "支付宝"));
            scenario.onActivity(activity ->
                    sheetTexts[0] = String.join(" | ", allWindowTexts(activity)));
            Log.i(TAG, "after choosing 支付宝, window texts: " + sheetTexts[0]);
            shot("05-confirm-dialog");

            assertTrue("点了支付宝那一行之后没有确认窗，当前窗口文字：" + sheetTexts[0],
                    sheetTexts[0].contains("是否切换为"));

            scenario.onActivity(activity -> clicked[1] = clickRowContaining(activity, "切换"));
            SystemClock.sleep(1200);
            scenario.onActivity(activity -> state[0] = componentState(activity));
            Log.i(TAG, "component state after 切换: " + state[0]);
            shot("06-after-switch");
        }

        assertTrue("确认窗里的「切换」没点到", clicked[1]);
        assertTrue("切换之后应当只剩支付宝启用，实际：" + state[0],
                state[0] != null && state[0].startsWith("alipay=on"));
    }
}
