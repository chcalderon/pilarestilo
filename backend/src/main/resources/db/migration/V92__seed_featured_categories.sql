-- V92__seed_featured_categories.sql
-- V32 added categories.featured (DEFAULT FALSE) and nothing ever set it, so the homepage
-- "Por Categoria" carousel (index.astro: collectFeatured -> node.featured && node.active)
-- always fell to its empty state ("Proximamente."). Every seeded category already carries an
-- image_url, so turn them all on to get the carousel populated; the shop trims the set from
-- /admin/categories afterwards.
UPDATE categories
SET featured = TRUE
WHERE active = TRUE;
