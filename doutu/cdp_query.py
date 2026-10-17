import json, urllib.request, websocket
items=json.load(urllib.request.urlopen('http://127.0.0.1:9223/json/list'))
ws=websocket.create_connection(items[0]['webSocketDebuggerUrl'], suppress_origin=True)
seq=0
def ev(expr):
 global seq
 seq+=1; ws.send(json.dumps({'id':seq,'method':'Runtime.evaluate','params':{'expression':expr,'returnByValue':True,'awaitPromise':True}}))
 while 1:
  m=json.loads(ws.recv())
  if m.get('id')==seq:return m
expr="""JSON.stringify([...document.querySelectorAll('body *')].filter(e=>e.children.length===0 && ['做个好梦','自在人如风','周末逃跑计划'].includes((e.textContent||'').trim())).map(e=>{let r=e.getBoundingClientRect();return {tag:e.tagName,cls:String(e.className),text:e.textContent,rect:{x:r.x,y:r.y,w:r.width,h:r.height}}}))"""
print(json.dumps(ev(expr),ensure_ascii=False,indent=2))
