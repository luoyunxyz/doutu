from PIL import Image
from collections import Counter
im=Image.open('app/src/main/assets/fake_world/assets/wechat-transfer-card-bg.png').convert('RGBA')
colors=[]
for y in range(im.height):
    for x in range(900,im.width):
        r,g,b,a=im.getpixel((x,y))
        if a:
            colors.append((x,y,r,g,b,a))
print('nontransparent x>900',len(colors),'sample',colors[:5],colors[-5:])
print(Counter((r//20,g//20,b//20) for x,y,r,g,b,a in colors).most_common(10))
