# Stop Home at caught up

In FeurStagram settings, open **Feed** and enable **Stop Home after you're all caught up**. It defaults to on. Home must be unblocked to use it. Keep **Following feed only** off to retain Instagram's algorithmically ranked Home feed. The new setting is separate from Following, Suggested accounts, Ads and the permanent surface lock; none of their preferences is changed.

The intended result is all existing Home content through the caught-up card, including the card itself, with no recommendation rows, section header or loading spinner underneath. The card's text and links are left to Instagram. Other existing FeurStagram filters still apply.

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

The runtime hook requires the discovered Home adapter type and a ranked Home paging source (`null`, empty, `homecoming_all` or the `feed_recs` continuation). Other adapters and explicit Following/Favorites/friends sources pass through. It does not rewrite the pagination header or select another feed. There is no additional network blocker: the recommendation/loading rows are excluded from the published list.

Discovery refuses an unknown or ambiguous layout during patching. A runtime access failure leaves the rows unchanged. If Instagram stops rendering the end card, there is deliberately no inferred cutoff that could hide ordinary posts.

## Verification

With the normal build prerequisites and GitHub Packages credentials described in [BUILD_FROM_SOURCE.md](BUILD_FROM_SOURCE.md):

```sh
./gradlew :patches:test :extensions:extension:testDebugUnitTest :patches:build
```

To include DEX discovery against a locally supplied APK:

```sh
FEURSTAGRAM_TEST_APK=/absolute/path/instagram.apk ./gradlew :patches:test
```

The host unit tests cover ordinary ranked posts (including inline recommendations before the marker), first/last and multi-row cards, continuation pages without a new marker, incremental prefixes, refresh, interleaved accounts, reopening Home, stale buffer rows, unrelated surfaces, disabled settings and conservative failure behaviour. DEX stand-ins cover discovery, ambiguity, missing protocol kinds, generic feed casts, rendered-card wrappers and inaccessible fields. The optional APK test checks the complete discovery path.

On 5 November 2026, the runtime tests and DEX tests were run independently of Gradle, and the selectors matched the released FeurStagram 454.0.0.13.77 APK (SHA-256 `494f8e81393a0c60afdb67141441ad11781451d2547ca4145adb8a5dc2c72e7a`). All extension Java sources compiled against Android API 35, and the new Kotlin sources compiled against the Morphe Desktop 2.0.0 bundled APIs. These independent checks do not replace the repository's normal build.

The full Gradle build was attempted but could not resolve `app.morphe.patches:1.3.2` from the configured plugin repositories. No complete patch bundle or installed APK was produced. On-device behaviour is **not tested**. Before merging, run the normal build and test an installed APK with multiple Home pages, refresh, account switching, cold/warm Home reopening, the disabled toggle, Following, Explore and profiles.
