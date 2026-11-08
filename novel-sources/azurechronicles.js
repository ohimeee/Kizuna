// Azure Chronicles (azurechronicles.com) — translated web novels, coin-paywalled chapters that
// turn free on a per-novel release schedule.
//
// There is no LNReader plugin for this site, and it is a custom WordPress theme (azurechronicles-2.1,
// "ac-" class prefix) rather than Madara or another known one, so every selector below was
// taken straight from the live HTML. No Cloudflare challenge / anti-bot: plain OkHttp requests
// get the full pages.
//
// Layout of the site, as used here:
//   * Catalogue: /series/ (28 novels per page, /series/page/N/), server-rendered. The same page
//     reads its filters from the query string, which is what the site's own filter bar does
//     (it just re-requests via admin-ajax): `q`, `genre[]`, `type`, `status`, `sort`
//     (rating | views | oldest | title | chapters; empty = newest). Verified that q + filters
//     combine and that paging goes through /series/page/N/?... .
//   * /latest/ (and /latest/page/N/) is the recently-updated feed, cards are `.hc-card`.
//   * /popular-series/ is NOT used for Popular: it is a fixed top-18 with no paging, so Popular
//     here is the catalogue sorted by views instead.
//   * Novel page /novel/{slug}/ carries title, cover, genres, status and synopsis, but its chapter
//     list is only the first 100 rows (the rest is behind a nonce-protected admin-ajax pager). The
//     site's own public REST API gives the whole list, so chapterList() uses that:
//     /wp-json/azurechronicles/v2/novels/{id}/chapters?per_page=100&order=asc&page=N
//     where {id} is the WordPress post id (the novel page's <body class="... postid-{id}">).
//     That API is listed in the site's wp-json index, needs no auth, and has no chapter URLs, so
//     URLs are built as /novel/{slug}/chapter-{chapter_number}/ (checked against the hrefs on the
//     novel page itself). If the API ever fails, chapterList() falls back to the 100 rows on the
//     novel page rather than returning nothing.
//   * Chapter page /novel/{slug}/chapter-{n}/: free chapters render the body in
//     `#ac-chapter-prose`. Paywalled chapters (coin_price > 0, `is_free: false`) render only a
//     "This chapter is locked" gate with no preview text at all, and the REST endpoint answers
//     403, so chapterContent() returns "" for those - the reader shows its own "may be locked"
//     message - and chapterList() flags them with `locked: true`.
//
// Author: the site has no author field on any novel (the API's `author` is empty for all 372
// catalogue entries), so novelDetails() leaves it out rather than guessing; "Translator" is the
// scanlation team, not the author.

Register(JSON.stringify({
  id: "azurechronicles",
  name: "Azure Chronicles",
  lang: "en",
  baseUrl: "https://azurechronicles.com",
  version: "1.0.0",
  supportsLatest: true,
  iconUrl: "https://www.google.com/s2/favicons?sz=64&domain=azurechronicles.com",
  // Option values are the exact <option value>s of the filter bar on /series/. The site's genre
  // and tag filters are multi-select, the app's are single-select, so one genre at a time. The
  // ~150-entry Tag filter is left out.
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
        { label: "Ecchi", value: "ecchi" },
        { label: "Fantasy", value: "fantasy" },
        { label: "Gender Bender", value: "gender-bender" },
        { label: "Harem", value: "harem" },
        { label: "Historical", value: "historical" },
        { label: "Horror", value: "horror" },
        { label: "Josei", value: "josei" },
        { label: "Martial Arts", value: "martial-arts" },
        { label: "Mature", value: "mature" },
        { label: "Mecha", value: "mecha" },
        { label: "Mystery", value: "mystery" },
        { label: "Psychological", value: "psychological" },
        { label: "Romance", value: "romance" },
        { label: "School Life", value: "school-life" },
        { label: "Sci-fi", value: "sci-fi" },
        { label: "Seinen", value: "seinen" },
        { label: "Shoujo", value: "shoujo" },
        { label: "Shoujo Ai", value: "shoujo-ai" },
        { label: "Shounen", value: "shounen" },
        { label: "Shounen Ai", value: "shounen-ai" },
        { label: "Slice of Life", value: "slice-of-life" },
        { label: "Supernatural", value: "supernatural" },
        { label: "Tragedy", value: "tragedy" },
        { label: "Wuxia", value: "wuxia" },
        { label: "Xianxia", value: "xianxia" },
        { label: "Xuanhuan", value: "xuanhuan" },
        { label: "Yuri", value: "yuri" },
      ],
    },
    {
      id: "type",
      name: "Type",
      options: [
        { label: "Light Novel", value: "light-novel" },
        { label: "Novel", value: "novel" },
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
      // The site's default (no value) is "Newest", which is what "Any" gives.
      id: "sort",
      name: "Sort",
      options: [
        { label: "Rating", value: "rating" },
        { label: "Views", value: "views" },
        { label: "Oldest", value: "oldest" },
        { label: "Title A-Z", value: "title" },
        { label: "Chapters", value: "chapters" },
      ],
    },
  ],
}));

