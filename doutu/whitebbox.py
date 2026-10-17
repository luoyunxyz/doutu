from PIL import Image
im=Image.open('test_chat_final.png').convert('RGB')
# white connected components-ish row/col bbox within second card
for yr in [(609,837),(1063,1290)]:
 pts=[]
 for y in range(*yr):
  for x in range(200,950):
   r,g,b=im.getpixel((x,y))
   if r>245 and g>245 and b>245:pts.append((x,y))
 print(yr,(min(x for x,y in pts),min(y for x,y in pts),max(x for x,y in pts),max(y for x,y in pts),len(pts)))
 # per y starts
 for y in range(yr[0],yr[1]):
  xs=[]
  for x in range(200,950):
   r,g,b=im.getpixel((x,y))
   if r>245 and g>245 and b>245:xs.append(x)
  if len(xs)>5 and (y==yr[0] or prev<=5):print('start',y,min(xs),max(xs))
  if len(xs)<=5 and 'prev' in locals() and prev>5:print('end',y-1)
  prev=len(xs)
