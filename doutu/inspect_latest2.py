from PIL import Image
for fn in [r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\ff87d3bc03110b5565ef4596a8a655a0.jpg','verify_success_latest2.png']:
 im=Image.open(fn).convert('RGB');w,h=im.size
 print(fn,w,h)
 for label, pred, yr in [('green',lambda r,g,b:g>120 and g>r+30 and g>b+15,(0,int(h*.2))),('black-rec',lambda r,g,b:r<80 and g<80 and b<80,(int(h*.1),int(h*.35))),('black-amt',lambda r,g,b:r<80 and g<80 and b<80,(int(h*.2),int(h*.45)))]:
  pts=[]
  for y in range(*yr):
   for x in range(int(w*.05),int(w*.95)):
    if pred(*im.getpixel((x,y))):pts.append((x,y))
  print(label,(min(x for x,y in pts),min(y for x,y in pts),max(x for x,y in pts),max(y for x,y in pts),len(pts)) if pts else None)
