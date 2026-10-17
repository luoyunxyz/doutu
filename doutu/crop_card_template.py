from PIL import Image, ImageDraw
from pathlib import Path
src=Path(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg')
im=Image.open(src).convert('RGBA')
# Exact card bounds from the supplied 1440x3168 chat screenshot, preserving rounded corners and right tail.
crop=im.crop((321,2529,1268,2859))
# Paint over only the baked amount/note with the sampled card orange so runtime text can be rendered in its exact place.
orange=im.getpixel((1000,2600))[:3]
d=ImageDraw.Draw(crop)
d.rectangle((215,55,710,202),fill=(*orange,255))
out=Path('app/src/main/assets/fake_world/assets/wechat-transfer-card-bg.png')
crop.save(out)
crop.save('transfer_card_bg_dynamic_preview.png')
print('orange',orange,'crop',crop.size,'saved',out)
