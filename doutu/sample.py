from PIL import Image
im=Image.open('test_chat_final.png').convert('RGB')
for p in [(300,600),(500,600),(500,700),(500,800),(250,270),(500,2000)]: print(p,im.getpixel(p))
