-- ============================================================
--  短视频去水印平台 · SQLite 建表脚本
--  由 src/db/schema.js 自动生成，请勿手工修改
--  改结构后执行：npm run db:sql
--  生成时间：2026-10-15T08:01:07.931Z
-- ============================================================
PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS "admin" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "username" TEXT NOT NULL,
  "password_hash" TEXT NOT NULL,
  "real_name" TEXT,
  "role_id" INTEGER NOT NULL DEFAULT 1,
  "phone" TEXT,
  "totp_secret" TEXT,
  "status" INTEGER NOT NULL DEFAULT 1,
  "last_login_at" TEXT,
  "last_login_ip" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_username" ON "admin" ("username");

CREATE INDEX IF NOT EXISTS "idx_role_id" ON "admin" ("role_id");

CREATE INDEX IF NOT EXISTS "idx_status" ON "admin" ("status");

CREATE TABLE IF NOT EXISTS "role" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "name" TEXT NOT NULL,
  "code" TEXT NOT NULL,
  "menus" TEXT,
  "perms" TEXT,
  "data_scope" INTEGER NOT NULL DEFAULT 1,
  "remark" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_code" ON "role" ("code");

CREATE TABLE IF NOT EXISTS "admin_role" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "admin_id" INTEGER NOT NULL,
  "role_id" INTEGER NOT NULL,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_admin_id_role_id" ON "admin_role" ("admin_id", "role_id");

CREATE INDEX IF NOT EXISTS "idx_role_id" ON "admin_role" ("role_id");

CREATE TABLE IF NOT EXISTS "admin_token" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "admin_id" INTEGER NOT NULL,
  "token" TEXT NOT NULL,
  "ip" TEXT,
  "ua" TEXT,
  "expire_at" TEXT NOT NULL,
  "last_seen" TEXT,
  "revoked" INTEGER NOT NULL DEFAULT 0,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_token" ON "admin_token" ("token");

CREATE INDEX IF NOT EXISTS "idx_admin_id" ON "admin_token" ("admin_id");

CREATE INDEX IF NOT EXISTS "idx_expire_at" ON "admin_token" ("expire_at");

CREATE TABLE IF NOT EXISTS "app_user" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "username" TEXT NOT NULL,
  "phone" TEXT,
  "email" TEXT,
  "password_hash" TEXT,
  "nickname" TEXT,
  "avatar" TEXT,
  "remark" TEXT,
  "level" INTEGER NOT NULL DEFAULT 1,
  "status" INTEGER NOT NULL DEFAULT 0,
  "bind_at" TEXT,
  "expire_at" TEXT,
  "max_device" INTEGER NOT NULL DEFAULT 1,
  "total_amount" TEXT NOT NULL DEFAULT 0,
  "total_task" INTEGER NOT NULL DEFAULT 0,
  "quota_day" INTEGER NOT NULL DEFAULT 20,
  "reg_ip" TEXT,
  "reg_channel" TEXT,
  "source" INTEGER NOT NULL DEFAULT 1,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_username" ON "app_user" ("username");

CREATE INDEX IF NOT EXISTS "idx_phone" ON "app_user" ("phone");

CREATE INDEX IF NOT EXISTS "idx_expire_at" ON "app_user" ("expire_at");

CREATE INDEX IF NOT EXISTS "idx_status_expire_at" ON "app_user" ("status", "expire_at");

CREATE INDEX IF NOT EXISTS "idx_total_amount" ON "app_user" ("total_amount");

CREATE INDEX IF NOT EXISTS "idx_created_at" ON "app_user" ("created_at");

CREATE INDEX IF NOT EXISTS "idx_remark" ON "app_user" ("remark");

CREATE TABLE IF NOT EXISTS "user_profile_log" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "user_id" INTEGER NOT NULL,
  "admin_id" INTEGER,
  "field" TEXT NOT NULL,
  "old_value" TEXT,
  "new_value" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS "idx_user_id_created_at" ON "user_profile_log" ("user_id", "created_at");

CREATE TABLE IF NOT EXISTS "user_device" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "user_id" INTEGER NOT NULL,
  "device_id" TEXT NOT NULL,
  "brand" TEXT,
  "model" TEXT,
  "os_version" TEXT,
  "app_version" TEXT,
  "first_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "last_at" TEXT,
  "status" INTEGER NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_user_id_device_id" ON "user_device" ("user_id", "device_id");

