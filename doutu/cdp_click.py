import json,urllib.request,websocket
items=json.load(urllib.request.urlopen('http://127.0.0.1:9223/json/list')); ws=websocket.create_connection(items[0]['webSocketDebuggerUrl'],suppress_origin=True); n=0
def ev(e):
 global n
 n+=1; ws.send(json.dumps({'id':n,'method':'Runtime.evaluate','params':{'expression':e,'returnByValue':True,'awaitPromise':True}}))
 while 1:
  m=json.loads(ws.recv())
  if m.get('id')==n:return m
expr="""(()=>{let e=[...document.querySelectorAll('strong')].find(x=>(x.textContent||'').trim().length>0); if(!e)return 'none'; let p=e;let a=[];for(let i=0;i<5&&p;i++,p=p.parentElement)a.push({tag:p.tagName,cls:String(p.className),role:p.getAttribute('role'),html:p.outerHTML.slice(0,300)});e.click();return JSON.stringify(a)})()"""
print(ev(expr))
