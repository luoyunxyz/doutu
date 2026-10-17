import json,urllib.request,websocket
items=json.load(urllib.request.urlopen('http://127.0.0.1:9223/json/list')); ws=websocket.create_connection(items[0]['webSocketDebuggerUrl'],suppress_origin=True); n=0
def ev(e):
 global n;n+=1;ws.send(json.dumps({'id':n,'method':'Runtime.evaluate','params':{'expression':e,'returnByValue':True}}))
 while 1:
  m=json.loads(ws.recv())
  if m.get('id')==n:return m
expr="""JSON.stringify([...document.querySelectorAll('body *')].filter(e=>{let r=e.getBoundingClientRect();return r.width>100&&r.height>20&&r.y>500}).slice(-40).map(e=>{let r=e.getBoundingClientRect();return {tag:e.tagName,id:e.id,cls:String(e.className).slice(0,120),role:e.getAttribute('role'),ce:e.getAttribute('contenteditable'),text:(e.innerText||e.textContent||'').trim().slice(0,50),rect:{x:r.x,y:r.y,w:r.width,h:r.height}}}))"""
print(json.dumps(ev(expr),ensure_ascii=False,indent=2))
