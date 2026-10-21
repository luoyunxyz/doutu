package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/** 对齐参考 activity_replace_preview：全屏预览 + 底部「返回修改 / 保存」。 */
public final class ReplacePreviewActivity extends Activity {
    public static Bitmap image;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (image == null) { finish(); return; }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageBitmap(image);
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(dp(20), 0, dp(20), 0);
        Button back = new Button(this);
        back.setText("返回修改");
        back.setAllCaps(false);
        back.setTextSize(14);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(108), dp(40)));
        Button save = new Button(this);
        save.setText("保存");
        save.setAllCaps(false);
        save.setTextSize(14);
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(212), dp(40));
        slp.leftMargin = dp(15);
        bar.addView(save, slp);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        setContentView(root);
    }

    private void save() {
        try {
            MediaStore.Images.Media.insertImage(getContentResolver(), image, "doutu_tool_" + System.currentTimeMillis(), "doutu tools");
            File dir = new File(getFilesDir(), "avatars");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "avatar_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(out);
            image.compress(Bitmap.CompressFormat.PNG, 95, fos);
            fos.close();
            Toast.makeText(this, "已保存到相册与本地头像", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
