# Stop Home at caught up

The cutoff starts locked. Unblock Home in FeurStagram settings and leave **Following feed only** off to use Instagram's ranked Home feed. Home shows everything through the **You're all caught up** card. The recommendations, section headers and loading row beneath it are hidden.

Long-press the caught-up card or open **FeurStagram settings → Feed → Unlock**. Enter the numeric password and tap **Unlock**. A correct password reopens Instagram immediately, so Home starts with a fresh adapter and can load recommendations normally. A wrong password leaves the cutoff locked. Use **Feed → Lock** to re-enable the cutoff and rebuild Home without a password.

The unlock applies to the new Instagram process, including later Home pages. The next cold launch starts locked. Returning to Instagram while the same process is alive continues that launch. The one-use restart handoff expires if Instagram fails to reopen promptly. Both older SharedPreferences keys are ignored, including the original unprotected setting.

The password is a local friction control. Other settings such as Following-only, Ads and Suggested accounts still apply.

## Why this does not use a parser flag

`FeedItemFilterPatch` sees a JSON field name for one unit. It has no account, request generation, page cursor or owning feed. The same parser is used by Explore and other feed surfaces. A process-wide or thread-local “past the end” flag there cannot reliably handle a later page on another thread, refreshes, account changes, cached reads or unrelated feeds. Resetting it on every page would instead let recommendation-only continuation pages through.

HushGram distinguishes the feed kind `END_OF_FEED_DEMARCATOR`, the rendered end card, Home's readers and paging sources. Its relevant references are:

- [FeedItemKinds.java](https://github.com/SysAdminDoc/HushGram/blob/781164bbc3f17b23e78ae75c4885e24fdf751191/extensions/instagram/src/main/java/app/hushgram/extension/instagram/feed/FeedItemKinds.java)
- [HideCaughtUpCard.kt](https://github.com/SysAdminDoc/HushGram/blob/781164bbc3f17b23e78ae75c4885e24fdf751191/patches/src/main/kotlin/app/morphe/patches/instagram/feed/suggested/HideCaughtUpCard.kt)
- [HomeNextPage.java](https://github.com/SysAdminDoc/HushGram/blob/781164bbc3f17b23e78ae75c4885e24fdf751191/extensions/instagram/src/main/java/app/hushgram/extension/instagram/feed/HomeNextPage.java)

This patch adapts that discovery and scoping, without importing HushGram's suggestion filter, settings framework or pagination controller.

## Publication boundary

The new patch finds Home's model builder by `MainfeedAdapter.buildModels`, its inherited row publisher, and the diff publisher containing `smartUpdate` / `smartUpdateSync`. It identifies the end-card model through the binder's kept Kotlin cast messages and validates its relationship to the feed item kind enum. No obfuscated name is hardcoded.

Immediately before publication, it scans the complete **active** row prefix, including any reused rows. It caps the active count after the first end card, keeping every consecutive row belonging to that same card. It also clamps a diff's reused prefix so a continuation cannot reintroduce rows below the boundary. This covers the ordinary publication path and the earlier direct publication inside an incremental diff.

The input feed, cached items, row-buffer order, requests and ranking are unchanged. A later build computes its active rows again. There is no stored boundary, cursor, account ID or timeout to reset. A new refresh/account/build is assessed from its own rows, and stale buffer entries beyond the active count are ignored.

The cutoff also identifies the card model published by ranked Home. A long press only works on that model. The binder clears its old gesture on rebind, and installs a new one after Instagram has bound the row if it has no native long-click action.

The runtime hook requires the discovered Home adapter type and a ranked Home paging source (`null`, empty, `homecoming_all` or the `feed_recs` continuation). Other adapters and explicit Following/Favorites/friends sources pass through. It does not rewrite the pagination header or select another feed. There is no additional network blocker: the recommendation/loading rows are excluded from the published list.

Discovery refuses an unknown or ambiguous layout during patching. A runtime access failure leaves the rows unchanged. If Instagram stops rendering the end card, there is deliberately no inferred cutoff that could hide ordinary posts.

## Verification

With the normal build prerequisites and GitHub Packages credentials described in [BUILD_FROM_SOURCE.md](BUILD_FROM_SOURCE.md):

```sh
./gradlew :patches:test :extensions:extension:testDebugUnitTest :patches:build :patches:buildAndroid
```

To include DEX discovery against a locally supplied APK:

```sh
FEURSTAGRAM_TEST_APK=/absolute/path/instagram.apk ./gradlew :patches:test
```

The host unit tests cover ordinary ranked posts (including inline recommendations before the marker), first/last and multi-row cards, continuation pages without a new marker, incremental prefixes, refresh, interleaved accounts, reopening Home, stale buffer rows, unrelated surfaces, disabled settings and conservative failure behaviour. DEX stand-ins cover discovery, ambiguity, missing protocol kinds, generic feed casts, rendered-card wrappers and inaccessible fields. The optional APK test checks the complete discovery path.

Run the positive password test by supplying the actual code in the local `FEURSTAGRAM_TEST_UNLOCK_CODE` environment variable. The code is not stored in the test fixtures or build logs. The session tests exercise first launch, a verified restart ticket, single consumption, manual relock, malformed files and expired handoffs.

Before merging, test an installed APK with a real Instagram account. Check that ranked Home stops at the card, a wrong code leaves it locked, a correct code from each entry point reveals recommendations and allows further pagination, and both manual lock and a later cold launch stop the feed again. Check that account switching, refresh, Following, Explore and profiles still behave normally. See PR #1 for the results of the host and APK checks.
