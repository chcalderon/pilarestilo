UPDATE products
SET list_price_amount = ROUND(price_amount * 1.20, 2),
    list_price_currency = price_currency
WHERE list_price_amount IS NULL
  AND price_amount > 0;
