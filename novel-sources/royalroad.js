// Royal Road (royalroad.com) — original web fiction, free to read, not translated/scanlated
// content. Written independently against Kizuna's NovelSource contract (see
// docs/novel-sources/README.md) — not a copy of any LNReader plugin.
//
// Selectors verified against real page source (fetched via curl with a browser UA, since a
// plain WebFetch got Cloudflare-blocked) after the first on-device test came back empty:
// - Listing items are `.fiction-list-item > figure > a > img`, not a direct `<a>` child of the
//   item as first assumed — and there's a second, duplicate-href `<a>` around the title text
//   (inside `h2.fiction-title`) that would've thrown off the parallel title/url/cover arrays if
//   selected too, so the figure/img path is deliberately the more specific one.
// - The novel detail page has no `img.thumbnail` — the cover comes from a `window.fictionCover =
//   "..."` JS variable instead, extracted the same way chapterList already reads
//   `window.chapters`.
// Everything else (title/author/description/genres/status/chapter-list selectors) matched the
// original guess exactly.

Register(JSON.stringify({
  id: "royalroad",
  name: "Royal Road",
  lang: "en",
  baseUrl: "https://www.royalroad.com",
  version: "1.0.0",
  supportsLatest: true,
}));

function parseListing(html) {
  var urls = selectAllAttr(html, ".fiction-list-item figure a", "href");
  var titles = selectAllAttr(html, ".fiction-list-item figure img", "alt");
  var covers = selectAllAttr(html, ".fiction-list-item figure img", "src");

  var novels = urls.map(function (url, i) {
    return {
      title: titles[i] || "",
      url: url.indexOf("http") === 0 ? url : "https://www.royalroad.com" + url,
      cover: covers[i] || null,
    };
  }).filter(function (n) { return n.title && n.url; });

  return JSON.stringify({
    novels: novels,
    hasNextPage: novels.length > 0,
  });
}

function parseChapterList(html) {
  var match = html.match(/window\.chapters\s*=\s*(\[[\s\S]*?\]);/);
  if (!match) return JSON.stringify([]);

  var chapters;
  try {
    chapters = JSON.parse(match[1]);
  } catch (e) {
    return JSON.stringify([]);
  }

  return JSON.stringify(chapters.map(function (ch, i) {
    var url = ch.url || "";
    return {
      name: ch.title || ("Chapter " + (i + 1)),
      url: url.indexOf("http") === 0 ? url : "https://www.royalroad.com" + url,
      chapterNumber: (ch.order != null ? ch.order : i) + 1,
      dateUpload: ch.date ? Date.parse(ch.date) || 0 : 0,
    };
  }));
}

globalThis.source = {

  popularNovels: function (page) {
    var html = Http.get("https://www.royalroad.com/fictions/search?page=" + page, "{}");
    return parseListing(html);
  },

  latestNovels: function (page) {
    var html = Http.get(
      "https://www.royalroad.com/fictions/search?page=" + page + "&orderBy=last_update",
      "{}",
    );
    return parseListing(html);
  },

  searchNovels: function (query, page) {
    var html = Http.get(
      "https://www.royalroad.com/fictions/search?title=" + encodeURIComponent(query) + "&page=" + page,
      "{}",
    );
    return parseListing(html);
  },

  novelDetails: function (url) {
    var html = Http.get(url, "{}");
    var statuses = selectAllText(html, ".label-sm");
    var rawStatus = statuses.length > 1 ? statuses[1] : (statuses[0] || "");
    var coverMatch = html.match(/window\.fictionCover\s*=\s*"([^"]*)"/);

    return JSON.stringify({
      title: selectText(html, "h1"),
      cover: coverMatch ? coverMatch[1] : null,
      author: selectText(html, "a[href^='/profile/']"),
      description: selectText(html, ".description"),
      genres: selectAllText(html, ".tags a"),
      status: rawStatus,
    });
  },

  chapterList: function (novelUrl) {
    var html = Http.get(novelUrl, "{}");
    return parseChapterList(html);
  },

  chapterContent: function (chapterUrl) {
    var html = Http.get(chapterUrl, "{}");
    return selectHtml(html, ".chapter-content");
  },

};
