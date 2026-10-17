from PIL import Image
from collections import deque
p=r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg'
im=Image.open(p).convert('RGB')
w,h=im.size
pix=im.load()
mask=set()
for y in range(h):
    for x in range(w):
        r,g,b=pix[x,y]
        if r>220 and 90<g<190 and b<100:
            mask.add((x,y))
print('count', len(mask))
if mask:
 xs=[p[0] for p in mask]; ys=[p[1] for p in mask]
 print('bbox', min(xs), min(ys), max(xs), max(ys))
# crude bbox by rows/cols for mask near bottom
rows={}
cols={}
for x,y in mask:
    rows[y]=rows.get(y,0)+1; cols[x]=cols.get(x,0)+1
# continuous y ranges with many pixels
ranges=[]; start=None; prev=None
for y in sorted(k for k,v in rows.items() if v>100):
    if start is None: start=prev=y
    elif y==prev+1: prev=y
    else: ranges.append((start,prev)); start=prev=y
if start is not None: ranges.append((start,prev))
print('yranges rows>100', ranges[:20])
for yr in ranges:
    y0,y1=yr
    pts=[(x,y) for x,y in mask if y0<=y<=y1]
    if pts:
        print(yr, min(x for x,y in pts), max(x for x,y in pts), len(pts))
