from PIL import Image
from pathlib import Path
img=Image.open(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg').convert('RGBA')
bbox=(321,2529,1268,2859)
crop=img.crop(bbox)
# make non-card outside transparent: keep orange body and white icon/text; drop grey background and avatar sliver.
p=crop.load()
for y in range(crop.height):
    for x in range(crop.width):
        r,g,b,a=p[x,y]
        orange = (r>=200 and 90<=g<=195 and b<=115)
        white_on_card = (r>=220 and g>=220 and b>=220 and x<910 and 35<y<300)
        # keep subtle antialiasing on icon/text/card around shape
        offwhite_on_card = (r>=185 and g>=185 and b>=170 and x<910 and 30<y<310)
        if not (orange or white_on_card or offwhite_on_card):
            p[x,y]=(r,g,b,0)
        else:
            # remove near-background greys accidentally captured outside the card
            if abs(r-237)<10 and abs(g-237)<10 and abs(b-237)<10:
                p[x,y]=(r,g,b,0)
# solidify orange body? leave natural antialias.
out=Path('app/src/main/assets/fake_world/assets/wechat-transfer-card-bg.png')
crop.save(out)
crop.save('transfer_card_bg_preview2.png')
print(crop.size)
