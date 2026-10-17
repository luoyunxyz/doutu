from PIL import Image
for fn in ['transfer_test_success.png','test_chat_final.png']:
 im=Image.open(fn).convert('RGB'); w,h=im.size
 pts=[]
 for y in range(h):
  for x in range(w):
   r,g,b=im.getpixel((x,y))
   if r>200 and 90<g<200 and b<130 and r-g>35: pts.append((x,y))
 print(fn, im.size, (min(x for x,y in pts),min(y for x,y in pts),max(x for x,y in pts),max(y for x,y in pts),len(pts)) if pts else None)
