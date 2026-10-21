package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/** 本地头像：只列出本工具生成的头像，支持查看、长按删除。 */
public final class LocalAvatarsActivity extends Activity {
    private LinearLayout list;
    private File dir;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dir = new File(getFilesDir(), "avatars");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f6fa);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        Button back = new Button(this);
        back.setText("‹ 返回");
        back.setAllCaps(false);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(88), dp(50)));
        TextView title = new TextView(this);
        title.setText("本地头像");
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(88), dp(50)));
        root.addView(bar);

        Button add = new Button(this);
        add.setText("＋ 添加本地头像");
        add.setAllCaps(false);
        add.setOnClickListener(v -> pickImages());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        alp.leftMargin = dp(16);
        alp.rightMargin = dp(16);
        alp.topMargin = dp(12);
        root.addView(add, alp);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(20), dp(20), dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        refresh();
        if (getIntent().getBooleanExtra("selftest", false)) finish();
    }

    private void refresh() {
        list.removeAllViews();
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有生成过头像\n在「换头像 / 自动替换 / AI一键模仿」里生成后会出现在这里");
            empty.setTextSize(14);
            empty.setTextColor(0xff8b90a0);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(80), 0, 0);
            list.addView(empty);
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        LinearLayout row = null;
        for (int i = 0; i < files.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                list.addView(row);
            }
            row.addView(tile(files[i]));
        }
    }

    private View tile(File f) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.setMargins(dp(6), dp(6), dp(6), dp(12));
        cell.setLayoutParams(clp);
        ImageView thumb = new ImageView(this);
        thumb.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(0xffeef0f6);
        cell.addView(thumb, new LinearLayout.LayoutParams(dp(82), dp(82)));
        TextView name = new TextView(this);
        name.setText(f.getName().replace(".png", ""));
        name.setTextSize(10);
        name.setTextColor(0xff8b90a0);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        cell.addView(name);
        thumb.setOnClickListener(v -> {
            ImageView big = new ImageView(this);
            big.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
            big.setAdjustViewBounds(true);
            new AlertDialog.Builder(this).setTitle("本地头像").setView(big)
                    .setPositiveButton("关闭", null).show();
        });
        thumb.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("删除头像").setMessage(f.getName())
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> {
                        if (f.delete()) { Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show(); refresh(); }
                    }).show();
            return true;
        });
        return cell;
    }

    private void pickImages() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(android.content.Intent.createChooser(intent, "选择头像"), 61); }
        catch (Exception e) { Toast.makeText(this, "没有可用的图片选择器", Toast.LENGTH_SHORT).show(); }
    }

    @Override
    protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 61 || result != RESULT_OK || data == null) return;
        java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
        if (data.getClipData() != null) {
            android.content.ClipData clip = data.getClipData();
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        int ok = 0;
        for (android.net.Uri uri : uris) {
            try {
                android.graphics.Bitmap bmp = com.luoyun.doutu.account.AvatarUpload.decode(this, uri);
                if (bmp == null) continue;
                if (!dir.exists()) dir.mkdirs();
                java.io.File out = new java.io.File(dir, "local_" + System.currentTimeMillis() + ".png");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, fos);
                fos.close();
                ok++;
            } catch (Exception ignored) { }
        }
        Toast.makeText(this, ok > 0 ? "已添加 " + ok + " 张" : "添加失败", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
