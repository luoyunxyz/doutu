from PIL import Image
for fn in [r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\ff87d3bc03110b5565ef4596a8a655a0.jpg','transfer_test_success.png']:
 im=Image.open(fn).convert('RGB'); w,h=im.size
 print('\n',fn,w,h)
 for y in range(h):
  c=sum(1 for x in range(int(w*.15),int(w*.85)) if max(im.getpixel((x,y)))<90)
  if c>5 and (y==0 or prev<=5): print('start',y)
  if c<=5 and 'prev' in locals() and prev>5: print('end',y-1)
  prev=c
 prev=0
