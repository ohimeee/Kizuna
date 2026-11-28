// Wattpad (wattpad.com): original stories written and posted by their authors. Written
// independently against Kizuna's NovelSource contract (see docs/novel-sources/README.md).
//
// Uses the same public JSON API the Wattpad website calls, checked live with curl:
// - Popular: /api/v3/stories?filter=hot, Latest: ?filter=new (offset paging; `nextUrl` is absent
//   on the last page). The v3 `category` parameter returns nothing, so genres go through search.
// - Search: /v4/search/stories?query=..., where a "#tag" term restricts results to that tag
//   (verified: "#horror" returned only horror-tagged stories). The Genre filter appends one.
// - Details and parts: /api/v3/stories/{id}?fields=... (parts in reading order, with dates).
// - Text: /apiv2/storytext?id={partId}. Without a page number it returns the whole part as
//   <p> paragraphs (with page=N it returns one screen-sized slice).
//
// Paid stories: Wattpad sells some parts of some stories. The API doesn't say which parts, but
// the story page embeds each part's "isBlocked" flag, so for a story with isPaywalled set the
// chapter list reads those flags and marks paid parts `locked`. Their text is whatever preview
// Wattpad serves to anyone logged out; nothing here tries to get the paid text.

var BASE = "https://www.wattpad.com";
var PAGE_SIZE = 20;
var LIST_FIELDS = "stories(id,title,cover),nextUrl";

Register(JSON.stringify({
  id: "wattpad",
  name: "Wattpad",
  lang: "en",
  baseUrl: BASE,
  version: "1.0.0",
  supportsLatest: true,
  iconUrl: "https://www.google.com/s2/favicons?sz=64&domain=wattpad.com",
  filters: [
    {
      id: "genre",
      name: "Genre",
      options: [
        { label: "Action", value: "action" },
        { label: "Adventure", value: "adventure" },
        { label: "ChickLit", value: "chicklit" },
        { label: "Classics", value: "classics" },
        { label: "Fanfiction", value: "fanfiction" },
        { label: "Fantasy", value: "fantasy" },
        { label: "General Fiction", value: "generalfiction" },
        { label: "Historical Fiction", value: "historicalfiction" },
        { label: "Horror", value: "horror" },
        { label: "Humor", value: "humor" },
        { label: "Mystery", value: "mystery" },
        { label: "Non-Fiction", value: "nonfiction" },
        { label: "Paranormal", value: "paranormal" },
        { label: "Poetry", value: "poetry" },
        { label: "Romance", value: "romance" },
        { label: "Science Fiction", value: "sciencefiction" },
        { label: "Short Story", value: "shortstory" },
        { label: "Teen Fiction", value: "teenfiction" },
        { label: "Thriller", value: "thriller" },
        { label: "Vampire", value: "vampire" },
        { label: "Werewolf", value: "werewolf" },
      ],
    },
  ],
}));

function getJson(url) {
  var text = Http.get(url, "{}");
  try { return JSON.parse(text); } catch (e) { return null; }
}

function storyId(url) {
  var m = String(url).match(/\/story\/(\d+)/);
  return m ? m[1] : null;
}

function listing(url) {
  var data = getJson(url) || {};
  var novels = (data.stories || []).map(function (s) {
    return { title: (s.title || "").trim(), url: BASE + "/story/" + s.id, cover: s.cover || null };
  }).filter(function (n) { return n.title; });
  return JSON.stringify({ novels: novels, hasNextPage: !!data.nextUrl && novels.length > 0 });
}

function offset(page) { return (Math.max(1, page) - 1) * PAGE_SIZE; }

// part id -> true for parts the story page marks as paid ("isBlocked"); empty if none or unknown
function blockedParts(id) {
  var html = Http.get(BASE + "/story/" + id, "{}"), blocked = {};
  var re = /"url":"https:\/\/www\.wattpad\.com\/(\d+)-[^"]*"[\s\S]{0,400}?"isBlocked":(true|false)/g, m;
  while ((m = re.exec(html))) { if (m[2] === "true") blocked[m[1]] = true; }
  return blocked;
}

globalThis.source = {

  popularNovels: function (page) {
    return listing(BASE + "/api/v3/stories?filter=hot&language=1&limit=" + PAGE_SIZE + "&offset=" + offset(page) + "&fields=" + LIST_FIELDS);
  },

  latestNovels: function (page) {
    return listing(BASE + "/api/v3/stories?filter=new&language=1&limit=" + PAGE_SIZE + "&offset=" + offset(page) + "&fields=" + LIST_FIELDS);
  },

  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");
    var q = (query || "").trim();
    if (filters.genre) q = (q ? q + " " : "") + "#" + filters.genre;
    if (!q) return globalThis.source.popularNovels(page);
    return listing(BASE + "/v4/search/stories?query=" + encodeURIComponent(q) + "&limit=" + PAGE_SIZE + "&offset=" + offset(page) + "&fields=" + LIST_FIELDS);
  },

  novelDetails: function (url) {
    var id = storyId(url);
    var s = id ? getJson(BASE + "/api/v3/stories/" + id + "?fields=title,cover,description,completed,tags,user(name)") : null;
    if (!s) return JSON.stringify({});
    return JSON.stringify({
      title: (s.title || "").trim(),
      cover: s.cover || null,
      author: s.user ? s.user.name : null,
      description: s.description || "",
      genres: s.tags || [],
      status: s.completed ? "Completed" : "Ongoing",
    });
  },

  chapterList: function (novelUrl) {
    var id = storyId(novelUrl);
    var s = id ? getJson(BASE + "/api/v3/stories/" + id + "?fields=isPaywalled,parts(id,title,url,createDate,draft,deleted)") : null;
    if (!s || !s.parts) return JSON.stringify([]);
    var blocked = s.isPaywalled ? blockedParts(id) : {};
    var parts = s.parts.filter(function (p) { return !p.draft && !p.deleted; });
    return JSON.stringify(parts.map(function (p, i) {
      return {
        name: (p.title || "").trim() || ("Part " + (i + 1)),
        url: p.url || (BASE + "/" + p.id),
        chapterNumber: i + 1,
        dateUpload: p.createDate ? Date.parse(p.createDate) || 0 : 0,
        locked: !!blocked[String(p.id)],
      };
    }));
  },

  chapterContent: function (chapterUrl) {
    var m = String(chapterUrl).match(/wattpad\.com\/(\d+)/);
    if (!m) return "";
    var html = Http.get(BASE + "/apiv2/storytext?id=" + m[1], "{}");
    if (html.charAt(0) === "{") return ""; // an API error object, not text
    return html.replace(/\s+data-p-id="[^"]*"/g, "");
  },

};
