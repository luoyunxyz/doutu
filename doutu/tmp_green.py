from PIL import Image
img=Image.open(r'C:\Users\22594\Documents\xwechat_files\wxid_od9wo8sn3e2522_7f42\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\ff87d3bc03110b5565ef4596a8a655a0.jpg').convert('RGB')
def bbox(pred, yr=(0,400), xr=(300,1100)):
    xs=[];ys=[]
    for y in range(*yr):
        for x in range(*xr):
            r,g,b=img.getpixel((x,y))
            if pred(r,g,b): xs.append(x);ys.append(y)
    return (min(xs),min(ys),max(xs),max(ys),len(xs)) if xs else None
for t in [(120,150),(140,120),(100,180),(80,120)]:
    print(t,bbox(lambda r,g,b,t=t:g>t[0] and r<t[1] and b<t[1],(100,400),(300,1100)))
