from PIL import Image
for fn in [r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg','test_chat_final.png']:
 im=Image.open(fn).convert('RGB');w,h=im.size; pts=[]
 for y in range(h):
  for x in range(w):
   r,g,b=im.getpixel((x,y))
   if 225<=r<=250 and 140<=g<=180 and 60<=b<=100 and r-g>60:pts.append((x,y))
 print(fn, (min(x for x,y in pts),min(y for x,y in pts),max(x for x,y in pts),max(y for x,y in pts),len(pts)) if pts else None)
