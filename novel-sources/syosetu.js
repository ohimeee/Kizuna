// Syosetu / Shosetsuka ni Naro (syosetu.com): Japan's largest free web-novel site, the
// home of most isekai light novels before they get published. Raw Japanese text, free to
// read, no login. Written against Kizuna's NovelSource contract (docs/novel-sources/README.md).
//
// Listings and novel details come from Syosetu's official public API
// (https://dev.syosetu.com/man/api/), which needs no key and returns JSON. Chapter lists and
// chapter text are scraped from ncode.syosetu.com, whose markup was checked live on 2026-11-25:
// - Table of contents: 100 episodes per page (`?p=N`), each `.p-eplist__sublist` holding an
//   `a.p-eplist__subtitle` and a `.p-eplist__update` date ("2012/11/22 17:00", JST).
// - Chapter body: `.js-novel-text.p-novel__text`, one `<p id="L1">` per line and
//   `<p><br /></p>` for blank lines. The author's preface/afterword use the same class plus
//   `--preface` / `--afterword`, so they are excluded explicitly.
// - Short stories (noveltype 2) have no table of contents: the body sits on the novel page.
// Syosetu novels have no cover images, so `cover` is always null.
// Adult works live on a separate site (novel18.syosetu.com) behind an age gate and are not
// covered.

var API = "https://api.syosetu.com/novelapi/api/?out=json";
var FIELDS = "&of=t-n-w-s-g-k-nt-e-ga-gl";
var PAGE_SIZE = 20;
var API_MAX_START = 2000; // the API refuses st > 2000

var GENRES = {
  "101": "異世界〔恋愛〕", "102": "現実世界〔恋愛〕", "201": "ハイファンタジー",
  "202": "ローファンタジー", "301": "純文学", "302": "ヒューマンドラマ", "303": "歴史",
  "304": "推理", "305": "ホラー", "306": "アクション", "307": "コメディー", "401": "VRゲーム",
  "402": "宇宙", "403": "空想科学", "404": "パニック", "9901": "童話", "9902": "詩",
  "9903": "エッセイ", "9904": "リプレイ", "9999": "その他", "9801": "ノンジャンル",
};

Register(JSON.stringify({
  id: "syosetu",
  name: "Syosetu (小説家になろう)",
  lang: "ja",
  baseUrl: "https://syosetu.com",
  version: "1.0.0",
  supportsLatest: true,
  iconUrl: "https://www.google.com/s2/favicons?sz=64&domain=syosetu.com",
  filters: [
    {
      id: "order",
      name: "Sort",
      options: [
        { label: "Most popular, all time (総合ポイント)", value: "hyoka" },
        { label: "Popular today (日間ポイント)", value: "dailypoint" },
        { label: "Popular this week (週間ポイント)", value: "weeklypoint" },
        { label: "Popular this month (月間ポイント)", value: "monthlypoint" },
        { label: "Most bookmarked (ブックマーク)", value: "favnovelcnt" },
        { label: "Recently updated (更新順)", value: "new" },
        { label: "Newest (新着)", value: "ncodedesc" },
      ],
    },
    {
      id: "genre",
      name: "Genre",
      options: [
        { label: "Isekai romance (異世界〔恋愛〕)", value: "101" },
        { label: "Real-world romance (現実世界〔恋愛〕)", value: "102" },
        { label: "High fantasy (ハイファンタジー)", value: "201" },
        { label: "Low fantasy (ローファンタジー)", value: "202" },
        { label: "Literary (純文学)", value: "301" },
        { label: "Human drama (ヒューマンドラマ)", value: "302" },
        { label: "Historical (歴史)", value: "303" },
        { label: "Mystery (推理)", value: "304" },
        { label: "Horror (ホラー)", value: "305" },
        { label: "Action (アクション)", value: "306" },
        { label: "Comedy (コメディー)", value: "307" },
        { label: "VR game (VRゲーム)", value: "401" },
        { label: "Space (宇宙)", value: "402" },
        { label: "Sci-fi (空想科学)", value: "403" },
        { label: "Panic (パニック)", value: "404" },
        { label: "Essay (エッセイ)", value: "9903" },
        { label: "Other (その他)", value: "9999" },
      ],
    },
    {
      id: "type",
      name: "Status",
      options: [
        { label: "Ongoing serial (連載中)", value: "r" },
        { label: "Completed serial (完結済)", value: "er" },
        { label: "Short story (短編)", value: "t" },
      ],
    },
  ],
}));

function ncodeOf(url) {
  var m = String(url).match(/syosetu\.com\/(n[0-9a-z]+)/i);
  return m ? m[1].toLowerCase() : "";
}

function novelUrl(ncode) {
  return "https://ncode.syosetu.com/" + ncode.toLowerCase() + "/";
}

