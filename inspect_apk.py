import zipfile, re
apk=r'D:\AAA\doutu\doutu\app\build\outputs\apk\debug\app-debug.apk'
with zipfile.ZipFile(apk) as z:
    hits=[n for n in z.namelist() if n.endswith('wechat-chat-actions.js')]
    print(hits)
    for n in hits:
        data=z.read(n).decode('utf-8','replace')
        print('keyboard', 'keyboardBaselineHeight' in data, '__fwSetKeyboardInset' in data, 'len',len(data))
        print(data[data.find('keyboardBaselineHeight')-100:data.find('keyboardBaselineHeight')+300])
