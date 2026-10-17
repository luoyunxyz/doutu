from PIL import Image
img=Image.open(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\ff87d3bc03110b5565ef4596a8a655a0.jpg').convert('RGB')
w,h=img.size
print(w,h)
def bbox(pred, yrange=(0,None), xrange=(0,None)):
    y0,y1=yrange; x0,x1=xrange
    if y1 is None: y1=h
    if x1 is None: x1=w
    xs=[];ys=[]; pix=img.load()
    for y in range(y0,y1):
        for x in range(x0,x1):
            if pred(*pix[x,y]): xs.append(x);ys.append(y)
    return (min(xs),min(ys),max(xs),max(ys),len(xs)) if xs else None
# green heading
print('green', bbox(lambda r,g,b: g>130 and r<80 and b<120, (100,400)))
# dark recipient text in central region excluding amount
print('black center upper', bbox(lambda r,g,b: r<70 and g<70 and b<70, (500,900),(300,1150)))
print('black amount', bbox(lambda r,g,b: r<50 and g<50 and b<50, (760,1100),(300,1150)))
# button grey
print('button grey', bbox(lambda r,g,b: 220<r<250 and 220<g<250 and 220<b<250, (2400,2800),(300,1150)))
