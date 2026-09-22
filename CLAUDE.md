# Kizuna

Fork of [Mihon](https://github.com/mihonapp/mihon) adding native light-novel reading alongside
Mihon's image-based (manga/manhwa/manhua) reading in one app. See [README](README.md) and
[NOTICE](NOTICE).

## Commit messages

**No trailers, ever.**

- Never add `Co-Authored-By:` — not for Claude, not for anyone.
- Never add `🤖 Generated with ...` or any other attribution/generation footer.

The title is an imperative summary, matching the existing log:

```
Add a bottom navigation bar and chapter list to the novel reader
Fix novel downloads losing their badge and failing permanently
Bump version to 0.29.0
```

A body is fine when the change needs one — why it was done, what was tried and rejected, or
anything a reviewer would otherwise have to reconstruct. Wrap it at 72 characters and keep it
about the change, not a file-by-file restatement of the diff. Skip it entirely when the title
already says everything.

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
