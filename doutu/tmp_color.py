from PIL import Image
im=Image.open(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg').convert('RGB')
for p in [(800,2700),(700,2600),(500,2800),(1100,2700),(400,2600)]:print(p,im.getpixel(p))
