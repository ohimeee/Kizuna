// NovelFire (novelfire.net) — aggregator/scanlation-style web novel reading site.
// Written independently against Kizuna's NovelSource contract (see docs/novel-sources/README.md).
//
// Selectors verified against real page source (curl with a browser UA, plain requests worked
// fine — no Cloudflare/bot-challenge encountered on this site, unlike Webnovel):
// - Listing/search items are `.novel-item` (`a[title]`/`a[href]` for title/url,
//   `img.lazy[data-src]` for cover — covers are lazy-loaded, `src` is just a placeholder gif).
//   Same markup on both the genre-listing pages and `/search?keyword=`.
// - Detail page: `h1.novel-title`, `.author a`, `.fixed-img .cover img[src]` (not lazy here),
//   the `<p>` elements inside `.summary .content` for description - not `.summary` itself (that
//   block opens with an `<h4 class="lined">Summary</h4>` heading, which prefixed every
//   description with the literal word "Summary"), and not the whole `.content` block either
//   (it ends with a "Show More" expand button whose label got swept into the text).
//   `.categories ul li a` for genres. Status has no fixed class name
//   (seen "ongoing"/likely "completed" as a variant) so it's selected positionally as the last
//   `<span>` in `.header-stats` rather than by a specific status class.
// - Chapter list lives on a separate `/book/{slug}/chapters` page (not the detail page itself),
//   all chapters on one page (no pagination seen up to ~95 chapters) — `.chapter-list li a`.
// - Chapter content is `#content` directly, clean `<p>` tags, no extra wrapper cruft observed.

Register(JSON.stringify({
  id: "novelfire",
  name: "NovelFire",
  lang: "en",
  baseUrl: "https://novelfire.net",
  version: "2.3.1",
  supportsLatest: true,
  iconUrl: "https://www.google.com/s2/favicons?sz=64&domain=novelfire.net",
  // Slugs scraped from the real genre-listing page's nav links/URLs
  // (/genre-{genre}/sort-{sort}/status-{status}/all-novel).
  filters: [
    {
      id: "genre",
      name: "Genre",
      options: [
        { label: "Action", value: "action" },
        { label: "Adult", value: "adult" },
        { label: "Adventure", value: "adventure" },
        { label: "Anime", value: "anime" },
        { label: "Arts", value: "arts" },
        { label: "Comedy", value: "comedy" },
        { label: "Drama", value: "drama" },
        { label: "Eastern", value: "eastern" },
        { label: "Ecchi", value: "ecchi" },
        { label: "Fan-fiction", value: "fan-fiction" },
        { label: "Fantasy", value: "fantasy" },
        { label: "Game", value: "game" },
        { label: "Gender Bender", value: "gender-bender" },
        { label: "Harem", value: "harem" },
        { label: "Historical", value: "historical" },
        { label: "Horror", value: "horror" },
        { label: "Isekai", value: "isekai" },
        { label: "Josei", value: "josei" },
        { label: "LGBT+", value: "lgbt" },
        { label: "Magic", value: "magic" },
        { label: "Magical Realism", value: "magical-realism" },
        { label: "Manhua", value: "manhua" },
        { label: "Martial Arts", value: "martial-arts" },
        { label: "Mature", value: "mature" },
        { label: "Mecha", value: "mecha" },
        { label: "Military", value: "military" },
        { label: "Modern Life", value: "modern-life" },
        { label: "Movies", value: "movies" },
        { label: "Mystery", value: "mystery" },
        { label: "Other", value: "other" },
        { label: "Psychological", value: "psychological" },
        { label: "Realistic Fiction", value: "realistic-fiction" },
        { label: "Reincarnation", value: "reincarnation" },
        { label: "Romance", value: "romance" },
        { label: "School Life", value: "school-life" },
        { label: "Sci-fi", value: "sci-fi" },
        { label: "Seinen", value: "seinen" },
        { label: "Shoujo", value: "shoujo" },
        { label: "Shoujo Ai", value: "shoujo-ai" },
        { label: "Shounen", value: "shounen" },
        { label: "Shounen Ai", value: "shounen-ai" },
        { label: "Slice of Life", value: "slice-of-life" },
        { label: "Smut", value: "smut" },
        { label: "Sports", value: "sports" },
        { label: "Supernatural", value: "supernatural" },
        { label: "System", value: "system" },
        { label: "Tragedy", value: "tragedy" },
        { label: "Urban", value: "urban" },
        { label: "Video Games", value: "video-games" },
        { label: "War", value: "war" },
        { label: "Wuxia", value: "wuxia" },
        { label: "Xianxia", value: "xianxia" },
        { label: "Xuanhuan", value: "xuanhuan" },
        { label: "Yaoi", value: "yaoi" },
        { label: "Yuri", value: "yuri" },
      ],
    },
    {
      id: "sort",
      name: "Sort Results By",
      options: [
        { label: "Popular", value: "popular" },
        { label: "New Novel", value: "new" },
        { label: "Latest Release", value: "latest-release" },
      ],
    },
    {
      id: "status",
      name: "Status",
      options: [
        { label: "Ongoing", value: "ongoing" },
        { label: "Completed", value: "completed" },
      ],
    },
    {
      id: "country",
      name: "Country",
      options: [
        { label: "Chinese", value: "chinese-novel" },
        { label: "Japanese", value: "japanese-novel" },
        { label: "English", value: "english-novel" },
      ],
    },
  ],
}));

