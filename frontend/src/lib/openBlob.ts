/**
 * Opens bytes fetched with an Authorization header in a new tab.
 *
 * Boletas and transfer receipts both live outside the public media root, so neither can be a plain
 * link — an anchor sends no header. The object url is revoked a minute later: long enough for the
 * tab to have loaded it, short enough that the blob is not held for the session.
 */
export function openBlobInNewTab(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
