// All API calls for Pilar Estilo go through this module only.

const PUBLIC_API_BASE: string =
  import.meta.env.PUBLIC_API_BASE_URL ?? '/api';

const INTERNAL_API_BASE: string =
  import.meta.env.INTERNAL_API_BASE_URL ?? 'http://backend:8080/api';

const API_BASE: string =
  typeof window === 'undefined' ? INTERNAL_API_BASE : PUBLIC_API_BASE;

// ─── Types ──────────────────────────────────────────────────────────────────

export interface MoneyDto {
  amount: number;
  currency: string;
}

export interface SizeStockDto {
  size: 'XS' | 'S' | 'M' | 'L' | 'XL' | 'UNICO';
  stock: number;
}

export interface ProductDto {
  id: string;
  name: string;
  description: string;
  price: MoneyDto;
  imageUrl: string;
  condition: 'NEW' | 'USED';
  brand: string;
  stock: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  avgRating?: number;
  reviewCount?: number;
  shippingOriginZone?: 'SANTIAGO' | 'RM' | 'REGIONES';
  sizeStocks?: SizeStockDto[];
  categorySlugs?: string[];
}

export interface WishlistDto {
  customerId: string;
  productIds: string[];
}

export interface ProductFilter {
  condition?: 'NEW' | 'USED';
  brand?: string;
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  active?: boolean;
  page?: number;
  size?: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface PaymentDto {
  id: string;
  orderId: string;
  method: string;
  status: string;
  proofReference?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt: string;
}

export interface OrderItemDto {
  id: string;
  productId: string;
  productName: string;
  unitPrice: MoneyDto;
  quantity: number;
}

export interface OrderDto {
  id: string;
  customerId: string;
  items: OrderItemDto[];
  subtotal: MoneyDto;
  discountAmount: MoneyDto;
  totalAmount: MoneyDto;
  paymentMethod: 'BANK_TRANSFER' | 'CASH_ON_DELIVERY' | 'AGREED_BY_WHATSAPP' | 'STORE_CREDIT' | 'PAYMENT_GATEWAY';
  notes?: string | null;
  status: 'CREATED' | 'PENDING_PAYMENT' | 'PAYMENT_UNDER_REVIEW' | 'PAID' | 'PREPARING_ORDER' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
  createdAt: string;
  updatedAt: string;
}

export interface CreateOrderItemRequest {
  productId: string;
  quantity: number;
}

export interface CreateOrderRequest {
  customerId: string;
  items: CreateOrderItemRequest[];
  paymentMethod: OrderDto['paymentMethod'];
  notes?: string;
  discountCode?: string;
}

export interface CreateProductRequest {
  name: string;
  description: string;
  price: MoneyDto;
  imageUrl: string;
  condition: 'NEW' | 'USED';
  brand: string;
  stock: number;
  active: boolean;
  categoryIds?: string[];
}

export interface UpdateProductRequest {
  name?: string;
  description?: string;
  price?: MoneyDto;
  imageUrl?: string;
  condition?: 'NEW' | 'USED';
  brand?: string;
  stock?: number;
  active?: boolean;
  categoryIds?: string[];
}

// ─── Fixture Fallback ───────────────────────────────────────────────────────

export const FIXTURE_PRODUCTS: ProductDto[] = [
  {
    id: 'fixture-1',
    name: 'Bolso Chanel Classic Flap',
    description: 'Bolso icónico de Chanel en piel de cordero acolchada. Hardware dorado. En excelente estado de conservación.',
    price: { amount: 850000, currency: 'CLP' },
    imageUrl: 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=600&q=80',
    condition: 'USED',
    brand: 'Chanel',
    stock: 1,
    active: true,
    createdAt: '2026-01-15T10:00:00Z',
    updatedAt: '2026-01-15T10:00:00Z',
  },
  {
    id: 'fixture-2',
    name: 'Cinturón Hermès Reversible',
    description: 'Cinturón reversible Hermès con hebilla H en metal dorado. Cuero negro/marrón. Talle 85.',
    price: { amount: 320000, currency: 'CLP' },
    imageUrl: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&q=80',
    condition: 'USED',
    brand: 'Hermès',
    stock: 1,
    active: true,
    createdAt: '2026-01-16T10:00:00Z',
    updatedAt: '2026-01-16T10:00:00Z',
  },
  {
    id: 'fixture-3',
    name: 'Zapatillas Gucci Ace',
    description: 'Zapatillas Gucci Ace de cuero blanco con bordado de abeja y flores. Talle 38. Sin uso.',
    price: { amount: 410000, currency: 'CLP' },
    imageUrl: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&q=80',
    condition: 'NEW',
    brand: 'Gucci',
    stock: 1,
    active: true,
    createdAt: '2026-01-17T10:00:00Z',
    updatedAt: '2026-01-17T10:00:00Z',
  },
  {
    id: 'fixture-4',
    name: 'Lentes Louis Vuitton My LV',
    description: 'Anteojos de sol Louis Vuitton My LV con montura acetato en negro. Protección UV400.',
    price: { amount: 180000, currency: 'CLP' },
    imageUrl: 'https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=600&q=80',
    condition: 'NEW',
    brand: 'Louis Vuitton',
    stock: 2,
    active: true,
    createdAt: '2026-01-18T10:00:00Z',
    updatedAt: '2026-01-18T10:00:00Z',
  },
  {
    id: 'fixture-5',
    name: 'Pañuelo Hermès Carré 90',
    description: 'Pañuelo de seda Hermès 90x90cm. Diseño Jungle Love. Colores vibrantes. En caja original.',
    price: { amount: 95000, currency: 'CLP' },
    imageUrl: 'https://images.unsplash.com/photo-1601924994987-69e26d50dc26?w=600&q=80',
    condition: 'NEW',
    brand: 'Hermès',
    stock: 3,
    active: true,
    createdAt: '2026-01-19T10:00:00Z',
    updatedAt: '2026-01-19T10:00:00Z',
  },
  {
    id: 'fixture-6',
    name: 'Cartera Prada Saffiano',
    description: 'Cartera mediana Prada en cuero saffiano negro con logo triangular. Compartimentos interiores. Como nueva.',
    price: { amount: 540000, currency: 'CLP' },
    imageUrl: 'https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=600&q=80',
    condition: 'USED',
    brand: 'Prada',
    stock: 1,
    active: true,
    createdAt: '2026-01-20T10:00:00Z',
    updatedAt: '2026-01-20T10:00:00Z',
  },
];

// ─── HTTP Helpers ────────────────────────────────────────────────────────────

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${API_BASE}${path}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(init?.headers ?? {}),
  };
  const res = await fetch(url, {
    ...init,
    headers,
  });
  if (!res.ok) {
    throw new Error(`API error ${res.status} for ${url}`);
  }
  if (res.status === 204) return undefined as unknown as T;
  return res.json() as Promise<T>;
}

