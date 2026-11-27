package com.feurstagram.extension;

import android.content.Context;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Stops the published Home rows at the caught-up card. The JSON item parser is
 * shared with other feeds and has no page/session identity; it cannot safely
 * remember that a previous page crossed END_OF_FEED_DEMARCATOR.
 *
 * Instead, inspect the complete active row prefix immediately before Instagram
 * publishes it, including rows reused by an incremental build. Only the active
 * count changes. The feed, cached items, ordering and caught-up model stay intact.
 * Nothing about a boundary is retained between builds, accounts or threads.
 *
 * Marker discovery and Home scoping are adapted from HushGram's FeedItemKinds,
 * HideCaughtUpCard and HomeNextPage (Copyright 2026 HushGram contributors, GPLv3).
 */
public final class HomeFeedEnd {
    private HomeFeedEnd() {}

    interface Access {
        boolean home(Object adapter);
        String source(Object adapter);
        Object rows(Object adapter);
        int size(Object rows);
        List<?> items(Object rows);
        Object model(Object row);
        boolean boundary(Object model);
        void resize(Object rows, int size);
    }

    private static final Access INSTAGRAM = new Access() {
        public boolean home(Object adapter) { return isHomeAdapter(adapter); }
        public String source(Object adapter) { return feedSource(adapter); }
        public Object rows(Object adapter) { return rowBuilder(adapter); }
        public int size(Object rows) { return rowCount(rows); }
        public List<?> items(Object rows) { return rowItems(rows); }
        public Object model(Object row) { return rowModel(row); }
        public boolean boundary(Object model) { return isEndCard(model); }
        public void resize(Object rows, int size) { setRowCount(rows, size); }
    };

    // Only a card published by ranked Home can open the prompt. Neither map keeps
    // adapters, card models, or row views alive.
    private static final WeakHashMap<Object, WeakReference<Object>> HOME_CARDS = new WeakHashMap<>();
    private static final WeakHashMap<View, UnlockGesture> ROW_GESTURES = new WeakHashMap<>();

    static int limit(Object adapter, int prefix) {
        if (!isHomeAdapter(adapter)) return prefix;
        return limit(adapter, prefix, Config.isStopHomeAtCaughtUp(), INSTAGRAM,
                card -> rememberHomeCard(adapter, card));
    }

    /** Also clamps the reused prefix of an incremental diff to the visible end. */
    static int limit(Object adapter, int prefix, boolean enabled, Access access) {
        return limit(adapter, prefix, enabled, access, null);
    }

    private static int limit(Object adapter, int prefix, boolean enabled, Access access,
                             Consumer<Object> observeCard) {
        if (!enabled || adapter == null || prefix < 0) return prefix;
        if (observeCard != null) observeCard.accept(null);
        try {
            if (!access.home(adapter) || !rankedHome(access.source(adapter))) return prefix;
            Object builder = access.rows(adapter);
            if (builder == null) return prefix;
            int count = access.size(builder);
            List<?> rows = access.items(builder);
            if (rows == null || count < 0 || count > rows.size()) return prefix;
            for (int i = 0; i < count; i++) {
                Object model = access.model(rows.get(i));
                if (model == null || !access.boundary(model)) continue;
                int end = i + 1;
                // A binder may emit several rows for one card; keep the whole card.
                while (end < count && access.model(rows.get(end)) == model) end++;
                if (end < count) access.resize(builder, end);
                if (observeCard != null) observeCard.accept(model);
                return Math.min(prefix, end);
            }
        } catch (Throwable ignored) {
            // A changed layout must leave content visible, never empty another feed.
        }
        return prefix;
    }

    /** The source names used by HushGram's HomeNextPage; other surfaces pass through. */
    static boolean rankedHome(String source) {
        return source == null || source.isEmpty() || "homecoming_all".equals(source)
                || "feed_recs".equals(source);
    }

    private static void rememberHomeCard(Object adapter, Object card) {
        synchronized (HOME_CARDS) {
            if (card == null) HOME_CARDS.remove(adapter);
            else HOME_CARDS.put(adapter, new WeakReference<>(card));
        }
    }

    private static boolean isPublishedHomeCard(Object card) {
        if (card == null || !Config.isStopHomeAtCaughtUp()
                || Config.isFeedBlocked() || Config.isFollowingFeedOnly()) return false;
        synchronized (HOME_CARDS) {
            for (WeakReference<Object> entry : HOME_CARDS.values()) {
                if (entry.get() == card) return true;
            }
            return false;
        }
    }

    /** Runs on every end-card bind, including reuse for a different feed. */
    public static void bindUnlockGesture(View row, Object card) {
        if (row == null) return;
        UnlockGesture previous = ROW_GESTURES.remove(row);
        if (previous != null && previous.installed) {
            row.setOnLongClickListener(null);
            row.setLongClickable(false);
        }
        if (!isPublishedHomeCard(card)) return;

        UnlockGesture gesture = new UnlockGesture(card);
        ROW_GESTURES.put(row, gesture);
        // Let Instagram bind its own controls first. A second bind invalidates
        // this callback before it can install a listener on a reused row.
        row.post(() -> {
            if (ROW_GESTURES.get(row) != gesture || row.isLongClickable()
                    || !isPublishedHomeCard(gesture.card.get())) return;
            row.setOnLongClickListener(gesture);
            gesture.installed = true;
        });
    }

    private static final class UnlockGesture implements View.OnLongClickListener {
        final WeakReference<Object> card;
        boolean installed;

        UnlockGesture(Object card) {
            this.card = new WeakReference<>(card);
        }

        @Override
        public boolean onLongClick(View row) {
            if (ROW_GESTURES.get(row) != this || !row.isShown()
                    || !isPublishedHomeCard(card.get())) return false;
            Context context = Settings.getActivityContext(row);
            return FeedUnlock.prompt(context);
        }
    }

    // Filled with validated, version-specific field access by StopHomeAtCaughtUpPatch.
    // Conservative unpatched defaults keep every row.
    static boolean isHomeAdapter(Object adapter) { return false; }
    static String feedSource(Object adapter) { return null; }
    static Object rowBuilder(Object adapter) { return null; }
    static int rowCount(Object rows) { return -1; }
    static List<?> rowItems(Object rows) { return null; }
    static Object rowModel(Object row) { return null; }
    static boolean isEndCard(Object model) { return false; }
    static void setRowCount(Object rows, int count) {}
}
