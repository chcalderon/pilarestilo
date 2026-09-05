const TZ = 'America/Santiago';

/** Parse "YYYY-MM-DD HH:MM:SS" (sv-SE locale output) into a UTC epoch ms. */
function parseSvSe(s: string): number {
  const [d, t] = s.split(' ');
  const [y, mo, day] = d.split('-').map(Number);
  const [h, mi, sec] = t.split(':').map(Number);
  return Date.UTC(y, mo - 1, day, h, mi, sec || 0);
}

/**
 * A Santiago wall-clock string ("2026-06-15T10:00", seconds optional) -> UTC ISO instant.
 * Iterative: guess the instant is the wall time in UTC, see what Santiago clock that shows,
 * correct by the delta. Converges in <= 2 passes even across a DST boundary.
 */
export function santiagoWallTimeToInstant(local: string): string {
  const [d, t] = local.split('T');
  const [y, mo, day] = d.split('-').map(Number);
  const [h, mi] = t.split(':').map(Number);
  const target = Date.UTC(y, mo - 1, day, h, mi, 0);
  let guess = target;
  for (let i = 0; i < 3; i++) {
    const shown = parseSvSe(new Date(guess).toLocaleString('sv-SE', { timeZone: TZ }));
    const delta = target - shown;
    if (delta === 0) break;
    guess += delta;
  }
  return new Date(guess).toISOString();
}

export function instantToSantiagoLabel(iso: string): string {
  return new Date(iso).toLocaleString('es-CL', {
    timeZone: TZ,
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function instantToSantiagoInputValue(iso: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: TZ,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(new Date(iso));
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '00';
  const hour = get('hour') === '24' ? '00' : get('hour');
  return `${get('year')}-${get('month')}-${get('day')}T${hour}:${get('minute')}`;
}