var BASE_URL = "https://azurechronicles.com";
var API_URL = BASE_URL + "/wp-json/azurechronicles/v2";

function absoluteUrl(url) {
  if (!url) return url;
  if (url.indexOf("http") === 0) return url;
  if (url.indexOf("//") === 0) return "https:" + url;
  return BASE_URL + (url.charAt(0) === "/" ? "" : "/") + url;
}

function hasNextPage(html) {
  // `link[rel=next]` in <head> is unreliable (it ignores the query string, so it is present even
  // on an empty search); the pager's own "Next" link is only rendered when there is one.
  return !!selectAttr(html, ".ac-latest-pagination a.next", "href");
}

// /series/ with optional query params. Page 1 has no /page/1/ segment.
function seriesUrl(page, params) {
  var path = BASE_URL + "/series/" + (page > 1 ? "page/" + page + "/" : "");
  var qs = [];
  Object.keys(params).forEach(function (key) {
    if (params[key]) qs.push(key + "=" + encodeURIComponent(params[key]));
  });
  return path + (qs.length ? "?" + qs.join("&") : "");
}

// Each catalogue card is an <article class="ac-grid2-card">. Parse them one by one instead of
// building parallel arrays, so a card with a missing cover can't shift every later cover.
function parseSeries(html) {
  var chunks = html.split('<article class="ac-grid2-card">').slice(1);
  var seen = {};
  var novels = [];
  chunks.forEach(function (chunk) {
    var url = selectAttr(chunk, "a.ac-grid2-title-link", "href") || selectAttr(chunk, "a.ac-grid2-cover-link", "href");
    var title = selectText(chunk, ".ac-grid2-title") || selectAttr(chunk, "a.ac-grid2-cover-link", "title");
    if (!url || !title || seen[url]) return;
    seen[url] = true;
    novels.push({
      title: title,
      url: absoluteUrl(url),
      cover: absoluteUrl(selectAttr(chunk, "img.ac-grid2-cover-img", "src")) || null,
    });
  });
  return JSON.stringify({ novels: novels, hasNextPage: novels.length > 0 && hasNextPage(html) });
}

// /latest/ cards are <div class="hc-card">; the cover is a CSS background-image, not an <img>.
function parseLatest(html) {
  var chunks = html.split('<div class="hc-card">').slice(1);
  var seen = {};
  var novels = [];
  chunks.forEach(function (chunk) {
    var url = selectAttr(chunk, "a.hc-card-cover", "href");
    var title = selectText(chunk, "h3.hc-card-title") || selectAttr(chunk, "a.hc-card-cover", "title");
    if (!url || !title || seen[url]) return;
    seen[url] = true;
    var style = selectAttr(chunk, "a.hc-card-cover", "style");
    var m = style.match(/url\(\s*['"]?([^'")]+)['"]?\s*\)/);
    novels.push({
      title: title,
      url: absoluteUrl(url),
      cover: m ? absoluteUrl(m[1]) : null,
    });
  });
  return JSON.stringify({ novels: novels, hasNextPage: novels.length > 0 && hasNextPage(html) });
}

