/**
 * 初始数据：角色、全局配置、商品、解析线路、快捷回复、公告。
 *
 * 幂等：重复执行只会更新已存在记录，不会产生重复行。
 * 部署时 `npm run db:init` 会自动调用；单独重灌用 `npm run db:seed`。
 */
import { logger } from '../core/logger.js';
import { now } from '../core/util.js';
import config from '../config.js';

const log = logger.child('seed');

/* ------------------------------ 角色 ------------------------------ */

/**
 * 《用户须知与免责声明》默认正文。
 * 客户端内置了一份同样的文案做兜底（TermsActivity.TERMS_TEXT），这里的这份是**后台可编辑**的版本：
 * 后台一改，客户端下次启动会拉到新版本并要求用户重新确认。两处内容不一致时以服务端为准。
 */
const TERMS_TEXT_DEFAULT = [
  '欢迎使用本软件。在您开始使用之前，请务必认真、完整地阅读以下全部条款。本条款是您与本软件开发者之间就使用本软件所订立的协议，自您点击「同意」之时起生效，对双方均具有约束力。您点击「同意」或继续使用本软件，即视为您已完整阅读、充分理解并自愿接受本条款的全部内容；如您不同意本条款的任何内容，请点击「拒绝」并立即停止使用本软件。',
  '',
  '一、软件性质与用途',
  '1. 本软件仅为技术演示、功能模拟与学习交流之用，不构成任何形式的商业服务承诺，亦不对处理结果作出任何准确性、完整性或可用性保证。',
  '2. 本软件不存储、不传播、不再分发任何第三方平台的内容，仅按用户本人的指令，对其自行提供的链接或素材在本地设备上进行处理。',
  '3. 本软件与任何第三方平台均无隶属、代理、合作或授权关系，相关名称、标识的权利归各自权利人所有。',
  '',
  '二、用户责任与后果自担',
  '1. 您通过本软件进行的一切操作、处理、下载、保存、使用、传播等行为，均为您本人独立意愿的体现，并由您本人独立决定、独立实施、独立承担全部后果。',
  '2. 您承诺所处理的素材已取得合法授权或归您本人所有，且您的使用行为符合您所在国家或地区的法律法规、公序良俗以及相关平台的服务条款。',
  '3. 因使用本软件所产生的一切结果、后果、损失、纠纷、索赔、行政处罚及其他法律责任，无论直接或间接，均由您本人承担，与本软件及其开发者无关。',
  '',
  '三、免责范围',
  '1. 本软件的开发者、运营者、公司及其员工、管理人员、关联方，均不对您使用本软件所产生的任何直接或间接后果承担任何责任，包括但不限于：素材侵权、版权纠纷、账号封禁或限流、数据丢失、设备损坏、预期利益损失、第三方索赔、行政或刑事处罚等。',
  '2. 本软件按「现状」提供，不对可用性、连续性、准确性、及时性、适用性作出任何明示或默示的保证；因网络故障、设备异常、第三方平台接口变更、政策调整或不可抗力导致的服务中断、处理失败或数据异常，开发者不承担任何责任。',
  '3. 您不得将本软件用于任何违反法律法规、侵犯他人合法权益或损害公共利益的用途；由此产生的一切后果由您自行承担，开发者保留在发现违法使用时停止提供服务并配合有关机关调查的权利。',
  '4. 您因使用本软件而作出的任何判断与决定，均由您自行负责；本软件不构成任何形式的法律、财务或其他专业建议。',
  '',
  '四、条款的变更、效力与争议解决',
  '1. 本条款可能因法律法规调整或软件功能变化而更新，更新后将在软件内公示；公示后继续使用即视为接受更新后的条款。',
  '2. 本条款的解释、效力与履行适用中华人民共和国法律（不含冲突规范）。因本条款产生的争议，双方应首先友好协商；协商不成的，提交开发者所在地有管辖权的人民法院解决。',
  '3. 本条款部分内容如被认定无效或不可执行，不影响其余条款的效力；该部分应以最接近原意且合法有效的方式予以替代。',
  '',
  '请您再次确认：您已完整阅读并理解上述全部内容，在知晓并自愿承担使用本软件的全部风险与后果的前提下，继续使用本软件。',
].join('\n');

