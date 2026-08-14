# Kizuna novel JS sources

Novel sources run as small JS plugin files inside an embedded QuickJS engine
(`JsNovelSource`, in `source-api/src/main/kotlin/eu/kanade/tachiyomi/source/novel/`).
This format is **inspired by LNReader's source structure, not byte-compatible with it** —
existing LNReader plugins need porting (usually just swapping their fetch/cheerio calls for
the bridge functions below), they can't be dropped in unmodified.

See [`example-source.js`](./example-source.js) for a minimal working plugin. Copy it as a
starting point when porting a new site.

## Contract

A plugin script must, at the top level:

1. Call `Register(json)` exactly once with its own metadata:
   ```js
   Register(JSON.stringify({
     id: "example-novel-site",  // stable slug; changing it creates a new library entry
     name: "Example Novel Site",
     lang: "en",
     baseUrl: "https://example.com",
     version: "1.0.0",          // optional, default "1.0.0"
     supportsLatest: true,      // optional, default true
     iconUrl: "https://example.com/favicon.ico", // optional — shown in Browse/Sources instead
                                 // of the generic default icon; JS sources have no APK to pull
                                 // one from like extension sources do, so supply your own (the
                                 // site's favicon, or e.g. `https://www.google.com/s2/favicons
                                 // ?sz=64&domain=example.com` if there's no clean direct one —
                                 // .ico/.svg URLs generally won't render, use a PNG-serving one)
     filters: [                 // optional, default none — see "Search filters" below
       {
         id: "genre",
         name: "Genre",
         options: [
           { label: "Fantasy", value: "fantasy" },
           { label: "Sci-fi", value: "sci-fi" },
         ],
       },
     ],
   }));
   ```
2. Assign `globalThis.source` to an object implementing:

   | function | returns |
   |---|---|
   | `popularNovels(page)` | JSON `{ novels: [{ title, url, cover? }], hasNextPage }` |
   | `latestNovels(page)` | same shape as `popularNovels` |
   | `searchNovels(query, page, filtersJson)` | same shape as `popularNovels` |
   | `novelDetails(url)` | JSON `{ title?, cover?, author?, description?, genres?, status? }` |
   | `chapterList(novelUrl)` | JSON array of `{ name, url, chapterNumber?, dateUpload?, locked? }`, chapter 1 first — `locked` (default false) flags a paywalled/premium chapter for a UI badge; `chapterContent()` on one should still return whatever preview text the site serves logged out, not an error |
   | `chapterContent(chapterUrl)` | plain string (not JSON) — the chapter body |

### Search filters

`filters` in `Register(...)` declares single-select dropdowns shown in the search filter sheet
(genre, category, etc — whatever single-value filter the site's search actually supports; most
sites only have one, and a plugin can just omit `filters` entirely if there's nothing useful to
filter by). Each one becomes a dropdown with an "Any" option prepended automatically.

`searchNovels`'s third argument, `filtersJson`, is a JSON object of `{ [filterId]: selectedValue }`
— only for filters the user actually picked something other than "Any" for, so a plain search with
no filters touched gets `"{}"`:
```js
searchNovels: function (query, page, filtersJson) {
  var filters = JSON.parse(filtersJson || "{}");
  var url = "https://example.com/search?q=" + encodeURIComponent(query) + "&page=" + page;
  if (filters.genre) url += "&genre=" + encodeURIComponent(filters.genre);
  // ...
},
```

`url` fields should be absolute URLs; the reader/library key chapters and novels by them.

## Available helpers

- `fetchApi(url, options)` — synchronous (no Promise); returns `{ text(), json() }`.
  `options.method` (default `GET`), `options.headers`, `options.body`.
- `Http.getCookie(url, name)` — reads a cookie previously set on the device's cookie jar for
  that URL's host (e.g. a CSRF token set by an earlier `Http.get`/`fetchApi` call). Needed by
  sites whose JSON APIs require a cookie value echoed back as a query param.
- `selectText(html, selector)` / `selectOwnText(html, selector)` — first match's (own) text.
- `selectAttr(html, selector, attr)` — first match's attribute value.
- `selectHtml(html, selector)` — first match's outer HTML.
- `selectAllText(html, selector)` / `selectAllAttr(html, selector, attr)` — arrays.

All HTML selection is backed by Jsoup (CSS selectors) on the Kotlin side, not a real DOM —
there's no live document, just parse-a-string-and-query each time you call one of these.

## Limitations (current MVP)

- Filters are single-select only (one dropdown per declared filter) — no multi-select, checkboxes,
  or sort options yet.
- No streaming/progressive chapter loading — `chapterContent` returns the whole chapter at once.
- Every call re-evaluates the plugin script in a fresh QuickJS instance (simplicity over
  performance for now); keep plugin scripts small.
