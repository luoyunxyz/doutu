-- ============================================================
--  短视频去水印平台 · MySQL 8.0 建表脚本
--  由 src/db/schema.js 自动生成，请勿手工修改
--  改结构后执行：npm run db:sql
--  生成时间：2026-10-15T08:01:07.928Z
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `admin` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `username` varchar(32) NOT NULL COMMENT '登录名',
  `password_hash` varchar(255) NOT NULL COMMENT 'scrypt/bcrypt 哈希',
  `real_name` varchar(32) COMMENT '姓名',
  `role_id` int NOT NULL DEFAULT 1 COMMENT '1超管 2运营 3客服 4财务',
  `phone` varchar(20) COMMENT '手机号',
  `totp_secret` varchar(64) COMMENT 'TOTP 二次验证密钥',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `last_login_at` datetime COMMENT '最后登录时间',
  `last_login_ip` varchar(45) COMMENT '最后登录IP',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台账号';

CREATE TABLE IF NOT EXISTS `role` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL COMMENT '角色名',
  `code` varchar(32) NOT NULL COMMENT '角色标识',
  `menus` text COMMENT '可见菜单 JSON 数组',
  `perms` text COMMENT '接口权限 JSON 数组，* 表示全部',
  `data_scope` tinyint NOT NULL DEFAULT 1 COMMENT '1全部 2仅自己名下',
  `remark` varchar(255) COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色与权限';

