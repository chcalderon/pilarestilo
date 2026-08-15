-- Lets an admin state which attributes a product's variants use, instead of the storefront
-- inferring it.
--
-- Until now the pair of variant attributes (Color/Talla, Color/Número, Material/Diseño, …) was
-- resolved from the product's categories through a fixed priority list. Three consequences the
-- admin could neither see nor control: a product in two categories got whichever category won the
-- priority order, moving a product to another category silently relabelled its variants, and a
-- product that did not follow its category's convention had no way out.
--
-- Nullable on purpose, and left null for every existing row. Null keeps the old behaviour —
-- derive from the categories — so nothing changes until someone chooses. There is no correct
-- backfill: the inferred value is a guess, and writing it down would turn a guess into a stated
-- fact.
ALTER TABLE products
  ADD COLUMN variant_type VARCHAR(20);

COMMENT ON COLUMN products.variant_type IS
  'Which variant attribute pair this product uses. NULL means derive it from the categories, which is what every row did before V69.';

-- Values match the CategoryType enum the storefront already knows. A CHECK rather than an enum
-- type: the set grows with the catalogue, and altering a CHECK is cheaper than altering a type.
ALTER TABLE products
  ADD CONSTRAINT chk_products_variant_type
  CHECK (variant_type IS NULL OR variant_type IN (
    'GENERIC', 'CLOTHING', 'SHOES', 'JEWELRY', 'ACCESSORY', 'COLLECTION', 'SEASON'
  ));
