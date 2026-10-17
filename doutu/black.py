from PIL import Image
for fn in [r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\ff87d3bc03110b5565ef4596a8a655a0.jpg','transfer_test_success.png']:
 im=Image.open(fn).convert('RGB'); w,h=im.size; print(fn)
 for yr in [(350,750),(400,650),(700,1000),(1800,2300)]:
  pts=[]
  for y in range(yr[0],min(yr[1],h)):
   for x in range(int(w*.1),int(w*.9)):
    r,g,b=im.getpixel((x,y))
    if r<80 and g<80 and b<80:pts.append((x,y))
  print(yr,(min(x for x,y in pts),min(y for x,y in pts),max(x for x,y in pts),max(y for x,y in pts),len(pts)) if pts else None)
