"""本地/内网调试启动脚本（只监听 127.0.0.1，不对外暴露解析引擎）。

生产环境用 gunicorn（见 deploy/ 里的 supervisor 配置），这个脚本只给开发用：
    .venv\\Scripts\\python.exe run-local.py
环境变量：
    API_ONLY=true   —— 必开：纯解析引擎模式，/api/v1/parse 免鉴权、不建库记请求日志
    PORT=8051       —— 监听端口（默认 8051，服务端 dw_route 里配的也是这个）
"""
import os

os.environ.setdefault("API_ONLY", "true")
os.environ.setdefault("FLASK_ENV", "production")

from app import app  # noqa: E402  （必须在设置环境变量之后导入）

if __name__ == "__main__":
    host = os.getenv("HOST", "127.0.0.1")
    port = int(os.getenv("PORT", "8051"))
    print(f"[media-parser] API_ONLY={os.environ.get('API_ONLY')} 监听 http://{host}:{port}", flush=True)
    app.run(host=host, port=port, threaded=True, debug=False)
