from pathlib import Path
p=Path('app/src/main/java/com/example/doutu/features/wei/WeiHomeActivity.java')
t=p.read_text(encoding='utf-8')
old='''                File file = new File(dir, "avatar_" + System.currentTimeMillis() + "_" + Math.abs(sourceUrl.hashCode()) + extension);\n                long total = 0;\n'''
new='''                // Use a stable cache key. The previous timestamp-based filename made the\n                // same remote avatar appear to change every time stabilization retried.\n                String stableKey = Integer.toUnsignedString(sourceUrl.hashCode(), 36);\n                File file = new File(dir, "avatar_" + stableKey + extension);\n                if (file.isFile() && file.length() > 0) {\n                    return "https://fake-world.local/__avatar_cache/" + file.getName();\n                }\n                long total = 0;\n'''
if old not in t: raise SystemExit('java target not found')
p.write_text(t.replace(old,new),encoding='utf-8')
print('patched stable native cache')
