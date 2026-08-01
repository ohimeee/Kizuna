// NovelArrow (novelarrow.com) — aggregator/scanlation-style web novel reading site (Next.js).
// Written independently against Kizuna's NovelSource contract (see docs/novel-sources/README.md).
//
// robots.txt on this site explicitly disallows ClaudeBot (and other named AI crawlers) site-wide,
// while allowing normal browsers/generic crawlers everywhere except /api/, /admin/, and account
// pages. That's a deliberate, informed exception granted by the project owner for this one source
// - not the default policy for adding sources to Kizuna.
//
// Selectors verified against real page source (curl with a browser UA):
// - Listing/search/genre-archive cards all share one markup (confirmed identical across
//   /novels/latest, /novels/popular, /novels/search, /genre/{slug}): title+href live on
//   `a:has(h2.truncate)`, cover on `img.site-cover-media-calm[src]`. 1:1 counts confirmed (20/20
//   on a real listing page).
// - Novel detail page is React Server Component-streamed - most of it (og:image/description meta,
//   rating, etc.) only exists as escaped JSON inside <script> pushes, NOT as literal DOM Jsoup can
//   select. What *is* real, literal DOM (confirmed): `h1` (title), `img.novel-cover-image[src]`
//   (cover), `a[href^="/author/"]` (author), `.site-reading-copy p` (full untruncated synopsis,
//   one <p> per paragraph), and genre tag links matching `a[href^="/genre/"][class*=underline-
//   offset-4]` (exactly 5/5 matched Shadow Slave's real og:keywords genres, distinct from the
//   site-wide genre nav grid which uses different classes/no underline-offset-4). Status has no
//   stable class either, but the little status-badge SVG's id is literally
//   `{status-word}-title-{reactAutoId}` (e.g. `ongoing-title-_R_9iaqnpfiv5mivb_`) - regex out the
//   word prefix rather than relying on the volatile React-generated suffix.
// - Full chapter list is NOT in server HTML at all (detail page only ever ships chapter 1 + the
//   ~29 most recent - confirmed on Shadow Slave: chapters 1-30 and 3134 present, nothing between,
//   out of 3134 total). The real list/content load via `/api-web/novels/{novelId}/chapters` and
//   `/api-web/novels/{novelId}/chapters/{chapterId}` - a route family confirmed live (3134/3134
//   chapters returned in one call) and *not* covered by robots.txt's `Disallow: /api/` (that's a
//   literal prefix match against `/api/`; `/api-web/` is a distinct path, not a subpath of it).
//   Endpoint shape cross-checked against LNReader's public, independently-written plugin
//   (github.com/LNReader/lnreader-plugins, plugins/english/novelarrow.ts) as a reference that this
//   route family is real and already used in the wild - Kizuna's implementation below is its own,
//   written against the JsNovelSource bridge functions, not a port of that file.
// - Genre archive supports real, confirmed pagination (`?page=N`, 20/page, distinct results per
//   page) and sort (`?sort=POPULAR` / `?sort=RATING` both confirmed to change the top result vs the
//   default/unsorted list).

Register(JSON.stringify({
  id: "novelarrow",
  name: "NovelArrow",
  lang: "en",
  baseUrl: "https://novelarrow.com",
  version: "1.0.0",
  supportsLatest: true,
  iconUrl: "https://www.google.com/s2/favicons?sz=64&domain=novelarrow.com",
  // Slugs scraped from the real site-wide genre nav (/genre/{slug} links on the homepage).
  filters: [
    {
      id: "genre",
      name: "Genre",
      options: [
        { label: "Action", value: "action" },
        { label: "Adult", value: "adult" },
        { label: "Adventure", value: "adventure" },
        { label: "Anime & Comics", value: "anime-&-comics" },
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
        { label: "LGBT+", value: "lgbt+" },
        { label: "LitRPG", value: "litrpg" },
        { label: "Magic", value: "magic" },
        { label: "Magical Realism", value: "magical-realism" },
        { label: "Martial Arts", value: "martial-arts" },
        { label: "Mature", value: "mature" },
        { label: "Mecha", value: "mecha" },
        { label: "Military", value: "military" },
        { label: "Modern Life", value: "modern-life" },
        { label: "Mystery", value: "mystery" },
        { label: "Other", value: "other" },
        { label: "Psychological", value: "psychological" },
        { label: "Realistic", value: "realistic" },
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
        { label: "Thriller", value: "thriller" },
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
        { label: "Latest", value: "LASTEST" },
        { label: "Popular", value: "POPULAR" },
        { label: "Rating", value: "RATING" },
      ],
    },
  ],
}));

