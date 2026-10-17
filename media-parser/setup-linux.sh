#!/usr/bin/env bash
# =============================================================================
# media-parser 在 Linux（宝塔服务器）上的依赖安装脚本
#
#   bash media-parser/setup-linux.sh
#
# 做三件事：
#   1. 建虚拟环境 .venv
#   2. 装 requirements.txt（Flask/gunicorn/requests/bs4/lxml/mini-racer/…）
#   3. 自检：能不能 import app（缺 tzdata 等依赖会在这里直接报出来）
#
# 之后用 gunicorn 起服务（宝塔 Supervisor 里配）：
#   /www/wwwroot/<站点>/media-parser/.venv/bin/gunicorn -w 4 -k gthread --threads 8 -t 120 -b 127.0.0.1:8051 app:app
# 必须带 API_ONLY=true，否则 /api/v1/parse 会要求 API Key，服务端那条线路就调不通。
# =============================================================================
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

PY="${PYTHON:-python3}"
if ! command -v "$PY" >/dev/null 2>&1; then
  echo "找不到 $PY，请先在宝塔【软件商店 → Python 项目管理器】装一个 Python 3.10+" >&2
  exit 1
fi

echo "==> 使用解释器：$($PY --version 2>&1)"
echo "==> 创建虚拟环境 .venv"
"$PY" -m venv .venv

echo "==> 安装依赖（国内服务器建议加 -i 镜像源，脚本已默认清华源）"
./.venv/bin/pip install --upgrade pip -q \
  -i https://pypi.tuna.tsinghua.edu.cn/simple
./.venv/bin/pip install -r requirements.txt \
  -i https://pypi.tuna.tsinghua.edu.cn/simple
# tzdata：Linux 一般自带时区库，但精简版镜像可能没有，装上更保险（代码里用了 ZoneInfo("Asia/Shanghai")）
./.venv/bin/pip install tzdata -q -i https://pypi.tuna.tsinghua.edu.cn/simple

echo "==> 自检：导入 app（会顺带建 data/media_parser.db）"
API_ONLY=true ./.venv/bin/python - <<'PY'
import os
os.environ.setdefault("API_ONLY", "true")
from app import app
print("   app 导入成功，API_ONLY =", app.config.get("API_ONLY"))
print("   路由数：", len([r for r in app.url_map.iter_rules()]))
PY

cat <<'TIP'

==> 完成。接下来在宝塔 Supervisor 里加这个进程：

[program:doutu-media-parser]
directory=/www/wwwroot/api.example.com/media-parser
command=/www/wwwroot/api.example.com/media-parser/.venv/bin/gunicorn -w 4 -k gthread --threads 8 -t 120 -b 127.0.0.1:8051 app:app
autostart=true
autorestart=true
startsecs=15
startretries=5
stopwaitsecs=10
user=www
environment=API_ONLY="true",PYTHONUNBUFFERED="1",TZ="Asia/Shanghai"

（完整版见 server/deploy/supervisor.conf 里的 [program:doutu-media-parser]）

自检：
  curl -s http://127.0.0.1:8051/api/health          # 期望 {"status":"ok"}
  curl -s "http://127.0.0.1:8051/api/v1/parse"      # 期望 retcode=400 INVALID_TEXT
TIP
