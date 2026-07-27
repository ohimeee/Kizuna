// Example Kizuna novel source. Copy this file as a starting point for porting a real site.
// See README.md in this directory for the full contract.

Register(JSON.stringify({
  id: "example-novel-site",
  name: "Example Novel Site",
  lang: "en",
  baseUrl: "https://example.com",
  version: "1.0.0",
  supportsLatest: true,
}));

globalThis.source = {

  popularNovels: function (page) {
    var html = fetchApi(baseUrlPath("/novels/popular?page=" + page)).text();
    var titles = selectAllText(html, ".novel-card .title");
    var urls = selectAllAttr(html, ".novel-card a.title", "href");
    var covers = selectAllAttr(html, ".novel-card img", "src");

    var novels = titles.map(function (title, i) {
      return { title: title, url: urls[i], cover: covers[i] };
    });

    return JSON.stringify({
      novels: novels,
      hasNextPage: selectText(html, ".pagination .next") !== "",
    });
  },

  latestNovels: function (page) {
    // Same shape as popularNovels; most sites just use a different listing endpoint.
    var html = fetchApi(baseUrlPath("/novels/latest?page=" + page)).text();
    var titles = selectAllText(html, ".novel-card .title");
    var urls = selectAllAttr(html, ".novel-card a.title", "href");
    var novels = titles.map(function (title, i) { return { title: title, url: urls[i] }; });
    return JSON.stringify({ novels: novels, hasNextPage: false });
  },

  searchNovels: function (query, page) {
    var html = fetchApi(baseUrlPath("/search?q=" + encodeURIComponent(query) + "&page=" + page)).text();
    var titles = selectAllText(html, ".novel-card .title");
    var urls = selectAllAttr(html, ".novel-card a.title", "href");
    var novels = titles.map(function (title, i) { return { title: title, url: urls[i] }; });
    return JSON.stringify({ novels: novels, hasNextPage: false });
  },

  novelDetails: function (url) {
    var html = fetchApi(url).text();
    return JSON.stringify({
      title: selectText(html, "h1.novel-title"),
      cover: selectAttr(html, ".novel-cover img", "src"),
      author: selectText(html, ".novel-author"),
      description: selectText(html, ".novel-description"),
      genres: selectAllText(html, ".novel-genres a"),
      status: selectText(html, ".novel-status"),
    });
  },

  chapterList: function (novelUrl) {
    var html = fetchApi(novelUrl).text();
    var names = selectAllText(html, ".chapter-list a");
    var urls = selectAllAttr(html, ".chapter-list a", "href");
    return JSON.stringify(names.map(function (name, i) {
      return { name: name, url: urls[i] };
    }));
  },

  chapterContent: function (chapterUrl) {
    var html = fetchApi(chapterUrl).text();
    return selectHtml(html, ".chapter-content");
  },

};

function baseUrlPath(path) {
  return "https://example.com" + path;
}
