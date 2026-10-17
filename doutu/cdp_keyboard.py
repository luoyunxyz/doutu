import json,urllib.request,websocket
items=json.load(urllib.request.urlopen('http://127.0.0.1:9223/json/list')); ws=websocket.create_connection(items[0]['webSocketDebuggerUrl'],suppress_origin=True); n=0
def ev(e):
 global n;n+=1;ws.send(json.dumps({'id':n,'method':'Runtime.evaluate','params':{'expression':e,'returnByValue':True}}))
 while 1:
  m=json.loads(ws.recv())
  if m.get('id')==n:return m
expr="""JSON.stringify({innerHeight,outerHeight,scrollY,visual:visualViewport&&{height:visualViewport.height,offsetTop:visualViewport.offsetTop,pageTop:visualViewport.pageTop},root:[...document.documentElement.classList],body:[...document.body.classList],style:getComputedStyle(document.documentElement).getPropertyValue('--fw-keyboard-offset'),input:(()=>{let e=document.querySelector('.fw-local-conversation__composer input');if(!e)return null;let r=e.getBoundingClientRect();return {r:{x:r.x,y:r.y,w:r.width,h:r.height},focus:document.activeElement===e,display:getComputedStyle(e).display}})(),composer:(()=>{let e=document.querySelector('.fw-local-conversation__composer');if(!e)return null;let r=e.getBoundingClientRect();return {r:{x:r.x,y:r.y,w:r.width,h:r.height},mb:getComputedStyle(e).marginBottom,transform:getComputedStyle(e).transform}})()})"""
print(json.dumps(ev(expr),ensure_ascii=False,indent=2))
