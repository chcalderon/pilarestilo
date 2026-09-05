import { describe, expect, it } from 'vitest';
import { __test__normalizeProduct as normalizeProduct } from '../api';

describe('normalizeProduct — galleryImageUrls', () => {
  it('passes the backend list through', () => {
    const p = normalizeProduct({
      id: 'p1', name: 'x', description: '', priceAmount: 1000, priceCurrency: 'CLP',
      imageUrl: '/c.jpg', condition: 'NEW', brand: 'b', stock: 1, active: true,
      createdAt: '', updatedAt: '', galleryImageUrls: ['/1.jpg', '/2.jpg'],
    });
    expect(p.galleryImageUrls).toEqual(['/1.jpg', '/2.jpg']);
  });

  it('defaults a missing list to []', () => {
    const p = normalizeProduct({
      id: 'p1', name: 'x', description: '', priceAmount: 1000, priceCurrency: 'CLP',
      imageUrl: '/c.jpg', condition: 'NEW', brand: 'b', stock: 1, active: true,
      createdAt: '', updatedAt: '',
    });
    expect(p.galleryImageUrls).toEqual([]);
  });
});
