import json,urllib.request,websocket
info=json.load(urllib.request.urlopen('http://127.0.0.1:9223/json/list'))[0]; ws=websocket.create_connection(info['webSocketDebuggerUrl'], suppress_origin=True)
expr="fetch('./overrides/wechat-chat-actions.js').then(r=>r.text()).then(t=>t.includes('__fwSetKeyboardInset')?t.slice(t.indexOf('keyboardBaselineHeight')-100,t.indexOf('keyboardBaselineHeight')+1600):'MISSING')"
ws.send(json.dumps({'id':1,'method':'Runtime.evaluate','params':{'expression':expr,'awaitPromise':True,'returnByValue':True}}))
while 1:
 r=json.loads(ws.recv())
 if r.get('id')==1: print(r);break
