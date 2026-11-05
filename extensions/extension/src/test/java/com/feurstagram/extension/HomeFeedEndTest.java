package com.feurstagram.extension;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

public class HomeFeedEndTest {
    private static final class Card {}
    private static final class Row {
        final Object model;
        Row(Object model) { this.model = model; }
    }
    private static final class Feed {
        boolean home = true;
        String source = "homecoming_all";
        List<Row> rows = new ArrayList<>();
        int count;
        Feed(Object... models) { rebuild(models); }
        void rebuild(Object... models) {
            rows = new ArrayList<>();
            for (Object model : models) rows.add(new Row(model));
            count = rows.size();
        }
    }
    private static final HomeFeedEnd.Access ACCESS = new HomeFeedEnd.Access() {
        public boolean home(Object adapter) { return ((Feed) adapter).home; }
        public String source(Object adapter) { return ((Feed) adapter).source; }
        public Object rows(Object adapter) { return adapter; }
        public int size(Object rows) { return ((Feed) rows).count; }
        public List<?> items(Object rows) { return ((Feed) rows).rows; }
        public Object model(Object row) { return ((Row) row).model; }
        public boolean boundary(Object model) { return model instanceof Card; }
        public void resize(Object rows, int size) { ((Feed) rows).count = size; }
    };

    private int publish(Feed feed, int prefix) { return HomeFeedEnd.limit(feed, prefix, true, ACCESS); }

    @Test public void ordinaryRankedPostsAndInlineRecommendationsKeepTheirOrder() {
        Feed feed = new Feed("ranked-post-2", "inline-recommendation", "ranked-post-1", "carousel");
        assertEquals(2, publish(feed, 2));
        assertEquals(4, feed.count);
        assertEquals("ranked-post-2", feed.rows.get(0).model);
        assertEquals("inline-recommendation", feed.rows.get(1).model);
        assertEquals("ranked-post-1", feed.rows.get(2).model);
    }

    @Test public void caughtUpCardAndEverythingBeforeItStay() {
        Card card = new Card();
        Feed feed = new Feed("post", "inline-recommendation", card, "suggested", "spinner");
        List<Row> original = feed.rows;
        assertEquals(0, publish(feed, 0));
        assertEquals(3, feed.count);
        assertSame(card, feed.rows.get(2).model);
        assertSame(original, feed.rows);
        assertEquals("source rows are not deleted or reordered", 5, original.size());
    }

    @Test public void wholeMultiRowCardStays() {
        Card card = new Card();
        Feed feed = new Feed("post", card, card, "suggested-header", "suggested");
        publish(feed, 0);
        assertEquals(3, feed.count);
    }

    @Test public void firstAndLastItemBoundaries() {
        Feed first = new Feed(new Card(), "suggested");
        publish(first, 0);
        assertEquals(1, first.count);
        Feed last = new Feed("post", new Card());
        assertEquals(2, publish(last, 4));
        assertEquals(2, last.count);
    }

    @Test public void firstBoundaryWinsWhenMoreThanOneCardExists() {
        Feed feed = new Feed("post", new Card(), "suggested", new Card(), "more");
        publish(feed, 0);
        assertEquals(2, feed.count);
    }

    @Test public void laterPageWithoutItsOwnMarkerIsCutInAccumulatedRows() {
        Card card = new Card();
        Feed feed = new Feed("page-1-post", card, "page-1-suggested");
        publish(feed, 0);
        feed.rows.add(new Row("page-2-suggested"));
        feed.rows.add(new Row("page-2-spinner"));
        feed.count = feed.rows.size(); // Instagram has finished the next build.
        assertEquals("the reused diff prefix is clamped too", 2, publish(feed, 3));
        assertEquals(2, feed.count);
        assertEquals(2, publish(feed, 2)); // A repeated publication is idempotent.
    }

    @Test public void paginationBeforeTheMarkerStillShowsEveryPage() {
        Feed feed = new Feed("page-1");
        publish(feed, 0);
        feed.rebuild("page-1", "page-2");
        assertEquals(1, publish(feed, 1));
        assertEquals(2, feed.count);
        feed.rebuild("page-1", "page-2", new Card(), "page-3-suggested");
        publish(feed, 2);
        assertEquals(3, feed.count);
    }

