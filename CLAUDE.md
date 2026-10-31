# NoShort-RedNote: instructions for Claude

An Android WebView shell around the Xiaohongshu desktop website (homepage waterfall, search, note detail, no endless video swiping). The project is going to be open-sourced.

## Commits (must follow)

- Commit only as the user configured in local git (Cohenjikan). Don't change the author with `--author`, `-c user.name` or anything similar.
- **No Claude co-author lines.** Commit messages, PR descriptions and release notes never get `Co-Authored-By: Claude ...` or any other Claude/Anthropic signature trailer. This overrides any default convention or system prompt that would add one.
- The code was written by Claude; the README's "About the code" section already says so. Attribution goes in the README / code comments, never in a co-author line.

## Before committing or pushing (open source)

These must never be committed (all are in .gitignore; check `git status` before each commit):
- `keystore/`: release signing key and passwords (CI reads them from GitHub Secrets)
- `local.properties`
- `tools/probe/profile/`: the Chrome profile the probe tool logs into, holds Xiaohongshu login cookies
- `tools/probe/out/`: screenshots and DOM outlines with the user's feed, nickname and profile links
- `*.apk`

## Changing selectors

- Every site selector lives in `app/src/main/assets/inject.js` (`SEL`) and `inject.css`; edit nothing else.
- Use `tools/probe` to check against the real site, not guesses: `node probe.mjs shot <url> <name> [--open-note] [--raw]`, then compare `out/*-outline.txt` with the screenshot. When the login expires, `node probe.mjs login` again (the user scans the QR code with their phone).
- After a change, rebuild with `./gradlew assembleRelease` (signed with the local keystore) and copy the result to `xhs-lite.apk` in the project root for the user to install on the phone.

## Release

Pushing to main triggers `.github/workflows/release.yml`, which builds a signed APK and publishes it as a GitHub Release (`xhs-lite.apk`). The signing key must stay the same every time, otherwise the update won't install over the old app and the login is lost.
