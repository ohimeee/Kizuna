// Webnovel (webnovel.com) — Qidian International's official licensed-translation platform.
// Written independently against Kizuna's NovelSource contract (see docs/novel-sources/README.md).
//
// REWRITE NOTE #3: popularNovels/latestNovels previously scraped m.webnovel.com's ranking pages
// (REWRITE NOTE #2 below), which turned out to be a fixed, non-personalized top-20 list that
// completely ignores pageIndex (page 1 and page 300 come back byte-identical, confirmed live) -
// not a real feed at all, just always the same 20 books. Fixed here by doing what LNReader's own
// (real, working) Webnovel plugin does: fetch `www.webnovel.com/stories/novel` directly with a
// spoofed *desktop* User-Agent via the `headers` param on `Http.get`. `www.webnovel.com` only
// redirects to the broken m. subdomain when it detects an Android-looking UA - Kizuna's shared
// OkHttp client's UserAgentInterceptor only fills in its own default UA when a request doesn't
// already specify one (confirmed by reading UserAgentInterceptor.kt), so a plugin-supplied
// User-Agent header is honored as-is. `www.webnovel.com/stories/novel` is real, server-rendered,
// genuinely paginated HTML (confirmed live: page 1 vs page 2 return meaningfully different books),
// and its genre-browse URLs (`/stories/{genre-slug}`) are real working filters too - both were
// missed entirely in the earlier m.-subdomain-only investigation.
//
// - popularNovels/latestNovels scrape `www.webnovel.com/stories/{genre-or-"novel"}?orderBy=N`
//   (orderBy=1 popular, orderBy=5 time-updated - same codes LNReader's plugin uses). Listing items
//   are `.g_thumb` anchors (title attr, href) with `.g_thumb > img[data-original]` for cover.
// - searchNovels calls the site's own JSON API: `/go/pcm/search/result`. This (and the two
//   endpoints below) require a `_csrfToken` query param that must match a same-named cookie the
//   site sets on first visit — see `csrfToken()`, backed by the `Http.getCookie` bridge. Confirmed
//   identical response shape on m. as on www. Left on m.webnovel.com since it already works there
//   with the app's normal (Android) UA and isn't affected by the redirect issue above.
// - novelDetails and the first-chapter-content fetch both go through
//   `/go/pcm/chapter/getContent?bookId=X&chapterId=0`, which is what the real site calls to hydrate
//   a book page — it returns `bookInfo` (title/author/tags/etc) *and* the first chapter's body in
//   one response. There's no reliable "completed/ongoing" field in that payload, so `status` is
//   left unset rather than guessed.
// - chapterList fetches `/book/{bookId}/catalog` and parses the `__NEXT_DATA__` script tag's
//   `props.initialState.entities.chapter` map (keyed by chapterId, not an array). Chapter URLs are
//   synthesized as `/book/{bookId}/{chapterId}` — confirmed live that webnovel.com's routing is
//   purely ID-based and accepts that bare form directly (200, no slug needed), so these are real,
//   openable URLs, not just internal identifiers.
// - chapterContent calls `/go/pcm/chapter/getContent` again with the real chapterId parsed out of
//   the chapter URL.
//
// ALSO IMPORTANT: Webnovel paywalls most chapters after the first several free ones per novel.
// This plugin does not attempt to bypass that — chapterContent() on a locked chapter will just
// return whatever preview/teaser text (or emptiness) the API actually serves while logged out.
// chapterList() does not filter out locked chapters; they're listed like any other, since a reader
// should be able to see a novel's full chapter count even if some entries can't be opened.

Register(JSON.stringify({
  id: "webnovel",
  name: "Webnovel",
  lang: "en",
  baseUrl: "https://m.webnovel.com",
  version: "4.1.0",
  supportsLatest: true,
  // Genre slugs/labels and sort codes taken from LNReader's real, working Webnovel plugin. Genre:
  // the "male" bucket (www.webnovel.com splits genres by a gender axis LNReader exposes as two
  // separate filters; collapsed into one list here since Kizuna's filter contract is single-select
  // only). Sort: orderBy codes used by /stories/{genre}?orderBy=N.
  filters: [
    {
      id: "genre",
      name: "Genre",
      options: [
        { label: "Action", value: "novel-action-male" },
        { label: "Animation, Comics, Games", value: "novel-acg-male" },
        { label: "Eastern", value: "novel-eastern-male" },
        { label: "Fantasy", value: "novel-fantasy-male" },
        { label: "Games", value: "novel-games-male" },
        { label: "History", value: "novel-history-male" },
        { label: "Horror", value: "novel-horror-male" },
        { label: "Realistic", value: "novel-realistic-male" },
        { label: "Sci-fi", value: "novel-scifi-male" },
        { label: "Sports", value: "novel-sports-male" },
        { label: "Urban", value: "novel-urban-male" },
        { label: "War", value: "novel-war-male" },
        { label: "LGBT+", value: "novel-lgbt-female" },
        { label: "Teen", value: "novel-teen-female" },
      ],
    },
    {
      id: "sort",
      name: "Sort Results By",
      options: [
        { label: "Popular", value: "1" },
        { label: "Recommended", value: "2" },
        { label: "Most Collections", value: "3" },
        { label: "Rating", value: "4" },
        { label: "Time Updated", value: "5" },
      ],
    },
  ],
}));

