from PIL import Image, ImageStat
im=Image.open(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg').convert('RGB')
for pt in [(650,2600),(650,2700),(1100,2800),(332,2541)]:
 print(pt, im.getpixel(pt))
