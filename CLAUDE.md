# Kizuna

Fork of [Mihon](https://github.com/mihonapp/mihon) adding native light-novel reading alongside
Mihon's image-based (manga/manhwa/manhua) reading in one app. See [README](README.md) and
[NOTICE](NOTICE).

## Commit messages

**Title only. No body. No trailers.**

- One line, that's the whole message. Never add a body or bullet list beneath it.
- Never add `Co-Authored-By:` — not for Claude, not for anyone.
- Never add `🤖 Generated with ...` or any other attribution/generation footer.

Write it as an imperative summary of the change, matching the existing log:

```
Add a bottom navigation bar and chapter list to the novel reader
Fix novel downloads losing their badge and failing permanently
Bump version to 0.29.0
```

If a change genuinely needs more explanation than a title holds, the explanation belongs in the
code as a comment, or in `docs/` — not in the commit message.

## Before editing the novel reader UI

Read [docs/novel-reader-ui-notes.md](docs/novel-reader-ui-notes.md) first. It is a regression log,
not a design doc: each entry records a decision *and* the plausible-looking alternative that was
tried and reverted. Several rounds of edits have accidentally undone earlier requests.

Mihon's own image reader (`ReaderActivity`) is out of scope — never change it.

## Novel sources

JS plugins run in an embedded QuickJS engine. Contract:
[docs/novel-sources/README.md](docs/novel-sources/README.md). The bridge interfaces need explicit
`-keep` rules in [app/proguard-rules.pro](app/proguard-rules.pro) — R8 strips them otherwise, and
**debug builds cannot catch this**. Verify novel sources on a release APK before publishing.
