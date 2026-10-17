# 管理后台前端（web/admin）

短视频去水印平台的**纯静态**管理后台：零构建、零依赖、零 CDN，
由 Node 服务端在 `/admin` 与 `/admin/*` 下直接托管（见 `src/app.js` 的 `serveStatic`）。

## 一、如何访问

1. 启动服务端（默认 `127.0.0.1:7101`，见 `.env` 的 `PORT` / `BIND_HOST`）：

   ```bash
   cd server
   npm start
   ```

2. 浏览器打开：

   ```
   http://127.0.0.1:7101/admin
   ```

   生产环境走 Nginx 时就是 `https://<你的域名>/admin`。路由用 hash，例如
   `http://127.0.0.1:7101/admin#/user`，刷新页面不会 404（hash 不发给服务端）。

3. 账号来自后台管理员表。首次创建：

   ```bash
   node scripts/create-admin.js --username=admin --password=YourPassw0rd --role=1
   ```

### 登录与鉴权说明

- 登录：`POST /admin/api/login`，成功后前端把 `data.token` 存进 `localStorage`（键名 `dw_admin_token`），
  之后所有请求自动带 `X-Admin-Token` 头。服务端同时会下发 `admin_token` Cookie，二选一即可。
- 会话失效：后端返回 `code = 6001` 时前端自动清除本地 token 并弹回登录页（HTTP 401/403 也做了兜底）。
- 敏感操作（删除用户 / 作废邀请码 / 退款 / 封禁 / 删除管理员 / 关闭 TOTP）会弹窗要求二次输入
  **当前管理员登录密码**，密码只随本次请求提交，不写入 `localStorage`、不写 Cookie、不留在全局变量里。
- 权限：登录响应里的 `admin.perms`（如 `["user.*","order.read"]`，`*` 为全部）决定按钮显隐，
  `admin.menus` 决定左侧菜单。前端 `hasPerm('user.write')` 支持 `user.*` 前缀通配，与后端
  `src/modules/admin/auth.js` 的 `hasPerm` 判定规则一致；**前端隐藏只是体验，真正的拦截在服务端**。

### 邀请码 / 激活模型（卡密体系已下线）

- 菜单键 `invite`（原 `card`）→ 邀请码管理，权限点 `invite.read / invite.write / invite.void / invite.export`。
- **一个邀请码只能创建一个账号**。邀请码只是注册凭证，注册出来的账号是否开通会员由后台决定：
  - 生成时「时长 = 0」（默认）→ 用户注册后是**未开通会员**：可以直接使用，只是界面与成品带水印；
  - 「时长 > 0」→ 注册即开通对应天数。
- 后台「开通会员」（`POST /admin/api/users/:id/activate`，`days` 不能为 0）是开通会员的入口，
  客户端已没有卡密激活入口；**会员只决定有没有水印**，未开通的账号一样能正常调用业务接口。
- 订单页的「补开会员」（`POST /admin/api/orders/:order_no/reissue`，body `{days}`）直接给订单用户续期，
  不再补发卡密。

## 二、文件结构

```
web/admin/
├── index.html   单页外壳：登录层 + 主框架（左侧菜单 / 顶部栏 / 内容区）
├── app.js       全部逻辑（经典脚本，非 ES module）
├── style.css    全部样式（系统字体栈，无外部字体）
└── README.md    本文件
```

`index.html` 只引用两个同目录文件，没有任何其它资源引用：

```html
<link rel="stylesheet" href="style.css">
<script src="app.js"></script>
```

> 刻意**不用** `type="module"`：经典脚本无需处理模块路径 / CORS / 缓存边界，
> 一个文件丢进静态目录就能跑，`node --check app.js` 也能直接做语法校验。

## 三、app.js 的组织方式

按「一个页面一个 `renderXxx(container)`」拆开，公共部分集中复用：

