from PIL import Image
p=r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg'
im=Image.open(p).convert('RGBA')
# crop approximate transfer icon, make transparent all orange/background preserving white glyph
crop=im.crop((350,2568,540,2758))
# set non-white pixels transparent? Need preserve antialiased white. use alpha based on luminance and whiteness
pix=crop.load(); w,h=crop.size
for y in range(h):
    for x in range(w):
        r,g,b,a=pix[x,y]
        # white-ish pixels in original icon/text: high and low saturation
        if r>190 and g>190 and b>190 and max(r,g,b)-min(r,g,b)<50:
            alpha=int(max(0,min(255,(r-175)*3)))
            pix[x,y]=(255,255,255,alpha)
        else:
            pix[x,y]=(255,255,255,0)
# trim bbox alpha
bbox=crop.getbbox()
print('bbox',bbox,'size',crop.size)
if bbox: crop=crop.crop(bbox)
out=r'D:\AAA\doutu\doutu\app\src\main\assets\fake_world\assets\wechat-transfer-card-icon.png'
crop.save(out)
print(out, crop.size)
