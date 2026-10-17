package com.luoyun.doutu.features.zhi.ui;
import android.content.res.Resources;
import android.graphics.*;
import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.Bill;
final class BillMarks {
    private static final java.util.Map<String,Bitmap> images = new java.util.HashMap<>();
    static void load(Resources res) {
        if (!images.isEmpty()) return;
        String[] names = {"云猫","花呗","闲鱼","移动","公交","交管","T","抖音"};
        int[] ids = {R.drawable.bill_logo_cat,R.drawable.bill_logo_huabei,R.drawable.bill_logo_xianyu,
            R.drawable.bill_logo_mobile,R.drawable.bill_logo_bus,R.drawable.bill_logo_traffic,
            R.drawable.bill_logo_token,R.drawable.bill_logo_douyin};
        for(int i=0;i<ids.length;i++) images.put(names[i],BitmapFactory.decodeResource(res,ids[i]));
    }
    private static final java.util.Map<Integer,Bitmap> artwork = new java.util.HashMap<>();
    static Bitmap art(Resources res, int id) {
        Bitmap bitmap = artwork.get(id);
        if (bitmap == null) { bitmap = BitmapFactory.decodeResource(res,id); artwork.put(id,bitmap); }
        return bitmap;
    }
    static Bitmap image(Bill bill) { return bill.icon != null ? bill.icon : images.get(bill.mark); }
}