CREATE INDEX IF NOT EXISTS "idx_device_id" ON "user_device" ("device_id");

CREATE INDEX IF NOT EXISTS "idx_status" ON "user_device" ("status");

CREATE TABLE IF NOT EXISTS "invite_code" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "code" TEXT NOT NULL,
  "batch_no" TEXT,
  "status" INTEGER NOT NULL DEFAULT 0,
  "used_by" INTEGER,
  "used_at" TEXT,
  "used_phone" TEXT,
  "expire_at" TEXT,
  "level" INTEGER NOT NULL DEFAULT 1,
  "duration" INTEGER NOT NULL DEFAULT 0,
  "quota_day" INTEGER NOT NULL DEFAULT 20,
  "max_device" INTEGER NOT NULL DEFAULT 1,
  "created_by" INTEGER,
  "remark" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_code" ON "invite_code" ("code");

CREATE INDEX IF NOT EXISTS "idx_status" ON "invite_code" ("status");

CREATE INDEX IF NOT EXISTS "idx_batch_no" ON "invite_code" ("batch_no");

CREATE INDEX IF NOT EXISTS "idx_used_by" ON "invite_code" ("used_by");

CREATE INDEX IF NOT EXISTS "idx_created_at" ON "invite_code" ("created_at");

CREATE TABLE IF NOT EXISTS "card_batch" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "batch_no" TEXT NOT NULL,
  "name" TEXT,
  "duration" INTEGER NOT NULL,
  "level" INTEGER NOT NULL DEFAULT 1,
  "max_device" INTEGER NOT NULL DEFAULT 1,
  "quota_day" INTEGER DEFAULT 20,
  "price" TEXT NOT NULL DEFAULT 0,
  "total" INTEGER NOT NULL,
  "used" INTEGER NOT NULL DEFAULT 0,
  "expire_at" TEXT,
  "created_by" INTEGER,
  "remark" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_batch_no" ON "card_batch" ("batch_no");

CREATE INDEX IF NOT EXISTS "idx_created_at" ON "card_batch" ("created_at");

CREATE INDEX IF NOT EXISTS "idx_level" ON "card_batch" ("level");

CREATE TABLE IF NOT EXISTS "card_key" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "code" TEXT NOT NULL,
  "batch_id" INTEGER NOT NULL,
  "status" INTEGER NOT NULL DEFAULT 0,
  "used_by" INTEGER,
  "used_at" TEXT,
  "device_id" TEXT,
  "order_id" INTEGER,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_code" ON "card_key" ("code");

CREATE INDEX IF NOT EXISTS "idx_batch_id" ON "card_key" ("batch_id");

CREATE INDEX IF NOT EXISTS "idx_status" ON "card_key" ("status");

CREATE INDEX IF NOT EXISTS "idx_used_by" ON "card_key" ("used_by");

CREATE TABLE IF NOT EXISTS "goods" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "name" TEXT NOT NULL,
  "duration" INTEGER NOT NULL,
  "level" INTEGER NOT NULL DEFAULT 1,
  "max_device" INTEGER NOT NULL DEFAULT 1,
  "quota_day" INTEGER NOT NULL DEFAULT 20,
  "price" TEXT NOT NULL DEFAULT 0,
  "origin_price" TEXT NOT NULL DEFAULT 0,
  "tag" TEXT,
  "sort" INTEGER NOT NULL DEFAULT 100,
  "status" INTEGER NOT NULL DEFAULT 1,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS "idx_status_sort" ON "goods" ("status", "sort");

CREATE TABLE IF NOT EXISTS "order" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "order_no" TEXT NOT NULL,
  "trade_no" TEXT,
  "user_id" INTEGER,
  "username" TEXT,
  "phone" TEXT,
  "type" INTEGER NOT NULL DEFAULT 1,
  "goods_id" INTEGER,
  "goods_name" TEXT,
  "duration" INTEGER,
  "origin_amount" TEXT NOT NULL DEFAULT 0,
  "amount" TEXT NOT NULL DEFAULT 0,
  "discount" TEXT NOT NULL DEFAULT 0,
  "fee" TEXT NOT NULL DEFAULT 0,
  "net_amount" TEXT NOT NULL DEFAULT 0,
  "pay_type" TEXT,
  "pay_channel" TEXT,
  "status" INTEGER NOT NULL DEFAULT 0,
  "card_id" INTEGER,
  "paid_at" TEXT,
  "client_ip" TEXT,
  "remark" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_order_no" ON "order" ("order_no");

