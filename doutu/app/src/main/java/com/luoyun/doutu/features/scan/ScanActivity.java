package com.luoyun.doutu.features.scan;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.luoyun.doutu.features.scan.ui.ScanOverlayScreen;

import java.io.IOException;
import java.util.List;

/**
 * 扫一扫: a live camera preview with the scan chrome of the reference screenshot on top.
 *
 * <p>The preview comes from the device camera; nothing is decoded or uploaded. The overlay, the
 * bottom tabs and the looping blue scan line are drawn locally.
 */
public final class ScanActivity extends Activity {
    private static final String EXTRA_NONCE = "scan_nonce";
    private static final int REQUEST_CAMERA = 7301;

    private final ScanOverlayScreen screen = new ScanOverlayScreen();
    private final ScanOverlayScreen.State state = new ScanOverlayScreen.State();

    private TextureView preview;
    private OverlayView overlay;
    private Camera camera;
    private Camera.Size previewSize;
    /** Clockwise turn applied to the buffer so the portrait preview comes out upright. */
    private int previewRotation = 90;
    private float downX;
    private float downY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, ScanActivity.class);
        intent.putExtra(EXTRA_NONCE, System.nanoTime());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources());
        state.clock = System.currentTimeMillis();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        preview = new TextureView(this);
        root.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        preview.setSurfaceTextureListener(surfaceListener);
        overlay = new OverlayView();
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        configureWindow(getWindow());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission()) {
            openCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] granted) {
        super.onRequestPermissionsResult(code, permissions, granted);
        if (code != REQUEST_CAMERA) return;
        if (granted.length > 0 && granted[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            Toast.makeText(this, "未获得相机权限，扫描页只显示界面", Toast.LENGTH_SHORT).show();
        }
    }

    /** The reference draws dark status icons over the preview and a dark navigation bar. */
    private void configureWindow(Window window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(0xff282d31);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    // ------------------------------------------------------------------ camera

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private final TextureView.SurfaceTextureListener surfaceListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) {
                    startPreview();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) {
                    configureTransform();
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            };

    private void openCamera() {
        if (camera != null) return;
        try {
            camera = Camera.open(0);
        } catch (RuntimeException e) {
            camera = null;
            return;
        }
        try {
            Camera.Parameters parameters = camera.getParameters();
            previewSize = choosePreviewSize(parameters);
            if (previewSize != null) {
                parameters.setPreviewSize(previewSize.width, previewSize.height);
            }
            List<String> focus = parameters.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            camera.setParameters(parameters);
            resolvePreviewRotation();
        } catch (RuntimeException e) {
            closeCamera();
            return;
        }
        if (preview.isAvailable()) startPreview();
    }

    /**
     * Rotation the buffer needs so that the preview is upright in this window.
     *
     * <p>The driver keeps {@code setDisplayOrientation} at its default and the frame is turned by
     * {@link #configureTransform()} instead. Doing both would rotate the picture twice and leave it
     * lying on its side.
     */
    private void resolvePreviewRotation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        try {
            Camera.getCameraInfo(0, info);
        } catch (RuntimeException e) {
            previewRotation = 90;
            return;
        }
        int displayRotation;
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                displayRotation = 90;
                break;
            case Surface.ROTATION_180:
                displayRotation = 180;
                break;
            case Surface.ROTATION_270:
                displayRotation = 270;
                break;
            default:
                displayRotation = 0;
                break;
        }
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            previewRotation = (360 - (info.orientation + displayRotation) % 360) % 360;
        } else {
            previewRotation = (info.orientation - displayRotation + 360) % 360;
        }
    }

    private void startPreview() {
        if (camera == null || preview == null || !preview.isAvailable()) return;
        try {
            camera.setPreviewTexture(preview.getSurfaceTexture());
            camera.startPreview();
        } catch (IOException | RuntimeException e) {
            return;
        }
        configureTransform();
    }

    private void closeCamera() {
        if (camera == null) return;
        try {
            camera.stopPreview();
        } catch (RuntimeException ignored) {
            // Preview was never started.
        }
        camera.release();
        camera = null;
    }

    /** Portrait screens want a tall preview, so prefer the size closest to the view's shape. */
    private Camera.Size choosePreviewSize(Camera.Parameters parameters) {
        List<Camera.Size> sizes;
        try {
            sizes = parameters.getSupportedPreviewSizes();
        } catch (RuntimeException e) {
            return null;
        }
        if (sizes == null || sizes.isEmpty()) return null;
        float target = (float) Math.max(1, overlay != null ? overlay.getHeight() : 2388)
                / Math.max(1, overlay != null ? overlay.getWidth() : 1080);
        Camera.Size best = null;
        float bestDelta = Float.MAX_VALUE;
        for (Camera.Size size : sizes) {
            if (size.width > 2000 || size.height > 2000) continue;
            float ratio = (float) size.height / size.width;
            float delta = Math.abs(ratio - target);
            if (delta < bestDelta - 0.02f
                    || (Math.abs(delta - bestDelta) <= 0.02f && best != null
                        && (long) size.width * size.height > (long) best.width * best.height)) {
                bestDelta = delta;
                best = size;
            }
        }
        return best != null ? best : sizes.get(0);
    }

    /** Turns the camera buffer upright and centre-crops it so it fills the screen. */
    private void configureTransform() {
        if (previewSize == null || preview == null) return;
        int viewWidth = preview.getWidth();
        int viewHeight = preview.getHeight();
        if (viewWidth == 0 || viewHeight == 0) return;
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.height, previewSize.width);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
        float scale = Math.max((float) viewHeight / previewSize.height,
                (float) viewWidth / previewSize.width);
        matrix.postScale(scale, scale, centerX, centerY);
        matrix.postRotate(previewRotation, centerX, centerY);
        preview.setTransform(matrix);
    }

    // ------------------------------------------------------------------ overlay view

    private final class OverlayView extends View {
        OverlayView() {
            super(ScanActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            state.clock = SystemClock.uptimeMillis();
            canvas.save();
            canvas.scale(getWidth() / ScanOverlayScreen.W, getHeight() / ScanOverlayScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
            postInvalidateOnAnimation();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * ScanOverlayScreen.W / Math.max(1, getWidth());
            float y = event.getY() * ScanOverlayScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x;
                    downY = y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(x - downX) > 8 || Math.abs(y - downY) > 8) moved = true;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!moved) act(screen.hitTest(state, x, y));
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        switch (token) {
            case "back":
                finish();
                break;
            case "pay":
                break;
            case "album":
                break;
            default:
                if (token.startsWith("tab:")) {
                    state.tab = Integer.parseInt(token.substring(4));
                    overlay.invalidate();
                }
                break;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