function novelSlug(novelUrl) {
  var m = novelUrl.match(/\/novel\/([^\/?#]+)/);
  return m ? m[1] : "";
}

function novelDescription(html) {
  var paragraphs = selectAllText(html, "#synopsis-content p");
  var text = paragraphs.filter(function (p) { return p; }).join("\n\n").trim();
  // Fallback for a synopsis that isn't wrapped in <p> at all. The "Show more" toggle lives
  // outside #synopsis-content, so it can't leak in here.
  return text || selectText(html, "#synopsis-content");
}

// The "Details" card is a list of <span class="ac-md-key">Label</span><span class="ac-md-val">Value</span>
// rows (Type / Translator / Status / Origin / Release Year); read them as parallel arrays.
function detailValue(html, label) {
  var keys = selectAllText(html, ".ac-md-key");
  var vals = selectAllText(html, ".ac-md-val");
  for (var i = 0; i < keys.length; i++) {
    if (keys[i].toLowerCase() === label.toLowerCase()) return vals[i] || "";
  }
  return "";
}

// Chapter list from the REST API: every chapter, oldest first, with real release dates and the
// free/paid flag. Returns null if the API doesn't answer with the expected JSON.
function chaptersFromApi(novelId, slug) {
  var out = [];
  var totalPages = 1;
  for (var page = 1; page <= totalPages && page <= 200; page++) {
    var body = Http.get(API_URL + "/novels/" + novelId + "/chapters?per_page=100&order=asc&page=" + page, "{}");
    var data;
    try {
      data = JSON.parse(body);
    } catch (e) {
      return null;
    }
    if (!data || !data.success || !data.data || !data.data.items) return null;
    totalPages = (data.data.meta && data.data.meta.total_pages) || 1;
    data.data.items.forEach(function (c) { out.push(c); });
  }
  if (!out.length) return null;

  var chapters = out
    .filter(function (c) { return c.is_released !== false; })
    .map(function (c) {
      var number = parseFloat(c.chapter_number);
      var date = c.public_at ? Date.parse(c.public_at) : NaN;
      return {
        name: c.title || ("Chapter " + c.chapter_number),
        url: BASE_URL + "/novel/" + slug + "/chapter-" + c.chapter_number + "/",
        chapterNumber: isNaN(number) ? undefined : number,
        dateUpload: isNaN(date) ? undefined : date,
        locked: !c.is_free && c.has_access === false,
      };
    });
  // order=asc already sorts by chapter number; this only guards against it being ignored.
  chapters.sort(function (a, b) {
    if (a.chapterNumber === undefined || b.chapterNumber === undefined) return 0;
    return a.chapterNumber - b.chapterNumber;
  });
  return chapters;
}

// Fallback: the 100 chapter rows rendered into the novel page (newest first there).
function chaptersFromPage(html) {
  var urls = selectAllAttr(html, "#chapters a.chapter-el", "href");
  var labels = selectAllAttr(html, "#chapters a.chapter-el", "data-ac-chapter-label");
  var locks = selectAllAttr(html, "#chapters a.chapter-el", "data-ac-locked");
  var chapters = urls.map(function (url, i) {
    var m = url.match(/chapter-([\d.]+)\/?$/);
    return {
      name: labels[i] || ("Chapter " + (m ? m[1] : i + 1)),
      url: absoluteUrl(url),
      chapterNumber: m ? parseFloat(m[1]) : undefined,
      locked: locks[i] === "1",
    };
  });
  chapters.sort(function (a, b) { return (a.chapterNumber || 0) - (b.chapterNumber || 0); });
  return chapters;
}

// Chapters often open and close with a spacer `<p>&nbsp;</p>`; drop those at the edges only (blank
// paragraphs in the middle are the translator's scene spacing and stay).
function trimBlankParagraphs(html) {
  return html
    .replace(/^(<div[^>]*>)(?:\s*<p>(?:\s|&nbsp;)*<\/p>)+/i, "$1")
    .replace(/(?:\s*<p>(?:\s|&nbsp;)*<\/p>)+(\s*<\/div>\s*)$/i, "$1");
}

globalThis.source = {

  // "Popular" = the full catalogue by views. (/popular-series/ is a fixed top-18 without paging.)
  popularNovels: function (page) {
    return parseSeries(Http.get(seriesUrl(page, { sort: "views" }), "{}"));
  },

  latestNovels: function (page) {
    var url = BASE_URL + "/latest/" + (page > 1 ? "page/" + page + "/" : "");
    return parseLatest(Http.get(url, "{}"));
  },

  // The app routes the filter sheet through searchNovels too, so filter-only browsing (empty
  // query) works: /series/ takes q and every filter together.
  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");
    var params = { q: (query || "").trim() };
    // `genre[]` is the site's own multi-select param; one value from the single-select filter.
    if (filters.genre) params["genre%5B%5D"] = filters.genre;
    if (filters.type) params.type = filters.type;
    if (filters.status) params.status = filters.status;
    if (filters.sort) params.sort = filters.sort;
    // seriesUrl() encodes values only, so the pre-encoded `genre%5B%5D` key passes through intact.
    return parseSeries(Http.get(seriesUrl(page, params), "{}"));
  },

  novelDetails: function (url) {
    var html = Http.get(url, "{}");
    var cover = selectAttr(html, "#ac-cover-img img", "src") || selectAttr(html, 'meta[property="og:image"]', "content");
    return JSON.stringify({
      title: selectText(html, "#ac-novel-title"),
      cover: cover ? absoluteUrl(cover) : null,
      description: novelDescription(html),
      genres: selectAllText(html, "#ac-genres-row a span"),
      status: detailValue(html, "Status"),
    });
  },

  chapterList: function (novelUrl) {
    var slug = novelSlug(novelUrl);
    var html = Http.get(novelUrl, "{}");
    var idMatch = (selectAttr(html, "body", "class") || "").match(/postid-(\d+)/);
    if (idMatch) {
      var fromApi = chaptersFromApi(idMatch[1], slug);
      if (fromApi) return JSON.stringify(fromApi);
    }
    return JSON.stringify(chaptersFromPage(html));
  },

  // Free chapters: the prose block. Locked chapters have no `#ac-chapter-prose` at all (just the
  // coin gate), so this is "" for them, on purpose.
  chapterContent: function (chapterUrl) {
    var html = Http.get(chapterUrl, "{}");
    return trimBlankParagraphs(selectHtml(html, "#ac-chapter-prose"));
  },

};