var BASE_URL = "https://novelfire.net/";

function absoluteUrl(url) {
  if (!url) return url;
  if (url.indexOf("http") === 0) return url;
  return BASE_URL.replace(/\/$/, "") + url;
}

// Cloudflare rate-limits this site (HTTP 429 with the plain-text body "error code: 1015" and
// Retry-After: 2) after roughly ten requests in a few seconds, which a long chapter list easily
// triggers. The bridge returns the body whatever the status, so spot that body, wait out the
// Retry-After, and retry instead of parsing it as an empty page.
function sleep(ms) {
  var end = Date.now() + ms;
  while (Date.now() < end) { /* QuickJS has no timers; busy-wait */ }
}

function isBlockedBody(html) {
  if (!html) return true;
  if (html.length < 400 && /error code:\s*\d+|rate limit|too many requests/i.test(html)) return true;
  return /<title>\s*Just a moment/i.test(html);
}

function fetchPage(url) {
  for (var attempt = 0; attempt < 4; attempt++) {
    var html = Http.get(url, "{}");
    if (!isBlockedBody(html)) return html;
    sleep(2500);
  }
  throw new Error("NovelFire is rate-limiting or blocking requests (Cloudflare). Wait a moment and retry.");
}

// `.summary .content` carries a "Show More" expand button inside it
// (`<div class="expand"><a class="expand-btn">...<span>Show More</span></a></div>`), so taking the
// block's whole text tacks that label onto the end of every description. Read just the paragraphs.
function novelDescription(html) {
  var paragraphs = selectAllText(html, ".summary .content p");
  if (paragraphs && paragraphs.length) {
    var text = paragraphs.join("\n\n").trim();
    if (text) return text;
  }
  // Fallback for any novel whose blurb isn't wrapped in <p> at all.
  var raw = selectText(html, ".summary .content") || "";
  return raw.replace(/\s*Show More\s*$/i, "").trim();
}

function parseListing(html) {
  // `.novel-item` comes in three layouts (genre listing, search, latest-release) that differ in
  // where the title attribute lives, whether the cover is lazy, and how many links an item has
  // (latest-release has three: cover, title, chapter). Select exactly one node per item so the
  // parallel arrays stay aligned: the item's first link, its `.novel-title`, its cover img.
  var urls = selectAllAttr(html, ".novel-item > a, .novel-item .cover-wrap > a", "href");
  var titles = selectAllText(html, ".novel-item .novel-title");
  var lazyCovers = selectAllAttr(html, ".novel-item .novel-cover img", "data-src");
  var srcCovers = selectAllAttr(html, ".novel-item .novel-cover img", "src");

  var novels = urls.map(function (url, i) {
    var cover = lazyCovers[i] || (srcCovers[i] && srcCovers[i].indexOf("data:") !== 0 ? srcCovers[i] : "");
    return {
      title: titles[i] || "",
      url: absoluteUrl(url),
      cover: cover ? absoluteUrl(cover) : null,
    };
  }).filter(function (n) { return n.title && n.url; });

  return JSON.stringify({
    novels: novels,
    hasNextPage: novels.length > 0,
  });
}

