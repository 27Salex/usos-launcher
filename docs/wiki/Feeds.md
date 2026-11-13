# Feeds

- Room `feeds.db` (v2). `FeedParser.parse(XmlPullParser, limit)` (pure; tests use kxml2), relaxed mode; image from media:thumbnail > media:content > enclosure > first `<img>`.
- **Reads:** all articles are shown; opening an unread one spends a read (`ReadTokens`, pure, injected clock; bucket max = "reads per hour" 3..10, default 5, refills 1 every 60/N min; stored in DataStore, atomic `spendReadToken`). Read = free. Without reads the tap shows "NO READS - +1 AT HH:MM".
- Starred articles are never pruned; `prune(feedId, keep)` keeps the latest `keep` per feed (10/20/30/50, default 20). Deleting a feed detaches its saved articles (`feedId = -id`).
- Refresh: on opening and on filter change only feeds older than 10 min; pull-to-refresh always (own implementation, no material3).
- Adding (`FeedManagerScreen`, `FeedRepository.discover`): accepts a feed URL, site or domain; `FeedFetcher.probe` then `FeedDiscovery` (alternate links, known hosts, common paths). Suggested and default feeds follow the `feeds_language` resource and are seeded only once (`feeds_seeded`).
- Thumbnails: `ThumbnailLoader` (OkHttp + LruCache), cached in color; gray is a draw-time filter.