function apiGet(params) {
  var text = Http.get(API + FIELDS + params, "{}");
  var data = JSON.parse(text);
  return { total: (data[0] && data[0].allcount) || 0, items: data.slice(1) };
}

function listing(params, page) {
  var start = (page - 1) * PAGE_SIZE + 1;
  if (start > API_MAX_START) return JSON.stringify({ novels: [], hasNextPage: false });
  var res = apiGet("&lim=" + PAGE_SIZE + "&st=" + start + params);
  var novels = res.items.map(function (n) {
    return { title: n.title, url: novelUrl(n.ncode), cover: null };
  });
  var next = start + PAGE_SIZE;
  return JSON.stringify({
    novels: novels,
    hasNextPage: novels.length === PAGE_SIZE && next <= res.total && next <= API_MAX_START,
  });
}

// "2012/11/22 17:00" in Japan time -> epoch millis.
function parseJst(text) {
  var m = String(text).match(/(\d{4})\/(\d{1,2})\/(\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?/);
  if (!m) return 0;
  return Date.UTC(+m[1], +m[2] - 1, +m[3], (+(m[4] || 0)) - 9, +(m[5] || 0));
}

function decodeEntities(s) {
  return s.replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&quot;/g, "\"").replace(/&#39;/g, "'");
}

// Furigana: keep the base text, drop the reading. The reader has no ruby rendering, and
// "漢字(かんじ)" inline would break up every sentence that uses it.
function stripRuby(html) {
  return html.replace(/<rp>[\s\S]*?<\/rp>/gi, "").replace(/<rt>[\s\S]*?<\/rt>/gi, "");
}

globalThis.source = {

  popularNovels: function (page) {
    return listing("&order=hyoka", page);
  },

  latestNovels: function (page) {
    return listing("&order=new", page);
  },

  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");
    var params = "&order=" + encodeURIComponent(filters.order || "hyoka");
    if (query) params += "&word=" + encodeURIComponent(query);
    if (filters.genre) params += "&genre=" + encodeURIComponent(filters.genre);
    if (filters.type) params += "&type=" + encodeURIComponent(filters.type);
    return listing(params, page);
  },

  novelDetails: function (url) {
    var ncode = ncodeOf(url);
    var n = apiGet("&ncode=" + ncode).items[0];
    if (!n) return JSON.stringify({});
    var genres = [];
    if (GENRES[n.genre]) genres.push(GENRES[n.genre]);
    String(n.keyword || "").split(/\s+/).forEach(function (k) { if (k) genres.push(k); });
    // end: 0 = finished (or a short story), 1 = still being serialized
    var status = n.noveltype == 2 || n.end == 0 ? "Completed" : "Ongoing";
    return JSON.stringify({
      title: n.title,
      cover: null,
      author: n.writer,
      description: n.story,
      genres: genres,
      status: status,
    });
  },

  chapterList: function (url) {
    var ncode = ncodeOf(url);
    var base = novelUrl(ncode);
    var first = Http.get(base, "{}");

    // Short story: no table of contents, the text is on this page.
    if (first.indexOf("p-eplist__sublist") < 0) {
      var title = selectText(first, "h1.p-novel__title") || "本文";
      return JSON.stringify([{ name: title, url: base, chapterNumber: 1, dateUpload: 0 }]);
    }

    var lastPage = 1;
    var re = new RegExp("/" + ncode + "/\\?p=(\\d+)", "g");
    var m;
    while ((m = re.exec(first)) !== null) lastPage = Math.max(lastPage, +m[1]);

    var chapters = [];
    for (var p = 1; p <= lastPage; p++) {
      var html = p === 1 ? first : Http.get(base + "?p=" + p, "{}");
      var hrefs = selectAllAttr(html, ".p-eplist__sublist a.p-eplist__subtitle", "href");
      var names = selectAllText(html, ".p-eplist__sublist a.p-eplist__subtitle");
      var dates = selectAllText(html, ".p-eplist__sublist .p-eplist__update");
      for (var i = 0; i < hrefs.length; i++) {
        chapters.push({
          name: decodeEntities(names[i] || ("第" + (chapters.length + 1) + "話")),
          url: "https://ncode.syosetu.com" + hrefs[i],
          chapterNumber: chapters.length + 1,
          dateUpload: parseJst(dates[i] || ""),
        });
      }
    }
    return JSON.stringify(chapters);
  },

  chapterContent: function (url) {
    var html = Http.get(url, "{}");
    var body = selectHtml(
      html,
      ".js-novel-text.p-novel__text:not(.p-novel__text--preface):not(.p-novel__text--afterword)",
    );
    return stripRuby(body);
  },

};
