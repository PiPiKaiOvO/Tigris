# Tigris website

Official site for Tigris, an independent open-source Android TV launcher.
Static Astro build, deployed to Cloudflare Pages. **Not affiliated with Apple.**

## Commands

```sh
pnpm install                     # once
pnpm exec astro build            # -> dist/
pnpm exec astro check            # type check (0 errors expected)
python scripts/build-assets.py   # regenerate WebP + OG card from ../docs/screenshots
python scripts/preview.py        # serve dist/ at http://127.0.0.1:8789
```

Deploy the built folder, not the sources:

```sh
cd dist && wrangler pages deploy . --project-name tigris-tv --branch main
```

## Layout

```
src/data/site.ts        brand facts, asset paths, every ADB command string
src/data/zh.ts          Chinese copy (source of truth for the shape)
src/data/en.ts          English copy, checked with `satisfies Copy`
src/data/lang.ts        the Copy interface both locales must satisfy
src/data/strings.ts     short UI labels used by the client script
src/components/*.astro  Header, Hero, Aerial, Features, Compare, Gallery,
                        Install, Compatibility, Faq, Footer
src/pages/index.astro   /            (Chinese)
src/pages/en/index.astro /en/        (English)
src/pages/[lang].astro  /zh/, /en/ fallback bodies for direct links
public/img/*            optimised WebP captures + OG card + illustrations
public/404.html         static 404 served by Cloudflare Pages
```

## Content rules (do not regress)

- **Screenshots** come from `TVHome/docs/screenshots/{home,grid,shelf,cc,frame}.jpg`
  and are converted by `scripts/build-assets.py`. Never substitute a generated
  image for a capture.
- **The "before" pane** of the comparison is an SVG illustration and is labelled
  「安装前示意图（非同一设备实拍）」 / "illustration, not the same TV" in the UI.
- **The aerial screensaver** is shown as a labelled motion illustration
  (`public/img/aerial-illustration.svg`). No fabricated aerial frame is claimed
  to be a real capture; the photo frame next to it *is* a real capture.
- **Commands carry no shell variables.** `com.leslie.tvhome` is spelled out so a
  snippet pastes into bash, zsh, Windows PowerShell or CMD. Windows differences
  (`curl.exe`, `python` instead of `python3`) are stated beside the snippets.
- **Never suggest disabling the Xiaomi stock launcher** (`com.mitv.tvhome`).
  Both languages say so explicitly and offer the home guard instead.
- **Every download link** points at GitHub `releases/latest/download/…`.
- Adding a UI string means adding it to `zh.ts`, `en.ts` and, if the client
  script uses it, `strings.ts`; `astro check` fails on drift.
