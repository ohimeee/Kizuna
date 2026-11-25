// Portions copyright (c) 2021 Rajarshee Chatterjee (LNReader plugins), MIT License; see NOTICE.
//
// WTR-LAB (wtr-lab.com): Chinese web novels machine-translated into English, mostly by Gemini
// ("AI" translations). Ported from LNReader's WTR-LAB plugin
// (github.com/lnreader/lnreader-plugins, plugins/english/wtrlab.ts, MIT licence) to Kizuna's
// NovelSource contract (docs/novel-sources/README.md), then cut down. See NOTICE.
//
// What this port keeps and drops, checked live on 2026-11-25:
// - Chapters are read in **AI mode only**. `POST /api/reader/get {translate:"ai"}` returns a
//   `content_url` whose JSON holds `body` as a plain array of English paragraphs plus
//   `glossary_data.terms` ([english, chinese] pairs). Names in the body are tokens like
//   `※9⛬` that index into those terms. No login, no encryption.
// - The Web/Web+ modes are left out on purpose. Their body arrives AES-encrypted Chinese that
//   the LNReader plugin decrypts with a key scraped from the site's JS and then runs through
//   Google's internal translate endpoint with a scraped API key. Working around the site's
//   encryption and borrowing Google's key aren't things this plugin should do, and AI mode
//   reads better anyway.
// - A chapter with no AI translation yet (newest chapters are "locked" until the community
//   unlocks them) shows a short notice instead of text.
// - Titles can hold reveal tokens `%{Safe Name|base64}`; the base64 is the real name (e.g.
//   "Ninja World" hides "Naruto"). Like the original, the real name wins.

var SITE = "https://wtr-lab.com";
var LANG_PATH = "/en/";
var FINDER_PAGE_SIZE = 10;

var GENRES = [
  "Action", "Adult", "Adventure", "Comedy", "Drama", "Ecchi", "Erciyuan", "Fan-Fiction", "Fantasy",
  "Game", "Gender-Bender", "Harem", "Historical", "Horror", "Josei", "Martial-Arts", "Mature",
  "Mecha", "Military", "Mystery", "Psychological", "Romance", "School-Life", "Sci-Fi", "Seinen",
  "Shoujo", "Shoujo-Ai", "Shounen", "Shounen-Ai", "Slice-Of-Life", "Smut", "Sports",
  "Supernatural", "Tragedy", "Urban-Life", "Wuxia", "Xianxia", "Xuanhuan", "Yaoi", "Yuri",
];

Register(JSON.stringify({
  id: "wtrlab",
  name: "WTR-LAB",
  lang: "en",
  baseUrl: SITE,
  version: "1.0.0",
  supportsLatest: true,
  iconUrl: "https://www.google.com/s2/favicons?sz=64&domain=wtr-lab.com",
  filters: [
    {
      id: "orderBy",
      name: "Sort",
      options: [
        { label: "Weekly views", value: "weekly_rank" },
        { label: "Monthly views", value: "monthly_rank" },
        { label: "All-time views", value: "view" },
        { label: "Rating", value: "rating" },
        { label: "Readers", value: "reader" },
        { label: "Chapters", value: "chapter" },
        { label: "Recently updated", value: "update" },
        { label: "Recently added", value: "date" },
      ],
    },
    {
      id: "status",
      name: "Status",
      options: [
        { label: "Ongoing", value: "ongoing" },
        { label: "Completed", value: "completed" },
        { label: "Hiatus", value: "hiatus" },
      ],
    },
    {
      id: "genre",
      name: "Genre",
      options: GENRES.map(function (g, i) { return { label: g.replace(/-/g, " "), value: String(i + 1) }; }),
    },
  ],
}));

// ---- base64 -> UTF-8 (QuickJS has no atob/TextDecoder) ----
var B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

