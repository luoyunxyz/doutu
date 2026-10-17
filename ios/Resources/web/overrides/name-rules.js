// Random chat-record rules ported from ../name (README.md + python/chat_generator.py).
//
// This file is the *pure* part of the rule set: material pools, mode weights,
// range-spec parsing, and a seeded generator that returns plain conversation
// objects. It renders nothing and knows nothing about the DOM - the WeChat
// override (wechat-chat-actions.js) turns its output into stored chat records.
//
// Everything is local: no network, no accounts, no payment.

(() => {
  'use strict';

  const FALLBACK_POOLS = {
    nicknames: ['树猫', '黎落', '秋韵', '豆汁', '不败冬', '续言篇', '往愿语', '慢半拍', '青柠味', '晚风'],
    realnames: ['陈哲远', '李沁', '程潇', '倪妮', '赵露思', '张伟', '李静', '王芳'],
    remarks: ['王小明', '刘芳', '张伟', '李静', '陈涛', '杨丽', '张磊', '王秀英'],
    addresses: ['北京 北京', '上海 上海', '广东 深圳', '浙江 杭州', '四川 成都'],
    alipayNicknames: ['树猫', '黎落', '秋韵'],
    chatTexts: ['在吗', '你好', '收到', '好的，没问题', '晚点回复你', '辛苦啦', '谢谢', '那就这么定了'],
  };

  const pools = Object.assign({}, FALLBACK_POOLS, window.__fwNamePools || {});

  // "一次性只能批量添加100个记录" - the cap the reference app showed.
  const MAX_RECORDS_PER_BATCH = 100;

  // Documented convention from the rule set. This app stores message *kinds*
  // (see below) rather than numeric codes, so these are kept only so a caller
  // can align with a target app that uses the same numbering.
  const TYPE_CODES = {
    text: 0, emoji: 15, image: 1, voice: 2, video: 3, location: 4, card: 5,
    file: 6, redPacket: 7, transfer: 8, system: 9, share: 10, groupCollect: 11,
    payNotify: 12, collectNotify: 13, transferNotify: 14,
  };

  // Money-ish kinds render as the transfer card this app already supports.
  const MONEY_KINDS = ['redPacket', 'transfer', 'groupCollect', 'payNotify', 'collectNotify', 'transferNotify'];
  // 2026-10-11: only plain chat plus photos remain. 语音 / 名片 / 文件 / 表情 / 位置
  // and the app's system notices were all dropped at the user's request.
  const MEDIA_KINDS = ['image'];

  // Per-mode weights, derived from the rule set's _default_kinds() but limited to
  // what a normal person actually sends in a chat.
  const MODE_WEIGHTS = {
    chat: [['text', 8], ['image', 2], ['redPacket', 0.7], ['transfer', 0.7]],
    group: [['text', 6], ['image', 2], ['redPacket', 1], ['transfer', 0.6]],
    service: [['text', 8]],
    pay: [['text', 8], ['transfer', 2]],
    collect: [['text', 8]],
    transfer: [['text', 8], ['transfer', 3]],
  };

  // The reference app's copy pool ships system notices ("消息已发出，但被对方拒收了。",
  // 朋友验证 …) that no real person types; they are filtered out of the chat lines.
  const SYSTEM_LINE_BLOCKLIST = [
    '消息已发出，但被对方拒收了。',
    '开启了朋友验证，你还不是他（她）朋友。请先发送朋友验证请求，对方验证通过后，才能聊天。',
    '收款成功，已存入零钱。点击可查看详情',
    '你已收款，资金已存入零钱',
    '过期未收款，已退还',
    '对方已收钱',
    '朋友已收钱',
    '已存入零钱',
  ];
  const SYSTEM_LINE_PATTERN = /零钱|收款|验证|拒收|退还|消息已发出|撤回|加入了群聊|添加你为朋友/;
  const CHAT_LINES = (pools.chatTexts || [])
    .map(line => String(line == null ? '' : line).trim())
    .filter(line => line && !SYSTEM_LINE_BLOCKLIST.includes(line) && !SYSTEM_LINE_PATTERN.test(line));

  // Photos for image messages go through the Unsplash source endpoint the user asked for.
  const IMAGE_SIZES = ['800x600', '800x800', '1080x810', '720x960', '1024x768'];
  const IMAGE_KEYWORDS = ['nature', 'city', 'food', 'travel', 'cat', 'sky', 'coffee', 'flowers', 'street'];
  function unsplashImageUrl(rnd) {
    if (rnd() < 0.35) {
      const first = IMAGE_KEYWORDS[Math.floor(rnd() * IMAGE_KEYWORDS.length)];
      const second = IMAGE_KEYWORDS[Math.floor(rnd() * IMAGE_KEYWORDS.length)];
      return 'https://source.unsplash.com/featured/?' + first + ',' + second;
    }
    const size = IMAGE_SIZES[Math.floor(rnd() * IMAGE_SIZES.length)];
    return 'https://source.unsplash.com/random/' + size + '?sig=' + Math.random().toString(36).slice(2);
  }

  const RED_PACKET_GREETINGS = ['恭喜发财，大吉大利', '新年快乐', '辛苦啦', '生日快乐', '一路顺风'];

  const NOTIFY_LABELS = { payNotify: '微信支付', collectNotify: '收款到账', transferNotify: '微信转账' };

  // ------------------------------------------------------------------ utils

  // "520.13" (single) or "200-1000" (range); allowed characters 0123456789.-
  function parseRangeSpec(text) {
    const raw = String(text == null ? '' : text).trim();
    if (!raw) return null;
    if (!/^[0-9.\-]+$/.test(raw)) throw new Error('随机值格式无效（只能输入 0123456789.- ）：' + raw);
    let low;
    let high;
    const dash = raw.slice(1).indexOf('-');
    if (dash >= 0) {
      low = Number(raw.slice(0, dash + 1));
      high = Number(raw.slice(dash + 2));
    } else {
      low = high = Number(raw);
    }
    if (!Number.isFinite(low) || !Number.isFinite(high)) throw new Error('随机值格式无效：' + raw);
    if (high < low) { const swap = low; low = high; high = swap; }
    return { low, high, isSingle: low === high };
  }

  function sampleRange(spec, rnd) {
    if (!spec) return null;
    if (spec.isSingle) return Math.round(spec.low * 100) / 100;
    return Math.round((spec.low + rnd() * (spec.high - spec.low)) * 100) / 100;
  }

  // Deterministic PRNG (mulberry32) so a seed reproduces a batch exactly.
  function createRng(seed) {
    let a = (Number(seed) >>> 0) || 0x9e3779b9;
    return () => {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function makePick(rnd, taken) {
    return (list) => {
      for (let i = 0; i < 40; i += 1) {
        const value = list[Math.floor(rnd() * list.length)];
        if (!taken || !taken.has(value)) { if (taken) taken.add(value); return value; }
      }
      return list[Math.floor(rnd() * list.length)];
    };
  }

  // ------------------------------------------------------------------ engine

  function weightsFor(mode, content) {
    const base = MODE_WEIGHTS[mode] || MODE_WEIGHTS.chat;
    const map = new Map(base);
    if (content === 'text') {
      for (const kind of [...MONEY_KINDS, ...MEDIA_KINDS]) map.delete(kind);
      if (!map.has('text')) map.set('text', 8);
    } else if (content === 'mix') {
      // Picture/text blend: keep money kinds but heavily down-weight them.
      for (const kind of MONEY_KINDS) if (map.has(kind)) map.set(kind, Math.min(map.get(kind), 0.5));
    }
    const out = [];
    for (const [kind, weight] of map) if (weight > 0) out.push([kind, weight]);
    return out.length ? out : [['text', 1]];
  }

  function weightedKind(weights, rnd) {
    let total = 0;
    for (const [, weight] of weights) total += weight;
    let point = rnd() * total;
    for (const [kind, weight] of weights) {
      point -= weight;
      if (point <= 0) return kind;
    }
    return weights[weights.length - 1][0];
  }

  function buildContent(kind, ctx) {
    const { pick, rnd, amountSpec } = ctx;
    switch (kind) {
      case 'text': return { text: pick(ctx.chatLines) };
      case 'image': return { text: unsplashImageUrl(rnd) };
      case 'redPacket':
        return { transfer: true, amount: sampleRange(amountSpec || { low: 1, high: 200 }, rnd), note: pick(RED_PACKET_GREETINGS) };
      case 'transfer':
        return { transfer: true, amount: sampleRange(amountSpec || { low: 1, high: 5000 }, rnd), note: '转账' };
      case 'groupCollect':
        return { transfer: true, amount: sampleRange(amountSpec || { low: 1, high: 200 }, rnd), note: '群收款' };
      case 'payNotify':
      case 'collectNotify':
      case 'transferNotify':
        return { transfer: true, amount: sampleRange(amountSpec || { low: 1, high: 2000 }, rnd), note: NOTIFY_LABELS[kind] };
      default: return { text: pick(ctx.chatLines) };
    }
  }

  const DEFAULT_DELAY = { low: 3.5, high: 5.0 };

  /**
   * Build one batch of conversations.
   *
   * @param {object} options
   *   count      total records (cap MAX_RECORDS_PER_BATCH)
   *   contacts   how many conversations to spread them over
   *   mode       chat | group | mix | service | pay | collect | transfer
   *   content    text | mix | all
   *   amount     RangeSpec for money kinds (may be null)
   *   delay      RangeSpec for seconds between messages (may be null)
   *   start/end  Date or ms; window the messages fall in
   *   seed       optional number for reproducibility
   *   takenNames Set of display names to avoid (mutated as names are used)
   *   avatarCount how many avatars the caller can render
   * @returns {Array} conversations: { mode, isGroup, name, nickname, avatarIndex,
   *   members, membersAvatars, messages, firstSendTime, lastSendTime }
   */
  function generateConversations(options) {
    const opts = options || {};
    const total = Math.max(0, Math.floor(Number(opts.count) || 0));
    if (total > MAX_RECORDS_PER_BATCH) {
      throw new Error('一次最多生成 ' + MAX_RECORDS_PER_BATCH + ' 条记录');
    }
    const wantContacts = Math.max(1, Math.floor(Number(opts.contacts) || 1));
    if (total && wantContacts > total) throw new Error('会话数不能大于记录总数');
    if (!total) return [];

    const mode = opts.mode === 'mix' ? 'mix' : (MODE_WEIGHTS[opts.mode] ? opts.mode : 'chat');
    const content = opts.content === 'text' || opts.content === 'mix' ? opts.content : 'all';
    const rnd = createRng(opts.seed);
    const taken = opts.takenNames instanceof Set ? opts.takenNames : new Set();
    const pick = makePick(rnd, taken);
    const avatarCount = Math.max(1, Math.floor(Number(opts.avatarCount) || 1));

    const now = Date.now();
    const end = opts.end instanceof Date ? opts.end.getTime() : (Number(opts.end) || now);
    const start = opts.start instanceof Date ? opts.start.getTime() : (Number(opts.start) || (end - 7 * 24 * 3600 * 1000));
    const span = Math.max(end - start, 1000);
    const delay = opts.delay || DEFAULT_DELAY;

    // Split `total` across `wantContacts`, giving everyone at least one.
    const counts = new Array(wantContacts).fill(1);
    for (let i = wantContacts; i < total; i += 1) counts[Math.floor(rnd() * wantContacts)] += 1;

    const conversations = [];
    for (let index = 0; index < wantContacts; index += 1) {
      const conversationMode = mode === 'mix' ? (rnd() < 0.4 ? 'group' : 'chat') : mode;
      const isGroup = conversationMode === 'group';
      const weights = weightsFor(conversationMode, content);

      const displayName = isGroup
        ? '群聊'
        : (pick(pools.remarks) || pick(pools.nicknames) || '好友');
      const nickname = pick(pools.nicknames) || displayName;

      // Each conversation lives in its own sub-window so contacts overlap in
      // time the way a real chat list does.
      const winLength = Math.max(span * (0.3 + rnd() * 0.3), 1000);
      const winStart = start + rnd() * Math.max(span - winLength, 0);
      let cursor = rnd() * Math.max(winLength * 0.1, 1000);

      const members = [];
      if (isGroup) {
        const memberCount = 3 + Math.floor(rnd() * 6);
        for (let m = 0; m < memberCount; m += 1) {
          const memberName = pick(pools.nicknames);
          if (memberName) members.push({ name: memberName, avatarIndex: 1 + Math.floor(rnd() * avatarCount) });
        }
      }

      const messages = [];
      const baseSort = 1 + Math.floor(rnd() * 50);
      for (let i = 0; i < counts[index]; i += 1) {
        const step = Math.max(sampleRange(delay, rnd) || 3.5, 0.001) * 1000;
        cursor = Math.min(cursor + step, winLength);
        const createdAt = Math.round(winStart + cursor);
        const kind = weightedKind(weights, rnd);
        const built = buildContent(kind, {
          pick, rnd, amountSpec: opts.amount,
          chatLines: CHAT_LINES.length ? CHAT_LINES : (pools.chatTexts || []),
        });
        const isSender = rnd() < (isGroup ? 0.35 : 0.5);
        const message = {
          kind,
          side: isSender ? 'outgoing' : 'incoming',
          createdAt,
          sortIndex: baseSort + i,
        };
        if (built.transfer) {
          message.type = 'transfer';
          message.amount = Math.max(0.01, Number(built.amount) || 0.01);
          message.note = built.note || '';
        } else {
          message.text = built.text == null ? '' : String(built.text);
          if (kind === 'image') message.type = 'image';
          if (built.size) message.fileSize = built.size;
          if (built.fileType) message.fileType = built.fileType;
          if (built.duration) message.duration = built.duration;
        }
        if (isGroup && !isSender) {
          const sender = members.length ? members[Math.floor(rnd() * members.length)].name : '';
          if (sender) message.sender = sender;
        }
        messages.push(message);
      }
      messages.sort((a, b) => a.createdAt - b.createdAt);

      conversations.push({
        mode: conversationMode,
        isGroup,
        name: isGroup ? ('群聊(' + members.length + ')') : displayName,
        nickname,
        remark: isGroup ? '' : displayName,
        avatarIndex: 1 + Math.floor(rnd() * avatarCount),
        region: pick(pools.addresses) || '',
        members,
        memberAvatars: members.map(m => m.avatarIndex),
        messages,
        firstSendTime: messages.length ? messages[0].createdAt : null,
        lastSendTime: messages.length ? messages[messages.length - 1].createdAt : null,
      });
    }
    return conversations;
  }

  window.__fwNameGen = {
    pools,
    MAX_RECORDS_PER_BATCH,
    TYPE_CODES,
    parseRangeSpec,
    generateConversations,
    modes: Object.keys(MODE_WEIGHTS),
  };
})();
