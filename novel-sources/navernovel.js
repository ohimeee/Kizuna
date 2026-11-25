// Naver Webnovel (novel.naver.com): Naver's free web-novel service. Raw Korean text, no login.
// Written against Kizuna's NovelSource contract (docs/novel-sources/README.md).
//
// Three sections, all on the mobile site (the desktop site redirects phones there anyway):
// - webnovel:  official serials. Only the free episodes are listed; once a series finishes, the
//              rest moves to paid Naver Series and simply stops appearing here.
// - best:      Best League, promoted amateur novels, fully free.
// - challenge: Challenge League, open amateur novels, fully free.
// Paid Naver Series / KakaoPage-style catalogues need a login and payment and are not covered.
//
// Markup checked live on 2026-11-25:
// - Novel cards: `a[href="/<section>/list?novelId=N"]` holding `span.title` and an `img`.
// - Novel page: `.info_top h2.title`, `.info_top .item` (genre, then author link
//   `a[href*='target=author']`, then "매일 연재" style schedule), `#synopsisContent`, `.tag_area a.tag`.
// - Episode list: `/<section>/list?novelId=N&order=Oldest&page=P`, `li.item.volumeComment > a.link`
//   with `span.title` and `span.date` ("2026.11.24", or "07:00" for today).
// - Chapter: `.viewer_cont_subj` (title) and `#con_data` holding plain `<p>` paragraphs.

var BASE = "https://m.novel.naver.com";

Register(JSON.stringify({
  id: "navernovel",
  name: "Naver Webnovel (네이버 웹소설)",
  lang: "ko",
  baseUrl: BASE,
  version: "1.0.0",
  supportsLatest: true,
  iconUrl: "https://www.google.com/s2/favicons?sz=64&domain=novel.naver.com",
  filters: [
    {
      id: "section",
      name: "Section",
      options: [
        { label: "Official webnovels (웹소설)", value: "webnovel" },
        { label: "Best League (베스트리그)", value: "best" },
        { label: "Challenge League (챌린지리그)", value: "challenge" },
      ],
    },
    {
      id: "genre",
      name: "Genre",
      options: [
        { label: "Romance (로맨스)", value: "101" },
        { label: "Romance fantasy (로판)", value: "109" },
        { label: "Fantasy (판타지)", value: "102" },
        { label: "Modern fantasy (현판)", value: "110" },
        { label: "Martial arts (무협)", value: "103" },
        { label: "Mystery (미스터리)", value: "104" },
        { label: "Light novel (라이트노벨)", value: "106" },
      ],
    },
  ],
}));

function absolute(url) {
  return url.indexOf("http") === 0 ? url : BASE + url;
}

function decode(s) {
  return String(s || "")
    .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&quot;/g, "\"").replace(/&#39;/g, "'")
    .replace(/&[lr]squo;/g, "'").replace(/&[lr]dquo;/g, "\"");
}

// Novel cards appear in several layouts (carousels, ranked lists, search results), so they
// are parsed by regex over each `<a href=".../list?novelId=N">...</a>` block instead of
// parallel selector arrays, which drift apart when one card lacks an image.
function parseCards(html) {
  var seen = {};
  var novels = [];
  var re = /<a href="(\/(?:webnovel|best|challenge)\/list\?novelId=\d+)"[^>]*>([\s\S]*?)<\/a>/g;
  var m;
  while ((m = re.exec(html)) !== null) {
    var url = absolute(m[1]);
    if (seen[url]) continue;
    var inner = m[2];
    var title = (inner.match(/<span class="title">([\s\S]*?)<\/span>/) || [])[1];
    if (!title) title = (inner.match(/<img[^>]*alt="([^"]*)"/) || [])[1];
    if (!title) continue;
    var cover = (inner.match(/<img[^>]*src="([^"]+)"/) || [])[1] || null;
    if (cover) cover = cover.replace(/\?type=.*$/, "?type=f180_248_2");
    seen[url] = true;
    novels.push({ title: decode(title.replace(/<[^>]+>/g, "").trim()), url: url, cover: cover });
  }
  return novels;
}

function hasPage(html, page) {
  return html.indexOf("page=" + page + "\"") >= 0 || html.indexOf("page=" + page + "&") >= 0;
}

function weekdayCode() {
  // Korea is UTC+9; Naver's weekday tabs use mon..sun
  var day = new Date(Date.now() + 9 * 3600 * 1000).getUTCDay();
  return ["sun", "mon", "tue", "wed", "thu", "fri", "sat"][day];
}

