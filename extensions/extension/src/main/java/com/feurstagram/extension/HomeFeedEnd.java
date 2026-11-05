package com.feurstagram.extension;

import java.util.List;

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

    static int limit(Object adapter, int prefix) {
        return limit(adapter, prefix, Config.isStopHomeAtCaughtUp(), INSTAGRAM);
    }

    /** Also clamps the reused prefix of an incremental diff to the visible end. */
    static int limit(Object adapter, int prefix, boolean enabled, Access access) {
        if (!enabled || adapter == null || prefix < 0) return prefix;
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
