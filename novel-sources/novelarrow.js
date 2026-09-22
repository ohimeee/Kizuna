// NovelPing (novelping.com) — formerly NovelArrow (novelarrow.com), which now 301s here.
//
// The rebrand was a full rewrite, not a reskin: the old site was a Next.js app whose listings
// were scraped out of server-rendered HTML at /genre/{slug}. NovelPing is a jQuery/Bootstrap app
// with a different URL scheme, different markup, and a small AJAX API. Every selector below was
// re-derived from the live site rather than carried over.
//
// `id` deliberately stays "novelarrow" even though the site is now NovelPing — the id is the
// stable key the library and download manager store per entry, so changing it would orphan every
// existing NovelArrow entry into a second, unrelated source.
//
// Layout of the site, as used here:
//   * Site-wide listings are plain pages: /sort/hot, /sort/popular, /sort/updates, /sort/complete,
//     each paginated with ?page=N at 20 novels per page.
//   * Genre listings are /novelping-genres/{slug}, with sort as a ?sort= query param and status as
//     an extra path segment (/ongoing, /completed). The site's own filter <select>s are pure
//     navigation - each <option value> is the destination URL (see /js/list-filter.js) - so the
//     filter values below are exactly what the site itself links to, not guesses.
//   * A jQuery AJAX API lives at /ajax (base declared inline on every page as `ajaxUrl`):
//       - /ajax/chapter-archive?novelId={slug}  full chapter list, every chapter in ONE response
//       - /ajax/search-novel?keyword={q}        keyword search
//     Both return HTML fragments, not JSON.
//
// The chapter archive is the important one: the /book/{slug} page itself only renders a 30-chapter
// preview (and ignores ?page=), so scraping the book page would silently cap every novel at 30
// chapters. The archive endpoint returns the lot - verified at 3195 chapters for shadow-slave in a
// single request - and needs no auth or crawler token despite the site wrapping its own calls in a
// `withCrawlerAccess()` helper.

