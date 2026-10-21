package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
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
        RelativeLayout root = new RelativeLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        view.setImageBitmap(image);
        RelativeLayout.LayoutParams vlp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        vlp.addRule(RelativeLayout.CENTER_VERTICAL);
        root.addView(view, vlp);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(dp(20), 0, dp(0), 0);
        RelativeLayout.LayoutParams blp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        blp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        root.addView(bar, blp);

        TextView back = new TextView(this);
        back.setText("返回修改");
        back.setTextSize(14);
        back.setTextColor(0xff666666);
        back.setGravity(Gravity.CENTER);
        GradientDrawable b1 = new GradientDrawable();
        b1.setColor(Color.WHITE);
        b1.setCornerRadius(dp(20));
        b1.setStroke(dp(1), 0xffececec);
        back.setBackground(b1);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(108), dp(40)));

        TextView save = new TextView(this);
        save.setText("保存");
        save.setTextSize(14);
        save.setTextColor(0xff333333);
        save.setGravity(Gravity.CENTER);
        GradientDrawable b2 = new GradientDrawable();
        b2.setColor(0xffffda43);
        b2.setCornerRadius(dp(20));
        save.setBackground(b2);
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(212), dp(40));
        slp.leftMargin = dp(15);
        bar.addView(save, slp);

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
            Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
