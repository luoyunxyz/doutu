from PIL import Image
for fn in ['transfer_test_success.png']:
 im=Image.open(fn).convert('RGB'); w,h=im.size; print(fn,w,h)
 def bbox(pred,yr=(0,None),xr=(0,None)):
  y0,y1=yr; x0,x1=xr; y1=y1 or h;x1=x1 or w
  pts=[(x,y) for y in range(y0,y1) for x in range(x0,x1) if pred(*im.getpixel((x,y)))]
  print((min(x for x,y in pts),min(y for x,y in pts),max(x for x,y in pts),max(y for x,y in pts),len(pts)) if pts else None)
 bbox(lambda r,g,b:g>120 and r<80 and b<120,(50,350))
 bbox(lambda r,g,b:r<70 and g<70 and b<70,(300,800),(200,900))
 bbox(lambda r,g,b:r<50 and g<50 and b<50,(500,900),(200,900))
 bbox(lambda r,g,b:220<r<250 and 220<g<250 and 220<b<250,(1800,2300),(100,950))
