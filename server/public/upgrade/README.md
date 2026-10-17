# public/upgrade —— 客户端版本包与增量包

> **本部署的实际情况（2026-10-17 修正）**：宝塔这套 Nginx 把 `location ^~ /upgrade/`
> **反代给了 Node**，而不是直接用 alias 发静态文件 —— 所以光把 APK 丢进这个目录是下不动的，
> 后台「版本推送」里填的地址会 404。现在 Node 侧补了一条静态路由
> （`src/modules/upgrade/routes.js` 的 `upgradeFileRouter`，挂在 `/upgrade/*`），
> 由它读这个目录，两边才算接上：
>
> ```
> https://tdapi.luoyun.xyz/upgrade/app/doutu-1.1.0.apk  →  public/upgrade/app/doutu-1.1.0.apk
> ```
>
> 如果哪天把 Nginx 改成 alias 直发（规划书原本的写法），这条路由自然不会再被命中，也不用删。

Nginx 把 `/upgrade/*` 直接映射到本目录（见 `deploy/nginx.conf` 的 `location /upgrade/`），
这样 APK 与增量包由 Nginx 直接发静态文件，不经过 Node 进程，也不占用业务带宽。

## 目录约定

```
public/upgrade/
├── app/                       # 全量包：v1.3.0-official.apk
├── patch/                     # 增量包：12-13.patch（bsdiff 生成）
└── icon/                      # 更新弹窗用的图标（可选）
```

## 发布流程

1. 把 APK 放到 `app/`，文件名建议 `v{版本名}-{渠道}.apk`；
2. 增量包用 `bsdiff old.apk new.apk patch/12-13.patch` 生成，并算出 MD5：
   `md5sum patch/12-13.patch`；
3. 在后台「版本推送」里新增版本，填 `url`、`patch_url`、`patch_md5`、`size`、`changelog`，
   灰度先设 `gray_percent=20`；
4. 客户端调 `GET /api/v1/upgrade/check` 拿到更新信息（灰度按 `device_id` 哈希取模，
   同一设备每次看到的结果一致）。

## 注意

- `url` 必须写成客户端可访问的完整地址（一般用 `https://dl.example.com/upgrade/...`）；
- 增量包必须带 MD5，客户端应用失败要自动回退全量包；
- 这个目录不进版本库（`.gitignore`），只放产物。