var BASE_URL = "https://m.webnovel.com/";
var WWW_BASE_URL = "https://www.webnovel.com/";

// www.webnovel.com redirects any Android-looking User-Agent to the m. subdomain (whose ranking
// pages don't actually paginate - see file header). Spoofing a desktop UA for requests that need
// to stay on www. avoids that redirect; confirmed Kizuna's shared OkHttp client only fills in its
// own UA when a request doesn't already carry one, so this override sticks.
var DESKTOP_HEADERS = JSON.stringify({
  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
});

function csrfToken() {
  var token = Http.getCookie(BASE_URL, "_csrfToken");
  if (!token) {
    Http.get(BASE_URL + "stories/novel", "{}");
    token = Http.getCookie(BASE_URL, "_csrfToken");
  }
  return token;
}

function absoluteUrl(url) {
  if (!url) return url;
  if (url.indexOf("http") === 0) return url;
  if (url.indexOf("//") === 0) return "https:" + url;
  return "https://m.webnovel.com" + url;
}

function coverUrl(bookId, coverUpdateTime) {
  return "https://book-pic.webnovel.com/bookcover/" + bookId +
    "?coverUpdateTime=" + (coverUpdateTime || Date.now()) + "&imageMogr2/thumbnail/600x";
}

// Both plain numeric book URLs ("/book/123") and slugged ones
// ("/book/some-title_123") end in the bookId - just take the trailing digits.
function bookIdFromUrl(url) {
  var path = url.split("?")[0].replace(/\/$/, "");
  var match = path.match(/(\d+)$/);
  return match ? match[1] : "";
}

// Chapter URLs are "/book/{bookId}/{chapterId}" (synthesized by chapterList - see file header).
function parseChapterUrl(url) {
  var path = url.split("?")[0].replace(/\/$/, "");
  var parts = path.split("/");
  var chapterMatch = (parts[parts.length - 1] || "").match(/(\d+)$/);
  var bookMatch = (parts[parts.length - 2] || "").match(/(\d+)$/);
  return {
    chapterId: chapterMatch ? chapterMatch[1] : "",
    bookId: bookMatch ? bookMatch[1] : "",
  };
}

// www.webnovel.com's /stories/{genre} listing pages - real server-rendered HTML, genuinely
// paginated (unlike the m. ranking pages this used to scrape). Matches LNReader's real plugin's
// selectors exactly.
function parseListing(html) {
  var urls = selectAllAttr(html, ".g_thumb", "href");
  var titles = selectAllAttr(html, ".g_thumb", "title");
  var covers = selectAllAttr(html, ".g_thumb > img", "data-original");

  var novels = urls.map(function (url, i) {
    var cover = covers[i] || null;
    if (cover && cover.indexOf("http") !== 0) cover = "https:" + cover;
    return {
      title: titles[i] || "",
      url: absoluteUrl(url),
      cover: cover,
    };
  }).filter(function (n) { return n.title && n.url; });

  return JSON.stringify({
    novels: novels,
    hasNextPage: novels.length > 0,
  });
}

function formatChapterBody(info) {
  var paragraphs = [];
  if (info.contents && info.contents.length) {
    paragraphs = info.contents.map(function (c) { return c.content || ""; });
  } else if (info.content) {
    paragraphs = [info.content];
  }

  var body = paragraphs
    .map(function (p) { return p.replace(/\r\n|\r|\n/g, "").trim(); })
    .filter(function (p) { return p; })
    .map(function (p) { return "<p>" + p + "</p>"; })
    .join("");

  return info.chapterName ? ("<h2>" + info.chapterName + "</h2>" + body) : body;
}

