package com.luoyun.doutu.features.call;

/**
 * One person the user may pick as the caller of a simulated call.
 *
 * <p>{@code name} is what the call page prints — the remark when there is one, exactly the way the
 * page's own {@code callContact()} picks it, so picking 大叔 here shows 大叔 there.
 *
 * <p>{@code avatarUrl} is whatever the page uses for that person: an https link for the contacts the
 * bundled page ships, or a {@code ./assets/...} path for its built-in ones (both are understood by
 * {@link CallImages}).
 */
public final class CallContact {
    public final String id;
    public final String name;
    public final String avatarUrl;

    public CallContact(String id, String name, String avatarUrl) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
    }
}