CREATE UNIQUE INDEX IF NOT EXISTS "uk_trade_no" ON "order" ("trade_no");

CREATE INDEX IF NOT EXISTS "idx_user_id" ON "order" ("user_id");

CREATE INDEX IF NOT EXISTS "idx_paid_at" ON "order" ("paid_at");

CREATE INDEX IF NOT EXISTS "idx_status_paid_at" ON "order" ("status", "paid_at");

CREATE INDEX IF NOT EXISTS "idx_phone" ON "order" ("phone");

CREATE INDEX IF NOT EXISTS "idx_created_at" ON "order" ("created_at");

CREATE INDEX IF NOT EXISTS "idx_goods_id" ON "order" ("goods_id");

CREATE TABLE IF NOT EXISTS "payment_record" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "order_no" TEXT,
  "trade_no" TEXT,
  "channel" TEXT,
  "amount" TEXT DEFAULT 0,
  "status" INTEGER NOT NULL DEFAULT 0,
  "raw" TEXT,
  "msg" TEXT,
  "ip" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS "idx_order_no" ON "payment_record" ("order_no");

CREATE INDEX IF NOT EXISTS "idx_trade_no" ON "payment_record" ("trade_no");

CREATE INDEX IF NOT EXISTS "idx_created_at" ON "payment_record" ("created_at");

CREATE INDEX IF NOT EXISTS "idx_status" ON "payment_record" ("status");

CREATE TABLE IF NOT EXISTS "revenue_daily" (
  "stat_date" TEXT NOT NULL,
  "order_count" INTEGER NOT NULL DEFAULT 0,
  "user_count" INTEGER NOT NULL DEFAULT 0,
  "gross" TEXT NOT NULL DEFAULT 0,
  "fee" TEXT NOT NULL DEFAULT 0,
  "net" TEXT NOT NULL DEFAULT 0,
  "refund" TEXT NOT NULL DEFAULT 0,
  "refund_count" INTEGER NOT NULL DEFAULT 0,
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  PRIMARY KEY ("stat_date")
);

CREATE TABLE IF NOT EXISTS "reconcile_log" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "stat_date" TEXT NOT NULL,
  "channel" TEXT,
  "local_count" INTEGER NOT NULL DEFAULT 0,
  "local_amount" TEXT NOT NULL DEFAULT 0,
  "platform_count" INTEGER NOT NULL DEFAULT 0,
  "platform_amount" TEXT NOT NULL DEFAULT 0,
  "diff_count" INTEGER NOT NULL DEFAULT 0,
  "diff_amount" TEXT NOT NULL DEFAULT 0,
  "detail" TEXT,
  "status" INTEGER NOT NULL DEFAULT 0,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS "idx_stat_date" ON "reconcile_log" ("stat_date");

CREATE INDEX IF NOT EXISTS "idx_status" ON "reconcile_log" ("status");

CREATE TABLE IF NOT EXISTS "dw_task" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "task_no" TEXT NOT NULL,
  "user_id" INTEGER NOT NULL,
  "platform" TEXT NOT NULL,
  "share_url" TEXT,
  "item_id" TEXT,
  "route_id" INTEGER,
  "status" INTEGER NOT NULL DEFAULT 0,
  "watermark" INTEGER NOT NULL DEFAULT 1,
  "media_type" TEXT DEFAULT 'VIDEO',
  "title" TEXT,
  "cover" TEXT,
  "images" TEXT,
  "src_url" TEXT,
  "src_key" TEXT,
  "out_url" TEXT,
  "out_key" TEXT,
  "out_size" INTEGER,
  "duration_ms" INTEGER,
  "video_ms" INTEGER,
  "retry_count" INTEGER NOT NULL DEFAULT 0,
  "worker_id" TEXT,
  "err_msg" TEXT,
  "client_ip" TEXT,
  "device_id" TEXT,
  "started_at" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "finished_at" TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_task_no" ON "dw_task" ("task_no");

CREATE INDEX IF NOT EXISTS "idx_user_id_created_at" ON "dw_task" ("user_id", "created_at");

CREATE INDEX IF NOT EXISTS "idx_status" ON "dw_task" ("status");

CREATE INDEX IF NOT EXISTS "idx_item_id" ON "dw_task" ("item_id");

