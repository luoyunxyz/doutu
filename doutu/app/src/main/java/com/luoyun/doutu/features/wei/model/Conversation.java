package com.luoyun.doutu.features.wei.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A single conversation shown in the inbox and contact directory. */
public final class Conversation {
    public static final class Message {
        private final String text;
        private final boolean sentByMe;

        public Message(String text, boolean sentByMe) {
            this.text = text;
            this.sentByMe = sentByMe;
        }

        public String getText() { return text; }
        public boolean isSentByMe() { return sentByMe; }
    }

    private final String id;
    private final String title;
    private final String initials;
    private final int accentColor;
    private final int avatarResourceId;
    private final ArrayList<Message> messages = new ArrayList<>();
    private String updatedAt;

    public Conversation(String id, String title, String initials, int accentColor,
                       int avatarResourceId, String updatedAt) {
        this.id = id;
        this.title = title;
        this.initials = initials;
        this.accentColor = accentColor;
        this.avatarResourceId = avatarResourceId;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getInitials() { return initials; }
    public int getAccentColor() { return accentColor; }
    public int getAvatarResourceId() { return avatarResourceId; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getPreview() {
        return messages.isEmpty() ? "开始一段新的对话" : messages.get(messages.size() - 1).getText();
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void addMessage(String text, boolean sentByMe) {
        if (text != null && !text.trim().isEmpty()) messages.add(new Message(text.trim(), sentByMe));
    }
}
