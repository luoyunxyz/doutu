package com.luoyun.doutu.features.wei.data;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.wei.model.Conversation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * In-memory source for the reference inbox and its interactive conversation details.
 */
public final class ConversationRepository {
    private final ArrayList<Conversation> conversations = new ArrayList<>();
    private final int[] accents = {
            0xff6759c8, 0xff248c83, 0xffd16a55, 0xff3979b9, 0xffb45d91, 0xff758b43
    };
    private int nextId = 1;

    public ConversationRepository() {
        // This seed's message history and avatar are the exact redmi thread from the
        // supplied detail reference; keep that identity stable when the inbox is reordered.
        addSeed("redmi", "r", 0, R.drawable.chat_avatar_redmi, "9月14日",
                "太难了", false,
                "我感觉我天赋还可以", false,
                "但是没用啊", false,
                "我感觉我纯属执念", true,
                "又喜欢计算机", true,
                "又想用计算机挣钱", true,
                "我也是啊", false,
                "其实挺难受的", false,
                "我现在主要是不知道该怎么学", false,
                "学啥都没人带", false,
                "屡屡碰壁", false,
                "我交了快三万的学费", true,
                "到头来还不是啥也不会", true,
                "哎", false);
        addSeed("公众号", "公", 3, R.drawable.chat_avatar_public, "20:52",
                "[3条] 电子课本站：豆包，我把和前女友的所有聊…", false);
        addSeed("服务号", "服", 1, R.drawable.chat_avatar_service, "20:35",
                "[联学车等6条通知] 鱼皮编程导航：又跌爆了。。。", false);
        addSeed("黄俊熙", "黄", 2, R.drawable.avatar_dog, "20:01", "[动画表情]", false);
        addSeed("阿宁", "阿", 4, R.drawable.chat_avatar_arning, "19:46", "能用了", false);
        addSeed("微信游戏", "游", 5, R.drawable.chat_avatar_games, "18:43",
                "[4条] 预约《怪物猎人：旅人》等待开启狩猎之…", false);
        addSeed("程序员客栈133群", "程", 0, R.drawable.chat_avatar_group, "16:38",
                "[7条] 特~谱的🍰：没意思，没项目，没事情，…", false);
        addSeed("赵海洋", "赵", 2, R.drawable.chat_avatar_zhao, "16:24", "看着就有点历史了", false);
        addSeed("二进制星穹坍缩·数据终焉执典", "二", 3, R.drawable.chat_avatar_grid, "14:48",
                "十点半：但是，我觉得手柄玩儿游戏都挺难受的，…", false);
        addSeed("项目讨论群", "项", 0, R.drawable.chat_avatar_group, "13:20",
                "新的需求文档已经发群里了", false);
        addSeed("学习资料分享", "学", 1, R.drawable.chat_avatar_public, "12:08",
                "今天的课程资料已更新", false);
        addSeed("家人群", "家", 2, R.drawable.chat_avatar_grid, "昨天", 
                "晚上一起吃饭吗？", false);
        addSeed("软件测试通知", "测", 3, R.drawable.chat_avatar_service, "昨天",
                "测试版本可以下载了", false);
        addSeed("聊天记录", "聊", 4, R.drawable.chat_avatar_arning, "昨天", "最近分享的内容", false);
    }

    private void addSeed(String title, String initials, int accentIndex, int avatarResourceId, String time,
                         String firstMessage, boolean firstSentByMe, Object... remaining) {
        Conversation conversation = new Conversation("conversation-" + nextId++, title, initials,
                accents[accentIndex % accents.length], avatarResourceId, time);
        conversation.addMessage(firstMessage, firstSentByMe);
        for (int i = 0; i + 1 < remaining.length; i += 2) {
            conversation.addMessage((String) remaining[i], (Boolean) remaining[i + 1]);
        }
        conversations.add(conversation);
    }

    public List<Conversation> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(conversations));
    }

    public List<Conversation> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return getAll();
        ArrayList<Conversation> matches = new ArrayList<>();
        for (Conversation item : conversations) {
            if (item.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                    || item.getPreview().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(item);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public Conversation create(String title) {
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) return null;
        String initials = cleanTitle.substring(0, cleanTitle.offsetByCodePoints(0, 1));
        Conversation item = new Conversation("conversation-" + nextId++, cleanTitle, initials,
                accents[(nextId - 2) % accents.length], R.drawable.chat_avatar_arning, "刚刚");
        item.addMessage("对话已创建", false);
        conversations.add(0, item);
        return item;
    }

    public void send(Conversation conversation, String text) {
        if (conversation == null || text == null || text.trim().isEmpty()) return;

        // Resolve the canonical object by immutable ID before mutating or moving it.
        // List positions change after every send; they must never identify a chat.
        int conversationIndex = indexOf(conversation.getId());
        if (conversationIndex < 0) return;

        Conversation canonical = conversations.get(conversationIndex);
        canonical.addMessage(text, true);
        canonical.setUpdatedAt("刚刚");
        if (conversationIndex > 0) {
            conversations.remove(conversationIndex);
            conversations.add(0, canonical);
        }
    }

    private int indexOf(String conversationId) {
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).getId().equals(conversationId)) return i;
        }
        return -1;
    }
}
