import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { getCampaignDetail, refreshCampaignMetrics } from '../api';

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockClear();
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => ({}) });
});
afterEach(() => vi.unstubAllGlobals());

describe('campaign api', () => {
  it('URL-encodes the label in getCampaignDetail', async () => {
    await getCampaignDetail('Liquidación primavera', 'tok');
    const url = fetchMock.mock.calls[0][0] as string;
    expect(url).toContain('/campaigns/detail?label=Liquidaci%C3%B3n%20primavera');
  });

  it('URL-encodes the label in refreshCampaignMetrics and POSTs', async () => {
    await refreshCampaignMetrics('Verano & Sol', 'tok');
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain('label=Verano%20%26%20Sol');
    expect((init as RequestInit).method).toBe('POST');
  });
});
