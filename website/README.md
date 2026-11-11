# Stunt website

The project website at <https://stuntmock.openbrod.org>, built with [Astro](https://astro.build).

- `src/pages/index.astro` – landing page
- `src/pages/docs/[slug].astro` – renders the Markdown in the repository's `docs/` folder (one page per file)
- `src/pages/changelog.astro` – renders `CHANGELOG.md`
- `public/javadoc/` – not checked in; the workflow copies `build/docs/javadoc` here

```bash
cd website
npm install
npm run dev        # http://localhost:4321

# with JavaDoc
(cd .. && ./gradlew javadoc) && cp -r ../build/docs/javadoc public/javadoc
```

Deployed by `.github/workflows/pages.yml` on every push to `main` that touches the site, the docs, the changelog
or the main sources. GitHub Pages must be set to **Source: GitHub Actions** (Settings → Pages).