function buildQuery(params: Record<string, unknown>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null);
  if (!entries.length) return '';
  return '?' + entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&');
}

// ─── Normalizers ─────────────────────────────────────────────────────────────
// Backend serializes Money as flat fields (priceAmount/priceCurrency).
// Frontend types use nested price: { amount, currency }.

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function normalizeProduct(raw: any): ProductDto {
  return {
    ...raw,
    price: raw.price ?? { amount: raw.priceAmount, currency: raw.priceCurrency ?? 'CLP' },
  };
}

function authHeaders(token?: string): Record<string, string> {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function toProductMutationBody(data: CreateProductRequest | UpdateProductRequest) {
  const { price, ...rest } = data;
  return {
    ...rest,
    ...(price ? { priceAmount: price.amount, priceCurrency: price.currency } : {}),
  };
}

// ─── API Functions ───────────────────────────────────────────────────────────

export async function getProducts(filter?: ProductFilter): Promise<Page<ProductDto>> {
  try {
    const query = buildQuery((filter ?? {}) as Record<string, unknown>);
    const page = await apiFetch<Page<unknown>>(`/products${query}`);
    return { ...page, content: page.content.map(normalizeProduct) };
  } catch {
    return {
      content: FIXTURE_PRODUCTS,
      totalElements: FIXTURE_PRODUCTS.length,
      totalPages: 1,
      size: FIXTURE_PRODUCTS.length,
      number: 0,
    };
  }
}

export async function getProduct(id: string): Promise<ProductDto> {
  try {
    const raw = await apiFetch<unknown>(`/products/${encodeURIComponent(id)}`);
    return normalizeProduct(raw);
  } catch {
    const fixture = FIXTURE_PRODUCTS.find((p) => p.id === id);
    if (fixture) return fixture;
    throw new Error(`Product ${id} not found`);
  }
}

export async function getFeaturedProducts(): Promise<ProductDto[]> {
  const query = buildQuery({ active: true, size: 8, page: 0 });

  try {
    const firstPage = await apiFetch<Page<unknown>>(`/products${query}`);
    return firstPage.content.map(normalizeProduct);
  } catch {
    // Retry once for transient upstream hiccups before falling back.
    await new Promise((resolve) => setTimeout(resolve, 180));
    try {
      const secondPage = await apiFetch<Page<unknown>>(`/products${query}`);
      return secondPage.content.map(normalizeProduct);
    } catch {
      return FIXTURE_PRODUCTS.slice(0, 8);
    }
  }
}

export async function createProduct(data: CreateProductRequest, token?: string): Promise<ProductDto> {
  const raw = await apiFetch<unknown>('/products', {
    method: 'POST',
    body: JSON.stringify(toProductMutationBody(data)),
    headers: authHeaders(token),
  });
  return normalizeProduct(raw);
}

export async function updateProduct(id: string, data: UpdateProductRequest, token?: string): Promise<ProductDto> {
  const raw = await apiFetch<unknown>(`/products/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(toProductMutationBody(data)),
    headers: authHeaders(token),
  });
  return normalizeProduct(raw);
}

export async function deleteProduct(id: string, token?: string): Promise<void> {
  await apiFetch<void>(`/products/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
}

async function listPaymentsByStatus(status: string, token?: string): Promise<PaymentDto[]> {
  const size = 100;
  let pageNumber = 0;
  let totalPages = 1;
  const all: PaymentDto[] = [];

  while (pageNumber < totalPages && pageNumber < 50) {
    const query = buildQuery({
      status,
      page: pageNumber,
      size,
      sort: 'createdAt,desc',
    });

    const page = await apiFetch<Page<PaymentDto>>(`/payments${query}`, {
      headers: authHeaders(token),
    });

    all.push(...(page.content ?? []));
    totalPages = Math.max(page.totalPages ?? 1, 1);
    pageNumber += 1;
  }

  return all;
}

export async function getPendingPayments(token?: string): Promise<PaymentDto[]> {
  try {
    return await listPaymentsByStatus('PENDING', token);
  } catch {
    return [];
  }
}

export async function getReviewQueuePayments(token?: string): Promise<PaymentDto[]> {
  try {
    const [submitted, underReview] = await Promise.all([
      listPaymentsByStatus('SUBMITTED', token),
      listPaymentsByStatus('UNDER_REVIEW', token),
    ]);

    const merged = [...submitted, ...underReview];
    const byId = new Map<string, PaymentDto>();
    for (const payment of merged) {
      byId.set(payment.id, payment);
    }

    return [...byId.values()].sort(
      (a, b) => new Date(String(b.createdAt)).getTime() - new Date(String(a.createdAt)).getTime()
    );
  } catch {
    return [];
  }
}

export async function approvePayment(paymentId: string, reviewerId: string, token?: string): Promise<void> {
  await apiFetch<void>(`/payments/${encodeURIComponent(paymentId)}/review`, {
    method: 'PATCH',
    body: JSON.stringify({ action: 'APPROVE', reviewerId }),
    headers: authHeaders(token),
  });
}

export async function rejectPayment(paymentId: string, reviewerId: string, token?: string): Promise<void> {
  await apiFetch<void>(`/payments/${encodeURIComponent(paymentId)}/review`, {
    method: 'PATCH',
    body: JSON.stringify({ action: 'REJECT', reviewerId }),
    headers: authHeaders(token),
  });
}

// ─── Auth Types ───────────────────────────────────────────────────────────────

export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  userId: string;
  email: string;
  role: string;
}

export interface ReviewDto {
  id: string;
  productId: string;
  userId: string;
  rating: number;
  title: string | null;
  comment: string | null;
  approved: boolean;
  createdAt: string;
}

export interface ReviewSummaryDto {
  productId: string;
  avgRating: number;
  count: number;
}

// ─── Auth API ─────────────────────────────────────────────────────────────────

export async function loginUser(email: string, password: string): Promise<AuthTokenResponse> {
  return apiFetch<AuthTokenResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export async function registerUser(
  email: string,
  password: string,
  fullName: string
): Promise<AuthTokenResponse> {
  return apiFetch<AuthTokenResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password, fullName }),
  });
}