CREATE INDEX IF NOT EXISTS "idx_platform_created_at" ON "dw_task" ("platform", "created_at");

CREATE INDEX IF NOT EXISTS "idx_created_at" ON "dw_task" ("created_at");

CREATE TABLE IF NOT EXISTS "dw_route" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "platform" TEXT NOT NULL,
  "name" TEXT NOT NULL,
  "type" INTEGER NOT NULL DEFAULT 1,
  "endpoint" TEXT,
  "config" TEXT,
  "priority" INTEGER NOT NULL DEFAULT 100,
  "enabled" INTEGER NOT NULL DEFAULT 1,
  "succ_1h" INTEGER NOT NULL DEFAULT 0,
  "fail_1h" INTEGER NOT NULL DEFAULT 0,
  "succ_total" INTEGER NOT NULL DEFAULT 0,
  "fail_total" INTEGER NOT NULL DEFAULT 0,
  "avg_ms" INTEGER NOT NULL DEFAULT 0,
  "last_ok_at" TEXT,
  "last_err" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_platform_name" ON "dw_route" ("platform", "name");

CREATE INDEX IF NOT EXISTS "idx_platform_enabled_priority" ON "dw_route" ("platform", "enabled", "priority");

CREATE TABLE IF NOT EXISTS "license_session" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "user_id" INTEGER NOT NULL,
  "device_id" TEXT NOT NULL,
  "token" TEXT NOT NULL,
  "refresh" TEXT NOT NULL,
  "sign_key" TEXT NOT NULL,
  "app_version" TEXT,
  "os_version" TEXT,
  "ip" TEXT,
  "ua" TEXT,
  "expire_at" TEXT NOT NULL,
  "refresh_expire_at" TEXT,
  "revoked" INTEGER NOT NULL DEFAULT 0,
  "revoked_at" TEXT,
  "last_seen" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_token" ON "license_session" ("token");

CREATE UNIQUE INDEX IF NOT EXISTS "uk_refresh" ON "license_session" ("refresh");

CREATE INDEX IF NOT EXISTS "idx_user_id_device_id" ON "license_session" ("user_id", "device_id");

CREATE INDEX IF NOT EXISTS "idx_expire_at" ON "license_session" ("expire_at");

CREATE INDEX IF NOT EXISTS "idx_revoked" ON "license_session" ("revoked");

CREATE TABLE IF NOT EXISTS "ticket" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "ticket_no" TEXT NOT NULL,
  "user_id" INTEGER NOT NULL,
  "title" TEXT NOT NULL,
  "category" INTEGER NOT NULL DEFAULT 1,
  "priority" INTEGER NOT NULL DEFAULT 2,
  "status" INTEGER NOT NULL DEFAULT 0,
  "admin_id" INTEGER,
  "message_count" INTEGER NOT NULL DEFAULT 0,
  "last_reply_at" TEXT,
  "last_reply_by" INTEGER,
  "sla_at" TEXT,
  "closed_at" TEXT,
  "context" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_ticket_no" ON "ticket" ("ticket_no");

CREATE INDEX IF NOT EXISTS "idx_user_id_created_at" ON "ticket" ("user_id", "created_at");

CREATE INDEX IF NOT EXISTS "idx_status_priority" ON "ticket" ("status", "priority");

CREATE INDEX IF NOT EXISTS "idx_admin_id" ON "ticket" ("admin_id");

CREATE INDEX IF NOT EXISTS "idx_created_at" ON "ticket" ("created_at");

CREATE TABLE IF NOT EXISTS "ticket_message" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "ticket_id" INTEGER NOT NULL,
  "sender_type" INTEGER NOT NULL DEFAULT 1,
  "sender_id" INTEGER,
  "content" TEXT,
  "attachments" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS "idx_ticket_id_created_at" ON "ticket_message" ("ticket_id", "created_at");

CREATE TABLE IF NOT EXISTS "quick_reply" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "title" TEXT NOT NULL,
  "content" TEXT NOT NULL,
  "category" INTEGER NOT NULL DEFAULT 1,
  "sort" INTEGER NOT NULL DEFAULT 100,
  "admin_id" INTEGER,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS "idx_category_sort" ON "quick_reply" ("category", "sort");

CREATE TABLE IF NOT EXISTS "sys_config" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "key" TEXT NOT NULL,
  "value" TEXT,
  "group_name" TEXT NOT NULL DEFAULT 'base',
  "title" TEXT,
  "type" TEXT NOT NULL DEFAULT 'string',
  "is_public" INTEGER NOT NULL DEFAULT 0,
  "remark" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_key" ON "sys_config" ("key");

