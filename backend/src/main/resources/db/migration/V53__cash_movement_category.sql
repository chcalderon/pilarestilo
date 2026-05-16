-- Add category column for semantic classification of cash movements
ALTER TABLE cash_movements ADD COLUMN IF NOT EXISTS category VARCHAR(30);

-- Add reference_type column for future polymorphic references
ALTER TABLE cash_movements ADD COLUMN IF NOT EXISTS reference_type VARCHAR(30);

-- Reclassify legacy SALE → CASH_SALE (IN direction)
UPDATE cash_movements SET category = 'CASH_SALE', type = 'IN'
WHERE type = 'SALE';

-- Reclassify legacy REFUND → REFUND category (OUT direction)
UPDATE cash_movements SET category = 'REFUND', type = 'OUT'
WHERE type = 'REFUND';

-- Any remaining IN without category → ADJUSTMENT
UPDATE cash_movements SET category = 'ADJUSTMENT'
WHERE category IS NULL AND type = 'IN';

-- Any remaining OUT without category → ADJUSTMENT
UPDATE cash_movements SET category = 'ADJUSTMENT'
WHERE category IS NULL AND type = 'OUT';