CREATE TABLE IF NOT EXISTS `admin_role` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id` bigint UNSIGNED NOT NULL,
  `role_id` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_id_role_id` (`admin_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员-角色绑定';

CREATE TABLE IF NOT EXISTS `admin_token` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id` bigint UNSIGNED NOT NULL,
  `token` char(64) NOT NULL COMMENT '会话 token',
  `ip` varchar(45),
  `ua` varchar(255),
  `expire_at` datetime NOT NULL,
  `last_seen` datetime,
  `revoked` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台会话令牌';

CREATE TABLE IF NOT EXISTS `app_user` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `phone` varchar(20) COMMENT '手机号',
  `email` varchar(128),
  `password_hash` varchar(255) COMMENT 'scrypt 哈希',
  `nickname` varchar(64),
  `avatar` varchar(255) COMMENT '头像在存储里的 key（客户端通过 /avatar/:id 读取）',
  `remark` varchar(255) COMMENT '后台备注',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '1普通 2月卡 3季卡 4年卡 5永久',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0未激活 1正常 2已过期 3封禁',
  `bind_at` datetime COMMENT '首次开通时间',
  `expire_at` datetime COMMENT '到期时间',
  `max_device` tinyint NOT NULL DEFAULT 1 COMMENT '允许绑定设备数',
  `total_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '累计消费',
  `total_task` int NOT NULL DEFAULT 0 COMMENT '累计任务数',
  `quota_day` int NOT NULL DEFAULT 20 COMMENT '每日配额',
  `reg_ip` varchar(45),
  `reg_channel` varchar(32) COMMENT '注册渠道/推广码',
  `source` tinyint NOT NULL DEFAULT 1 COMMENT '1卡密 2支付 3后台添加',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_expire_at` (`expire_at`),
  KEY `idx_status_expire_at` (`status`, `expire_at`),
  KEY `idx_total_amount` (`total_amount`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_remark` (`remark`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='终端用户';

CREATE TABLE IF NOT EXISTS `user_profile_log` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint UNSIGNED NOT NULL,
  `admin_id` bigint UNSIGNED,
  `field` varchar(32) NOT NULL,
  `old_value` varchar(255),
  `new_value` varchar(255),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id_created_at` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资料变更历史';

CREATE TABLE IF NOT EXISTS `user_device` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint UNSIGNED NOT NULL,
  `device_id` varchar(64) NOT NULL COMMENT 'Android ID + 硬件指纹哈希',
  `brand` varchar(32),
  `model` varchar(64),
  `os_version` varchar(20),
  `app_version` varchar(20),
  `first_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_at` datetime,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0已解绑 1正常 2封禁',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id_device_id` (`user_id`, `device_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备';

CREATE TABLE IF NOT EXISTS `invite_code` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` char(16) NOT NULL COMMENT '邀请码(大写去混淆字符)',
  `batch_no` varchar(32) COMMENT '生成批次',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0未使用 1已使用 2已作废 3已过期',
  `used_by` bigint UNSIGNED,
  `used_at` datetime,
  `used_phone` varchar(20) COMMENT '注册时填写的手机号，便于后台核对',
  `expire_at` datetime COMMENT '邀请码自身的有效期',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '注册后预设等级',
  `duration` int NOT NULL DEFAULT 0 COMMENT '注册后预设时长(天)，0=注册后仍需后台激活',
  `quota_day` int NOT NULL DEFAULT 20 COMMENT '注册后预设每日配额',
  `max_device` tinyint NOT NULL DEFAULT 1 COMMENT '注册后预设设备数',
  `created_by` bigint UNSIGNED,
  `remark` varchar(255),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_used_by` (`used_by`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码（一个邀请码只能创建一个账号）';

CREATE TABLE IF NOT EXISTS `card_batch` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(32) NOT NULL COMMENT '批次号 CB20260214-001',
  `name` varchar(64),
  `duration` int NOT NULL COMMENT '时长(天)，0=永久',
  `level` tinyint NOT NULL DEFAULT 1,
  `max_device` tinyint NOT NULL DEFAULT 1,
  `quota_day` int DEFAULT 20,
  `price` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '单张面值',
  `total` int NOT NULL COMMENT '生成总数',
  `used` int NOT NULL DEFAULT 0 COMMENT '已使用数',
  `expire_at` datetime COMMENT '卡密本身的有效期',
  `created_by` bigint UNSIGNED,
  `remark` varchar(255),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡密批次';

CREATE TABLE IF NOT EXISTS `card_key` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` char(16) NOT NULL COMMENT '卡密(大写去混淆字符)',
  `batch_id` bigint UNSIGNED NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0未使用 1已使用 2已作废 3已过期',
  `used_by` bigint UNSIGNED,
  `used_at` datetime,
  `device_id` varchar(64),
  `order_id` bigint UNSIGNED,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `idx_status` (`status`),
  KEY `idx_used_by` (`used_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡密';

CREATE TABLE IF NOT EXISTS `goods` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `duration` int NOT NULL COMMENT '天数，0=永久',
  `level` tinyint NOT NULL DEFAULT 1,
  `max_device` tinyint NOT NULL DEFAULT 1,
  `quota_day` int NOT NULL DEFAULT 20,
  `price` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '实付价',
  `origin_price` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '划线价',
  `tag` varchar(16) COMMENT '角标文案',
  `sort` int NOT NULL DEFAULT 100,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售卖商品';

CREATE TABLE IF NOT EXISTS `order` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) NOT NULL COMMENT '业务订单号',
  `trade_no` varchar(64) COMMENT '第三方交易号',
  `user_id` bigint UNSIGNED,
  `username` varchar(64) COMMENT '冗余，后台列表免联表',
  `phone` varchar(20) COMMENT '冗余',
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '1新购 2续费 3升级',
  `goods_id` bigint UNSIGNED,
  `goods_name` varchar(128),
  `duration` int COMMENT '购买时长(天)',
  `origin_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '原价',
  `amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '实付',
  `discount` decimal(10,2) NOT NULL DEFAULT 0,
  `fee` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '通道手续费',
  `net_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '净收入',
  `pay_type` varchar(20) COMMENT 'alipay/wxpay/qqpay',
  `pay_channel` varchar(32) COMMENT '聚合通道标识',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已关闭 3已退款',
  `card_id` bigint UNSIGNED,
  `paid_at` datetime,
  `client_ip` varchar(45),
  `remark` varchar(255),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_trade_no` (`trade_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_paid_at` (`paid_at`),
  KEY `idx_status_paid_at` (`status`, `paid_at`),
  KEY `idx_phone` (`phone`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单流水';

CREATE TABLE IF NOT EXISTS `payment_record` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) COMMENT '业务订单号',
  `trade_no` varchar(64) COMMENT '第三方交易号',
  `channel` varchar(32),
  `amount` decimal(10,2) DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0已记录 1已入账 2验签失败 3金额不符 4重复回调',
  `raw` text COMMENT '回调原始报文',
  `msg` varchar(255),
  `ip` varchar(45),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_trade_no` (`trade_no`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调原始记录';

CREATE TABLE IF NOT EXISTS `revenue_daily` (
  `stat_date` date NOT NULL COMMENT '统计日',
  `order_count` int NOT NULL DEFAULT 0,
  `user_count` int NOT NULL DEFAULT 0 COMMENT '当日去重付费人数',
  `gross` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '实付合计',
  `fee` decimal(12,2) NOT NULL DEFAULT 0,
  `net` decimal(12,2) NOT NULL DEFAULT 0,
  `refund` decimal(12,2) NOT NULL DEFAULT 0,
  `refund_count` int NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收益日聚合';

CREATE TABLE IF NOT EXISTS `reconcile_log` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL,
  `channel` varchar(32),
  `local_count` int NOT NULL DEFAULT 0,
  `local_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `platform_count` int NOT NULL DEFAULT 0,
  `platform_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `diff_count` int NOT NULL DEFAULT 0,
  `diff_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `detail` text COMMENT '差异明细 JSON',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1有差异 2对账失败',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付对账结果';

CREATE TABLE IF NOT EXISTS `dw_task` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_no` varchar(32) NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `platform` varchar(20) NOT NULL COMMENT 'douyin/kuaishou/...',
  `share_url` text COMMENT '原始分享链接',
  `item_id` varchar(64),
  `route_id` bigint UNSIGNED,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0排队 1解析中 2合成中 3成功 4失败 5超时',
  `watermark` tinyint NOT NULL DEFAULT 1 COMMENT '是否加整体水印',
  `media_type` varchar(16) DEFAULT 'VIDEO' COMMENT 'VIDEO/IMAGE',
  `title` varchar(255),
  `cover` varchar(512),
  `images` text COMMENT '图集地址 JSON 数组',
  `src_url` varchar(512) COMMENT '原始直链(短期)',
  `src_key` varchar(255) COMMENT '原片在存储里的 key，用于复用缓存',
  `out_url` varchar(512) COMMENT '成品地址',
  `out_key` varchar(255),
  `out_size` bigint UNSIGNED,
  `duration_ms` int COMMENT '处理耗时',
  `video_ms` int COMMENT '视频时长',
  `retry_count` tinyint NOT NULL DEFAULT 0,
  `worker_id` varchar(64),
  `err_msg` varchar(255),
  `client_ip` varchar(45),
  `device_id` varchar(64),
  `started_at` datetime,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_user_id_created_at` (`user_id`, `created_at`),
  KEY `idx_status` (`status`),
  KEY `idx_item_id` (`item_id`),
  KEY `idx_platform_created_at` (`platform`, `created_at`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='去水印任务';

CREATE TABLE IF NOT EXISTS `dw_route` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `platform` varchar(20) NOT NULL COMMENT 'all 表示兜底线路',
  `name` varchar(64) NOT NULL,
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '1自研(native 适配器/本地解析引擎) 2第三方API',
  `endpoint` varchar(255),
  `config` text COMMENT '密钥/请求头/超时等 JSON',
  `priority` int NOT NULL DEFAULT 100 COMMENT '越小越优先',
  `enabled` tinyint NOT NULL DEFAULT 1,
  `succ_1h` int NOT NULL DEFAULT 0,
  `fail_1h` int NOT NULL DEFAULT 0,
  `succ_total` int NOT NULL DEFAULT 0,
  `fail_total` int NOT NULL DEFAULT 0,
  `avg_ms` int NOT NULL DEFAULT 0,
  `last_ok_at` datetime,
  `last_err` varchar(255),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_name` (`platform`, `name`),
  KEY `idx_platform_enabled_priority` (`platform`, `enabled`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='解析线路';

CREATE TABLE IF NOT EXISTS `license_session` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint UNSIGNED NOT NULL,
  `device_id` varchar(64) NOT NULL,
  `token` char(64) NOT NULL,
  `refresh` char(64) NOT NULL,
  `sign_key` char(64) NOT NULL COMMENT '会话级动态密钥',
  `app_version` varchar(20),
  `os_version` varchar(20),
  `ip` varchar(45),
  `ua` varchar(255),
  `expire_at` datetime NOT NULL,
  `refresh_expire_at` datetime,
  `revoked` tinyint NOT NULL DEFAULT 0,
  `revoked_at` datetime,
  `last_seen` datetime,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  UNIQUE KEY `uk_refresh` (`refresh`),
  KEY `idx_user_id_device_id` (`user_id`, `device_id`),
  KEY `idx_expire_at` (`expire_at`),
  KEY `idx_revoked` (`revoked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权会话';

CREATE TABLE IF NOT EXISTS `ticket` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `ticket_no` varchar(32) NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `title` varchar(128) NOT NULL,
  `category` tinyint NOT NULL DEFAULT 1 COMMENT '1激活 2解析 3支付 4其他',
  `priority` tinyint NOT NULL DEFAULT 2 COMMENT '1高 2中 3低',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2待用户回复 3已关闭',
  `admin_id` bigint UNSIGNED,
  `message_count` int NOT NULL DEFAULT 0,
  `last_reply_at` datetime,
  `last_reply_by` tinyint COMMENT '1用户 2客服',
  `sla_at` datetime COMMENT 'SLA 截止时间',
  `closed_at` datetime,
  `context` text COMMENT '提交时带上的设备/版本/最近任务 JSON',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`),
  KEY `idx_user_id_created_at` (`user_id`, `created_at`),
  KEY `idx_status_priority` (`status`, `priority`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单';

CREATE TABLE IF NOT EXISTS `ticket_message` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `ticket_id` bigint UNSIGNED NOT NULL,
  `sender_type` tinyint NOT NULL DEFAULT 1 COMMENT '1用户 2客服',
  `sender_id` bigint UNSIGNED,
  `content` text,
  `attachments` text COMMENT '图片/文件 URL JSON 数组',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ticket_id_created_at` (`ticket_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单消息';

CREATE TABLE IF NOT EXISTS `quick_reply` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `title` varchar(64) NOT NULL,
  `content` text NOT NULL COMMENT '支持 {用户名} {到期时间} {卡密} 变量',
  `category` tinyint NOT NULL DEFAULT 1,
  `sort` int NOT NULL DEFAULT 100,
  `admin_id` bigint UNSIGNED,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_sort` (`category`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快捷回复模板';

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `key` varchar(64) NOT NULL,
  `value` text,
  `group_name` varchar(32) NOT NULL DEFAULT 'base',
  `title` varchar(64),
  `type` varchar(16) NOT NULL DEFAULT 'string' COMMENT 'string/int/bool/json',
  `is_public` tinyint NOT NULL DEFAULT 0 COMMENT '1=下发给客户端',
  `remark` varchar(255),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`key`),
  KEY `idx_group_name` (`group_name`),
  KEY `idx_is_public` (`is_public`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局配置';

CREATE TABLE IF NOT EXISTS `app_version` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `platform` varchar(16) NOT NULL DEFAULT 'android',
  `version_code` int NOT NULL,
  `version_name` varchar(20) NOT NULL,
  `channel` varchar(32) NOT NULL DEFAULT 'all' COMMENT '渠道包',
  `url` varchar(255) NOT NULL COMMENT '全量包地址',
  `patch_url` varchar(255) COMMENT '增量包地址',
  `patch_md5` char(32),
  `size` bigint UNSIGNED,
  `changelog` text,
  `force` tinyint NOT NULL DEFAULT 0 COMMENT '0提示 1强制',
  `gray_percent` tinyint NOT NULL DEFAULT 100 COMMENT '灰度百分比',
  `min_version` int COMMENT '低于此版本强制升级',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0下架 1发布',
  `published_at` datetime,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_version_code_channel` (`platform`, `version_code`, `channel`),
  KEY `idx_status_platform` (`status`, `platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端版本';

CREATE TABLE IF NOT EXISTS `notice` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `title` varchar(64) NOT NULL,
  `content` text,
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '1弹窗 2顶部滚动',
  `target_level` varchar(32) DEFAULT '1,2,3,4,5' COMMENT '可见等级',
  `start_at` datetime,
  `end_at` datetime,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0下线 1上线',
  `sort` int NOT NULL DEFAULT 100,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告';

CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id` bigint UNSIGNED NOT NULL,
  `username` varchar(32),
  `module` varchar(32) NOT NULL COMMENT 'user/card/order/ticket/panel/config',
  `action` varchar(32) NOT NULL COMMENT 'create/update/delete/login/export',
  `target_id` varchar(64),
  `detail` text COMMENT '变更前后快照 JSON',
  `ip` varchar(45),
  `ua` varchar(255),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_id_created_at` (`admin_id`, `created_at`),
  KEY `idx_module_created_at` (`module`, `created_at`),
  KEY `idx_target_id` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台操作审计';

CREATE TABLE IF NOT EXISTS `login_log` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_type` tinyint NOT NULL DEFAULT 1 COMMENT '1客户端 2后台',
  `user_id` bigint UNSIGNED,
  `username` varchar(64),
  `ip` varchar(45),
  `ua` varchar(255),
  `device_id` varchar(64),
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0失败 1成功',
  `msg` varchar(128),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_type_created_at` (`user_type`, `created_at`),
  KEY `idx_username` (`username`),
  KEY `idx_ip` (`ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

CREATE TABLE IF NOT EXISTS `risk_event` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint UNSIGNED,
  `device_id` varchar(64),
  `type` varchar(32) NOT NULL COMMENT 'multi_device/multi_account/geo_jump/replay',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '1提示 2告警 3自动封禁',
  `detail` text,
  `ip` varchar(45),
  `handled` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id_created_at` (`user_id`, `created_at`),
  KEY `idx_type_created_at` (`type`, `created_at`),
  KEY `idx_handled` (`handled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控事件';

CREATE TABLE IF NOT EXISTS `sys_worker` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `worker_id` varchar(64) NOT NULL COMMENT '主机-进程号-随机串',
  `host` varchar(64),
  `pid` int,
  `concurrency` int DEFAULT 1,
  `processed` int NOT NULL DEFAULT 0 COMMENT '累计处理任务数',
  `last_seen` datetime NOT NULL,
  `started_at` datetime,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_worker_id` (`worker_id`),
  KEY `idx_last_seen` (`last_seen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体处理进程心跳（用于进程存活告警）';

CREATE TABLE IF NOT EXISTS `schema_meta` (
  `key` varchar(64) NOT NULL,
  `value` varchar(255),
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库结构与数据版本';
