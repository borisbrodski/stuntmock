import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://stuntmock.openbrod.org',
  trailingSlash: 'always',
  markdown: {
    shikiConfig: { themes: { light: 'github-light', dark: 'one-dark-pro' } },
  },
});
