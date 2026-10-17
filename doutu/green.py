from PIL import Image
for fn in [r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\ff87d3bc03110b5565ef4596a8a655a0.jpg','transfer_test_success.png']:
 im=Image.open(fn).convert('RGB'); w,h=im.size; pts=[]
 for y in range(0,int(h*.2)):
  for x in range(w):
   r,g,b=im.getpixel((x,y))
   if g>120 and g>r+30 and g>b+15:pts.append((x,y))
 print(fn, (min(x for x,y in pts),min(y for x,y in pts),max(x for x,y in pts),max(y for x,y in pts),len(pts)) if pts else None)