const ROLES = [
  {
    id: 1,
    name: '超级管理员',
    code: 'super',
    menus: ['*'],
    perms: ['*'],
    data_scope: 1,
    remark: '全部菜单与数据',
  },
  {
    id: 2,
    name: '运营',
    code: 'operator',
    menus: ['dashboard', 'user', 'invite', 'order', 'revenue', 'ticket', 'version', 'notice', 'config'],
    perms: ['user.*', 'invite.*', 'order.*', 'revenue.*', 'ticket.*', 'version.*', 'notice.*', 'config.read'],
    data_scope: 1,
    remark: '不含管理员账号与角色管理',
  },
  {
    id: 3,
    name: '客服',
    code: 'support',
    menus: ['dashboard', 'user', 'ticket'],
    perms: ['user.read', 'user.remark', 'user.device', 'ticket.*'],
    data_scope: 2,
    remark: '只看自己名下用户',
  },
  {
    id: 4,
    name: '财务',
    code: 'finance',
    menus: ['dashboard', 'order', 'revenue'],
    perms: ['order.read', 'order.export', 'order.refund', 'revenue.*'],
    data_scope: 1,
    remark: '订单与收益，不含用户管理',
  },
];

/* ---------------------------- 全局配置 ---------------------------- */

const CONFIGS = [
  // 站点
  ['site_name', '逗图去水印', 'base', '站点名称', 'string', 1, '客户端展示'],
  ['service_switch', '1', 'base', '服务总开关', 'bool', 1, '关闭后除授权外全部接口返回维护中'],
  ['maintenance_notice', '系统升级维护中，请稍后重试', 'base', '维护提示文案', 'string', 1, ''],
  ['customer_service_url', '', 'base', '在线客服入口', 'string', 1, '可填第三方 IM 链接，留空则用内置工单'],
  ['customer_service_qq', '', 'base', '客服QQ', 'string', 1, ''],
  ['customer_service_wx', '', 'base', '客服微信', 'string', 1, ''],
  // 「我 → 人工客服」那一页：二维码 + 上面一行字 + 下面一行联系方式，三样都在后台改
  ['cs_notice', '开通会员、不会使用等问题均可询问客服', '客服', '客服页顶部文案', 'string', 0, '显示在二维码上方（客户端「我 → 人工客服」）'],
  ['cs_contact', '', '客服', '客服联系方式', 'string', 0, '手机号或微信号，显示在二维码下方，客户端点击可复制'],
  ['cs_qr_image', '', '客服', '客服二维码图片地址', 'string', 0, 'http(s) 图片地址，显示在客服页中间；留空时客户端显示占位'],
  // 「娱乐（更多玩法）」整页由服务端下发（2026-10-17）：分区、顺序、标题、副标题、开关、点击去向
  // 全在这里。为什么不写死在客户端 —— 玩法与文案是最容易被整包抄走的部分，放在服务端之后，
  // 抄走 APK 也拿不到这些内容，运营改文案/下线玩法也不用发版。
  // 结构：{ version, sections:[{ title, accent, rows:[{ key, title, subtitle, badge, switch, enabled }] }] }
  // key 是稳定标识（客户端据此决定点进去哪个页面），改标题不会影响功能。
  [
    'playbook_entertainment',
    JSON.stringify(
      {
        version: 1,
        sections: [
          {
            title: '实用功能',
            accent: 'blue',
            rows: [
              { key: 'parse', title: '短视频一键去水印', subtitle: '支持抖音、快手、小红书、B站等100+平台' },
              { key: 'order', title: '淘宝订单详情', subtitle: '查看订单详情，轻松管理购物记录' },
            ],
          },
          {
            title: '社恐神器',
            accent: 'purple',
            rows: [
              { key: 'call', title: '模拟朋友来电', subtitle: '自定义来电，帮你从尴尬场合脱身' },
              { key: 'shutdown', title: '模拟没电关机', subtitle: '模拟手机没电关机效果' },
              { key: 'sound', title: '模拟收款声音', subtitle: '小支/小微到账播报，金额自己定' },
            ],
          },
          {
            title: '乐趣挑逗',
            accent: 'orange',
            rows: [
              { key: 'tease', title: '撩妹加V', subtitle: '假装摄像头损坏，去购物加老板(娘)的微信~', switch: true },
            ],
          },
        ],
      },
      null,
      2,
    ),
    '玩法',
    '娱乐页玩法配置',
    'json',
    0,
    '「娱乐」整页由服务端下发：分区/顺序/标题/副标题/开关/点击去向。enabled=false 隐藏该行，switch=true 渲染成开关',
  ],
  ['playbook_version', '1', '玩法', '玩法配置版本', 'int', 0, '每次改动 +1，客户端据此判断要不要重画娱乐页'],
  ['icp_number', '', 'base', '备案号', 'string', 1, ''],
  [
    'legal_notice',
    '本软件仅用于处理您自己创作或拥有版权的素材，请勿用于去除他人作品的版权水印、搬运或二次发布。' +
      '平台提供侵权投诉与下架通道，收到有效投诉将定位到具体账号与任务并停止服务。',
    'base',
    '版权承诺条款',
    'string',
    1,
    '客户端应在首次使用与激活页展示（合规硬性要求）',
  ],

  // 用户与配额
  [
    'register_open',
    '1',
    'user',
    '开放邀请码注册',
    'bool',
    1,
    '客户端凭邀请码自助注册；关闭后只能由后台创建账号',
  ],
  [
    'watermark_overlay_text',
    '开通会员去除水印',
    'user',
    '未激活水印文案',
    'string',
    1,
    '未激活会员时，客户端全屏叠加的斜向水印文字',
  ],
  [
    'activation_notice',
    '未开通会员，可直接使用（成品带水印）',
    'user',
    '未开通会员提示',
    'string',
    1,
    '客户端在未开通会员状态下展示的提示文案',
  ],
  ['default_quota_day', '20', 'user', '默认每日配额', 'int', 0, '新建用户默认值'],
  ['default_max_device', '1', 'user', '默认绑定设备数', 'int', 0, ''],
  ['unbind_limit_month', '2', 'user', '每月解绑次数', 'int', 0, '防止一号多机共享'],

  // 首页横幅（公告里 type=3 的那类；这几项是「没有任何公告时」的兜底文案与轮播节奏）
  ['home_banner_enabled', '1', 'banner', '首页横幅开关', 'bool', 1, '关掉后首页大图不再叠加文字框'],
  [
    'home_banner_default_title',
    '',
    'banner',
    '首页横幅默认主文案',
    'string',
    1,
    '留空则按时间自动问候（早上好/下午好/晚上好…）；填了就用这里固定的，支持 {greeting} 占位',
  ],
  [
    'home_banner_default_hint',
    '想改哪里点哪里，如果不行就长按👉',
    'banner',
    '首页横幅默认副文案',
    'string',
    1,
    '第二行小字；留空则不显示第二行',
  ],
  // 免责声明：后台可编辑，改一次自动递增版本，客户端据此要求用户重新确认
  [
    'terms_text',
    TERMS_TEXT_DEFAULT,
    'notice',
    '用户须知与免责声明正文',
    'text',
    1,
    '首次启动时向用户展示的法律条款。**只有超级管理员可以修改**，每次修改都会写入审计日志并自动递增版本号，' +
      '所有已同意的用户下次启动会被要求重新确认',
  ],
  [
    'terms_version',
    '1',
    'notice',
    '免责声明版本号',
    'int',
    1,
    '保存条款时自动加一；客户端用它判断是否需要重新弹出，请勿手工修改',
  ],
  [
    'notice_rotate_sec',
    '8',
    'banner',
    '横幅轮播间隔(秒)',
    'int',
    1,
    '同时有多条「首页横幅」公告时，每隔多少秒切换一条（最小 3 秒）',
  ],
  // 两条文字各自独立轮播：上排（人物旁边那行）与下排（白色长条里那行）分别维护文字池
  [
    'banner_title_list',
    '[\n  "{greeting}"\n]',
    'banner',
    '上排文字池（每行一条）',
    'json',
    1,
    '首页大图上、人物旁边那行字。**每行一条**，按「上排轮播间隔」循环显示；支持 {greeting} 占位（按时间问候）',
  ],
  [
    'banner_hint_list',
    '[\n  "想改哪里点哪里，如果不行就长按",\n  "嫌功能少？欢迎联系客服添加功能",\n  "联系客服开通会员"\n]',
    'banner',
    '下排文字池（每行一条）',
    'json',
    1,
    '人物下面那条白色长条里的字。**每行一条**，按「下排轮播间隔」**上下滚动**循环显示（加/减行即可增删文案）',
  ],
  [
    'banner_title_rotate_sec',
    '8',
    'banner',
    '上排轮播间隔(秒)',
    'int',
    1,
    '上排文字池里多条文字时的切换间隔（最小 3 秒）',
  ],
  [
    'banner_hint_rotate_sec',
    '5',
    'banner',
    '下排轮播间隔(秒)',
    'int',
    1,
    '下排长条里每条文字停留几秒（最小 3 秒），切换时上下滚动',
  ],
  [
    'banner_greetings',
    '[\n  "0-5|凌晨了！这个点就别忙了，早点睡，明天再弄也不迟~",\n  "5-8|早晨好！新的一天开始了，慢慢来~",\n  "8-11|上午好！有需要随时招呼我~",\n  "11-13|中午好！先好好吃顿饭，忙活不差这一会儿~",\n  "13-17|下午好！今天想弄点什么？",\n  "17-19|傍晚好！忙了一天，先歇口气~",\n  "19-22|晚上好！一天将尽，放松身心，别太辛苦~",\n  "22-24|夜深了！早点休息吧，明天再弄也不迟~"\n]',
    'banner',
    '按时段问候语（每行 起点-终点|文案）',
    'json',
    1,
    '上排文字池里 {greeting} 占位的内容。每行一条，格式 `起点小时-终点小时|文案`（终点不含，可跨零点如 22-5）；' +
      '留空则用客户端内置表。凌晨那段建议写成提醒早睡',
  ],

  // 分享好友：App 里「微 → 分享好友」与「我 → 邀请好友」点一下就复制这个链接
  [
    'share_link',
    'https://tdapi.luoyun.xyz/',
    'share',
    '分享好友链接',
    'string',
    1,
    'App 里点「分享好友 / 邀请好友」时复制到剪贴板的那条链接，改完客户端下次拉配置即生效',
  ],
  [
    'share_tip',
    '链接已复制到剪贴板，粘贴给好友即可~',
    'share',
    '分享好友提示语',
    'string',
    1,
    '复制成功后弹给用户看的那句话，留空则用客户端内置文案',
  ],

  // 水印策略（服务端强制，客户端无法传参覆盖）
  ['watermark_enabled', '1', 'watermark', '启用整体水印', 'bool', 0, '总开关：关掉后所有成品都不带水印（慎用）'],
  [
    'watermark_free_for_member',
    '1',
    'watermark',
    '会员成品免水印',
    'bool',
    0,
    '1=开通会员的成品不带水印（默认，也是「开通会员去除水印」的落地）；0=所有人都带水印',
  ],
  ['watermark_text_template', '{nickname}', 'watermark', '水印文案模板', 'string', 0, '支持 {nickname} {user_id} {date} {platform}'],
  ['watermark_position', 'bottom-right', 'watermark', '水印位置', 'string', 0, 'bottom-right / bottom-left / top-right / top-left'],
  ['watermark_opacity', '0.42', 'watermark', '水印透明度', 'string', 0, '建议 0.35~0.5'],
  ['watermark_width_ratio', '0.2', 'watermark', '水印宽度占比', 'string', 0, '成片宽度的 18%~22%'],
  ['watermark_show_platform', '1', 'watermark', '水印含平台标识', 'bool', 0, ''],
  ['watermark_rotate_corners', '0', 'watermark', '四角轮换', 'bool', 0, '按时间轮换位置，防裁剪'],
  ['watermark_logo', '', 'watermark', '水印 logo 图路径', 'string', 0, '留空则只画文字'],

  // 解析与合成
  ['parse_timeout_sec', '25', 'dw', '单次解析超时', 'int', 0, ''],
  ['task_timeout_sec', '120', 'dw', '单任务超时', 'int', 0, '超时不计费'],
  ['task_retry', '1', 'dw', '失败重试次数', 'int', 0, '换备用线路'],
  ['queue_max', '50', 'dw', '队列积压告警阈值', 'int', 0, ''],
  ['batch_max', '10', 'dw', '批量解析上限', 'int', 1, '仅高等级用户可用'],
  [
    'parse_member_only',
    '1',
    'dw',
    '解析仅限会员',
    'bool',
    1,
    '1=未开通会员不能解析（返回 2001，客户端引导开通会员）；0=未开通也能解析，只是成品带水印',
  ],
  ['batch_min_level', '3', 'dw', '批量解析最低等级', 'int', 1, ''],
  ['origin_cache_hours', '24', 'dw', '成品缓存时长(小时)', 'int', 0, '同一用户 + 同一链接 + 同水印策略命中时直接复用成品'],

  // 支付
  ['pay_channels', '["alipay","wxpay"]', 'pay', '可用支付方式', 'json', 1, ''],
  ['pay_fee_rate', '0.02', 'pay', '通道费率', 'string', 0, '用于净收入统计'],
  ['pay_min_amount', '1.00', 'pay', '最低支付金额', 'string', 1, ''],
  ['pay_order_expire_min', '30', 'pay', '订单有效期(分钟)', 'int', 0, '超时自动关闭'],

  // 客户端版本与安全
  ['app_latest_version_code', '1', 'app', '最新版本号', 'int', 1, ''],
  ['app_force_update', '0', 'app', '强制更新', 'bool', 1, ''],
  ['app_update_title', '新版本可用', 'app', '更新弹窗标题', 'string', 1, ''],
  ['app_download_url', '', 'app', '全量包下载地址', 'string', 1, ''],
  ['app_version_blocklist', '[]', 'app', '被吊销的客户端版本', 'json', 0, '发现被破解的版本号写进来，立即拒绝其请求'],
  ['config_version', '1', 'app', '配置版本号', 'int', 1, '客户端据此判断是否刷新缓存'],
  ['risk_ban_enabled', '0', 'security', '风控自动封禁', 'bool', 0, '同设备多账号等行为命中后自动封禁'],

  // 客服
  ['ticket_sla_hours', '24', 'ticket', '工单 SLA(小时)', 'int', 0, ''],
  ['ticket_notify_user', '0', 'ticket', '工单回复短信通知', 'bool', 0, ''],
];