globalThis.source = {

  popularNovels: function (page) {
    var html = fetchPage(BASE_URL + "genre-all/sort-popular/status-all/all-novel?page=" + page);
    return parseListing(html);
  },

  latestNovels: function (page) {
    var html = fetchPage(BASE_URL + "latest-release-novels?page=" + page);
    return parseListing(html);
  },

  // The app's filter sheet (genre/sort/status/country above) always calls searchNovels, never
  // popularNovels/latestNovels directly - so genre/sort/status/country browsing (no keyword) is
  // handled here too, via the same genre-listing URL popularNovels uses.
  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");

    if (!query && (filters.genre || filters.sort || filters.status || filters.country)) {
      var genre = filters.genre || "all";
      var sort = filters.sort || "popular";
      var status = filters.status || "all";
      var country = filters.country || "all-novel";
      var url = BASE_URL + "genre-" + genre + "/sort-" + sort + "/status-" + status + "/" + country + "?page=" + page;
      return parseListing(fetchPage(url));
    }

    var html = fetchPage(BASE_URL + "search?keyword=" + encodeURIComponent(query) + "&page=" + page);
    return parseListing(html);
  },

  novelDetails: function (url) {
    var html = fetchPage(url);

    return JSON.stringify({
      title: selectText(html, "h1.novel-title"),
      cover: selectAttr(html, ".fixed-img .cover img", "src") || null,
      author: selectText(html, ".author a"),
      description: novelDescription(html),
      genres: selectAllText(html, ".categories ul li a"),
      status: selectText(html, ".header-stats span:last-child strong"),
    });
  },

  chapterList: function (novelUrl) {
    // The chapters page paginates at 100/page (confirmed live against a 3000+ chapter novel) -
    // a single fetch silently truncated long novels to their first 100 chapters. Loop until a
    // page comes back with no chapter links, a short page, or only chapters already collected.
    var baseChaptersUrl = novelUrl.replace(/\/?$/, "") + "/chapters";
    var chapters = [];
    var seen = {};
    var page = 1;

    while (true) {
      var html = fetchPage(baseChaptersUrl + "?page=" + page);
      var urls = selectAllAttr(html, ".chapter-list li a", "href");
      if (urls.length === 0) break;

      var titles = selectAllText(html, ".chapter-list li a .chapter-title");
      var added = 0;
      for (var i = 0; i < urls.length; i++) {
        if (seen[urls[i]]) continue;
        seen[urls[i]] = true;
        added += 1;
        chapters.push({
          name: titles[i] || ("Chapter " + (chapters.length + 1)),
          url: absoluteUrl(urls[i]),
          chapterNumber: chapters.length + 1,
        });
      }
      if (added === 0 || urls.length < 100) break;
      page += 1;
    }

    return JSON.stringify(chapters);
  },

  chapterContent: function (chapterUrl) {
    // novelfire.net 302-redirects every chapter page to a mirror (readnovel.site) that serves only
    // a "Loading..." page whose script forwards the browser on (to novelphoenix.com, the sister
    // site with the same slugs). The HTTP client follows the 302 but never runs that script, so
    // #content came back empty. Follow the JS redirect by hand (same fix as LNReader's plugin).
    var html = fetchPage(chapterUrl);
    var content = selectHtml(html, "#content");
    if (content) return content;

    var m = html.match(/window\.location\.href\s*=\s*["']([^"']+)["']/);
    if (m) {
      content = selectHtml(fetchPage(m[1].replace(/\\\//g, "/")), "#content");
      if (content) return content;
    }
    // Last resort: the sister site serves the same chapter under /novel/ instead of /book/.
    var alt = chapterUrl.replace("https://novelfire.net/book/", "https://novelphoenix.com/novel/");
    if (alt !== chapterUrl) {
      content = selectHtml(fetchPage(alt), "#content");
      if (content) return content;
    }
    throw new Error("Chapter content (#content) not found - the site may be redirecting to a mirror. Retry.");
  },

};
