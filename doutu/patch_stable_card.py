from pathlib import Path
p=Path(r'D:\AAA\doutu\doutu\app\src\main\assets\fake_world\overrides\wechat-chat-actions.js')
s=p.read_text(encoding='utf-8')
# Stabilize avatar as soon as a local chat is opened.
s=s.replace("    friend.messages = Array.isArray(friend.messages) ? friend.messages : [];\n    // Defer contact-list serialization until the chat entrance animation is underway.","    friend.messages = Array.isArray(friend.messages) ? friend.messages : [];\n    getStableFriendAvatar(friend, friend.avatar || CONTACT_FALLBACKS[0].avatar);\n    // Defer contact-list serialization until the chat entrance animation is underway.")
s=s.replace("    const friendAvatar = friend.avatar || makeBatchAvatarUrl();", "    const friendAvatar = getStableFriendAvatar(friend, friend.avatar || CONTACT_FALLBACKS[0].avatar);")
# Friend rows: never bind a redirect-random API URL when we already have/can create a frozen cache URL.
s=s.replace("if (img) { img.src = friend.avatar || makeAvatarUrl(); img.onerror = () => { img.onerror = null; img.src = CONTACT_FALLBACKS[0].avatar; }; }", "if (img) { img.src = getStableFriendAvatar(friend, friend.avatar || makeAvatarUrl()); img.onerror = () => { img.onerror = null; img.src = CONTACT_FALLBACKS[0].avatar; }; }")
s=s.replace("const img = row.querySelector('img'); img.src = friend.avatar || makeAvatarUrl();", "const img = row.querySelector('img'); img.src = getStableFriendAvatar(friend, friend.avatar || makeAvatarUrl());")
# Profile page avatar.
s=s.replace('page.querySelector(".fw-friend-profile__avatar").src=friend.avatar||CONTACT_FALLBACKS[0].avatar;', 'page.querySelector(".fw-friend-profile__avatar").src=getStableFriendAvatar(friend, friend.avatar||CONTACT_FALLBACKS[0].avatar);')
# Insert helper after freezeRemoteAvatar function, if absent.
marker="  function stabilizeSavedAvatars(limit = 8) {"
helper="""  function getStableFriendAvatar(friend, fallback = CONTACT_FALLBACKS[0].avatar) {\n    const source = friend?.avatar || fallback || CONTACT_FALLBACKS[0].avatar;\n    if (!friend) return source;\n    if (!isRemoteAvatarValue(source)) return source;\n    const frozen = freezeRemoteAvatar(source);\n    if (frozen && frozen !== source && (frozen.startsWith(AVATAR_CACHE_PREFIX) || frozen.startsWith('data:image/'))) {\n      friend.avatar = frozen;\n      try { saveState(); } catch (_) {}\n      try { syncNativeAddressBook(); } catch (_) {}\n      return frozen;\n    }\n    return source;\n  }\n"""
if 'function getStableFriendAvatar(' not in s:
    s=s.replace(marker, helper+marker)
p.write_text(s,encoding='utf-8')
print('patched js')