Register(JSON.stringify({
  id: "novelarrow",
  name: "NovelPing",
  lang: "en",
  baseUrl: "https://novelping.com",
  version: "2.0.0",
  supportsLatest: true,
  // The site's favicon is an .ico, which doesn't render in the sources list; its apple-touch-icon
  // is a real PNG at the same origin.
  iconUrl: "https://novelping.com/apple-touch-icon.png",
  // Slugs taken from the site-wide genre nav (/novelping-genres/{slug} links on the homepage).
  filters: [
    {
      id: "genre",
      name: "Genre",
      options: [
        { label: "Action", value: "action" },
        { label: "Adult", value: "adult" },
        { label: "Adventure", value: "adventure" },
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
        { label: "LitRPG", value: "litrpg" },
        { label: "Magic", value: "magic" },
        { label: "Magical Realism", value: "magical-realism" },
        { label: "Martial Arts", value: "martial-arts" },
        { label: "Mature", value: "mature" },
        { label: "Mecha", value: "mecha" },
        { label: "Military", value: "military" },
        { label: "Modern Life", value: "modern-life" },
        { label: "Mystery", value: "mystery" },
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
      // Values are the site's own ?sort= codes, including its "LASTEST" spelling - that is the
      // literal parameter the site sends, so it is not a typo to fix here.
      id: "sort",
      name: "Sort by",
      options: [
        { label: "Popular this week", value: "POPULAR_WEEK" },
        { label: "Last updated", value: "LASTEST" },
        { label: "Recently added", value: "NEW" },
        { label: "Top (most viewed)", value: "ALL_TIME" },
        { label: "Top rated", value: "RATING" },
        { label: "Most chapters", value: "CHAPTERS" },
      ],
    },
    {
      // Status is a path segment on genre listings, not a query param.
      id: "status",
      name: "Status",
      options: [
        { label: "Ongoing only", value: "ongoing" },
        { label: "Completed only", value: "completed" },
      ],
    },
  ],
}));

var BASE = "https://novelping.com";
var AJAX = BASE + "/ajax";

// Listing cards are `h3.novel-title > a` with an absolute href, and the matching cover is the
// `img.cover` in the same row. Scoped away from `.list-side` (the "hot novels" sidebar, which is
// rendered from the same partial) so the title and cover arrays stay index-aligned - they are
// zipped positionally below, so any extra match in one and not the other would shift every cover
// onto the wrong novel.
var LIST_SCOPE = ".list-novel:not(.list-side) ";
var TITLE_SEL = LIST_SCOPE + "h3.novel-title a";
var COVER_SEL = LIST_SCOPE + "img.cover";

function parseListing(html) {
  var urls = selectAllAttr(html, TITLE_SEL, "href");
  var titles = selectAllText(html, TITLE_SEL);
  var covers = selectAllAttr(html, COVER_SEL, "src");

  var novels = [];
  for (var i = 0; i < urls.length; i++) {
    var title = (titles[i] || "").replace(/\s+/g, " ").trim();
    if (!urls[i] || !title) continue;
    novels.push({ title: title, url: urls[i], cover: covers[i] || "" });
  }

  // 20 per page site-wide; a short page means there is nothing after it. Asking for a page past
  // the end returns an empty list rather than clamping to the last page, so this terminates.
  return JSON.stringify({ novels: novels, hasNextPage: novels.length >= 20 });
}

function listingPage(path, page) {
  var url = BASE + path + (page > 1 ? "?page=" + page : "");
  return parseListing(Http.get(url, "{}"));
}

globalThis.source = {
  popularNovels: function (page) {
    return listingPage("/sort/hot", page);
  },

  latestNovels: function (page) {
    return listingPage("/sort/updates", page);
  },

  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");
    var q = (query || "").trim();

    if (q.length > 0) {
      // Keyword search is the AJAX endpoint the site's own search box uses. It returns a flat
      // list of `a.list-group-item` links with no covers and no paging, so one page only.
      var html = Http.get(AJAX + "/search-novel?keyword=" + encodeURIComponent(q), "{}");
      var urls = selectAllAttr(html, "a.list-group-item", "href");
      var titles = selectAllText(html, "a.list-group-item");
      var results = [];
      for (var i = 0; i < urls.length; i++) {
        var t = (titles[i] || "").replace(/\s+/g, " ").trim();
        if (urls[i] && t) results.push({ title: t, url: urls[i], cover: "" });
      }
      return JSON.stringify({ novels: results, hasNextPage: false });
    }

    // No keyword: browse by filter. Genre is a path, status an extra path segment, sort a query
    // param - mirroring exactly what the site's own filter dropdowns navigate to.
    var path = filters.genre
      ? "/novelping-genres/" + filters.genre
      : "/sort/hot";

    if (filters.genre && filters.status) {
      path += "/" + filters.status;
    }

    var params = [];
    // "Popular this week" is the site's default and has no ?sort= value of its own.
    if (filters.sort && filters.sort !== "POPULAR_WEEK") {
      params.push("sort=" + encodeURIComponent(filters.sort));
    }
    if (page > 1) params.push("page=" + page);

    var url = BASE + path + (params.length ? "?" + params.join("&") : "");
    return parseListing(Http.get(url, "{}"));
  },

  novelDetails: function (url) {
    var html = Http.get(url, "{}");

    var genres = selectAllText(html, "a.meta-chip");
    var cleanGenres = [];
    for (var i = 0; i < genres.length; i++) {
      var g = (genres[i] || "").replace(/\s+/g, " ").trim();
      if (g) cleanGenres.push(g);
    }

    // The description block is collapsed behind a "show more" toggle, but the full text is in the
    // markup either way - `.desc-text` is the inner node, without the toggle button's own label.
    var description = selectText(html, ".desc-text");
    if (!description) description = selectText(html, "#novel-description-content");

    return JSON.stringify({
      title: (selectText(html, "h1") || "").replace(/\s+/g, " ").trim(),
      cover: selectAttr(html, "img.cover", "src"),
      author: (selectText(html, "ul.info-meta li a[href*=/author/]") || "").replace(/\s+/g, " ").trim(),
      description: (description || "").replace(/[ \t]+/g, " ").trim(),
      genres: cleanGenres,
      status: (selectText(html, "ul.info-meta li:contains(Status) a") || "").replace(/\s+/g, " ").trim(),
    });
  },

  chapterList: function (novelUrl) {
    // /book/{slug} -> {slug}. The archive endpoint keys off the slug, which is also what the page
    // exposes as data-novel-id.
    var slug = novelUrl.replace(/[?#].*$/, "").replace(/\/+$/, "").split("/").pop();
    var html = Http.get(AJAX + "/chapter-archive?novelId=" + encodeURIComponent(slug), "{}");

    var urls = selectAllAttr(html, "li.chapter-list-item a", "href");
    var titles = selectAllText(html, "li.chapter-list-item a span.chapter-title");

    var chapters = [];
    for (var i = 0; i < urls.length; i++) {
      if (!urls[i]) continue;
      var name = (titles[i] || "").replace(/\s+/g, " ").trim();
      if (!name) name = "Chapter " + (i + 1);

      // Slugs are "chapter-{n}-{title-words}", so the number is reliable when present; fall back
      // to position for the occasional prologue/extra that isn't numbered.
      var num = i + 1;
      var m = urls[i].match(/\/chapter-(\d+)/);
      if (m) num = parseInt(m[1], 10);

      chapters.push({ name: name, url: urls[i], chapterNumber: num });
    }

    // Natural reading order, chapter 1 first - JsNovelSource reverses this internally to match
    // Mihon's newest-first sourceOrder convention.
    return JSON.stringify(chapters);
  },

  chapterContent: function (chapterUrl) {
    var html = Http.get(chapterUrl, "{}");
    return selectHtml(html, "#chr-content");
  },
};
