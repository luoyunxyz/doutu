from pathlib import Path
p=Path(r'D:\AAA\doutu\doutu\app\src\main\assets\fake_world\overrides\wechat-chat-actions.js')
s=p.read_text(encoding='utf-8')
old="""    }, 250);
    queueApply();
    scheduleAvatarStabilization();
  }
"""
new="""    }, 250);
    // Freeze the first visible batch before rows bind their <img src>; this prevents
    // xxapi 302 avatars from visibly changing on every 小微主页 open.
    try { stabilizeSavedAvatars(24); } catch (_) {}
    queueApply();
    scheduleAvatarStabilization();
  }
"""
if old not in s: raise SystemExit('init patch target not found')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')
print('patched immediate stabilization')