export async function getAuthMe(token: string): Promise<{ id: string; email: string; role: string }> {
  return apiFetch<{ id: string; email: string; role: string }>('/auth/me', {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function getMyOrders(token: string, page = 0, size = 20): Promise<Page<OrderDto>> {
  try {
    const query = buildQuery({ page, size, sort: 'createdAt,desc' });
    return await apiFetch<Page<OrderDto>>(`/orders/mine${query}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    return { content: [], totalElements: 0, totalPages: 0, size, number: page };
  }
}

export async function createOrder(data: CreateOrderRequest, token: string): Promise<OrderDto> {
  return apiFetch<OrderDto>('/orders', {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { Authorization: `Bearer ${token}` },
  });
}

// ─── Review API ───────────────────────────────────────────────────────────────

export async function getProductReviews(productId: string): Promise<ReviewDto[]> {
  try {
    return await apiFetch<ReviewDto[]>(`/products/${encodeURIComponent(productId)}/reviews`);
  } catch {
    return [];
  }
}

export async function getReviewSummary(productId: string): Promise<ReviewSummaryDto | null> {
  try {
    return await apiFetch<ReviewSummaryDto>(
      `/products/${encodeURIComponent(productId)}/reviews/summary`
    );
  } catch {
    return null;
  }
}

export async function createReview(
  productId: string,
  token: string,
  data: { rating: number; title?: string; comment?: string }
): Promise<ReviewDto> {
  return apiFetch<ReviewDto>(`/products/${encodeURIComponent(productId)}/reviews`, {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function deleteReview(reviewId: string, token: string): Promise<void> {
  await apiFetch<void>(`/reviews/${encodeURIComponent(reviewId)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function getMyReviews(token: string): Promise<ReviewDto[]> {
  try {
    return await apiFetch<ReviewDto[]>('/reviews/mine', {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    return [];
  }
}

export async function getAdminReviews(token: string, approved?: boolean): Promise<ReviewDto[]> {
  const query = approved !== undefined ? `?approved=${approved}` : '';
  return apiFetch<ReviewDto[]>(`/reviews${query}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function approveReview(reviewId: string, token: string): Promise<ReviewDto> {
  return apiFetch<ReviewDto>(`/reviews/${encodeURIComponent(reviewId)}/approve`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
  });
}

// ─── Category Types ───────────────────────────────────────────────────────────

export interface CategoryDto {
  id: string;
  slug: string;
  nameEs: string;
  nameEn: string;
  parentId: string | null;
  sortOrder: number;
  active: boolean;
  imageUrl?: string;
}

export interface CategoryTreeNode extends CategoryDto {
  children: CategoryTreeNode[];
}

export interface CreateCategoryRequest {
  slug: string;
  nameEs: string;
  nameEn: string;
  parentId?: string;
  sortOrder: number;
  imageUrl?: string;
}

// ─── Category API ─────────────────────────────────────────────────────────────

export async function getCategories(): Promise<CategoryDto[]> {
  try {
    return await apiFetch<CategoryDto[]>('/categories');
  } catch {
    return [];
  }
}

export async function getCategoryTree(): Promise<CategoryTreeNode[]> {
  try {
    return await apiFetch<CategoryTreeNode[]>('/categories/tree');
  } catch {
    return [];
  }
}

export async function createCategory(data: CreateCategoryRequest, token: string): Promise<CategoryDto> {
  return apiFetch<CategoryDto>('/categories', {
    method: 'POST',
    body: JSON.stringify(data),
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function updateCategory(
  id: string,
  data: Partial<CreateCategoryRequest & { active: boolean }>,
  token: string
): Promise<CategoryDto> {
  return apiFetch<CategoryDto>(`/categories/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function deleteCategory(id: string, token: string): Promise<void> {
  await apiFetch<void>(`/categories/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
}

// ─── Search API ───────────────────────────────────────────────────────────────

export async function searchProducts(q: string, page = 0, size = 12): Promise<Page<ProductDto>> {
  try {
    const query = buildQuery({ q, page, size });
    const res = await apiFetch<Page<unknown>>(`/products/search${query}`);
    return { ...res, content: res.content.map(normalizeProduct) };
  } catch {
    const lower = q.toLowerCase();
    const matches = FIXTURE_PRODUCTS.filter(
      p => p.name.toLowerCase().includes(lower) || p.brand.toLowerCase().includes(lower)
    );
    return { content: matches, totalElements: matches.length, totalPages: 1, size: matches.length, number: 0 };
  }
}

// ─── Wishlist API ─────────────────────────────────────────────────────────────

export async function getWishlist(token: string): Promise<WishlistDto> {
  return apiFetch<WishlistDto>('/wishlist', {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function addToWishlist(productId: string, token: string): Promise<void> {
  await apiFetch<void>(`/wishlist/items/${encodeURIComponent(productId)}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function removeFromWishlist(productId: string, token: string): Promise<void> {
  await apiFetch<void>(`/wishlist/items/${encodeURIComponent(productId)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
}