globalThis.source = {

  popularNovels: function (page) {
    var url = WWW_BASE_URL + "stories/novel?orderBy=1&pageIndex=" + page;
    return parseListing(Http.get(url, DESKTOP_HEADERS));
  },

  latestNovels: function (page) {
    var url = WWW_BASE_URL + "stories/novel?orderBy=5&pageIndex=" + page;
    return parseListing(Http.get(url, DESKTOP_HEADERS));
  },

  // IMPORTANT: the app's filter sheet (genre/sort above) always calls searchNovels, never
  // popularNovels/latestNovels directly - Mihon's source model only ever threads FilterList
  // through the search entry point, confirmed by reading BrowseSourceViewModel.Listing. So genre/
  // sort browsing (no keyword) is handled here too, via the same www. stories/{genre} listing
  // popularNovels uses, rather than the go/pcm keyword-search JSON API (which ignores a
  // categoryId param entirely and changes response shape when keywords is empty - confirmed live
  // that it isn't a usable filtered-browse path at all).
  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");

    if (!query && (filters.genre || filters.sort)) {
      var path = filters.genre || "novel";
      var orderBy = filters.sort || "1";
      var url = WWW_BASE_URL + "stories/" + path + "?orderBy=" + orderBy + "&pageIndex=" + page;
      return parseListing(Http.get(url, DESKTOP_HEADERS));
    }

    var searchUrl = BASE_URL + "go/pcm/search/result" +
      "?_csrfToken=" + csrfToken() +
      "&pageIndex=" + page +
      "&encryptType=3&_fsae=0" +
      "&keywords=" + encodeURIComponent(query);
    var json = Http.get(searchUrl, "{}");

    var data;
    try { data = JSON.parse(json); } catch (e) { data = null; }
    var bookInfo = data && data.data && data.data.bookInfo;
    var items = (bookInfo && bookInfo.bookItems) || [];

    var novels = items.map(function (b) {
      return {
        title: b.bookName || "",
        url: BASE_URL + "book/" + b.bookId,
        cover: coverUrl(b.bookId, b.coverUpdateTime),
      };
    }).filter(function (n) { return n.title && n.url; });

    return JSON.stringify({
      novels: novels,
      hasNextPage: !!bookInfo && !bookInfo.isLast && novels.length > 0,
    });
  },

  novelDetails: function (url) {
    var bookId = bookIdFromUrl(url);
    var apiUrl = BASE_URL + "go/pcm/chapter/getContent" +
      "?_csrfToken=" + csrfToken() +
      "&bookId=" + bookId + "&chapterId=0&encryptType=3&_fsae=0";
    var json = Http.get(apiUrl, "{}");

    var data;
    try { data = JSON.parse(json); } catch (e) { data = null; }
    var info = data && data.data && data.data.bookInfo;
    if (!info) return JSON.stringify({});

    var author = info.authorName || "";
    if (!author && info.authorItems) {
      author = info.authorItems.map(function (a) { return a.name; }).filter(Boolean).join(", ");
    }

    var genres = (info.tagInfos || []).map(function (t) { return t.tagName; }).filter(Boolean);

    return JSON.stringify({
      title: info.bookName || "",
      cover: coverUrl(bookId, info.coverUpdateTime),
      author: author,
      description: info.description || "",
      genres: genres,
    });
  },

  chapterList: function (novelUrl) {
    var bookId = bookIdFromUrl(novelUrl);
    var html = Http.get(BASE_URL + "book/" + bookId + "/catalog", "{}");

    var match = html.match(/<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
    if (!match) return JSON.stringify([]);

    var data;
    try { data = JSON.parse(match[1]); } catch (e) { return JSON.stringify([]); }

    var chapterMap = (
      data.props &&
      data.props.initialState &&
      data.props.initialState.entities &&
      data.props.initialState.entities.chapter
    ) || {};

    var chapters = Object.keys(chapterMap)
      .map(function (id) { return chapterMap[id]; })
      .filter(function (ch) { return ch.chapterIndex >= 1; })
      .sort(function (a, b) { return a.chapterIndex - b.chapterIndex; });

    return JSON.stringify(chapters.map(function (ch) {
      return {
        name: ch.chapterName || "",
        url: BASE_URL + "book/" + bookId + "/" + ch.chapterId,
        chapterNumber: ch.chapterIndex,
      };
    }));
  },

  chapterContent: function (chapterUrl) {
    var parsed = parseChapterUrl(chapterUrl);
    if (!parsed.bookId || !parsed.chapterId) return "";

    var apiUrl = BASE_URL + "go/pcm/chapter/getContent" +
      "?encryptType=3&_fsae=0" +
      "&_csrfToken=" + csrfToken() +
      "&bookId=" + parsed.bookId + "&chapterId=" + parsed.chapterId;
    var json = Http.get(apiUrl, "{}");

    var data;
    try { data = JSON.parse(json); } catch (e) { return ""; }
    var info = data && data.data && data.data.chapterInfo;
    if (!info) return "";

    return formatChapterBody(info);
  },

};