function base64ToUtf8(input) {
  var s = String(input).replace(/-/g, "+").replace(/_/g, "/").replace(/=+$/, "");
  if (!/^[A-Za-z0-9+/]*$/.test(s) || s.length % 4 === 1) return "";
  var bytes = [];
  var buffer = 0;
  var bits = 0;
  for (var i = 0; i < s.length; i++) {
    buffer = (buffer << 6) | B64.indexOf(s.charAt(i));
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      bytes.push((buffer >> bits) & 0xff);
    }
  }
  var out = "";
  for (var j = 0; j < bytes.length;) {
    var b = bytes[j++];
    var cp;
    if (b < 0x80) cp = b;
    else if (b >= 0xf0) cp = ((b & 0x07) << 18) | ((bytes[j++] & 0x3f) << 12) | ((bytes[j++] & 0x3f) << 6) | (bytes[j++] & 0x3f);
    else if (b >= 0xe0) cp = ((b & 0x0f) << 12) | ((bytes[j++] & 0x3f) << 6) | (bytes[j++] & 0x3f);
    else cp = ((b & 0x1f) << 6) | (bytes[j++] & 0x3f);
    out += String.fromCodePoint(cp);
  }
  return out;
}

function escapeHtml(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// %{"Safe": "b64"} or %{Safe|b64} -> the decoded real text, else the safe text.
function resolveTokens(str) {
  if (!str) return "";
  return String(str).replace(
    /%\{(?:"([^"]*)":\s*"?([A-Za-z0-9+/=_-]+)"?|([^|{}%]+?)\s*\|\s*([A-Za-z0-9+/=_-]+))\}/g,
    function (match, safe1, b64a, safe2, b64b) {
      var safe = (safe1 || safe2 || "").trim();
      var decoded = base64ToUtf8((b64a || b64b || "").trim());
      return decoded || safe || match;
    },
  );
}

function novelUrl(rawId, slug) {
  return SITE + LANG_PATH + "serie-" + rawId + "/" + slug;
}

function nextData(html) {
  var m = html.match(/<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
  return m ? JSON.parse(m[1]) : null;
}

function toNovel(serie) {
  var data = serie.data || {};
  return {
    title: resolveTokens(data.title || serie.slug || ""),
    url: novelUrl(serie.raw_id, serie.slug),
    cover: data.image || null,
  };
}

function finder(params, page) {
  var finderHtml = Http.get(SITE + "/en/novel-finder", "{}");
  var nd = nextData(finderHtml);
  if (!nd) throw new Error("WTR-LAB: novel finder page changed (no __NEXT_DATA__)");
  var url = SITE + "/_next/data/" + nd.buildId + "/en/novel-finder.json?order=desc&release_status=all" +
    "&addition_age=all&page=" + page + params;
  var props = JSON.parse(Http.get(url, "{}")).pageProps || {};
  var seen = {};
  var novels = (props.series || []).filter(function (s) {
    if (seen[s.raw_id]) return false;
    seen[s.raw_id] = true;
    return true;
  }).map(toNovel);
  var total = props.count || 0;
  return JSON.stringify({
    novels: novels,
    hasNextPage: novels.length > 0 && (total ? page * FINDER_PAGE_SIZE < total : novels.length >= FINDER_PAGE_SIZE),
  });
}

function parseNovelPath(url) {
  var m = String(url).match(/serie-(\d+)\/([^/?#]+)/);
  return m ? { rawId: m[1], slug: m[2] } : null;
}

globalThis.source = {

  popularNovels: function (page) {
    return finder("&orderBy=weekly_rank&status=all", page);
  },

  latestNovels: function (page) {
    var res = JSON.parse(Http.post(
      SITE + "/api/home/recent",
      JSON.stringify({ page: page }),
      JSON.stringify({ "Content-Type": "application/json" }),
    ));
    var novels = (res.data || []).map(function (d) { return toNovel(d.serie); });
    return JSON.stringify({ novels: novels, hasNextPage: novels.length > 0 });
  },

  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");
    var params = "&orderBy=" + encodeURIComponent(filters.orderBy || (query ? "view" : "weekly_rank")) +
      "&status=" + encodeURIComponent(filters.status || "all");
    if (query) params += "&text=" + encodeURIComponent(query);
    if (filters.genre) params += "&gi=" + encodeURIComponent(filters.genre) + "&gc=and";
    return finder(params, page);
  },

  novelDetails: function (url) {
    var html = Http.get(url, "{}");
    var nd = nextData(html);
    var props = (nd && nd.props && nd.props.pageProps) || {};
    var serie = (props.serie && props.serie.serie_data) || {};
    var data = serie.data || {};
    var genres = (serie.genres || []).map(function (id) { return GENRES[id - 1]; })
      .filter(function (g) { return !!g; })
      .map(function (g) { return g.replace(/-/g, " "); });
    (props.tags || []).forEach(function (t) {
      var title = t && t.title ? resolveTokens(String(t.title).trim()) : "";
      if (title && genres.indexOf(title) < 0) genres.push(title);
    });
    var status = serie.status === 0 ? "Ongoing" : (serie.status === 1 ? "Completed" : "");
    return JSON.stringify({
      title: resolveTokens(data.title || ""),
      cover: data.image || null,
      author: resolveTokens(data.author || ""),
      description: resolveTokens(data.description || ""),
      genres: genres,
      status: status,
    });
  },

  chapterList: function (url) {
    var p = parseNovelPath(url);
    if (!p) return JSON.stringify([]);
    var chapters = [];
    for (var start = 1; start < 100000; start += 500) {
      var res = JSON.parse(Http.get(
        SITE + "/api/chapters/" + p.rawId + "?start=" + start + "&end=" + (start + 499),
        "{}",
      ));
      var batch = res.chapters || (res.data && res.data.chapters) || [];
      batch.forEach(function (c) {
        chapters.push({
          name: resolveTokens(c.title || c.name || ("Chapter " + c.order)),
          url: novelUrl(p.rawId, p.slug) + "/chapter-" + c.order,
          chapterNumber: c.order,
          dateUpload: c.updated_at ? Date.parse(c.updated_at.replace(" ", "T").replace(/\+00$/, "Z")) || 0 : 0,
          locked: !!c.locked,
        });
      });
      if (batch.length < 500) break;
    }
    chapters.sort(function (a, b) { return a.chapterNumber - b.chapterNumber; });
    return JSON.stringify(chapters);
  },

  chapterContent: function (url) {
    var m = String(url).match(/serie-(\d+)\/[^/]+\/chapter-(\d+)/);
    if (!m) throw new Error("WTR-LAB: unrecognised chapter URL " + url);
    var res = JSON.parse(Http.post(
      SITE + "/api/reader/get",
      JSON.stringify({
        translate: "ai", language: "en", raw_id: +m[1], chapter_no: +m[2], retry: false, force_retry: false,
      }),
      JSON.stringify({ "Content-Type": "application/json", Accept: "application/json", Referer: url }),
    ));

    var notice = "<p><i>This chapter has no AI translation on WTR-LAB yet. New chapters stay locked " +
      "until readers unlock them on the site; check back later or open it in WebView.</i></p>";
    if (!res || res.success === false || res.error || !res.content_url) return notice;

    // Only ever follow a content URL on WTR-LAB itself.
    var contentUrl = /^https?:\/\//i.test(res.content_url) ? res.content_url : SITE + res.content_url;
    if (contentUrl.indexOf(SITE + "/") !== 0) return notice;

    var payload = JSON.parse(Http.get(contentUrl, JSON.stringify({ Referer: url })));
    var data = payload && payload.data && payload.data.data;
    if (!data || !Array.isArray(data.body)) return notice;

    var terms = (data.glossary_data && data.glossary_data.terms) || [];
    var html = "";
    data.body.forEach(function (line) {
      if (typeof line !== "string") return;
      var text = resolveTokens(line).replace(/(?:wtr-lab\s+)?※(\d+)[⛬〓]/g, function (t, i) {
        return terms[+i] ? terms[+i][0] : t;
      });
      text = text.replace(/\[\s*image\s*\]/gi, "").trim();
      if (text) html += "<p>" + escapeHtml(text) + "</p>";
    });
    return html || notice;
  },

};
