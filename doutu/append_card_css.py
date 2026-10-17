from pathlib import Path
p=Path('app/src/main/assets/fake_world/overrides/wechat-chat-actions.css')
with p.open('a',encoding='utf-8') as f:
 f.write(r'''

/* 2026-10-08: use the reference transfer artwork as the card surface;
   runtime text is layered above the painted-over text area. */
.fw-chat-message--transfer .fw-transfer-chat-card {
  position: relative !important;
  display: block !important;
  width: min(61.3vw, 295px) !important;
  height: min(21.5vw, 104px) !important;
  min-height: 0 !important;
  padding: 0 !important;
  border: 0 !important;
  border-radius: 0 !important;
  background: transparent !important;
  overflow: visible !important;
}
.fw-chat-message--transfer .fw-transfer-chat-card::after { display:none !important; }
.fw-transfer-chat-card__artwork {
  position:absolute !important; inset:0 !important; width:100% !important; height:100% !important;
  object-fit:fill !important; display:block !important; pointer-events:none !important;
}
.fw-chat-message--transfer .fw-transfer-chat-card__content {
  position:absolute !important; z-index:1 !important; left:34% !important; top:15% !important;
  width:62% !important; padding:0 !important; display:block !important;
  color:#fff !important; text-align:left !important;
}
.fw-chat-message--transfer .fw-transfer-chat-card__content strong {
  display:block !important; margin:0 !important; color:#fff !important;
  font-family:Roboto,"Noto Sans CJK SC",sans-serif !important;
  font-size:4vw !important; line-height:5.1vw !important; font-weight:700 !important;
  letter-spacing:0 !important; text-shadow:none !important;
}
.fw-chat-message--transfer .fw-transfer-chat-card__content span {
  display:block !important; margin-top:.2vw !important; color:#fff !important;
  font-family:"Noto Sans CJK SC","Microsoft YaHei",sans-serif !important;
  font-size:3.35vw !important; line-height:4.3vw !important; font-weight:400 !important;
  white-space:nowrap !important; overflow:hidden !important; text-overflow:ellipsis !important;
}
.fw-chat-message--transfer .fw-transfer-chat-card__footer {
  position:absolute !important; z-index:1 !important; left:3.8% !important; bottom:8.2% !important;
  margin:0 !important; padding:0 !important; border:0 !important; color:#fff !important;
  font-size:3.35vw !important; line-height:4.3vw !important; font-weight:400 !important;
}
''')
print('appended card artwork css')
