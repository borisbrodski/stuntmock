import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';

// The documentation is the Markdown in the repository's docs/ folder, so the site and GitHub show the same text.
const docs = defineCollection({
  loader: glob({ pattern: '*.md', base: '../docs' }),
});

const changelog = defineCollection({
  loader: glob({ pattern: 'CHANGELOG.md', base: '..' }),
});

export const collections = { docs, changelog };
