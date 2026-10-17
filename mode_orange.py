from PIL import Image
from collections import Counter
im=Image.open(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg').convert('RGB')
c=Counter()
for y in range(2541,2848):
 for x in range(332,1229):
  r,g,b=im.getpixel((x,y))
  if r>200 and 100<g<190 and b<120:
   c[(r//5*5,g//5*5,b//5*5)] +=1
for color,n in c.most_common(10): print(color,n)