| 区块 | 内容 |
| --- | --- |
| 工具 | `esc()` 转义、`fmtTime()` `YYYY-MM-DD HH:mm`、`fmtMoney()` 两位小数（纯字符串处理，不做浮点运算）、`fmtBytes` / `fmtDuration` / `dayDiff` |
| API | `api(path, {method, body, query})` 统一封装 + `apiGet/apiPost/apiPut/apiDelete`、`download()` blob 导出 |
| UI 原语 | `toast` / `openModal` / `confirmDialog` / `promptDialog` / `openDrawer` |
| 表格 | 统一的 `renderTable({columns, rows, fields, state, total, ...})`，内置筛选栏、排序、分页（20/50/100）、加载中、空状态、行斑马纹 |
| 图表 | `barChart()` / `lineChart()`（原生 canvas） |
| 路由 | `Router`（hash 路由 + `MENUS` 表 + `renderNav()`） |
| 页面 | `renderDashboard` / `renderUser` / `renderInvite` / `renderOrder` / `renderRevenue` / `renderTask` / `renderTicket` / `renderVersion` / `renderNotice` / `renderConfig` / `renderPanel` / `renderAudit` |

## 四、如何加一个页面

四步，全部在 `app.js` 里完成：

1. **写渲染函数**（放在「各页面」区块）：

   ```js
   async function renderFoo(host) {
     if (!hasPerm('foo.read')) { requirePermOrWarn('foo.read'); return; }
     setPageHeader('示例页面', '一句话说明');
     var s = { page: 1, limit: 20, sort: '', order: 'desc', keyword: '' };
     var fields = [
       function (st) { return textFilter('keyword', '关键词', st.keyword, '模糊搜索', 'wide'); },
     ];
     fields[0].reset = function (st) { st.keyword = ''; };

     async function reload() {
       var res = await apiGet('/foo', s);              // 路径不要带 /admin/api 前缀
       if (res.code !== 0) return;
       var data = res.data || {};
       renderTable(host, {
         title: '示例列表', state: s, fields: fields, reload: reload,
         columns: [
           { key: 'id', title: 'ID', sortable: true, className: 'num' },
           { key: 'name', title: '名称', render: function (r) { return esc(r.name); } },
           {
             key: 'ops', title: '操作', className: 'col-actions',
             render: function (r) { return '<button type="button" class="btn-link" data-op="' + esc(r.id) + '">详情</button>'; },
           },
         ],
         rows: data.rows || [], total: data.total || 0,
         empty: '暂无数据',
         onRowMount: function (container) { /* 绑定行内按钮事件 */ },
       });
     }
     reload();
   }
   ```

2. **注册到 `MENUS`**（键名必须和角色里配置的 `menus` 值、以及左侧菜单一致）：

   ```js
   foo: { title: '示例页面', icon: '例', group: '运营', render: renderFoo },
   ```

3. **给角色勾选菜单**：进「面板管理 → 角色权限」，勾上 `foo` 与 `foo.read` / `foo.write`。

4. **对齐后端接口**：路由写在 `src/modules/admin/routes.js` 或 `business.js`，
   前缀会自动拼上 `/admin/api`。**不要臆造字段**，先读后端返回值再写 `columns`。

约定：

- 所有插入 DOM 的用户数据都要过 `esc()`（备注、标题、邀请码等字段都可能被注入）。
- 金额用 `fmtMoney()`（后端返回字符串，前端只补两位小数，不做浮点运算）。
- 时间用 `fmtTime()`，空值显示 `-`。
- 导出用 `download(path, query, 文件名)`，它用 `fetch` 拿 blob 再触发下载，
  文件名从 `Content-Disposition` 解析（token 在 header 里，所以不能直接 `location.href`）。

## 五、图表为什么不用第三方库

- **离线 / 内网可用**：部署环境不保证能访问 CDN。ECharts 之类体积大（数百 KB 起），
  要么引 CDN（断网即白屏、还可能被劫持），要么把文件塞进仓库（静态目录只托管
  `web/admin`，等于为了图表多发一份大文件）。
- **需求很小**：后台只要柱状图（收益分布）和折线图（近 30 天趋势）两种，
  原生 `<canvas>` 各写一个函数（`barChart` / `lineChart`）就够了，一共一百多行，
  含坐标轴、网格、数值标签、hover tooltip、图例。
- **可控**：颜色、内边距、标签密度、金额格式都直接改代码，不用查第三方配置文档；
  也不受第三方库版本升级 / 破坏性变更影响。
- **细节处理好了就够用**：`devicePixelRatio` 缩放保证高清屏不模糊，
  窗口 `resize` 时按注册的重绘函数重画，tooltip 用绝对定位的 DOM 元素而不是在
  canvas 里手动画文字，样式统一交给 `style.css`。

如果将来真需要复杂图表（热力图、桑基图等），再引入图表库是合理的，
但当前两种图形没必要为此付出离线与体积的代价。
