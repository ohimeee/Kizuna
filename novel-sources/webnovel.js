// Webnovel (webnovel.com) — Qidian International's official licensed-translation platform.
// Written independently against Kizuna's NovelSource contract (see docs/novel-sources/README.md).
//
// CONFIDENCE NOTE (read before debugging): this port is LOWER confidence than royalroad.js.
// Webnovel's real markup involves sibling-of-text-node traversal (e.g. "the element right after
// the one containing the text 'Author:'") that Kizuna's plain CSS-selector bridge can't express
// directly — the selectors below are best-effort approximations, especially `novelDetails`'
// author/status fields and the exact detail-page title class. Expect to need on-device debugging
// (Logcat) and adjustment against the real page source before this reliably works.
//
// ALSO IMPORTANT: Webnovel paywalls most chapters after the first several free ones per novel.
// This plugin does not attempt to bypass that — chapterContent() on a locked chapter will just
// return whatever preview/teaser text (or emptiness) the page actually serves, same as visiting
// the site directly while logged out.

Register(JSON.stringify({
  id: "webnovel",
  name: "Webnovel",
  lang: "en",
  baseUrl: "https://www.webnovel.com",
  version: "1.0.0",
  supportsLatest: true,
}));

function parseListing(html) {
  var urls = selectAllAttr(html, ".g_thumb", "href");
  var titles = selectAllAttr(html, ".g_thumb", "title");
  var covers = selectAllAttr(html, ".g_thumb > img", "data-original");
  if (covers.every(function (c) { return !c; })) {
    covers = selectAllAttr(html, ".g_thumb > img", "src");
  }

  var novels = urls.map(function (url, i) {
    var cover = covers[i] || null;
    if (cover && cover.indexOf("http") !== 0) cover = "https:" + cover;
    return {
      title: titles[i] || "",
      url: url.indexOf("http") === 0 ? url : "https://www.webnovel.com" + url,
      cover: cover,
    };
  }).filter(function (n) { return n.title && n.url; });

  return JSON.stringify({
    novels: novels,
    hasNextPage: novels.length > 0,
  });
}

globalThis.source = {

  popularNovels: function (page) {
    var html = Http.get("https://www.webnovel.com/stories/novel?orderBy=popular&pageIndex=" + page, "{}");
    return parseListing(html);
  },

  latestNovels: function (page) {
    var html = Http.get("https://www.webnovel.com/stories/novel?orderBy=update&pageIndex=" + page, "{}");
    return parseListing(html);
  },

  searchNovels: function (query, page) {
    var html = Http.get(
      "https://www.webnovel.com/search?keywords=" + encodeURIComponent(query) + "&pageIndex=" + page,
      "{}",
    );
    return parseListing(html);
  },

  novelDetails: function (url) {
    var html = Http.get(url, "{}");
    var cover = selectAttr(html, ".det-hd-detail img", "src") || selectAttr(html, ".g_thumb > img", "src");
    if (cover && cover.indexOf("http") !== 0) cover = "https:" + cover;

    return JSON.stringify({
      title: selectText(html, "h1"),
      cover: cover,
      author: selectText(html, ".det-info a"),
      description: selectText(html, ".j_synopsis"),
      genres: selectAllText(html, ".det-hd-tag"),
      status: selectText(html, ".det-hd-detail"),
    });
  },

  chapterList: function (novelUrl) {
    var catalogUrl = novelUrl.replace(/\/?$/, "") + "/catalog";
    var html = Http.get(catalogUrl, "{}");
    var names = selectAllText(html, ".volume-item li a");
    var urls = selectAllAttr(html, ".volume-item li a", "href");

    return JSON.stringify(names.map(function (name, i) {
      var url = urls[i] || "";
      return {
        name: name,
        url: url.indexOf("http") === 0 ? url : "https://www.webnovel.com" + url,
        chapterNumber: i + 1,
      };
    }));
  },

  chapterContent: function (chapterUrl) {
    var html = Http.get(chapterUrl, "{}");
    var title = selectText(html, ".cha-tit");
    var body = selectHtml(html, ".cha-words");
    return title ? ("<h2>" + title + "</h2>" + body) : body;
  },

};