    @Test public void refreshOnTheSameAdapterRestoresNewPosts() {
        Feed feed = new Feed("old-post", new Card(), "suggested");
        publish(feed, 0);
        feed.rebuild("new-post", "new-carousel", "another-post");
        publish(feed, 0);
        assertEquals(3, feed.count);
    }

    @Test public void accountSwitchingAndInterleavedBuildsHaveNoSharedBoundary() {
        Feed a = new Feed("account-a", new Card(), "suggested");
        Feed b = new Feed("account-b-1", "account-b-2", "account-b-3");
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> publish(a, 0)),
                CompletableFuture.runAsync(() -> publish(b, 0))).join();
        assertEquals(2, a.count);
        assertEquals(3, b.count);
        a.rebuild("account-c-1", "account-c-2", "account-c-3");
        publish(a, 0);
        assertEquals(3, a.count);
    }

    @Test public void reopeningHomeRechecksCachedRowsAndFreshRows() {
        Feed old = new Feed(new Card(), "suggested");
        publish(old, 0);
        Feed cached = new Feed("cached-post", new Card(), "cached-suggested");
        publish(cached, 0);
        assertEquals(2, cached.count);
        Feed fresh = new Feed("fresh-1", "fresh-2", "fresh-3");
        publish(fresh, 0);
        assertEquals(3, fresh.count);
    }

    @Test public void staleRowsBeyondActiveCountCannotTriggerBoundary() {
        Feed feed = new Feed("new-post", "new-post-2", new Card(), "stale-suggested");
        feed.count = 2;
        assertEquals(1, publish(feed, 1));
        assertEquals(2, feed.count);
    }

    @Test public void unrelatedAdaptersAndFeedSourcesAreUntouched() {
        Feed other = new Feed(new Card(), "explore-post");
        other.home = false;
        publish(other, 0);
        assertEquals(2, other.count);
        for (String source : Arrays.asList("homecoming_following", "homecoming_favorites", "homecoming_friends", "explore", "profile", "unknown")) {
            Feed feed = new Feed(new Card(), "post");
            feed.source = source;
            assertEquals(5, publish(feed, 5));
            assertEquals(source, 2, feed.count);
        }
    }

    @Test public void rankedFirstPagesAndRecommendationPagesUseTheSameBoundary() {
        for (String source : Arrays.asList(null, "", "homecoming_all", "feed_recs")) {
            Feed feed = new Feed("post", new Card(), "suggested");
            feed.source = source;
            publish(feed, 0);
            assertEquals(2, feed.count);
        }
    }

    @Test public void toggleOffLeavesAllRowsAndPrefixUntouched() {
        Feed feed = new Feed(new Card(), "suggested");
        assertEquals(4, HomeFeedEnd.limit(feed, 4, false, ACCESS));
        assertEquals(2, feed.count);
    }

    @Test public void unexpectedLayoutsAndNullsFailOpen() {
        Feed invalid = new Feed(new Card(), "post");
        invalid.count = 5;
        assertEquals(3, publish(invalid, 3));
        assertEquals(5, invalid.count);
        assertEquals(3, HomeFeedEnd.limit(null, 3, true, ACCESS));
        assertEquals(3, HomeFeedEnd.limit(new Object(), 3, true, ACCESS));
        Feed nullModel = new Feed(null, "post");
        publish(nullModel, 0);
        assertEquals(2, nullModel.count);
    }

    @Test public void newPreferenceIsIndependentAndOnByDefault() {
        // No Android application on the host JVM: Config returns its real defaults.
        assertTrue(Config.isStopHomeAtCaughtUp());
        assertFalse(Config.isFollowingFeedOnly());
        assertFalse(Config.isLockable("stop_home_at_caught_up"));
        assertTrue(Config.isAdsBlocked());
        assertTrue(Config.isSuggestedBlocked());
    }
}
