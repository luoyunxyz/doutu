package com.luoyun.doutu.features.pay;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the square a QR code occupies inside a photograph.
 *
 * <p>Collection codes are handed around as screenshots — a whole poster with the symbol in the
 * middle — so pasting the picture as-is would fill the card with the poster's background.  This
 * locator looks for the three finder patterns (the nested squares in the corners), checks that
 * they sit on an isosceles right triangle whose spacing matches a real symbol version, and
 * returns the bounding square of the symbol in the source bitmap's coordinates.  It is a small
 * hand-rolled version of what a QR decoder's detector does; nothing is decoded or transmitted.
 */
public final class QrLocator {
    /** Longest edge of the working copy; big enough for a sharp module, small enough to be fast. */
    private static final int WORK_MAX = 1600;
    private static final int MAX_CLUSTERS = 14;

    private QrLocator() {
    }

    /** @return the symbol's bounds in {@code src} coordinates, or null when none is found. */
    public static RectF locate(Bitmap src) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw < 32 || sh < 32) return null;
        float scale = Math.min(1f, WORK_MAX / (float) Math.max(sw, sh));
        int w = Math.max(1, Math.round(sw * scale));
        int h = Math.max(1, Math.round(sh * scale));
        int[] px = new int[w * h];
        Bitmap work = (w == sw && h == sh) ? src : Bitmap.createScaledBitmap(src, w, h, true);
        work.getPixels(px, 0, w, 0, 0, w, h);
        if (work != src) work.recycle();

        byte[] gray = new byte[w * h];
        int[] histogram = new int[256];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int lum = ((p >> 16 & 0xff) * 77 + (p >> 8 & 0xff) * 151 + (p & 0xff) * 28) >> 8;
            gray[i] = (byte) lum;
            histogram[lum]++;
        }
        int threshold = otsu(histogram, w * h);
        byte[] bin = new byte[w * h];
        for (int i = 0; i < gray.length; i++) {
            bin[i] = (byte) ((gray[i] & 0xff) < threshold ? 1 : 0);
        }

        List<float[]> candidates = horizontalPass(bin, w, h);
        candidates = verticalPass(bin, w, h, candidates);
        List<float[]> clusters = cluster(candidates);
        Collections.sort(clusters, (a, b) -> Float.compare(b[3], a[3]));
        float[] corner = null;
        float[] first = null;
        float[] second = null;
        float best = -1f;
        int limit = Math.min(clusters.size(), MAX_CLUSTERS);
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                for (int k = j + 1; k < limit; k++) {
                    float[][] triple = {clusters.get(i), clusters.get(j), clusters.get(k)};
                    float lo = Math.min(triple[0][2], Math.min(triple[1][2], triple[2][2]));
                    float hi = Math.max(triple[0][2], Math.max(triple[1][2], triple[2][2]));
                    if (hi / lo > 1.7f) continue;
                    for (int c = 0; c < 3; c++) {
                        float[] p = triple[c];
                        float[] q = triple[(c + 1) % 3];
                        float[] s = triple[(c + 2) % 3];
                        double d1 = dist(p, q);
                        double d2 = dist(p, s);
                        double d3 = dist(q, s);
                        double m = (p[2] + q[2] + s[2]) / 3.0;
                        if (Math.min(d1, d2) < 4 * m || Math.max(d1, d2) > 200 * m) continue;
                        if (Math.abs(d1 - d2) / Math.max(d1, d2) > 0.22) continue;
                        double hyp = Math.sqrt(d1 * d1 + d2 * d2);
                        if (Math.abs(d3 - hyp) / d3 > 0.22) continue;
                        // Adjacent finder centres sit (V - 7) modules apart and V is one of
                        // 21, 25 ... 177, which rules out text and other look-alike patterns.
                        double modules = d1 / m + 7.0;
                        int version = (int) Math.round(modules);
                        if (version < 21 || version > 177 || (version - 21) % 4 != 0) continue;
                        if (Math.abs(modules - version) > 0.9) continue;
                        float score = p[3] + q[3] + s[3]
                                - (float) (Math.abs(d1 - d2) + Math.abs(d3 - hyp))
                                - (float) Math.abs(modules - version) * 20f;
                        if (score > best) {
                            best = score;
                            corner = p;
                            first = q;
                            second = s;
                        }
                    }
                }
            }
        }
        if (corner == null) return null;
        float m = (corner[2] + first[2] + second[2]) / 3f;
        float minX = Math.min(corner[0], Math.min(first[0], second[0]));
        float maxX = Math.max(corner[0], Math.max(first[0], second[0]));
        float minY = Math.min(corner[1], Math.min(first[1], second[1]));
        float maxY = Math.max(corner[1], Math.max(first[1], second[1]));
        RectF box = new RectF(
                (minX - 3.5f * m) / scale, (minY - 3.5f * m) / scale,
                (maxX + 3.5f * m) / scale, (maxY + 3.5f * m) / scale);
        box.left = Math.max(0f, box.left);
        box.top = Math.max(0f, box.top);
        box.right = Math.min(sw, box.right);
        box.bottom = Math.min(sh, box.bottom);
        if (box.width() < 32f || box.height() < 32f) return null;
        return box;
    }

    // ------------------------------------------------------------------ passes

    private static List<float[]> horizontalPass(byte[] bin, int w, int h) {
        List<float[]> out = new ArrayList<>();
        int[] start = new int[5];
        int[] length = new int[5];
        int[] color = new int[5];
        int filled = 0;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int current = bin[row] & 1;
            int count = 0;
            for (int x = 0; x < w; x++) {
                int v = bin[row + x] & 1;
                if (v == current) {
                    count++;
                    continue;
                }
                // A run just ended: push it and re-examine the trailing five.
                if (filled == 5) {
                    System.arraycopy(start, 1, start, 0, 4);
                    System.arraycopy(length, 1, length, 0, 4);
                    System.arraycopy(color, 1, color, 0, 4);
                } else {
                    filled++;
                }
                start[filled - 1] = x - count;
                length[filled - 1] = count;
                color[filled - 1] = current;
                if (filled == 5 && color[0] == 1 && color[1] == 0 && color[2] == 1
                        && color[3] == 0 && color[4] == 1) {
                    float score = ratio(length);
                    if (score > 0f) {
                        float centre = start[2] + length[2] / 2f;
                        float module = sum(length) / 7f;
                        out.add(new float[]{centre, y, module, score});
                    }
                }
                current = v;
                count = 1;
            }
            if (filled == 5) {
                System.arraycopy(start, 1, start, 0, 4);
                System.arraycopy(length, 1, length, 0, 4);
                System.arraycopy(color, 1, color, 0, 4);
            } else {
                filled++;
            }
            start[filled - 1] = w - count;
            length[filled - 1] = count;
            color[filled - 1] = current;
            if (filled == 5 && color[0] == 1 && color[1] == 0 && color[2] == 1
                    && color[3] == 0 && color[4] == 1) {
                float score = ratio(length);
                if (score > 0f) {
                    out.add(new float[]{start[2] + length[2] / 2f, y, sum(length) / 7f, score});
                }
            }
        }
        return out;
    }

    private static List<float[]> verticalPass(byte[] bin, int w, int h, List<float[]> cands) {
        Map<Integer, int[][]> columns = new HashMap<>();
        List<float[]> out = new ArrayList<>();
        for (float[] c : cands) {
            int x = Math.round(c[0]);
            float y = c[1];
            if (x < 0 || x >= w) continue;
            int[][] runs = columns.get(x);
            if (runs == null) {
                runs = columnRuns(bin, w, h, x);
                columns.put(x, runs);
            }
            int i = -1;
            for (int k = 0; k < runs[0].length; k++) {
                if (runs[2][k] == 1 && runs[0][k] <= y && y < runs[0][k] + runs[1][k]) {
                    i = k;
                    break;
                }
            }
            if (i < 2 || i + 2 >= runs[0].length) continue;
            if (runs[2][i - 2] != 1 || runs[2][i - 1] != 0 || runs[2][i + 1] != 0
                    || runs[2][i + 2] != 1) {
                continue;
            }
            int[] five = {runs[1][i - 2], runs[1][i - 1], runs[1][i], runs[1][i + 1],
                    runs[1][i + 2]};
            float score = ratio(five);
            if (score <= 0.8f) continue;
            out.add(new float[]{c[0], runs[0][i] + runs[1][i] / 2f, sum(five) / 7f,
                    score * c[3]});
        }
        return out;
    }

    /** Column run-length encoding: start rows, lengths and colours, in order. */
    private static int[][] columnRuns(byte[] bin, int w, int h, int x) {
        int[] start = new int[h];
        int[] length = new int[h];
        int[] color = new int[h];
        int n = 0;
        int current = bin[x] & 1;
        int count = 0;
        for (int y = 0; y < h; y++) {
            int v = bin[y * w + x] & 1;
            if (v == current) {
                count++;
                continue;
            }
            start[n] = y - count;
            length[n] = count;
            color[n] = current;
            n++;
            current = v;
            count = 1;
        }
        start[n] = h - count;
        length[n] = count;
        color[n] = current;
        n++;
        return new int[][]{start, length, color, {n}};
    }

    private static List<float[]> cluster(List<float[]> cands) {
        Collections.sort(cands, (a, b) -> Float.compare(b[3], a[3]));
        List<float[]> groups = new ArrayList<>();
        for (float[] c : cands) {
            boolean merged = false;
            for (float[] g : groups) {
                float gx = g[0] / g[4];
                float gy = g[1] / g[4];
                if (Math.abs(gx - c[0]) < c[2] * 2.5f && Math.abs(gy - c[1]) < c[2] * 2.5f) {
                    g[0] += c[0];
                    g[1] += c[1];
                    g[2] += c[2];
                    g[3] += c[3];
                    g[4] += 1;
                    merged = true;
                    break;
                }
            }
            if (!merged) groups.add(new float[]{c[0], c[1], c[2], c[3], 1f});
        }
        List<float[]> out = new ArrayList<>();
        for (float[] g : groups) {
            if (g[4] < 2) continue;
            out.add(new float[]{g[0] / g[4], g[1] / g[4], g[2] / g[4], g[3]});
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    /** 1.0 when the five runs are exactly 1:1:3:1:1, negative when they are too small. */
    private static float ratio(int[] counts) {
        int total = sum(counts);
        if (total < 7) return -1f;
        float m = total / 7f;
        float err = Math.abs(counts[0] - m) + Math.abs(counts[1] - m)
                + Math.abs(counts[2] - 3f * m) + Math.abs(counts[3] - m)
                + Math.abs(counts[4] - m);
        return 1f - err / total;
    }

    private static int sum(int[] v) {
        int t = 0;
        for (int x : v) t += x;
        return t;
    }

    private static double dist(float[] a, float[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static int otsu(int[] histogram, int total) {
        long sumAll = 0;
        for (int i = 0; i < 256; i++) sumAll += (long) i * histogram[i];
        long sumB = 0;
        int wB = 0;
        double best = -1;
        int threshold = 128;
        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;
            sumB += (long) t * histogram[t];
            double mB = sumB / (double) wB;
            double mF = (sumAll - sumB) / (double) wF;
            double between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > best) {
                best = between;
                threshold = t;
            }
        }
        return threshold;
    }
}
