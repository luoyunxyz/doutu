from PIL import Image
from pathlib import Path
img_path = Path(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\a53f6a15e7a6466bf937dd69dbc9239e.jpg')
img = Image.open(img_path).convert('RGB')
print('size', img.size)
# Scan only lower chat-card band for orange bubble pixels.
xs=[]; ys=[]
pix=img.load()
for y in range(2350, 2920):
    for x in range(250, 1280):
        r,g,b = pix[x,y]
        if r >= 215 and 105 <= g <= 185 and b <= 95:
            xs.append(x); ys.append(y)
print('count', len(xs))
print('bbox orange', (min(xs), min(ys), max(xs), max(ys)))
# include anti-aliased triangle and rounded border margin
bbox=(min(xs)-12, min(ys)-12, max(xs)+15, max(ys)+12)
print('crop bbox', bbox, 'size', (bbox[2]-bbox[0], bbox[3]-bbox[1]))
out = Path('app/src/main/assets/fake_world/assets/wechat-transfer-card-bg.png')
out.parent.mkdir(parents=True, exist_ok=True)
img.crop(bbox).save(out)
img.crop(bbox).save('transfer_card_bg_preview.png')
print('saved', out, Image.open(out).size)
