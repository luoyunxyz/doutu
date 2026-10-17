from PIL import Image
import numpy as np
from collections import deque
p=r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg'
im=Image.open(p).convert('RGB')
x1,y1,x2,y2=322,2528,1232,2852
crop=im.crop((x1,y1,x2,y2)); arr=np.array(crop)
# white-ish in crop
mask=(arr[:,:,0]>200)&(arr[:,:,1]>200)&(arr[:,:,2]>190)
# exclude gray bg roughly (all channels similar 230)? But white text/icon also white; no matter. limit to card bounds by orange dilation? rough only where y>?
# connected comps 8-neighbor for white pixels
h,w=mask.shape; seen=np.zeros(mask.shape,bool); comps=[]
for yy in range(h):
  for xx in range(w):
    if not mask[yy,xx] or seen[yy,xx]: continue
    q=[(xx,yy)]; seen[yy,xx]=1; pts=[]
    while q:
      x,y=q.pop(); pts.append((x,y))
      for dy in (-1,0,1):
       for dx in (-1,0,1):
        if dx==0 and dy==0: continue
        nx,ny=x+dx,y+dy
        if 0<=nx<w and 0<=ny<h and mask[ny,nx] and not seen[ny,nx]: seen[ny,nx]=1; q.append((nx,ny))
    if len(pts)>20:
      xs=[p[0] for p in pts]; ys=[p[1] for p in pts]
      comps.append((len(pts),min(xs),min(ys),max(xs),max(ys)))
print('crop',crop.size)
for c in sorted(comps, reverse=True)[:30]: print(c)
