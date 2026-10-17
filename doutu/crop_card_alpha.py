from PIL import Image, ImageDraw
from pathlib import Path
src=Path(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg')
im=Image.open(src).convert('RGBA')
crop=im.crop((321,2529,1268,2859))
orange=im.getpixel((1000,2600))[:3]
p=crop.load()
for y in range(crop.height):
    for x in range(crop.width):
        r,g,b,a=p[x,y]
        orange_like=(r>=205 and 95<=g<=195 and b<=115)
        white_icon=(r>=210 and g>=210 and b>=210 and x<220 and y<220)
        white_footer=(r>=210 and g>=210 and b>=210 and x<170 and y>215)
        if not (orange_like or white_icon or white_footer):
            p[x,y]=(r,g,b,0)
# explicit wipe right/bottom background strips that threshold could keep from screenshot matting.
for y in range(crop.height):
    for x in range(crop.width):
        r,g,b,a=p[x,y]
        if a and r>220 and g>220 and b>220 and not ((x<220 and y<220) or (x<170 and y>215)):
            p[x,y]=(r,g,b,0)
d=ImageDraw.Draw(crop)
d.rectangle((215,55,710,202), fill=(*orange,255))
out=Path('app/src/main/assets/fake_world/assets/wechat-transfer-card-bg.png')
crop.save(out)
crop.save('transfer_card_bg_alpha_preview.png')
print('saved',crop.size,orange)