/* ------------------------------ 商品 ------------------------------ */

const GOODS = [
  { name: '月卡', duration: 30, level: 2, max_device: 1, quota_day: 30, price: '19.90', origin_price: '29.90', tag: '', sort: 10 },
  { name: '季卡', duration: 90, level: 3, max_device: 2, quota_day: 50, price: '49.90', origin_price: '89.70', tag: '最超值', sort: 20 },
  { name: '年卡', duration: 365, level: 4, max_device: 3, quota_day: 100, price: '129.00', origin_price: '358.80', tag: '推荐', sort: 30 },
  { name: '永久卡', duration: 0, level: 5, max_device: 3, quota_day: 200, price: '299.00', origin_price: '999.00', tag: '限量', sort: 40 },
];

/* --------------------------- 快捷回复 --------------------------- */

const QUICK_REPLIES = [
  ['激活问题', '您好，您的账号注册成功后需要在后台开通会员才能使用。请提供注册手机号，我们核对后立即为您开通，开通后页面上的水印会消失。', 1],
  ['解析失败', '您好，您反馈的链接我们已经记录，解析规则正在紧急处理中，通常 2 小时内恢复，恢复后原链接可直接重试。', 2],
  ['设备超限', '您好，您的账号已绑定 {用户名} 台设备上限，请在「我的-设备管理」中解绑不用的设备，或联系客服协助处理。', 1],
  ['支付问题', '您好，您的订单我们已收到，如未自动开通请提供订单号与支付截图，我们会立即为您处理。', 3],
  ['没有邀请码', '您好，注册需要邀请码（一个邀请码只能创建一个账号）。请提供您想使用的手机号，我们为您分配一个邀请码。', 1],
];