CREATE INDEX IF NOT EXISTS "idx_group_name" ON "sys_config" ("group_name");

CREATE INDEX IF NOT EXISTS "idx_is_public" ON "sys_config" ("is_public");

CREATE TABLE IF NOT EXISTS "app_version" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "platform" TEXT NOT NULL DEFAULT 'android',
  "version_code" INTEGER NOT NULL,
  "version_name" TEXT NOT NULL,
  "channel" TEXT NOT NULL DEFAULT 'all',
  "url" TEXT NOT NULL,
  "patch_url" TEXT,
  "patch_md5" TEXT,
  "size" INTEGER,
  "changelog" TEXT,
  "force" INTEGER NOT NULL DEFAULT 0,
  "gray_percent" INTEGER NOT NULL DEFAULT 100,
  "min_version" INTEGER,
  "status" INTEGER NOT NULL DEFAULT 1,
  "published_at" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_platform_version_code_channel" ON "app_version" ("platform", "version_code", "channel");

CREATE INDEX IF NOT EXISTS "idx_status_platform" ON "app_version" ("status", "platform");

CREATE TABLE IF NOT EXISTS "notice" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "title" TEXT NOT NULL,
  "content" TEXT,
  "type" INTEGER NOT NULL DEFAULT 1,
  "target_level" TEXT DEFAULT '1,2,3,4,5',
  "start_at" TEXT,
  "end_at" TEXT,
  "status" INTEGER NOT NULL DEFAULT 1,
  "sort" INTEGER NOT NULL DEFAULT 100,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS "idx_status_sort" ON "notice" ("status", "sort");

CREATE TABLE IF NOT EXISTS "audit_log" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "admin_id" INTEGER NOT NULL,
  "username" TEXT,
  "module" TEXT NOT NULL,
  "action" TEXT NOT NULL,
  "target_id" TEXT,
  "detail" TEXT,
  "ip" TEXT,
  "ua" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS "idx_admin_id_created_at" ON "audit_log" ("admin_id", "created_at");

CREATE INDEX IF NOT EXISTS "idx_module_created_at" ON "audit_log" ("module", "created_at");

CREATE INDEX IF NOT EXISTS "idx_target_id" ON "audit_log" ("target_id");

CREATE TABLE IF NOT EXISTS "login_log" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "user_type" INTEGER NOT NULL DEFAULT 1,
  "user_id" INTEGER,
  "username" TEXT,
  "ip" TEXT,
  "ua" TEXT,
  "device_id" TEXT,
  "status" INTEGER NOT NULL DEFAULT 1,
  "msg" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS "idx_user_type_created_at" ON "login_log" ("user_type", "created_at");

CREATE INDEX IF NOT EXISTS "idx_username" ON "login_log" ("username");

CREATE INDEX IF NOT EXISTS "idx_ip" ON "login_log" ("ip");

CREATE TABLE IF NOT EXISTS "risk_event" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "user_id" INTEGER,
  "device_id" TEXT,
  "type" TEXT NOT NULL,
  "level" INTEGER NOT NULL DEFAULT 1,
  "detail" TEXT,
  "ip" TEXT,
  "handled" INTEGER NOT NULL DEFAULT 0,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS "idx_user_id_created_at" ON "risk_event" ("user_id", "created_at");

CREATE INDEX IF NOT EXISTS "idx_type_created_at" ON "risk_event" ("type", "created_at");

CREATE INDEX IF NOT EXISTS "idx_handled" ON "risk_event" ("handled");

CREATE TABLE IF NOT EXISTS "sys_worker" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "worker_id" TEXT NOT NULL,
  "host" TEXT,
  "pid" INTEGER,
  "concurrency" INTEGER DEFAULT 1,
  "processed" INTEGER NOT NULL DEFAULT 0,
  "last_seen" TEXT NOT NULL,
  "started_at" TEXT,
  "created_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_worker_id" ON "sys_worker" ("worker_id");

CREATE INDEX IF NOT EXISTS "idx_last_seen" ON "sys_worker" ("last_seen");

CREATE TABLE IF NOT EXISTS "schema_meta" (
  "key" TEXT NOT NULL,
  "value" TEXT,
  "updated_at" TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  PRIMARY KEY ("key")
);
