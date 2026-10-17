from PIL import Image
for fn,yr in [(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg',(2450,2900)),('test_chat_final.png',(0,1900))]:
 im=Image.open(fn).convert('RGB');w,h=im.size
 print('\n',fn)
 active=[]
 for y in range(yr[0],min(yr[1],h)):
  c=0
  for x in range(w):
   r,g,b=im.getpixel((x,y))
   if 225<=r<=250 and 140<=g<=180 and 60<=b<=100 and r-g>60:c+=1
  if c>50:active.append(y)
 groups=[]
 for y in active:
  if not groups or y>groups[-1][-1]+1:groups.append([y])
  else:groups[-1].append(y)
 print([(g[0],g[-1]) for g in groups])
