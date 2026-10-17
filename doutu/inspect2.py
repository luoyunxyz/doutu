from PIL import Image
for fn in ['verify_success_latest.png']:
 im=Image.open(fn).convert('RGB');w,h=im.size
 for yr in [(650,850),(800,1150)]:
  pts=[]
  for y in range(*yr):
   for x in range(50,1030):
    r,g,b=im.getpixel((x,y))
    if r<80 and g<80 and b<80:pts.append((x,y))
  print(yr,(min(x for x,y in pts),min(y for x,y in pts),max(x for x,y in pts),max(y for x,y in pts),len(pts)) if pts else None)
