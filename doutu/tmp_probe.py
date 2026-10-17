from PIL import Image
import numpy as np
p=r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg'
im=Image.open(p).convert('RGB')
arr=np.array(im)
mask=(arr[:,:,0]>220)&(arr[:,:,1]>110)&(arr[:,:,1]<190)&(arr[:,:,2]<110)
Y=np.indices(mask.shape)[0]
m=mask & (Y>2400)
ys,xs=np.where(m)
print(im.size, int(xs.min()), int(xs.max()), int(ys.min()), int(ys.max()), len(xs))
for y in [2520,2530,2540,2600,2700,2840,2850]:
    row=np.where(mask[y])[0]
    print('row',y, int(row.min()) if row.size else None, int(row.max()) if row.size else None, int(row.size))
