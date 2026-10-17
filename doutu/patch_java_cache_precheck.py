from pathlib import Path
p=Path(r'D:\AAA\doutu\doutu\app\src\main\java\com\example\doutu\features\wei\WeiHomeActivity.java')
s=p.read_text(encoding='utf-8')
old='''            try {
                if (sourceUrl == null || !sourceUrl.startsWith("https://")) return "";
                connection = (HttpURLConnection) new URL(sourceUrl).openConnection();'''
new='''            try {
                if (sourceUrl == null || !sourceUrl.startsWith("https://")) return "";
                File cacheDir = new File(getFilesDir(), "fw_avatar_cache");
                String existingKey = Integer.toUnsignedString(sourceUrl.hashCode(), 36);
                String[] knownExtensions = new String[]{".jpg", ".jpeg", ".png", ".webp"};
                for (String existingExtension : knownExtensions) {
                    File cachedFile = new File(cacheDir, "avatar_" + existingKey + existingExtension);
                    if (cachedFile.isFile() && cachedFile.length() > 0) {
                        return "https://fake-world.local/__avatar_cache/" + cachedFile.getName();
                    }
                }
                connection = (HttpURLConnection) new URL(sourceUrl).openConnection();'''
if old not in s:
    raise SystemExit('target not found')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')
print('patched java cache precheck')
