// Webnovel (webnovel.com) — Qidian International's official licensed-translation platform.
// Written independently against Kizuna's NovelSource contract (see docs/novel-sources/README.md).
//
// REWRITE NOTE #2: the first rewrite of this file targeted `www.webnovel.com`'s desktop HTML,
// verified working via curl with a desktop User-Agent. On-device it still came back empty — turns
// out webnovel.com 302-redirects any request whose User-Agent looks like Android straight to
// `m.webnovel.com`, whose markup is completely different (a Next.js page: no `.g_thumb` ranking
// anchors, no `.volume-item` catalog list — chapter data instead lives inside a `__NEXT_DATA__`
// JSON blob). Confirmed on-device via Logcat (OkHttp request/response logging) showing the 302,
// then re-verified every endpoint below directly against `m.webnovel.com` with an Android UA
// before rewriting again. Base URL is `m.webnovel.com` throughout now, so there's no redirect to
// follow at all.
//
// - popularNovels/latestNovels scrape `/ranking/novel/all_time/{popular,update}_rank?pageIndex=N`.
//   On m., real book entries are `a[data-report-uiname="bookcover"]` (title attr, href, and
//   data-report-did=bookId) - a plain `[data-report-did]` selector also catches the page's own
//   tab-nav links ("Novels"/"Fan-fic", with tiny ids 1/4), which is not obvious without the real
//   HTML in front of you. No cover image is in the server HTML at all (client lazy-loaded), so the
//   cover is constructed from the bookId via `coverUrl()` instead of scraped. Fixed top-N ranking
//   lists, not a true infinite feed, so hasNextPage just reflects "did this page return anything".
// - searchNovels calls the site's own JSON API: `/go/pcm/search/result`. This (and the two
//   endpoints below) require a `_csrfToken` query param that must match a same-named cookie the
//   site sets on first visit — see `csrfToken()`, backed by the `Http.getCookie` bridge. Confirmed
//   identical response shape on m. as on www.
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
  version: "3.0.0",
  supportsLatest: true,
}));

var BASE_URL = "https://m.webnovel.com/";

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

function parseRankingListing(html) {
  // data-report-did also appears on the page's tab-nav links ("Novels"/"Fan-fic", ids 1/4) -
  // data-report-uiname="bookcover" is what actually distinguishes real book entries from those.
  var urls = selectAllAttr(html, 'a[data-report-uiname="bookcover"]', "href");
  var titles = selectAllAttr(html, 'a[data-report-uiname="bookcover"]', "title");
  var bookIds = selectAllAttr(html, 'a[data-report-uiname="bookcover"]', "data-report-did");

  var novels = urls.map(function (url, i) {
    return {
      title: titles[i] || "",
      url: absoluteUrl(url),
      cover: bookIds[i] ? coverUrl(bookIds[i]) : null,
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
    var html = Http.get(BASE_URL + "ranking/novel/all_time/popular_rank?pageIndex=" + page, "{}");
    return parseRankingListing(html);
  },

  latestNovels: function (page) {
    var html = Http.get(BASE_URL + "ranking/novel/all_time/update_rank?pageIndex=" + page, "{}");
    return parseRankingListing(html);
  },

  // No usable single-value genre/category filter identified on Webnovel's search API - the third
  // arg (filtersJson) is accepted for contract consistency but unused.
  searchNovels: function (query, page, filtersJson) {
    var url = BASE_URL + "go/pcm/search/result" +
      "?_csrfToken=" + csrfToken() +
      "&pageIndex=" + page +
      "&encryptType=3&_fsae=0" +
      "&keywords=" + encodeURIComponent(query);
    var json = Http.get(url, "{}");

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
