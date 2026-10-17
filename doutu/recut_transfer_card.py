from PIL import Image, ImageDraw
import numpy as np
from pathlib import Path
src=Path(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg')
out=Path(r'D:\AAA\doutu\doutu\app\src\main\assets\fake_world\assets\wechat-transfer-card-bg.png')
preview=Path(r'D:\AAA\doutu\doutu\transfer_card_bg_recut_preview.png')
# Exact card/tail bounds measured from reference: orange card pixels x=332..1228 y=2540..2847.
# Keep a small transparent margin for antialiasing, but stop before the right avatar.
x1,y1,x2,y2=322,2528,1232,2852
im=Image.open(src).convert('RGBA').crop((x1,y1,x2,y2))
arr=np.array(im).astype(np.int16)
r,g,b,a=arr[:,:,0],arr[:,:,1],arr[:,:,2],arr[:,:,3]
# Reference chat background around the card is neutral light gray.
bg=np.array([237,237,237], dtype=np.int16)
dist=np.abs(arr[:,:,:3]-bg).sum(axis=2)
# Orange surface including JPEG antialiasing.
orange=(r>=205)&(g>=95)&(g<=190)&(b<=120)&((r-g)>45)
# White card details/icon/footer text. It is only valid inside/near the orange card, not on gray background.
white=(r>185)&(g>185)&(b>175)&(dist>28)
# Build an approximate card silhouette row-by-row from orange pixels and preserve nearby white details.
mask=np.zeros(orange.shape, dtype=bool)
ys,xs=np.where(orange)
for yy in range(mask.shape[0]):
    cols=np.where(orange[yy])[0]
    if cols.size:
        lo=max(0, int(cols.min())-3); hi=min(mask.shape[1]-1, int(cols.max())+3)
        mask[yy,lo:hi+1]=True
# Smooth vertical antialiasing: preserve rows immediately adjacent to orange rows.
for yy in range(1,mask.shape[0]-1):
    if not mask[yy].any() and (mask[yy-1].any() or mask[yy+1].any()):
        cols=np.where(mask[yy-1] | mask[yy+1])[0]
        if cols.size: mask[yy,cols.min():cols.max()+1]=True
# Keep only card surface/details; anything else (including avatar remnants) is transparent.
keep=mask | (white & mask)
arr[:,:,3]=np.where(keep, a, 0)
# Paint over the amount + description area with the sampled orange so runtime white text can be layered exactly.
rgba=Image.fromarray(np.clip(arr,0,255).astype('uint8'), 'RGBA')
draw=ImageDraw.Draw(rgba)
orange_pixels=np.array(rgba)[:,:,:3][orange & keep]
fill=tuple(np.median(orange_pixels,axis=0).astype(int).tolist()+[255]) if len(orange_pixels) else (249,155,54,255)
# Source text area local coords from connected white components: amount x≈236..374 y≈68..113, desc x≈235..627 y≈140..184.
# Cover generously but avoid the left icon and footer label.
draw.rounded_rectangle([220,54,690,190], radius=3, fill=fill)
# Reapply transparency outside card after painting.
arr=np.array(rgba)
arr[:,:,3]=np.where(keep, arr[:,:,3], 0)
rgba=Image.fromarray(arr, 'RGBA')
rgba.save(out)
# Preview on the same chat gray.
bgim=Image.new('RGBA', rgba.size, (237,237,237,255)); bgim.alpha_composite(rgba); bgim.save(preview)
print('saved', out, 'size', rgba.size, 'fill', fill)