// "2026.11.24" -> epoch millis (KST). "07:00" (posted today) -> today at that time.
function parseDate(text) {
  var s = String(text || "").trim();
  var m = s.match(/(\d{4})\.(\d{1,2})\.(\d{1,2})/);
  if (m) return Date.UTC(+m[1], +m[2] - 1, +m[3], -9);
  var t = s.match(/^(\d{1,2}):(\d{2})$/);
  if (t) {
    var now = new Date(Date.now() + 9 * 3600 * 1000);
    return Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), +t[1] - 9, +t[2]);
  }
  return 0;
}

function sectionOf(url) {
  var m = String(url).match(/\/(webnovel|best|challenge)\//);
  return m ? m[1] : "webnovel";
}

function novelIdOf(url) {
  var m = String(url).match(/novelId=(\d+)/);
  return m ? m[1] : "";
}

globalThis.source = {

  // Best League ranking: the biggest fully-free pool. The page loads more by infinite scroll,
  // so there is no "next page" link to look for; a full page (50 ranked novels, plus a few
  // carousel picks) means there is probably another one.
  popularNovels: function (page) {
    var html = Http.get(BASE + "/best/popular?page=" + page, "{}");
    var novels = parseCards(html);
    return JSON.stringify({ novels: novels, hasNextPage: novels.length >= 40 && page < 50 });
  },

  // Today's official serials (Korea time). One page.
  latestNovels: function (page) {
    if (page > 1) return JSON.stringify({ novels: [], hasNextPage: false });
    var html = Http.get(BASE + "/webnovel/weekday?week=" + weekdayCode(), "{}");
    return JSON.stringify({ novels: parseCards(html), hasNextPage: false });
  },

  searchNovels: function (query, page, filtersJson) {
    var filters = JSON.parse(filtersJson || "{}");
    var html;
    if (query) {
      html = Http.get(BASE + "/search?keyword=" + encodeURIComponent(query) + "&page=" + page, "{}");
      var found = parseCards(html);
      if (filters.section) {
        found = found.filter(function (n) { return sectionOf(n.url) === filters.section; });
      }
      return JSON.stringify({ novels: found, hasNextPage: found.length > 0 && hasPage(html, page + 1) });
    }
    var section = filters.section || "webnovel";
    var genre = filters.genre || "101";
    html = Http.get(BASE + "/" + section + "/genre?genre=" + genre + "&page=" + page, "{}");
    var novels = parseCards(html);
    return JSON.stringify({ novels: novels, hasNextPage: novels.length > 0 && hasPage(html, page + 1) });
  },

  novelDetails: function (url) {
    var html = Http.get(url, "{}");
    var items = selectAllText(html, ".info_top .info_group .item");
    var schedule = items.join(" ");
    var tags = selectAllText(html, ".tag_area a.tag").map(function (t) { return t.replace(/^#/, ""); });
    var genres = [];
    if (items[0]) genres.push(items[0]);
    tags.forEach(function (t) { if (t && genres.indexOf(t) < 0) genres.push(t); });
    var cover = selectAttr(html, "meta[property='og:image']", "content");
    return JSON.stringify({
      title: selectText(html, ".info_top h2.title") || selectAttr(html, "meta[property='og:title']", "content"),
      cover: cover ? cover.replace(/\?type=.*$/, "?type=f180_248_2") : null,
      author: selectText(html, ".info_top a[href*='target=author']"),
      description: selectText(html, "#synopsisContent"),
      genres: genres,
      status: /완결/.test(schedule) ? "Completed" : (/연재|휴재/.test(schedule) ? "Ongoing" : ""),
    });
  },

  chapterList: function (url) {
    var section = sectionOf(url);
    var id = novelIdOf(url);
    var listBase = BASE + "/" + section + "/list?novelId=" + id + "&order=Oldest&page=";
    var chapters = [];
    var seen = {};
    for (var page = 1; page <= 500; page++) {
      var html = Http.get(listBase + page, "{}");
      var hrefs = selectAllAttr(html, "li.volumeComment > a.link", "href");
      var names = selectAllText(html, "li.volumeComment > a.link span.title");
      var dates = selectAllText(html, "li.volumeComment > a.link span.date");
      var added = 0;
      for (var i = 0; i < hrefs.length; i++) {
        var chapterUrl = absolute(hrefs[i].replace(/#.*$/, ""));
        if (seen[chapterUrl]) continue;
        seen[chapterUrl] = true;
        added++;
        chapters.push({
          name: decode(names[i] || ((chapters.length + 1) + "화")),
          url: chapterUrl,
          chapterNumber: chapters.length + 1,
          dateUpload: parseDate(dates[i]),
        });
      }
      // Past the last page Naver repeats the final page, so stop when nothing new shows up.
      if (added === 0 || !hasPage(html, page + 1)) break;
    }
    return JSON.stringify(chapters);
  },

  chapterContent: function (url) {
    var html = Http.get(url, "{}");
    return selectHtml(html, "#con_data");
  },

};
