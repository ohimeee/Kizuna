// NovelFire (novelfire.net) — aggregator/scanlation-style web novel reading site.
// Written independently against Kizuna's NovelSource contract (see docs/novel-sources/README.md).
//
// Selectors verified against real page source (curl with a browser UA, plain requests worked
// fine — no Cloudflare/bot-challenge encountered on this site, unlike Webnovel):
// - Listing/search items are `.novel-item` (`a[title]`/`a[href]` for title/url,
//   `img.lazy[data-src]` for cover — covers are lazy-loaded, `src` is just a placeholder gif).
//   Same markup on both the genre-listing pages and `/search?keyword=`.
// - Detail page: `h1.novel-title`, `.author a`, `.fixed-img .cover img[src]` (not lazy here),
//   `.summary` for description, `.categories ul li a` for genres. Status has no fixed class name
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
  version: "2.0.0",
  supportsLatest: true,
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
        { label: "Mystery", value: "mystery" },
        { label: "Psychological", value: "psychological" },
        { label: "Realistic Fiction", value: "realistic-fiction" },
        { label: "Reincarnation", value: "reincarnation" },
        { label: "Romance", value: "romance" },
        { label: "School Life", value: "school-life" },
        { label: "Sci-fi", value: "sci-fi" },
        { label: "Seinen", value: "seinen" },
        { label: "Shoujo", value: "shoujo" },
        { label: "Shounen", value: "shounen" },
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
      ],
    },
    {
      id: "sort",
      name: "Sort Results By",
      options: [
        { label: "Popular", value: "popular" },
        { label: "New", value: "new" },
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
  ],
}));

var BASE_URL = "https://novelfire.net/";

function absoluteUrl(url) {
  if (!url) return url;
  if (url.indexOf("http") === 0) return url;
  return BASE_URL.replace(/\/$/, "") + url;
}

function parseListing(html) {
  var urls = selectAllAttr(html, ".novel-item a", "href");
  var titles = selectAllAttr(html, ".novel-item a", "title");
  var covers = selectAllAttr(html, ".novel-item img.lazy", "data-src");

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
    var html = Http.get(BASE_URL + "genre-all/sort-popular/status-all/all-novel?page=" + page, "{}");
    return parseListing(html);
  },

  latestNovels: function (page) {
    var html = Http.get(BASE_URL + "latest-release-novels?page=" + page, "{}");
    return parseListing(html);
  },

  // The app's filter sheet (genre/sort/status above) always calls searchNovels, never
  // popularNovels/latestNovels directly - so genre/sort/status browsing (no keyword) is handled
  // here too, via the same genre-listing URL popularNovels uses.
  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");

    if (!query && (filters.genre || filters.sort || filters.status)) {
      var genre = filters.genre || "all";
      var sort = filters.sort || "popular";
      var status = filters.status || "all";
      var url = BASE_URL + "genre-" + genre + "/sort-" + sort + "/status-" + status + "/all-novel?page=" + page;
      return parseListing(Http.get(url, "{}"));
    }

    var html = Http.get(BASE_URL + "search?keyword=" + encodeURIComponent(query) + "&page=" + page, "{}");
    return parseListing(html);
  },

  novelDetails: function (url) {
    var html = Http.get(url, "{}");

    return JSON.stringify({
      title: selectText(html, "h1.novel-title"),
      cover: selectAttr(html, ".fixed-img .cover img", "src") || null,
      author: selectText(html, ".author a"),
      description: selectText(html, ".summary"),
      genres: selectAllText(html, ".categories ul li a"),
      status: selectText(html, ".header-stats span:last-child strong"),
    });
  },

  chapterList: function (novelUrl) {
    // The chapters page paginates at 100/page (confirmed live against a 3000+ chapter novel) -
    // a single fetch silently truncated long novels to their first 100 chapters. Loop until a
    // page comes back with no chapter links.
    var baseChaptersUrl = novelUrl.replace(/\/?$/, "") + "/chapters";
    var allUrls = [];
    var allTitles = [];
    var page = 1;

    while (true) {
      var html = Http.get(baseChaptersUrl + "?page=" + page, "{}");
      var urls = selectAllAttr(html, ".chapter-list li a", "href");
      if (urls.length === 0) break;

      var titles = selectAllAttr(html, ".chapter-list li a", "title");
      allUrls = allUrls.concat(urls);
      allTitles = allTitles.concat(titles);
      page += 1;
    }

    return JSON.stringify(allUrls.map(function (url, i) {
      return {
        name: allTitles[i] || ("Chapter " + (i + 1)),
        url: absoluteUrl(url),
        chapterNumber: i + 1,
      };
    }));
  },

  chapterContent: function (chapterUrl) {
    var html = Http.get(chapterUrl, "{}");
    return selectHtml(html, "#content");
  },

};
