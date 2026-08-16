import type { OrderDto } from './api';

/**
 * The one place an order status becomes words a person reads.
 *
 * <p>These labels lived inside AccountPage as a local function, so the admin dispatch queue had no
 * way to reach them and printed the raw enum — "Estado: CREATED" — at whoever was packing. A second
 * copy would have been the more expensive mistake: two lists of the same eight statuses drift, and
 * then the same order reads one way to the customer and another to the shop.
 */
export type OrderStatus = OrderDto['status'];

const LABELS_ES: Record<OrderStatus, string> = {
  CREATED: 'Creado',
  PENDING_PAYMENT: 'Pendiente de pago',
  PAYMENT_UNDER_REVIEW: 'Pago en revision',
  PAID: 'Pagado',
  PREPARING_ORDER: 'Preparando pedido',
  SHIPPED: 'Enviado',
  DELIVERED: 'Entregado',
  CANCELLED: 'Cancelado',
};

const LABELS_EN: Record<OrderStatus, string> = {
  CREATED: 'Created',
  PENDING_PAYMENT: 'Pending payment',
  PAYMENT_UNDER_REVIEW: 'Payment under review',
  PAID: 'Paid',
  PREPARING_ORDER: 'Preparing order',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

/** Falls back to the raw status rather than an empty cell, so a new one is visible, not invisible. */
export function orderStatusLabel(status: OrderStatus, locale: 'es' | 'en' = 'es'): string {
  return (locale === 'es' ? LABELS_ES : LABELS_EN)[status] ?? status;
}
