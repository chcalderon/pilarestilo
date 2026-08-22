import { useCallback, useEffect, useRef, useState } from 'react';
import { verifyStockForItem, type CartItem } from './cartStore';

export interface StockIssue {
  /**
   * SOLD_OUT: nothing left. SHORT: fewer units than the line asks for, fixable by lowering the
   * quantity. NEEDS_VARIANT: the product sells by size or colour and this line names neither, so
   * it can never be ordered however much stock exists — only re-adding from the product page
   * fixes it.
   */
  type: 'SOLD_OUT' | 'SHORT' | 'NEEDS_VARIANT';
  availableQty: number;
  productName: string;
}

/** Keyed by cart line id, not product id: the same product in two variants is two lines. */
export type StockIssues = Record<string, StockIssue>;

function productIdOf(item: CartItem): string {
  return item.productId || item.id.split('::')[0] || '';
}

/**
 * Checks every cart line against live stock, in parallel.
 *
 * <p>Plain async function rather than a hook so the rules can be tested without rendering
 * anything. The hook below is only the React glue: mount trigger, stale-response guard, state.
 *
 * <p>A line whose check could not run is deliberately left alone rather than flagged. Reporting
 * "sold out" because a request timed out would talk a customer out of a purchase they could have
 * completed — see `verified` in `verifyStockForItem`.
 */
export async function checkStockForItems(items: CartItem[]): Promise<StockIssues> {
  if (items.length === 0) return {};

  const results = await Promise.all(
    items.map(async (item) => {
      const productId = productIdOf(item);
      if (!productId) return null;

      const variant =
        item.variantColor || item.variantSize
          ? { color: item.variantColor, size: item.variantSize }
          : null;

      const result = await verifyStockForItem(productId, variant, item.quantity);
      if (result.ok) return null;

      if (result.reason === 'NEEDS_VARIANT') {
        return [
          item.id,
          { type: 'NEEDS_VARIANT' as const, availableQty: 0, productName: result.productName },
        ] as const;
      }

      return [
        item.id,
        {
          type: result.availableQty === 0 ? ('SOLD_OUT' as const) : ('SHORT' as const),
          availableQty: result.availableQty,
          productName: result.productName,
        },
      ] as const;
    })
  );

  const issues: StockIssues = {};
  for (const entry of results) {
    if (entry) issues[entry[0]] = entry[1];
  }
  return issues;
}

/**
 * Drops issues that no longer apply because the line left the cart or its quantity came down to
 * what is available. Returns the same object when nothing changed, so React can skip the render.
 */
export function pruneResolvedIssues(issues: StockIssues, items: CartItem[]): StockIssues {
  const next: StockIssues = {};
  for (const [lineId, issue] of Object.entries(issues)) {
    const item = items.find((candidate) => candidate.id === lineId);
    if (!item) continue;
    if (issue.type === 'SHORT' && item.quantity <= issue.availableQty) continue;
    next[lineId] = issue;
  }
  return Object.keys(next).length === Object.keys(issues).length ? issues : next;
}

/**
 * React glue over the two functions above.
 *
 * <p>One implementation, used by both the cart and the checkout review step. Writing the check
 * twice is how two copies drift until only one of them gets the next fix.
 *
 * <p>A line whose check could not run is deliberately left alone rather than flagged. Reporting
 * "sold out" because a request timed out would talk a customer out of a purchase they could have
 * completed — see `verified` in `verifyStockForItem`. The backend still refuses an order it
 * cannot fulfil, so this is about telling the customer early, not about correctness.
 */
export function useStockCheck(items: CartItem[]) {
  const [issues, setIssues] = useState<StockIssues>({});
  const [checking, setChecking] = useState(false);

  /** Discards a slow response once a newer check has started. */
  const runIdRef = useRef(0);

  const check = useCallback(async (): Promise<StockIssues> => {
    const runId = runIdRef.current + 1;
    runIdRef.current = runId;
    setChecking(true);
    try {
      const found = await checkStockForItems(items);
      /* A newer run started while this one was in flight; its answer is the current one. */
      if (runIdRef.current !== runId) return {};
      setIssues(found);
      return found;
    } finally {
      if (runIdRef.current === runId) setChecking(false);
    }
  }, [items]);

  /*
   * Keyed on which lines are present, not on the array identity.
   *
   * <p>Running once on mount looked right and was not: the cart store persists to localStorage
   * and zustand rehydrates a tick after the first render, so the check fired against an empty
   * cart, found nothing, and never ran again. Every test passed — they hand the hook its items
   * directly and never hydrate — while nothing was ever flagged in a browser.
   *
   * <p>Line ids rather than the items themselves, so adjusting a quantity does not fire a burst
   * of requests and make the badges flicker while the customer is still deciding.
   */
  const lineKey = items
    .map((item) => item.id)
    // Ordered so the same cart always produces the same key. Default sort compares strings by
    // UTF-16 code unit, not by locale: a dependency that changed with the browser's language
    // would refire the check for no reason.
    .sort()
    .join('|');
  useEffect(() => {
    void check();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lineKey]);

  useEffect(() => {
    setIssues((current) => pruneResolvedIssues(current, items));
  }, [items]);

  const clearIssue = useCallback((lineId: string) => {
    setIssues((current) => {
      if (!current[lineId]) return current;
      const next = { ...current };
      delete next[lineId];
      return next;
    });
  }, []);

  return { issues, checking, check, clearIssue };
}
