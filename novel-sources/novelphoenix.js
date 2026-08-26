// Novel Phoenix (novelphoenix.com) — aggregator-style web novel reading site.
// Written against Kizuna's NovelSource contract (see docs/novel-sources/README.md).
//
// This site runs the same engine as NovelFire - LNReader ships both from a single shared
// template (plugins/multisrc/novelfire/sources.json lists novelfire.net and novelphoenix.com as
// the only two entries), and every selector below was re-verified against novelphoenix.com's own
// live HTML rather than assumed from that shared lineage:
// - Listing/search items are `.novel-item` (`a[title]`/`a[href]`, `img.lazy[data-src]` for the
//   lazy-loaded cover - plain `src` is a placeholder gif). Same markup on genre listings and
//   `/search?keyword=`.
// - Novel URLs are `/novel/{slug}` here, not NovelFire's `/book/{slug}`. Nothing hardcodes that,
//   since URLs always come from the listing's own hrefs.
// - Detail page: `h1.novel-title`, `.author a`, `.fixed-img .cover img[src]` (already absolute,
//   not lazy), `.categories ul li a` for genres, and the last `.header-stats` span's `<strong>`
//   for status (no dedicated status class - it's positional, `<strong class="ongoing">Ongoing</strong>`).
// - Description is `.summary .content`, NOT `.summary`: the container opens with an
//   `<h4 class="lined">Summary</h4>` heading, so selecting the whole block prefixes every
//   description with the literal word "Summary".
// - Chapter list lives on `/novel/{slug}/chapters` and paginates at 100/page. Verified that a
//   page past the end (`?page=999`) returns zero chapter links rather than clamping to the last
//   page, so the paging loop below terminates instead of spinning forever.
// - Chapter content is `#content` (confirmed ~96 clean `<p>` tags on a real chapter).

Register(JSON.stringify({
  id: "novelphoenix",
  name: "Novel Phoenix",
  lang: "en",
  baseUrl: "https://novelphoenix.com",
  version: "1.0.0",
  supportsLatest: true,
  iconUrl: "https://www.google.com/s2/favicons?sz=64&domain=novelphoenix.com",
  // Genre slugs scraped from the site's own nav on /genre-all/sort-popular/status-all/all-novel
  // (56 of them; labels tidied where the site's own casing is inconsistent, e.g. "Magical realism").
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
        { label: "Urban Life", value: "urban-life" },
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

var BASE_URL = "https://novelphoenix.com/";

function absoluteUrl(url) {
  if (!url) return url;
  if (url.indexOf("http") === 0) return url;
  return BASE_URL.replace(/\/$/, "") + url;
}

// The site prefixes its own "Chapter N - " onto titles that frequently already start with the
// same number, giving "Chapter 1 - 1: Nightmare Begins". Collapse that one duplicated number so
// it reads "Chapter 1: Nightmare Begins". Only fires when the two numbers actually match, so a
// genuine title like "Chapter 5 - The 3 Kings" is left alone.
function cleanChapterTitle(title) {
  if (!title) return title;
  var match = title.match(/^\s*Chapter\s+(\d+)\s*-\s*\1\s*[:.\-]?\s*([\s\S]*)$/i);
  if (!match) return title.trim();
  var rest = match[2].trim();
  return rest ? "Chapter " + match[1] + ": " + rest : "Chapter " + match[1];
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

  // The app's filter sheet always routes through searchNovels, never popularNovels/latestNovels,
  // so filter-only browsing (no keyword) is handled here via the same genre-listing URL.
  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");

    if (!query && (filters.genre || filters.sort || filters.status || filters.country)) {
      var genre = filters.genre || "all";
      var sort = filters.sort || "popular";
      var status = filters.status || "all";
      var country = filters.country || "all-novel";
      var url = BASE_URL + "genre-" + genre + "/sort-" + sort + "/status-" + status + "/" + country + "?page=" + page;
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
      description: selectText(html, ".summary .content"),
      genres: selectAllText(html, ".categories ul li a"),
      status: selectText(html, ".header-stats span:last-child strong"),
    });
  },

  chapterList: function (novelUrl) {
    // 100 chapters per page; loop until a page returns none. Confirmed live that an over-the-end
    // page yields zero links rather than repeating the last one, so this terminates.
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
        name: cleanChapterTitle(allTitles[i]) || ("Chapter " + (i + 1)),
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