/* --------------------------- 解析线路 --------------------------- */

function defaultRoutes() {
  const routes = [
    {
      platform: 'douyin',
      name: '抖音原生适配器',
      type: 1,
      endpoint: 'native:douyin',
      config: JSON.stringify({ timeoutMs: 15000 }),
      priority: 10,
      enabled: 1,
    },
  ];
  if (config.parser.base) {
    routes.push({
      platform: 'all',
      name: '本地解析引擎 media-parser',
      type: 1,
      // 本地 sidecar（Python，见项目根目录 media-parser/）：50 个平台的自研解析，
      // 以 API_ONLY=true 启动时 /api/v1/parse 免鉴权，所以 key 留空即可。
      endpoint: `${config.parser.base}/api/v1/parse`,
      config: JSON.stringify({ key: config.parser.key, timeoutMs: config.parser.timeoutMs }),
      priority: 50,
      enabled: 1,
    });
  }
  routes.push({
    platform: 'all',
    name: '第三方API兜底',
    type: 2,
    endpoint: '',
    config: JSON.stringify({ timeoutMs: 20000 }),
    priority: 900,
    enabled: 0,
  });
  return routes;
}

/* ------------------------------ 执行 ------------------------------ */

export async function seedAll(db) {
  const ts = now();

  // 角色（id 固定，便于 admin.role_id 直接引用）
  for (const role of ROLES) {
    const existing = await db.get('SELECT `id` FROM `role` WHERE `code` = ?', [role.code]);
    const data = {
      name: role.name,
      code: role.code,
      menus: JSON.stringify(role.menus),
      perms: JSON.stringify(role.perms),
      data_scope: role.data_scope,
      remark: role.remark,
      updated_at: ts,
    };
    if (existing) await db.update('role', { id: existing.id }, data);
    else await db.insert('role', { id: role.id, ...data, created_at: ts });
  }

  // 全局配置
  for (const [key, value, group, title, type, isPublic, remark] of CONFIGS) {
    const existing = await db.get('SELECT `id` FROM `sys_config` WHERE `key` = ?', [key]);
    if (existing) {
      // 已存在的配置只补元信息，不覆盖运营改过的值
      await db.update('sys_config', { id: existing.id }, { group_name: group, title, type, is_public: isPublic, remark, updated_at: ts });
    } else {
      await db.insert('sys_config', {
        key,
        value,
        group_name: group,
        title,
        type,
        is_public: isPublic,
        remark,
        created_at: ts,
        updated_at: ts,
      });
    }
  }

  // 商品
  for (const g of GOODS) {
    const existing = await db.get('SELECT `id` FROM `goods` WHERE `name` = ?', [g.name]);
    if (existing) await db.update('goods', { id: existing.id }, { ...g, updated_at: ts });
    else await db.insert('goods', { ...g, status: 1, created_at: ts, updated_at: ts });
  }

  // 解析线路
  for (const r of defaultRoutes()) {
    const existing = await db.get('SELECT `id` FROM `dw_route` WHERE `platform` = ? AND `name` = ?', [r.platform, r.name]);
    if (existing) {
      await db.update('dw_route', { id: existing.id }, { ...r, updated_at: ts });
    } else {
      await db.insert('dw_route', { ...r, created_at: ts, updated_at: ts });
    }
  }

  // 快捷回复
  for (const [title, content, category] of QUICK_REPLIES) {
    const existing = await db.get('SELECT `id` FROM `quick_reply` WHERE `title` = ?', [title]);
    if (!existing) {
      await db.insert('quick_reply', {
        title,
        content,
        category,
        sort: 100,
        created_at: ts,
        updated_at: ts,
      });
    }
  }

  // 一条欢迎公告（默认下线，避免上线首日弹窗打扰）
  const noticeExists = await db.get('SELECT `id` FROM `notice` WHERE `title` = ?', ['欢迎使用']);
  if (!noticeExists) {
    await db.insert('notice', {
      title: '欢迎使用',
      content: '本软件仅用于处理您自己拥有版权的素材，请勿用于搬运他人作品。',
      type: 1,
      target_level: '1,2,3,4,5',
      status: 0,
      sort: 10,
      created_at: ts,
      updated_at: ts,
    });
  }

  log.info('初始数据写入完成', { roles: ROLES.length, configs: CONFIGS.length, goods: GOODS.length });
  return { roles: ROLES.length, configs: CONFIGS.length, goods: GOODS.length };
}

export { ROLES, CONFIGS, GOODS, QUICK_REPLIES, defaultRoutes };
export default { seedAll };