var BASE_URL = "https://novelarrow.com/";
var JSON_HEADERS = JSON.stringify({ "Accept": "application/json" });

function absoluteUrl(url) {
  if (!url) return url;
  if (url.indexOf("http") === 0) return url;
  return BASE_URL.replace(/\/$/, "") + url;
}

function novelIdFromUrl(url) {
  var path = url.split("?")[0].replace(/\/$/, "");
  return path.split("/").pop();
}

function parseChapterUrl(url) {
  var path = url.split("?")[0].replace(/\/$/, "");
  var parts = path.split("/");
  return {
    chapterId: parts[parts.length - 1] || "",
    novelId: parts[parts.length - 2] || "",
  };
}

// Shared by /novels/latest, /novels/popular, /novels/search, and /genre/{slug} - all four render
// the exact same card markup (confirmed live: 20/20 matching title/cover pairs on a real page).
function parseListing(html) {
  var urls = selectAllAttr(html, "a:has(h2.truncate)", "href");
  var titles = selectAllText(html, "h2.truncate");
  var covers = selectAllAttr(html, "img.site-cover-media-calm", "src");

  var novels = urls.map(function (url, i) {
    return {
      title: titles[i] || "",
      url: absoluteUrl(url),
      cover: covers[i] ? absoluteUrl(covers[i]) : null,
    };
  }).filter(function (n) { return n.title && n.url; });

  return JSON.stringify({
    novels: novels,
    hasNextPage: novels.length > 0,
  });
}

globalThis.source = {

  popularNovels: function (page) {
    return parseListing(Http.get(BASE_URL + "novels/popular?page=" + page, "{}"));
  },

  latestNovels: function (page) {
    return parseListing(Http.get(BASE_URL + "novels/latest?page=" + page, "{}"));
  },

  // The app's filter sheet (genre/sort above) always calls searchNovels, never popularNovels/
  // latestNovels directly - so genre/sort browsing (no keyword) is handled here too.
  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");

    if (!query && filters.genre) {
      var url = BASE_URL + "genre/" + encodeURIComponent(filters.genre) + "?page=" + page;
      if (filters.sort) url += "&sort=" + filters.sort;
      return parseListing(Http.get(url, "{}"));
    }

    if (!query && filters.sort) {
      var listPath = filters.sort === "POPULAR" ? "novels/popular" : "novels/latest";
      return parseListing(Http.get(BASE_URL + listPath + "?page=" + page, "{}"));
    }

    var searchUrl = BASE_URL + "novels/search?keyword=" + encodeURIComponent(query) + "&page=" + page;
    return parseListing(Http.get(searchUrl, "{}"));
  },

  novelDetails: function (url) {
    var html = Http.get(url, "{}");
    var statusMatch = html.match(/id="([a-z]+)-title-_R_/);

    return JSON.stringify({
      title: selectText(html, "h1"),
      cover: selectAttr(html, "img.novel-cover-image", "src") || null,
      author: selectText(html, "a[href^='/author/']"),
      description: selectAllText(html, ".site-reading-copy p").join("\n\n"),
      genres: selectAllText(html, "a[href^='/genre/'][class*=underline-offset-4]"),
      status: statusMatch ? statusMatch[1] : "",
    });
  },

  // Full list in one call - /api-web/novels/{id}/chapters?sort=asc isn't paginated in practice
  // (confirmed live: 3134/3134 chapters for Shadow Slave in a single response).
  chapterList: function (novelUrl) {
    var novelId = novelIdFromUrl(novelUrl);
    var json = Http.get(BASE_URL + "api-web/novels/" + novelId + "/chapters?sort=asc", JSON_HEADERS);

    var data;
    try { data = JSON.parse(json); } catch (e) { data = null; }
    var items = (data && data.items) || [];

    return JSON.stringify(items.map(function (item, i) {
      return {
        name: item.chapter_name || ("Chapter " + (i + 1)),
        url: BASE_URL + "chapter/" + novelId + "/" + item.chapter_id,
        chapterNumber: i + 1,
      };
    }));
  },

  chapterContent: function (chapterUrl) {
    var parsed = parseChapterUrl(chapterUrl);
    if (!parsed.novelId || !parsed.chapterId) return "";

    var apiUrl = BASE_URL + "api-web/novels/" + parsed.novelId + "/chapters/" + parsed.chapterId;
    var headers = JSON.stringify({ "Accept": "application/json", "x-track-reading-progress": "false" });
    var json = Http.get(apiUrl, headers);

    var data;
    try { data = JSON.parse(json); } catch (e) { return ""; }
    var info = data && data.item && data.item.chapterInfo;
    return (info && info.chapter_content) || "";
  },

};
