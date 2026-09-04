import { useState, useEffect, useRef, useCallback } from 'react';
import { User, Star, ShoppingBag, Trash2, Loader2, Camera, MapPin, X } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie, type StoredUser } from '../../lib/authStore';
import NotificationHistory from '../NotificationHistory';
import { orderStatusLabel } from '../../lib/orderStatusLabels';
import RetractoButton from './RetractoButton';
import { openBlobInNewTab } from '../../lib/openBlob';
import {
  emptyAddressDraft,
  draftFromAddress,
  useCityOptions,
  useComunaOptions,
  type AddressDraft,
} from '../checkout/useAddressBook';
import {
  getMyReviews,
  getLocationTree,
  deleteReview,
  getMyOrders,
  getMyReturns,
  type ReturnRequestDto,
  getMyProfile,
  updateMyProfile,
  changeMyPassword,
  getMyAddresses,
  createMyAddress,
  updateMyAddress,
  deleteMyAddress,
  setMyAddressAsDefault,
  confirmOrderDelivery,
  getPaymentByOrder,
  submitPaymentProof,
  uploadPaymentProof,
  fetchPaymentProof,
  uploadMyAvatar,
  createGatewayCheckoutSession,
  simulateGatewayPaymentStatus,
  getPublicShippingConfig,
  type ReviewDto,
  type OrderDto,
  type PaymentDto,
  type UserProfileDto,
  type CustomerAddressDto,
  type CreateCustomerAddressRequest,
  type LocationCityDto,
  type LocationCommuneDto,
  type LocationRegionDto,
  type ShippingZoneConfig,
} from '../../lib/api';

interface Props {
  readonly locale: 'es' | 'en';
}

/** Picks one of two locale-paired labels by a boolean condition, without nesting ternaries. */
function toggleLabel(
  condition: boolean,
  es: boolean,
  trueEs: string,
  trueEn: string,
  falseEs: string,
  falseEn: string,
): string {
  if (condition) return es ? trueEs : trueEn;
  return es ? falseEs : falseEn;
}

type Tab = 'profile' | 'reviews' | 'orders' | 'addresses' | 'notifications';
type ProofFeedback = { type: 'success' | 'error' | 'warning'; text: string };
/**
 * 'skipped' is a step the order will never reach because it ended early. Rendering those as
 * 'todo' said the journey was still going, which is what a cancelled order looked like.
 */
type TimelineState = 'done' | 'current' | 'todo' | 'skipped' | 'ended';
type TimelineStepStatus = Exclude<OrderDto['status'], 'CANCELLED'>;
type NotificationChannelPreference = 'AUTO' | 'WHATSAPP' | 'EMAIL' | 'BOTH';
const ORDER_TIMELINE_FLOW: TimelineStepStatus[] = [
  'CREATED',
  'PENDING_PAYMENT',
  'PAYMENT_UNDER_REVIEW',
  'PAID',
  'PREPARING_ORDER',
  'SHIPPED',
  'DELIVERED',
];

function sanitizePhoneDraft(value: string | null | undefined): string {
  if (!value) return '';
  const trimmed = value.trim();
  if (!trimmed) return '';
  const digits = trimmed.replace(/\D/g, '');
  return digits.length >= 8 && digits.length <= 15 ? trimmed : '';
}

export function errorMessageOr(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

export function validateProfileDraft(fullName: string, rawPhone: string, es: boolean): string | null {
  if (!fullName.trim()) return es ? 'El nombre no puede estar vacio.' : 'Full name cannot be empty.';
  if (rawPhone.includes('@')) {
    return es ? 'Ingresa un telefono valido, no un correo.' : 'Enter a valid phone number, not an email.';
  }
  const digits = rawPhone.replace(/\D/g, '');
  if (rawPhone && (digits.length < 8 || digits.length > 15)) {
    return es ? 'El telefono debe tener entre 8 y 15 digitos.' : 'Phone must contain between 8 and 15 digits.';
  }
  return null;
}

export function validatePasswordChange(
  currentPassword: string, newPassword: string, confirmPassword: string, es: boolean,
): string | null {
  if (!currentPassword.trim()) return es ? 'Ingresa tu contraseña actual.' : 'Enter your current password.';
  if (newPassword.length < 8) {
    return es ? 'La nueva contraseña debe tener al menos 8 caracteres.' : 'New password must have at least 8 characters.';
  }
  if (newPassword !== confirmPassword) return es ? 'Las contraseñas nuevas no coinciden.' : 'New passwords do not match.';
  return null;
}

export function passwordChangeErrorMessage(error: unknown, es: boolean): string {
  const raw = error instanceof Error ? error.message.toLowerCase() : '';
  if (raw.includes('current password is invalid')) {
    return es ? 'La contraseña actual no es correcta.' : 'Current password is incorrect.';
  }
  return errorMessageOr(error, es ? 'No pudimos cambiar la contraseña.' : 'Could not change password.');
}

function validateAddressContactFields(draft: AddressDraft, es: boolean): string | null {
  if (!draft.label.trim()) return es ? 'Debes ingresar un alias de dirección.' : 'Address alias is required.';
  if (!draft.recipientName.trim()) return es ? 'Debes ingresar destinatario.' : 'Recipient name is required.';
  if (!draft.phone.trim()) return es ? 'Debes ingresar teléfono.' : 'Phone is required.';
  const digits = draft.phone.replace(/\D/g, '');
  if (digits.length < 8 || digits.length > 15) {
    return es ? 'El teléfono debe tener entre 8 y 15 dígitos.' : 'Phone must contain between 8 and 15 digits.';
  }
  return null;
}

function validateAddressLocationFields(draft: AddressDraft, es: boolean): string | null {
  if (!draft.line1.trim()) return es ? 'Debes ingresar dirección.' : 'Address line is required.';
  if (!draft.regionId) return es ? 'Debes seleccionar region.' : 'Region selection is required.';
  if (!draft.cityId) return es ? 'Debes seleccionar ciudad.' : 'City selection is required.';
  if (!draft.comunaId) return es ? 'Debes seleccionar comuna.' : 'Comuna selection is required.';
  return null;
}

export function validateAddressDraft(draft: AddressDraft, es: boolean): string | null {
  return validateAddressContactFields(draft, es) ?? validateAddressLocationFields(draft, es);
}

export interface QueryParamResolution {
  readonly tab: Tab | null;
  readonly gatewayFeedback: ProofFeedback | null;
  readonly cleanedUrl: string | null;
}

const MP_RETURN_KEYS = [
  'mp', 'collection_status', 'status', 'payment_id', 'payment_type', 'merchant_order_id',
  'preference_id', 'external_reference', 'site_id', 'processing_mode', 'merchant_account_id',
];

function gatewayFallbackFeedback(es: boolean): ProofFeedback {
  return {
    type: 'warning',
    text: es
      ? 'Tu pedido ya está creado. No pudimos abrir la pasarela de pago automáticamente — puedes pagarlo aquí abajo cuando quieras.'
      : 'Your order was created. We could not open the payment gateway automatically — you can pay it below whenever you like.',
  };
}

function gatewayReturnFeedbackFor(normalizedSignal: string, es: boolean): ProofFeedback | null {
  if (normalizedSignal === 'success' || normalizedSignal === 'approved') {
    return {
      type: 'success',
      text: es
        ? 'Pago confirmado por pasarela. Estamos actualizando tu pedido.'
        : 'Gateway payment confirmed. We are updating your order.',
    };
  }
  if (normalizedSignal === 'pending' || normalizedSignal === 'in_process' || normalizedSignal === 'inprocess') {
    return {
      type: 'success',
      text: es
        ? 'Pago recibido como pendiente. Te avisaremos cuando quede confirmado.'
        : 'Payment received as pending. We will notify you once it is confirmed.',
    };
  }
  if (normalizedSignal === 'failure' || normalizedSignal === 'rejected' || normalizedSignal === 'cancelled') {
    return {
      type: 'error',
      text: es
        ? 'El pago fue rechazado o cancelado. Puedes reintentar cuando quieras.'
        : 'Payment was rejected or cancelled. You can retry anytime.',
    };
  }
  return null;
}

/**
 * Reads the tab-switch and payment-gateway-return query params out of `url`, without touching
 * React state or history -- a pure function so the query-param cascade (S3776) can be unit tested
 * without mounting the component.
 */
export function resolveQueryParamEffects(url: URL, es: boolean): QueryParamResolution {
  const searchParams = url.searchParams;
  const requestedTab = searchParams.get('tab');
  const tab = requestedTab === 'profile' || requestedTab === 'reviews' || requestedTab === 'orders'
    || requestedTab === 'addresses' || requestedTab === 'notifications'
    ? requestedTab
    : null;

  /*
   * Not a Mercado Pago return signal -- CheckoutPage sets this itself when it could not even
   * start a gateway session (the redirect used to just fail silently: the order exists, but
   * nothing said why she landed here instead of at Mercado Pago). Checked separately from the
   * mp/collection_status/status signals below since 'fallback' is never a value MP itself sends.
   */
  if ((searchParams.get('gw') ?? '').trim().toLowerCase() === 'fallback') {
    searchParams.delete('gw');
    const nextQuery = searchParams.toString();
    const cleanedUrl = nextQuery ? `${url.pathname}?${nextQuery}` : url.pathname;
    return { tab: 'orders', gatewayFeedback: gatewayFallbackFeedback(es), cleanedUrl };
  }

  const mpSignal = (searchParams.get('mp') ?? '').trim().toLowerCase();
  const collectionStatusSignal = (searchParams.get('collection_status') ?? '').trim().toLowerCase();
  const genericStatusSignal = (searchParams.get('status') ?? '').trim().toLowerCase();
  const normalizedSignal = mpSignal || collectionStatusSignal || genericStatusSignal;

  if (!normalizedSignal) {
    return { tab, gatewayFeedback: null, cleanedUrl: null };
  }

  const gatewayFeedback = gatewayReturnFeedbackFor(normalizedSignal, es);
  MP_RETURN_KEYS.forEach((key) => searchParams.delete(key));
  const nextQuery = searchParams.toString();
  const cleanedUrl = nextQuery ? `${url.pathname}?${nextQuery}` : url.pathname;

  return { tab: 'orders', gatewayFeedback, cleanedUrl };
}

/** Editing sets the default flag with a follow-up call since updateMyAddress doesn't take it;
 * creating only needs the follow-up when the server didn't already honor isDefault itself. */
async function persistAddress(
  payload: CreateCustomerAddressRequest,
  editingAddressId: string | null,
  token: string,
): Promise<void> {
  if (editingAddressId) {
    await updateMyAddress(editingAddressId, {
      label: payload.label,
      recipientName: payload.recipientName,
      phone: payload.phone,
      line1: payload.line1,
      line2: payload.line2,
      regionId: payload.regionId,
      cityId: payload.cityId,
      comunaId: payload.comunaId,
      comuna: payload.comuna,
      city: payload.city,
      region: payload.region,
      reference: payload.reference,
    }, token);
    if (payload.isDefault) {
      await setMyAddressAsDefault(editingAddressId, token);
    }
    return;
  }
  const created = await createMyAddress(payload, token);
  if (payload.isDefault && !created.isDefault) {
    await setMyAddressAsDefault(created.id, token);
  }
}

function paymentStatusLabel(status: string, es: boolean) {
  const labelsEs: Record<string, string> = {
    PENDING: 'Pendiente',
    SUBMITTED: 'Enviado',
    UNDER_REVIEW: 'En revision',
    APPROVED: 'Aprobado',
    REJECTED: 'Rechazado',
  };
  const labelsEn: Record<string, string> = {
    PENDING: 'Pending',
    SUBMITTED: 'Submitted',
    UNDER_REVIEW: 'Under review',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
  };
  return (es ? labelsEs : labelsEn)[status] ?? status;
}

function canSubmitProof(order: OrderDto, payment: PaymentDto | undefined) {
  if (order.paymentMethod !== 'TRANSFER') return false;
  if (!payment) return false;
  return payment.status === 'PENDING' || payment.status === 'SUBMITTED';
}

function canSimulateGateway(order: OrderDto, payment: PaymentDto | undefined) {
  if (order.paymentMethod !== 'WEBPAY' && order.paymentMethod !== 'MERCADOPAGO') return false;
  if (!payment) return false;
  return payment.status !== 'APPROVED' && payment.status !== 'REJECTED';
}

function formatMoney(amount: number, currency: string, es: boolean) {
  return new Intl.NumberFormat(es ? 'es-CL' : 'en-US', {
    style: 'currency',
    currency: currency || 'CLP',
    maximumFractionDigits: 0,
  }).format(amount ?? 0);
}

/** Accepts CANCELLED too: it is a terminal node on the track, not a step on the way. */
function orderTimelineLabel(status: TimelineStepStatus | 'CANCELLED', es: boolean) {
  if (status === 'CANCELLED') return es ? 'Cancelado' : 'Cancelled';
  const labelsEs: Record<TimelineStepStatus, string> = {
    CREATED: 'Creado',
    PENDING_PAYMENT: 'Pago pendiente',
    PAYMENT_UNDER_REVIEW: 'En revision',
    PAID: 'Pagado',
    PREPARING_ORDER: 'Preparacion',
    SHIPPED: 'Enviado',
    DELIVERED: 'Entregado',
  };
  const labelsEn: Record<TimelineStepStatus, string> = {
    CREATED: 'Created',
    PENDING_PAYMENT: 'Payment pending',
    PAYMENT_UNDER_REVIEW: 'Under review',
    PAID: 'Paid',
    PREPARING_ORDER: 'Preparing',
    SHIPPED: 'Shipped',
    DELIVERED: 'Delivered',
  };
  return (es ? labelsEs : labelsEn)[status];
}

function getOrderTimeline(status: OrderDto['status']) {
  if (status === 'CANCELLED') {
    /*
     * A cancelled order stopped; it is not waiting. Marking the rest 'todo' drew the same
     * hollow dots as an order still in progress, so the only thing saying it had ended was a
     * line of small red text below. The remaining steps are struck through and a terminal
     * node closes the track.
     */
    return {
      cancelled: true,
      steps: [
        ...ORDER_TIMELINE_FLOW.map((step, index) => ({
          step,
          state: (index === 0 ? 'done' : 'skipped') as TimelineState,
        })),
        { step: 'CANCELLED' as const, state: 'ended' as TimelineState },
      ],
    };
  }

  const currentIndex = ORDER_TIMELINE_FLOW.indexOf(status as TimelineStepStatus);
  const normalizedIndex = Math.max(currentIndex, 0);

  return {
    cancelled: false,
    steps: ORDER_TIMELINE_FLOW.map((step, index) => ({
      step,
      state: timelineStepState(index, normalizedIndex),
    })),
  };
}

function timelineStepState(index: number, currentIndex: number): TimelineState {
  if (index < currentIndex) return 'done';
  if (index === currentIndex) return 'current';
  return 'todo';
}

function paymentMethodLabel(method: OrderDto['paymentMethod'], es: boolean) {
  const labelsEs: Record<OrderDto['paymentMethod'], string> = {
    CASH: 'Efectivo',
    DEBIT: 'Débito',
    CREDIT: 'Crédito',
    TRANSFER: 'Transferencia',
    WEBPAY: 'WebPay',
    MERCADOPAGO: 'MercadoPago',
    OTHER: 'Otro',
  };
  const labelsEn: Record<OrderDto['paymentMethod'], string> = {
    CASH: 'Cash',
    DEBIT: 'Debit',
    CREDIT: 'Credit',
    TRANSFER: 'Transfer',
    WEBPAY: 'WebPay',
    MERCADOPAGO: 'MercadoPago',
    OTHER: 'Other',
  };
  return (es ? labelsEs : labelsEn)[method] ?? method;
}

function shippingPaymentModeLabel(mode: string | null | undefined, es: boolean) {
  const normalized = (mode ?? '').trim().toUpperCase();
  const labelsEs: Record<string, string> = {
    POR_PAGAR: 'Envio por pagar',
  };
  const labelsEn: Record<string, string> = {
    POR_PAGAR: 'Shipping paid on pickup',
  };
  return (es ? labelsEs : labelsEn)[normalized] ?? normalized;
}

/**
 * The zone an order was routed through is an internal detail -- it was never something the
 * customer picked -- so it never renders here as "LOCAL"/"REGIONAL". Only the ETA it produces
 * is customer-facing, and it says nothing at all if the zone is missing or the config hasn't
 * loaded rather than showing a blank "Entrega estimada:" line.
 */
function zoneEtaLabel(zones: ShippingZoneConfig[], code: string | null | undefined, es: boolean): string | undefined {
  if (!code) return undefined;
  const zone = zones.find((z) => z.code === code);
  if (!zone) return undefined;
  return (es ? zone.etaEs : zone.etaEn) || undefined;
}

function maskAccountNumber(accountNumber: string | null | undefined) {
  const normalized = (accountNumber ?? '').trim();
  if (!normalized) return '-';
  if (normalized.length <= 4) return normalized;
  return `${'*'.repeat(Math.max(0, normalized.length - 4))}${normalized.slice(-4)}`;
}

interface OrderTimelineStepsProps {
  readonly steps: ReturnType<typeof getOrderTimeline>['steps'];
  readonly es: boolean;
}

function timelineDotClass(state: TimelineState): string {
  if (state === 'done') return 'bg-pe-positive-ink border-pe-positive-ink';
  if (state === 'current') return 'bg-pe-rose-action border-pe-rose-action';
  if (state === 'ended') return 'bg-pe-danger-ink border-pe-danger-ink';
  return 'bg-transparent border-pe-black/20 dark:border-pe-cream/25';
}

function timelineLabelClass(state: TimelineState): string {
  if (state === 'done') return 'text-pe-positive-ink';
  if (state === 'current') return 'text-pe-rose-ink dark:text-pe-rose-ink';
  if (state === 'ended') return 'text-pe-danger-ink font-semibold';
  if (state === 'skipped') return 'text-pe-muted dark:text-pe-cream/25 line-through';
  return 'text-pe-muted dark:text-pe-cream/45';
}

function timelineConnectorClass(state: TimelineState): string {
  return state === 'done' || state === 'current' ? 'bg-pe-rose/45' : 'bg-pe-black/12 dark:bg-pe-cream/15';
}

interface TimelineStepDotProps {
  readonly node: ReturnType<typeof getOrderTimeline>['steps'][number];
  readonly es: boolean;
  readonly showConnector: boolean;
}

function TimelineStepDot({ node, es, showConnector }: TimelineStepDotProps) {
  return (
    <div className="flex items-center gap-2">
      <span className={`inline-flex h-2.5 w-2.5 rounded-full border ${timelineDotClass(node.state)}`} />
      <span className={`font-sans text-[0.62rem] tracking-[0.08em] uppercase whitespace-nowrap ${timelineLabelClass(node.state)}`}>
        {orderTimelineLabel(node.step, es)}
      </span>
      {showConnector && (
        <span className={`block h-px w-5 ${timelineConnectorClass(node.state)}`} aria-hidden="true" />
      )}
    </div>
  );
}

function OrderTimelineSteps({ steps, es }: OrderTimelineStepsProps) {
  return (
    <>
      {steps.map((node, index) => (
        <TimelineStepDot key={node.step} node={node} es={es} showConnector={index < steps.length - 1} />
      ))}
    </>
  );
}

interface OrderListItemProps {
  readonly order: OrderDto;
  readonly es: boolean;
  readonly shippingZones: ShippingZoneConfig[];
  readonly payment: PaymentDto | undefined;
  readonly loadingPayments: boolean;
  readonly isSubmittingProof: boolean;
  readonly proofFeedback: ProofFeedback | undefined;
  readonly isStartingGatewayCheckout: boolean;
  readonly isSimulatingGateway: boolean;
  readonly isConfirmingDelivery: boolean;
  readonly gatewayFeedback: ProofFeedback | undefined;
  readonly selectedFile: File | null | undefined;
  readonly existingReturn: ReturnRequestDto | null;
  readonly effectiveToken: string | null;
  readonly onSelectProofFile: (file: File | null) => void;
  readonly onOpenOwnProof: () => void;
  readonly onSubmitProof: () => void;
  readonly onStartGatewayCheckout: () => void;
  readonly onSimulateGateway: (simulation: 'APPROVED' | 'FAILED') => void;
  readonly onConfirmDelivery: () => void;
  readonly onReturnRequested: (created: ReturnRequestDto) => void;
}

interface OrderShippingInfoProps {
  readonly shippingEta: string | undefined;
  readonly shippingCourier: string | undefined;
  readonly shippingMode: string | undefined;
  readonly shippingReference: string | undefined;
  readonly es: boolean;
}

function OrderShippingInfo({ shippingEta, shippingCourier, shippingMode, shippingReference, es }: OrderShippingInfoProps) {
  if (!shippingEta && !shippingCourier && !shippingMode && !shippingReference) return null;
  return (
    <div className="border border-pe-black/8 bg-pe-cream/25 px-3 py-2">
      <p className="font-sans text-[0.62rem] tracking-[0.14em] uppercase text-pe-muted mb-1">
        {es ? 'Envio' : 'Shipping'}
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1 font-sans text-[0.72rem] text-pe-muted">
        {shippingEta && (
          <p><span className="text-pe-muted">{es ? 'Entrega estimada:' : 'Estimated delivery:'}</span> {shippingEta}</p>
        )}
        {shippingCourier && (
          <p><span className="text-pe-muted">{'Courier:'}</span> {shippingCourier}</p>
        )}
        {shippingMode && (
          <p><span className="text-pe-muted">{es ? 'Modalidad:' : 'Mode:'}</span> {shippingMode}</p>
        )}
        {shippingReference && (
          <p><span className="text-pe-muted">{es ? 'Referencia:' : 'Reference:'}</span> {shippingReference}</p>
        )}
      </div>
    </div>
  );
}

interface PaymentStatusBadgeProps {
  readonly payment: PaymentDto | undefined;
  readonly loadingPayments: boolean;
  readonly es: boolean;
}

function noPaymentLabel(loadingPayments: boolean, es: boolean): string {
  if (loadingPayments) return es ? 'Cargando...' : 'Loading...';
  return es ? 'Sin pago asociado' : 'No linked payment';
}

function PaymentStatusBadge({ payment, loadingPayments, es }: PaymentStatusBadgeProps) {
  if (payment) {
    return (
      <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-cream text-pe-muted">
        {paymentStatusLabel(payment.status, es)}
      </span>
    );
  }
  return (
    <span className="font-sans text-[0.62rem] tracking-wider uppercase text-pe-muted">
      {noPaymentLabel(loadingPayments, es)}
    </span>
  );
}

interface OrderTransferProofSectionProps {
  readonly payment: PaymentDto | undefined;
  readonly loadingPayments: boolean;
  readonly canUploadProof: boolean;
  readonly selectedFile: File | null | undefined;
  readonly isSubmittingProof: boolean;
  readonly proofFeedback: ProofFeedback | undefined;
  readonly es: boolean;
  readonly onSelectProofFile: (file: File | null) => void;
  readonly onOpenOwnProof: () => void;
  readonly onSubmitProof: () => void;
}

function hasTransferSnapshotData(payment: PaymentDto | undefined): boolean {
  return Boolean(
    payment?.transferAccountHolderName || payment?.transferAccountEmail
      || payment?.transferAccountNumber || payment?.transferBankName || payment?.transferAccountType,
  );
}

interface TransferSnapshotCardProps {
  readonly payment: PaymentDto | undefined;
  readonly es: boolean;
}

function TransferSnapshotCard({ payment, es }: TransferSnapshotCardProps) {
  return (
    <div className="border border-pe-black/8 bg-pe-cream/35 px-3 py-2">
      <p className="font-sans text-[0.62rem] tracking-[0.14em] uppercase text-pe-muted mb-1.5">
        {es ? 'Datos transferencia (snapshot)' : 'Transfer details (snapshot)'}
      </p>
      <dl className="grid grid-cols-1 gap-1 font-sans text-[0.72rem] text-pe-muted">
        <div className="flex items-center justify-between gap-3">
          <dt className="text-pe-muted">{es ? 'Nombre' : 'Name'}</dt>
          <dd className="text-right">{payment?.transferAccountHolderName || '-'}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-pe-muted">{es ? 'Correo' : 'Email'}</dt>
          <dd className="text-right">{payment?.transferAccountEmail || '-'}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-pe-muted">{es ? 'Cuenta' : 'Account'}</dt>
          <dd className="text-right">{maskAccountNumber(payment?.transferAccountNumber)}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-pe-muted">{es ? 'Banco' : 'Bank'}</dt>
          <dd className="text-right">{payment?.transferBankName || '-'}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-pe-muted">{es ? 'Tipo' : 'Type'}</dt>
          <dd className="text-right">{payment?.transferAccountType || '-'}</dd>
        </div>
      </dl>
    </div>
  );
}

interface ProofUploadFormProps {
  readonly selectedFile: File | null | undefined;
  readonly isSubmittingProof: boolean;
  readonly es: boolean;
  readonly onSelectProofFile: (file: File | null) => void;
  readonly onSubmitProof: () => void;
}

function ProofUploadForm({ selectedFile, isSubmittingProof, es, onSelectProofFile, onSubmitProof }: ProofUploadFormProps) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-col lg:flex-row lg:items-center gap-2">
        <label className="inline-flex items-center justify-center px-3 py-2 border border-pe-black/12 text-pe-muted hover:text-pe-charcoal hover:border-pe-black/20 transition-colors cursor-pointer font-sans text-[0.68rem] tracking-wider uppercase">
          {toggleLabel(Boolean(selectedFile), es, 'Cambiar imagen', 'Change image', 'Seleccionar imagen', 'Select image')}
          <input
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0] ?? null;
              onSelectProofFile(file);
            }}
          />
        </label>

        <button
          type="button"
          onClick={onSubmitProof}
          disabled={isSubmittingProof}
          className="inline-flex items-center justify-center px-4 py-2 bg-pe-rose-action text-white font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors disabled:opacity-60"
        >
          {toggleLabel(isSubmittingProof, es, 'Enviando...', 'Submitting...', 'Enviar comprobante', 'Submit proof')}
        </button>
      </div>

      {selectedFile && (
        <p className="font-sans text-[0.7rem] text-pe-muted">
          {es ? 'Archivo:' : 'File:'} {selectedFile.name}
        </p>
      )}
    </div>
  );
}

function OrderTransferProofSection({
  payment, loadingPayments, canUploadProof, selectedFile, isSubmittingProof, proofFeedback, es,
  onSelectProofFile, onOpenOwnProof, onSubmitProof,
}: OrderTransferProofSectionProps) {
  return (
    <div className="border-t border-pe-black/7 pt-3 flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-sans text-[0.66rem] tracking-[0.16em] uppercase text-pe-muted">
          {es ? 'Comprobante de transferencia' : 'Transfer proof'}
        </p>
        <PaymentStatusBadge payment={payment} loadingPayments={loadingPayments} es={es} />
      </div>

      {hasTransferSnapshotData(payment) && <TransferSnapshotCard payment={payment} es={es} />}

      {payment?.proofReference && (
        <button
          type="button"
          onClick={onOpenOwnProof}
          className="self-start font-sans text-[0.72rem] text-pe-rose-ink hover:underline underline-offset-2"
        >
          {es ? 'Ver comprobante enviado' : 'View submitted proof'}
        </button>
      )}

      {canUploadProof && (
        <ProofUploadForm
          selectedFile={selectedFile}
          isSubmittingProof={isSubmittingProof}
          es={es}
          onSelectProofFile={onSelectProofFile}
          onSubmitProof={onSubmitProof}
        />
      )}

      {payment?.status === 'UNDER_REVIEW' && (
        <p className="font-sans text-[0.72rem] text-pe-muted">
          {es ? 'Tu comprobante esta en revision del equipo.' : 'Your proof is being reviewed by our team.'}
        </p>
      )}

      {proofFeedback && (
        <p className={`font-sans text-[0.72rem] ${proofFeedback.type === 'success' ? 'text-pe-positive-ink' : 'text-pe-danger-ink'}`}>
          {proofFeedback.text}
        </p>
      )}
    </div>
  );
}

interface GatewaySimulationButtonsProps {
  readonly isSimulatingGateway: boolean;
  readonly isStartingGatewayCheckout: boolean;
  readonly es: boolean;
  readonly onStartGatewayCheckout: () => void;
  readonly onSimulateGateway: (simulation: 'APPROVED' | 'FAILED') => void;
}

function GatewaySimulationButtons({
  isSimulatingGateway, isStartingGatewayCheckout, es, onStartGatewayCheckout, onSimulateGateway,
}: GatewaySimulationButtonsProps) {
  const disabled = isSimulatingGateway || isStartingGatewayCheckout;
  return (
    <div className="flex flex-wrap gap-2">
      <button
        type="button"
        onClick={onStartGatewayCheckout}
        disabled={disabled}
        className="inline-flex items-center justify-center px-3 py-2 bg-pe-rose-action text-white font-sans text-[0.66rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors disabled:opacity-60"
      >
        {toggleLabel(isStartingGatewayCheckout, es, 'Abriendo...', 'Opening...', 'Ir a pagar', 'Pay now')}
      </button>
      <button
        type="button"
        onClick={() => onSimulateGateway('APPROVED')}
        disabled={disabled}
        className="inline-flex items-center justify-center px-3 py-2 bg-pe-positive text-white font-sans text-[0.66rem] tracking-wider uppercase hover:opacity-90 transition-colors disabled:opacity-60"
      >
        {toggleLabel(isSimulatingGateway, es, 'Simulando...', 'Simulating...', 'Simular aprobado', 'Simulate approve')}
      </button>
      <button
        type="button"
        onClick={() => onSimulateGateway('FAILED')}
        disabled={disabled}
        className="inline-flex items-center justify-center px-3 py-2 border border-pe-danger/40 text-pe-danger-ink font-sans text-[0.66rem] tracking-wider uppercase hover:bg-pe-danger-surface transition-colors disabled:opacity-60"
      >
        {toggleLabel(isSimulatingGateway, es, 'Simulando...', 'Simulating...', 'Simular rechazado', 'Simulate reject')}
      </button>
    </div>
  );
}

interface OrderGatewaySectionProps {
  readonly payment: PaymentDto | undefined;
  readonly loadingPayments: boolean;
  readonly canSimulate: boolean;
  readonly isSimulatingGateway: boolean;
  readonly isStartingGatewayCheckout: boolean;
  readonly gatewayFeedback: ProofFeedback | undefined;
  readonly es: boolean;
  readonly onStartGatewayCheckout: () => void;
  readonly onSimulateGateway: (simulation: 'APPROVED' | 'FAILED') => void;
}

function OrderGatewaySection({
  payment, loadingPayments, canSimulate, isSimulatingGateway, isStartingGatewayCheckout, gatewayFeedback, es,
  onStartGatewayCheckout, onSimulateGateway,
}: OrderGatewaySectionProps) {
  return (
    <div className="border-t border-pe-black/7 pt-3 flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-sans text-[0.66rem] tracking-[0.16em] uppercase text-pe-muted">
          {es ? 'Estado pasarela' : 'Gateway status'}
        </p>
        <PaymentStatusBadge payment={payment} loadingPayments={loadingPayments} es={es} />
      </div>

      {canSimulate && (
        <GatewaySimulationButtons
          isSimulatingGateway={isSimulatingGateway}
          isStartingGatewayCheckout={isStartingGatewayCheckout}
          es={es}
          onStartGatewayCheckout={onStartGatewayCheckout}
          onSimulateGateway={onSimulateGateway}
        />
      )}

      {gatewayFeedback && (
        <p className={`font-sans text-[0.72rem] ${gatewayFeedback.type === 'success' ? 'text-pe-positive-ink' : 'text-pe-danger-ink'}`}>
          {gatewayFeedback.text}
        </p>
      )}
    </div>
  );
}

interface OrderFooterActionsProps {
  readonly order: OrderDto;
  readonly es: boolean;
  readonly effectiveToken: string | null;
  readonly existingReturn: ReturnRequestDto | null;
  readonly isConfirmingDelivery: boolean;
  readonly onReturnRequested: (created: ReturnRequestDto) => void;
  readonly onConfirmDelivery: () => void;
}

function OrderFooterActions({
  order, es, effectiveToken, existingReturn, isConfirmingDelivery, onReturnRequested, onConfirmDelivery,
}: OrderFooterActionsProps) {
  return (
    <div className="border-t border-pe-black/7 pt-3 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      {order.status === 'DELIVERED' && effectiveToken && (
        <RetractoButton
          orderId={order.id}
          token={effectiveToken}
          locale={es ? 'es' : 'en'}
          existing={existingReturn}
          onRequested={onReturnRequested}
        />
      )}
      {order.status === 'SHIPPED' && (
        <button
          type="button"
          onClick={onConfirmDelivery}
          disabled={isConfirmingDelivery}
          className="inline-flex items-center justify-center px-3 py-2 bg-pe-positive text-white font-sans text-[0.66rem] tracking-wider uppercase hover:opacity-90 transition-colors disabled:opacity-60"
        >
          {toggleLabel(isConfirmingDelivery, es, 'Confirmando...', 'Confirming...', 'Marcar como recibido', 'Mark as received')}
        </button>
      )}
      <span className="font-sans text-[0.72rem] text-pe-muted">
        {new Date(order.createdAt).toLocaleDateString(es ? 'es-CL' : 'en-US', {
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        })}
      </span>
      <p className="font-display text-[1.05rem] text-pe-black">
        {'Total: '}
        {formatMoney(order.totalAmount.amount, order.totalAmount.currency, es)}
      </p>
    </div>
  );
}

function OrderListItem({
  order, es, shippingZones, payment, loadingPayments, isSubmittingProof, proofFeedback, isStartingGatewayCheckout,
  isSimulatingGateway, isConfirmingDelivery, gatewayFeedback, selectedFile, existingReturn, effectiveToken,
  onSelectProofFile, onOpenOwnProof, onSubmitProof, onStartGatewayCheckout, onSimulateGateway, onConfirmDelivery,
  onReturnRequested,
}: OrderListItemProps) {
  const timeline = getOrderTimeline(order.status);
  const canUploadProof = canSubmitProof(order, payment);
  const canSimulate = canSimulateGateway(order, payment);
  const shippingEta = zoneEtaLabel(shippingZones, order.shippingZoneCode, es);
  const shippingCourier = order.shippingCourierName?.trim() || order.shippingCourierId?.trim();
  const shippingMode = shippingPaymentModeLabel(order.shippingPaymentMode, es);
  const shippingReference = order.shippingAddressReference?.trim();

  return (
    <li className="bg-pe-white border border-pe-black/6 p-5 flex flex-col gap-3">
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
        <div>
          <p className="font-sans text-[0.65rem] tracking-[0.16em] uppercase text-pe-muted">
            {es ? 'Pedido' : 'Order'}
          </p>
          <p className="font-mono text-[0.82rem] text-pe-muted mt-0.5">{order.id}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-cream text-pe-muted">
            {paymentMethodLabel(order.paymentMethod, es)}
          </span>
          <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-rose/10 text-pe-rose-ink">
            {orderStatusLabel(order.status, es ? 'es' : 'en')}
          </span>
        </div>
      </div>

      <OrderShippingInfo
        shippingEta={shippingEta}
        shippingCourier={shippingCourier}
        shippingMode={shippingMode}
        shippingReference={shippingReference}
        es={es}
      />

      {/*
        * Wraps instead of scrolling. A 680px minimum pushed the last node past
        * the edge and left it behind a scrollbar — which for a cancelled order
        * hid the one marker that says how it ended.
        */}
      <div className="border border-pe-black/8 dark:border-pe-cream/10 bg-pe-cream/35 dark:bg-pe-black/20 px-3 py-3">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-2">
          <OrderTimelineSteps steps={timeline.steps} es={es} />
        </div>
        {timeline.cancelled && (
          <p className="font-sans text-[0.68rem] text-pe-danger-ink mt-2">
            {es
              ? 'Pedido cancelado. Si fue por falta de pago dentro del plazo, los productos volvieron a estar disponibles y puedes hacer un nuevo pedido.'
              : 'Order cancelled. If the payment window lapsed, the items are available again and you can place a new order.'}
          </p>
        )}
      </div>

      <ul className="flex flex-col gap-1.5 border-t border-pe-black/7 pt-3">
        {order.items.map((item) => (
          <li key={item.id} className="flex items-center justify-between gap-3">
            <span className="font-sans text-sm text-pe-charcoal">
              {item.productName} x{item.quantity}
            </span>
            <span className="font-sans text-sm text-pe-muted">
              {formatMoney(item.unitPrice.amount, item.unitPrice.currency, es)}
            </span>
          </li>
        ))}
      </ul>

      {order.paymentMethod === 'TRANSFER' && (
        <OrderTransferProofSection
          payment={payment}
          loadingPayments={loadingPayments}
          canUploadProof={canUploadProof}
          selectedFile={selectedFile}
          isSubmittingProof={isSubmittingProof}
          proofFeedback={proofFeedback}
          es={es}
          onSelectProofFile={onSelectProofFile}
          onOpenOwnProof={onOpenOwnProof}
          onSubmitProof={onSubmitProof}
        />
      )}

      {(order.paymentMethod === 'WEBPAY' || order.paymentMethod === 'MERCADOPAGO') && (
        <OrderGatewaySection
          payment={payment}
          loadingPayments={loadingPayments}
          canSimulate={canSimulate}
          isSimulatingGateway={isSimulatingGateway}
          isStartingGatewayCheckout={isStartingGatewayCheckout}
          gatewayFeedback={gatewayFeedback}
          es={es}
          onStartGatewayCheckout={onStartGatewayCheckout}
          onSimulateGateway={onSimulateGateway}
        />
      )}

      <OrderFooterActions
        order={order}
        es={es}
        effectiveToken={effectiveToken}
        existingReturn={existingReturn}
        isConfirmingDelivery={isConfirmingDelivery}
        onReturnRequested={onReturnRequested}
        onConfirmDelivery={onConfirmDelivery}
      />
    </li>
  );
}

interface ProfileTabProps {
  readonly es: boolean;
  readonly user: StoredUser;
  readonly profile: UserProfileDto | null;
  readonly displayName: string;
  readonly avatarUrl: string | null;
  readonly avatarDragging: boolean;
  readonly avatarUploading: boolean;
  readonly avatarFeedback: { type: 'success' | 'error'; text: string } | null;
  readonly avatarInputRef: React.RefObject<HTMLInputElement>;
  readonly onAvatarFile: (file: File) => void;
  readonly onAvatarDraggingChange: (dragging: boolean) => void;
  readonly profileName: string;
  readonly onProfileNameChange: (value: string) => void;
  readonly profilePhone: string;
  readonly onProfilePhoneChange: (value: string) => void;
  readonly profileNotificationChannel: NotificationChannelPreference;
  readonly onProfileNotificationChannelChange: (value: NotificationChannelPreference) => void;
  readonly profileLoading: boolean;
  readonly profileSaving: boolean;
  readonly profileFeedback: { type: 'success' | 'error'; text: string } | null;
  readonly onSaveProfile: () => void;
  readonly currentPassword: string;
  readonly onCurrentPasswordChange: (value: string) => void;
  readonly newPassword: string;
  readonly onNewPasswordChange: (value: string) => void;
  readonly confirmPassword: string;
  readonly onConfirmPasswordChange: (value: string) => void;
  readonly passwordSaving: boolean;
  readonly passwordFeedback: { type: 'success' | 'error'; text: string } | null;
  readonly onChangePassword: () => void;
}

interface AvatarUploadCardProps {
  readonly es: boolean;
  readonly displayName: string;
  readonly avatarUrl: string | null;
  readonly avatarDragging: boolean;
  readonly avatarUploading: boolean;
  readonly avatarFeedback: { type: 'success' | 'error'; text: string } | null;
  readonly avatarInputRef: React.RefObject<HTMLInputElement>;
  readonly onAvatarFile: (file: File) => void;
  readonly onAvatarDraggingChange: (dragging: boolean) => void;
}

function AvatarUploadCard({
  es, displayName, avatarUrl, avatarDragging, avatarUploading, avatarFeedback, avatarInputRef,
  onAvatarFile, onAvatarDraggingChange,
}: AvatarUploadCardProps) {
  return (
    <div
      className={`bg-pe-white p-6 border transition-colors duration-200 flex flex-col gap-4 ${avatarDragging ? 'border-pe-rose bg-pe-rose/5' : 'border-pe-black/6'}`}
      onDragOver={(e) => { e.preventDefault(); onAvatarDraggingChange(true); }}
      onDragLeave={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node)) onAvatarDraggingChange(false);
      }}
      onDrop={(e) => {
        e.preventDefault();
        onAvatarDraggingChange(false);
        const file = e.dataTransfer.files[0];
        if (file) onAvatarFile(file);
      }}
    >
      <p className="pe-eyebrow text-pe-muted">{es ? 'Foto de perfil' : 'Profile photo'}</p>
      <div className="flex items-center gap-5">
        <div className={`relative w-20 h-20 rounded-full shrink-0 overflow-hidden border-2 transition-colors duration-200 ${avatarDragging ? 'border-pe-rose' : 'border-pe-black/10'}`}>
          {avatarUrl ? (
            <img src={avatarUrl} alt={displayName} className="w-full h-full object-cover" />
          ) : (
            <div className="w-full h-full bg-pe-rose-action flex items-center justify-center text-pe-offwhite font-display text-2xl font-light">
              {displayName.substring(0, 2).toUpperCase()}
            </div>
          )}
          {avatarUploading && (
            <div className="absolute inset-0 bg-pe-black/40 flex items-center justify-center">
              <Loader2 size={20} className="animate-spin text-white" />
            </div>
          )}
        </div>
        <div className="flex flex-col gap-2">
          <p className="font-sans text-[0.72rem] text-pe-muted">
            {toggleLabel(avatarDragging, es, 'Suelta la imagen aquí', 'Drop the image here', 'Arrastra una foto aquí o', 'Drag a photo here or')}
          </p>
          <label className="inline-flex items-center gap-1.5 cursor-pointer px-3 py-1.5 border border-pe-black/15 font-sans text-[0.68rem] tracking-wider uppercase text-pe-muted hover:border-pe-rose hover:text-pe-rose-ink transition-colors duration-200">
            <Camera size={12} />
            {es ? 'Elegir archivo' : 'Choose file'}
            <input
              ref={avatarInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) onAvatarFile(file);
                e.target.value = '';
              }}
            />
          </label>
          {avatarFeedback && (
            <span className={`font-sans text-[0.7rem] ${avatarFeedback.type === 'success' ? 'text-pe-positive-ink' : 'text-pe-danger-ink'}`}>
              {avatarFeedback.text}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

interface ProfileDetailsCardProps {
  readonly es: boolean;
  readonly profileName: string;
  readonly onProfileNameChange: (value: string) => void;
  readonly profilePhone: string;
  readonly onProfilePhoneChange: (value: string) => void;
  readonly profileNotificationChannel: NotificationChannelPreference;
  readonly onProfileNotificationChannelChange: (value: NotificationChannelPreference) => void;
  readonly profileLoading: boolean;
  readonly profileSaving: boolean;
  readonly profileFeedback: { type: 'success' | 'error'; text: string } | null;
  readonly onSaveProfile: () => void;
}

interface NotificationChannelFieldProps {
  readonly es: boolean;
  readonly value: NotificationChannelPreference;
  readonly disabled: boolean;
  readonly onChange: (value: NotificationChannelPreference) => void;
}

function NotificationChannelField({ es, value, disabled, onChange }: NotificationChannelFieldProps) {
  return (
    <>
      <label htmlFor="profile-notif-channel" className="font-sans text-[0.72rem] text-pe-muted">
        {es ? 'Canal de notificaciones' : 'Notification channel'}
      </label>
      <select
        id="profile-notif-channel"
        value={value}
        onChange={(event) => onChange(event.target.value as NotificationChannelPreference)}
        disabled={disabled}
        className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-hidden focus:border-pe-rose focus-visible:ring-1 focus-visible:ring-pe-rose/40 disabled:opacity-60"
      >
        <option value="AUTO">{es ? 'Automatico (recomendado)' : 'Automatic (recommended)'}</option>
        <option value="WHATSAPP">WhatsApp</option>
        <option value="EMAIL">{es ? 'Correo' : 'Email'}</option>
        <option value="BOTH">{es ? 'Ambos' : 'Both'}</option>
      </select>
      <p className="font-sans text-[0.68rem] text-pe-muted">
        {es
          ? 'Si completas tu WhatsApp y eliges un canal, enviaremos notificaciones de pedido segun tu preferencia.'
          : 'If you provide WhatsApp and choose a channel, order notifications will follow your preference.'}
      </p>
    </>
  );
}

function ProfileDetailsCard({
  es, profileName, onProfileNameChange, profilePhone, onProfilePhoneChange, profileNotificationChannel,
  onProfileNotificationChannelChange, profileLoading, profileSaving, profileFeedback, onSaveProfile,
}: ProfileDetailsCardProps) {
  const disabled = profileLoading || profileSaving;
  return (
    <div className="bg-pe-white p-6 border border-pe-black/6 flex flex-col gap-3">
      <p className="pe-eyebrow text-pe-muted">{es ? 'Datos de perfil' : 'Profile details'}</p>
      <label htmlFor="profile-full-name" className="font-sans text-[0.72rem] text-pe-muted">{es ? 'Nombre completo' : 'Full name'}</label>
      <input
        id="profile-full-name"
        type="text"
        value={profileName}
        onChange={(event) => onProfileNameChange(event.target.value)}
        autoComplete="name"
        name="fullName"
        disabled={disabled}
        className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-hidden focus:border-pe-rose focus-visible:ring-1 focus-visible:ring-pe-rose/40 disabled:opacity-60"
        placeholder={es ? 'Tu nombre completo' : 'Your full name'}
      />
      <label htmlFor="profile-phone" className="font-sans text-[0.72rem] text-pe-muted">{es ? 'Telefono WhatsApp' : 'WhatsApp phone'}</label>
      <input
        id="profile-phone"
        type="tel"
        value={profilePhone}
        onChange={(event) => onProfilePhoneChange(event.target.value)}
        disabled={disabled}
        autoComplete="tel"
        inputMode="tel"
        name="whatsappPhone"
        className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-hidden focus:border-pe-rose focus-visible:ring-1 focus-visible:ring-pe-rose/40 disabled:opacity-60"
        placeholder={es ? '+56912345678' : '+14155550123'}
      />
      <NotificationChannelField
        es={es}
        value={profileNotificationChannel}
        disabled={disabled}
        onChange={onProfileNotificationChannelChange}
      />
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onSaveProfile}
          disabled={disabled}
          className="inline-flex items-center justify-center px-4 py-2 bg-pe-rose-action text-white font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors disabled:opacity-60"
        >
          {toggleLabel(profileSaving, es, 'Guardando...', 'Saving...', 'Guardar perfil', 'Save profile')}
        </button>
        {profileFeedback && (
          <span className={`font-sans text-[0.72rem] ${profileFeedback.type === 'success' ? 'text-pe-positive-ink' : 'text-pe-danger-ink'}`}>
            {profileFeedback.text}
          </span>
        )}
      </div>
    </div>
  );
}

interface ChangePasswordCardProps {
  readonly es: boolean;
  readonly user: StoredUser;
  readonly profile: UserProfileDto | null;
  readonly currentPassword: string;
  readonly onCurrentPasswordChange: (value: string) => void;
  readonly newPassword: string;
  readonly onNewPasswordChange: (value: string) => void;
  readonly confirmPassword: string;
  readonly onConfirmPasswordChange: (value: string) => void;
  readonly passwordSaving: boolean;
  readonly passwordFeedback: { type: 'success' | 'error'; text: string } | null;
  readonly onChangePassword: () => void;
}

function ChangePasswordCard({
  es, user, profile, currentPassword, onCurrentPasswordChange, newPassword, onNewPasswordChange, confirmPassword,
  onConfirmPasswordChange, passwordSaving, passwordFeedback, onChangePassword,
}: ChangePasswordCardProps) {
  return (
    <div className="bg-pe-white p-6 border border-pe-black/6 flex flex-col gap-3">
      <p className="pe-eyebrow text-pe-muted">{es ? 'Cambiar contraseña' : 'Change password'}</p>
      {/*
        Password managers look for the account these password fields belong to. With no
        username field they guess, and Chrome was picking the WhatsApp input and filling
        it with the saved login email. This gives it the real answer; it is hidden from
        sight but has to stay in the DOM and focusable-by-autofill, so it is not
        `type="hidden"` (which Chrome ignores for this purpose).
      */}
      <input
        type="text"
        name="username"
        autoComplete="username"
        value={profile?.email ?? user.email}
        readOnly
        tabIndex={-1}
        aria-hidden="true"
        className="sr-only"
      />
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div className="flex flex-col gap-1">
          <label htmlFor="pw-current" className="font-sans text-[0.72rem] text-pe-muted">{es ? 'Contraseña actual' : 'Current password'}</label>
          <input
            id="pw-current"
            type="password"
            value={currentPassword}
            onChange={(event) => onCurrentPasswordChange(event.target.value)}
            autoComplete="current-password"
            name="currentPassword"
            className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-hidden focus:border-pe-rose focus-visible:ring-1 focus-visible:ring-pe-rose/40"
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="pw-new" className="font-sans text-[0.72rem] text-pe-muted">{es ? 'Nueva contraseña' : 'New password'}</label>
          <input
            id="pw-new"
            type="password"
            value={newPassword}
            onChange={(event) => onNewPasswordChange(event.target.value)}
            autoComplete="new-password"
            name="newPassword"
            className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-hidden focus:border-pe-rose focus-visible:ring-1 focus-visible:ring-pe-rose/40"
          />
        </div>
        <div className="flex flex-col gap-1 sm:col-span-2">
          <label htmlFor="pw-confirm" className="font-sans text-[0.72rem] text-pe-muted">{es ? 'Confirmar nueva contraseña' : 'Confirm new password'}</label>
          <input
            id="pw-confirm"
            type="password"
            value={confirmPassword}
            onChange={(event) => onConfirmPasswordChange(event.target.value)}
            autoComplete="new-password"
            name="confirmPassword"
            className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-hidden focus:border-pe-rose focus-visible:ring-1 focus-visible:ring-pe-rose/40"
          />
        </div>
      </div>
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onChangePassword}
          disabled={passwordSaving}
          className="inline-flex items-center justify-center px-4 py-2 pe-btn-ink font-sans text-[0.68rem] tracking-wider uppercase transition-colors disabled:opacity-60"
        >
          {toggleLabel(passwordSaving, es, 'Actualizando...', 'Updating...', 'Actualizar contraseña', 'Update password')}
        </button>
        {passwordFeedback && (
          <span className={`font-sans text-[0.72rem] ${passwordFeedback.type === 'success' ? 'text-pe-positive-ink' : 'text-pe-danger-ink'}`}>
            {passwordFeedback.text}
          </span>
        )}
      </div>
    </div>
  );
}

function roleLabel(role: StoredUser['role'], es: boolean): string {
  if (role === 'ADMIN') return 'Admin';
  if (role === 'SELLER') return es ? 'Vendedor/a' : 'Seller';
  return es ? 'Cliente' : 'Customer';
}

function ProfileTab({
  es, user, profile, displayName, avatarUrl, avatarDragging, avatarUploading, avatarFeedback, avatarInputRef,
  onAvatarFile, onAvatarDraggingChange, profileName, onProfileNameChange, profilePhone, onProfilePhoneChange,
  profileNotificationChannel, onProfileNotificationChannelChange, profileLoading, profileSaving, profileFeedback,
  onSaveProfile, currentPassword, onCurrentPasswordChange, newPassword, onNewPasswordChange, confirmPassword,
  onConfirmPasswordChange, passwordSaving, passwordFeedback, onChangePassword,
}: ProfileTabProps) {
  return (
    <div className="mx-auto max-w-xl flex flex-col gap-5">
      <AvatarUploadCard
        es={es}
        displayName={displayName}
        avatarUrl={avatarUrl}
        avatarDragging={avatarDragging}
        avatarUploading={avatarUploading}
        avatarFeedback={avatarFeedback}
        avatarInputRef={avatarInputRef}
        onAvatarFile={onAvatarFile}
        onAvatarDraggingChange={onAvatarDraggingChange}
      />
      <ProfileDetailsCard
        es={es}
        profileName={profileName}
        onProfileNameChange={onProfileNameChange}
        profilePhone={profilePhone}
        onProfilePhoneChange={onProfilePhoneChange}
        profileNotificationChannel={profileNotificationChannel}
        onProfileNotificationChannelChange={onProfileNotificationChannelChange}
        profileLoading={profileLoading}
        profileSaving={profileSaving}
        profileFeedback={profileFeedback}
        onSaveProfile={onSaveProfile}
      />
      <ChangePasswordCard
        es={es}
        user={user}
        profile={profile}
        currentPassword={currentPassword}
        onCurrentPasswordChange={onCurrentPasswordChange}
        newPassword={newPassword}
        onNewPasswordChange={onNewPasswordChange}
        confirmPassword={confirmPassword}
        onConfirmPasswordChange={onConfirmPasswordChange}
        passwordSaving={passwordSaving}
        passwordFeedback={passwordFeedback}
        onChangePassword={onChangePassword}
      />
      <p className="font-sans text-[0.72rem] text-pe-muted">
        {es ? 'ID de cuenta: ' : 'Account ID: '}
        {user.id}
      </p>
    </div>
  );
}

interface ReviewsTabProps {
  readonly es: boolean;
  readonly locale: string;
  readonly loadingReviews: boolean;
  readonly reviews: ReviewDto[];
  readonly onDeleteReview: (reviewId: string) => void;
}

function ReviewsTab({ es, locale, loadingReviews, reviews, onDeleteReview }: ReviewsTabProps) {
  if (loadingReviews) {
    return (
      <div className="mx-auto max-w-2xl">
        <div className="flex justify-center py-16">
          <Loader2 size={24} className="animate-spin text-pe-rose-ink" />
        </div>
      </div>
    );
  }

  if (reviews.length === 0) {
    return (
      <div className="mx-auto max-w-2xl">
        <div className="text-center py-20">
          <Star size={32} className="text-pe-muted mx-auto mb-3" />
          <p className="font-display text-pe-charcoal/45 text-xl">{es ? 'Aun no escribiste resenas' : 'No reviews yet'}</p>
          <a
            href={`/${locale}/products`}
            className="inline-block mt-4 font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-rose-ink hover:underline underline-offset-2"
          >
            {es ? 'Explorar productos' : 'Browse products'}
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl">
      <ul className="flex flex-col gap-4">
        {reviews.map((review) => (
            <li key={review.id} className="bg-pe-white border border-pe-black/6 p-5 flex flex-col gap-2">
              <div className="flex items-start justify-between gap-4">
                <div className="flex gap-0.5">
                  {Array.from({ length: 5 }).map((_, i) => (
                    <Star
                      key={i}
                      size={13}
                      className={i < review.rating ? 'text-pe-rose-ink fill-pe-rose' : 'text-pe-muted'}
                    />
                  ))}
                </div>
                <button
                  type="button"
                  onClick={() => onDeleteReview(review.id)}
                  className="text-pe-muted hover:text-pe-rose-ink transition-colors duration-200 p-0.5"
                  aria-label={es ? 'Eliminar resena' : 'Delete review'}
                >
                  <Trash2 size={14} />
                </button>
              </div>
              {review.title && <p className="font-display text-pe-black font-medium text-[1rem]">{review.title}</p>}
              {review.comment && <p className="font-sans text-sm text-pe-muted leading-relaxed">{review.comment}</p>}
              <div className="flex items-center gap-3 mt-1">
                <span
                  className={`font-sans text-[0.65rem] tracking-wider uppercase px-2 py-0.5 ${
                    review.approved ? 'bg-pe-positive-surface text-pe-positive-ink' : 'bg-pe-cream text-pe-muted'
                  }`}
                >
                  {toggleLabel(review.approved, es, 'Aprobada', 'Approved', 'Pendiente', 'Pending')}
                </span>
                <span className="font-sans text-[0.68rem] text-pe-muted">
                  {new Date(review.createdAt).toLocaleDateString(es ? 'es-CL' : 'en-US')}
                </span>
              </div>
            </li>
          ))}
        </ul>
    </div>
  );
}

interface AddressCardProps {
  readonly address: CustomerAddressDto;
  readonly es: boolean;
  readonly addressDefaultingId: string | null;
  readonly addressDeletingId: string | null;
  readonly onEdit: (address: CustomerAddressDto) => void;
  readonly onSetDefault: (addressId: string) => void;
  readonly onDelete: (addressId: string) => void;
}

function AddressCard({ address, es, addressDefaultingId, addressDeletingId, onEdit, onSetDefault, onDelete }: AddressCardProps) {
  return (
    <li className="bg-pe-white border border-pe-black/8 p-4 flex flex-col gap-2">
      <div className="flex items-center justify-between gap-3">
        <p className="font-display text-pe-black text-lg">{address.label}</p>
        {address.isDefault && (
          <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-rose/12 text-pe-rose-ink">
            {es ? 'Principal' : 'Default'}
          </span>
        )}
      </div>
      <p className="font-sans text-sm text-pe-charcoal">{address.recipientName}</p>
      <p className="font-sans text-sm text-pe-muted">{address.phone}</p>
      <p className="font-sans text-sm text-pe-muted">
        {address.line1}
        {address.line2 ? `, ${address.line2}` : ''}
      </p>
      <p className="font-sans text-sm text-pe-muted">
        {address.comuna}, {address.city}, {address.region}
      </p>
      {address.reference && (
        <p className="font-sans text-[0.72rem] text-pe-muted">
          {es ? 'Referencia:' : 'Reference:'} {address.reference}
        </p>
      )}
      <div className="flex flex-wrap gap-2 pt-2">
        <button
          type="button"
          onClick={() => onEdit(address)}
          className="px-3 py-1.5 border border-pe-black/15 text-pe-charcoal font-sans text-[0.66rem] tracking-wider uppercase hover:border-pe-charcoal/30 transition-colors"
        >
          {es ? 'Editar' : 'Edit'}
        </button>
        {!address.isDefault && (
          <button
            type="button"
            onClick={() => onSetDefault(address.id)}
            disabled={addressDefaultingId === address.id}
            className="px-3 py-1.5 border border-pe-rose/30 text-pe-rose-ink font-sans text-[0.66rem] tracking-wider uppercase hover:bg-pe-rose/10 transition-colors disabled:opacity-60"
          >
            {toggleLabel(addressDefaultingId === address.id, es, 'Guardando...', 'Saving...', 'Marcar principal', 'Set default')}
          </button>
        )}
        <button
          type="button"
          onClick={() => onDelete(address.id)}
          disabled={addressDeletingId === address.id}
          className="px-3 py-1.5 border border-pe-danger/40 text-pe-danger-ink font-sans text-[0.66rem] tracking-wider uppercase hover:bg-pe-danger-surface transition-colors disabled:opacity-60"
        >
          {toggleLabel(addressDeletingId === address.id, es, 'Eliminando...', 'Deleting...', 'Eliminar', 'Delete')}
        </button>
      </div>
    </li>
  );
}

interface AddressFormFieldsProps {
  readonly es: boolean;
  readonly addressDraft: AddressDraft;
  readonly onDraftChange: (updater: (prev: AddressDraft) => AddressDraft) => void;
  readonly loadingLocations: boolean;
  readonly locationRegions: LocationRegionDto[];
  readonly cityOptions: LocationCityDto[];
  readonly comunaOptions: LocationCommuneDto[];
}

const addrLabelClass = 'font-sans text-[0.7rem] text-pe-muted mb-1';
const addrFieldClass = 'border border-pe-black/12 px-3 py-2 font-sans text-sm w-full focus:outline-hidden focus:border-pe-rose focus-visible:ring-1 focus-visible:ring-pe-rose/40';

function AddressFormFields({
  es, addressDraft, onDraftChange, loadingLocations, locationRegions, cityOptions, comunaOptions,
}: AddressFormFieldsProps) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
      <div className="flex flex-col sm:col-span-2">
        <label htmlFor="addr-label" className={addrLabelClass}>{es ? 'Alias (Casa, Oficina)' : 'Label (Home, Office)'}</label>
        <input id="addr-label" value={addressDraft.label} onChange={(e) => onDraftChange((p) => ({ ...p, label: e.target.value }))} className={addrFieldClass} />
      </div>
      <div className="flex flex-col sm:col-span-2">
        <label htmlFor="addr-recipient" className={addrLabelClass}>{es ? 'Destinatario' : 'Recipient'}</label>
        <input id="addr-recipient" autoComplete="name" value={addressDraft.recipientName} onChange={(e) => onDraftChange((p) => ({ ...p, recipientName: e.target.value }))} className={addrFieldClass} />
      </div>
      <div className="flex flex-col sm:col-span-2">
        <label htmlFor="addr-phone" className={addrLabelClass}>{es ? 'Teléfono' : 'Phone'}</label>
        <input id="addr-phone" type="tel" inputMode="tel" autoComplete="tel" value={addressDraft.phone} onChange={(e) => onDraftChange((p) => ({ ...p, phone: e.target.value }))} className={addrFieldClass} />
      </div>
      <div className="flex flex-col sm:col-span-2">
        <label htmlFor="addr-line1" className={addrLabelClass}>{es ? 'Dirección (línea 1)' : 'Address line 1'}</label>
        <input id="addr-line1" autoComplete="address-line1" value={addressDraft.line1} onChange={(e) => onDraftChange((p) => ({ ...p, line1: e.target.value }))} className={addrFieldClass} />
      </div>
      <div className="flex flex-col sm:col-span-2">
        <label htmlFor="addr-line2" className={addrLabelClass}>{es ? 'Dirección (línea 2, opcional)' : 'Address line 2 (optional)'}</label>
        <input id="addr-line2" autoComplete="address-line2" value={addressDraft.line2} onChange={(e) => onDraftChange((p) => ({ ...p, line2: e.target.value }))} className={addrFieldClass} />
      </div>
      <div className="flex flex-col sm:col-span-2">
        <label htmlFor="addr-region" className={addrLabelClass}>{es ? 'Región' : 'Region'}</label>
        <select
          id="addr-region"
          value={addressDraft.regionId}
          onChange={(e) => onDraftChange((p) => ({ ...p, regionId: e.target.value, cityId: '', comunaId: '', region: '', city: '', comuna: '' }))}
          className={`${addrFieldClass} bg-white`}
        >
          <option value="">{toggleLabel(loadingLocations, es, 'Cargando ubicaciones...', 'Loading locations...', 'Selecciona region', 'Select region')}</option>
          {locationRegions.map((region) => (
            <option key={region.id} value={region.id}>{region.name}</option>
          ))}
        </select>
      </div>
      <div className="flex flex-col">
        <label htmlFor="addr-city" className={addrLabelClass}>{es ? 'Ciudad' : 'City'}</label>
        <select
          id="addr-city"
          value={addressDraft.cityId}
          onChange={(e) => onDraftChange((p) => ({ ...p, cityId: e.target.value, comunaId: '', city: '', comuna: '' }))}
          disabled={!addressDraft.regionId}
          className={`${addrFieldClass} bg-white disabled:bg-pe-cream/25`}
        >
          <option value="">{es ? 'Selecciona ciudad' : 'Select city'}</option>
          {cityOptions.map((city) => (
            <option key={city.id} value={city.id}>{city.name}</option>
          ))}
        </select>
      </div>
      <div className="flex flex-col">
        <label htmlFor="addr-comuna" className={addrLabelClass}>{es ? 'Comuna' : 'Comuna'}</label>
        <select
          id="addr-comuna"
          value={addressDraft.comunaId}
          onChange={(e) => onDraftChange((p) => ({ ...p, comunaId: e.target.value }))}
          disabled={!addressDraft.cityId}
          className={`${addrFieldClass} bg-white disabled:bg-pe-cream/25`}
        >
          <option value="">{es ? 'Selecciona comuna' : 'Select comuna'}</option>
          {comunaOptions.map((comuna) => (
            <option key={comuna.id} value={comuna.id}>{comuna.name}</option>
          ))}
        </select>
      </div>
      <div className="flex flex-col sm:col-span-2">
        <label htmlFor="addr-reference" className={addrLabelClass}>{es ? 'Referencia (opcional)' : 'Reference (optional)'}</label>
        <input id="addr-reference" value={addressDraft.reference} onChange={(e) => onDraftChange((p) => ({ ...p, reference: e.target.value }))} className={addrFieldClass} />
      </div>
    </div>
  );
}

interface AddressModalProps {
  readonly es: boolean;
  readonly editingAddressId: string | null;
  readonly addressDraft: AddressDraft;
  readonly onDraftChange: (updater: (prev: AddressDraft) => AddressDraft) => void;
  readonly loadingLocations: boolean;
  readonly locationRegions: LocationRegionDto[];
  readonly cityOptions: LocationCityDto[];
  readonly comunaOptions: LocationCommuneDto[];
  readonly addressSaving: boolean;
  readonly onSave: () => void;
  readonly onClose: () => void;
}

function AddressModal({
  es, editingAddressId, addressDraft, onDraftChange, loadingLocations, locationRegions,
  cityOptions, comunaOptions, addressSaving, onSave, onClose,
}: AddressModalProps) {
  return (
    <div className="fixed inset-0 z-[90] bg-black/45 flex items-center justify-center p-4">
      <div className="w-full max-w-xl bg-pe-white border border-pe-black/10 p-5 flex flex-col gap-3 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between">
          <p className="font-display text-pe-black text-xl">
            {toggleLabel(Boolean(editingAddressId), es, 'Editar dirección', 'Edit address', 'Nueva dirección', 'New address')}
          </p>
          <button
            type="button"
            onClick={onClose}
            aria-label={es ? 'Cerrar modal' : 'Close modal'}
            className="inline-flex h-10 w-10 items-center justify-center border border-pe-black/15 text-pe-muted transition-colors hover:border-pe-black/30 hover:text-pe-charcoal focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose/45"
          >
            <X size={17} strokeWidth={1.9} />
          </button>
        </div>
        <AddressFormFields
          es={es}
          addressDraft={addressDraft}
          onDraftChange={onDraftChange}
          loadingLocations={loadingLocations}
          locationRegions={locationRegions}
          cityOptions={cityOptions}
          comunaOptions={comunaOptions}
        />
        <label className="inline-flex items-center gap-2 font-sans text-sm text-pe-charcoal">
          <input
            type="checkbox"
            checked={addressDraft.isDefault}
            onChange={(e) => onDraftChange((p) => ({ ...p, isDefault: e.target.checked }))}
            className="accent-pe-rose"
          />
          {es ? 'Dejar como principal' : 'Set as default'}
        </label>
        <div className="flex items-center gap-2 pt-2">
          <button
            type="button"
            onClick={onSave}
            disabled={addressSaving}
            className="px-4 py-2 bg-pe-rose-action text-white font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors disabled:opacity-60"
          >
            {toggleLabel(addressSaving, es, 'Guardando...', 'Saving...', 'Guardar dirección', 'Save address')}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 border border-pe-black/15 text-pe-muted font-sans text-[0.68rem] tracking-wider uppercase hover:border-pe-black/25 transition-colors"
          >
            {es ? 'Cancelar' : 'Cancel'}
          </button>
        </div>
      </div>
    </div>
  );
}

interface AddressesTabProps {
  readonly es: boolean;
  readonly addressFeedback: { type: 'success' | 'error'; text: string } | null;
  readonly loadingAddresses: boolean;
  readonly addresses: CustomerAddressDto[];
  readonly addressDefaultingId: string | null;
  readonly addressDeletingId: string | null;
  readonly onOpenCreateModal: () => void;
  readonly onEditAddress: (address: CustomerAddressDto) => void;
  readonly onSetDefaultAddress: (addressId: string) => void;
  readonly onDeleteAddress: (addressId: string) => void;
  readonly addressModalOpen: boolean;
  readonly editingAddressId: string | null;
  readonly addressDraft: AddressDraft;
  readonly onDraftChange: (updater: (prev: AddressDraft) => AddressDraft) => void;
  readonly loadingLocations: boolean;
  readonly locationRegions: LocationRegionDto[];
  readonly cityOptions: LocationCityDto[];
  readonly comunaOptions: LocationCommuneDto[];
  readonly addressSaving: boolean;
  readonly onSaveAddress: () => void;
  readonly onCloseModal: () => void;
}

function AddressesTab({
  es, addressFeedback, loadingAddresses, addresses, addressDefaultingId, addressDeletingId,
  onOpenCreateModal, onEditAddress, onSetDefaultAddress, onDeleteAddress, addressModalOpen,
  editingAddressId, addressDraft, onDraftChange, loadingLocations, locationRegions, cityOptions,
  comunaOptions, addressSaving, onSaveAddress, onCloseModal,
}: AddressesTabProps) {
  let addressListContent: React.ReactNode;
  if (loadingAddresses) {
    addressListContent = (
      <div className="flex justify-center py-16">
        <Loader2 size={24} className="animate-spin text-pe-rose-ink" />
      </div>
    );
  } else if (addresses.length === 0) {
    addressListContent = (
      <div className="bg-pe-white border border-pe-black/8 p-6">
        <p className="font-sans text-sm text-pe-muted">
          {es
            ? 'Aún no tienes direcciones. Agrega una para usarla en el carrito.'
            : 'You have no addresses yet. Add one to use it at checkout.'}
        </p>
      </div>
    );
  } else {
    addressListContent = (
      <ul className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {addresses.map((address) => (
          <AddressCard
            key={address.id}
            address={address}
            es={es}
            addressDefaultingId={addressDefaultingId}
            addressDeletingId={addressDeletingId}
            onEdit={onEditAddress}
            onSetDefault={onSetDefaultAddress}
            onDelete={onDeleteAddress}
          />
        ))}
      </ul>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <p className="font-display text-pe-black text-xl">
          {es ? 'Libreta de direcciones' : 'Address book'}
        </p>
        <button
          type="button"
          onClick={onOpenCreateModal}
          className="inline-flex items-center justify-center px-4 py-2 bg-pe-rose-action text-white font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors"
        >
          {es ? 'Agregar dirección' : 'Add address'}
        </button>
      </div>

      {addressFeedback && (
        <p className={`font-sans text-[0.74rem] ${addressFeedback.type === 'success' ? 'text-pe-positive-ink' : 'text-pe-danger-ink'}`}>
          {addressFeedback.text}
        </p>
      )}

      {addressListContent}

      {addressModalOpen && (
        <AddressModal
          es={es}
          editingAddressId={editingAddressId}
          addressDraft={addressDraft}
          onDraftChange={onDraftChange}
          loadingLocations={loadingLocations}
          locationRegions={locationRegions}
          cityOptions={cityOptions}
          comunaOptions={comunaOptions}
          addressSaving={addressSaving}
          onSave={onSaveAddress}
          onClose={onCloseModal}
        />
      )}
    </div>
  );
}

interface OrdersTabProps {
  readonly es: boolean;
  readonly locale: string;
  readonly gatewayReturnFeedback: ProofFeedback | null;
  readonly loadingOrders: boolean;
  readonly orders: OrderDto[];
  readonly shippingZones: ShippingZoneConfig[];
  readonly paymentsByOrder: Record<string, PaymentDto>;
  readonly loadingPayments: boolean;
  readonly proofSubmittingByOrder: Record<string, boolean>;
  readonly proofFeedbackByOrder: Record<string, ProofFeedback | undefined>;
  readonly gatewayCheckoutLoadingByOrder: Record<string, boolean>;
  readonly gatewaySimulatingByOrder: Record<string, boolean>;
  readonly deliveryConfirmingByOrder: Record<string, boolean>;
  readonly gatewayFeedbackByOrder: Record<string, ProofFeedback | undefined>;
  readonly proofFilesByOrder: Record<string, File | null>;
  readonly myReturns: ReturnRequestDto[];
  readonly effectiveToken: string | null;
  readonly onSelectProofFile: (orderId: string, file: File | null) => void;
  readonly onOpenOwnProof: (orderId: string) => void;
  readonly onSubmitProof: (orderId: string) => void;
  readonly onStartGatewayCheckout: (orderId: string) => void;
  readonly onSimulateGateway: (orderId: string, simulation: 'APPROVED' | 'FAILED') => void;
  readonly onConfirmDelivery: (orderId: string) => void;
  readonly onReturnRequested: (created: ReturnRequestDto) => void;
}

/** 'warning' is neither a success nor an error -- it's "nothing failed, but you have a manual
 * step left" (the automatic gateway redirect not starting). Giving it the danger styling would
 * read as alarming for something the order itself survived just fine. */
function gatewayReturnBannerClass(type: ProofFeedback['type']): string {
  if (type === 'success') return 'border-pe-positive/40 bg-pe-positive-surface text-pe-positive-ink';
  if (type === 'warning') return 'border-pe-warning/40 bg-pe-warning-surface text-pe-warning-ink';
  return 'border-pe-danger/40 bg-pe-danger-surface text-pe-danger-ink';
}

function GatewayReturnFeedbackBanner({ feedback }: { readonly feedback: ProofFeedback }) {
  return (
    <div className={`mb-4 border px-3 py-2 font-sans text-[0.74rem] ${gatewayReturnBannerClass(feedback.type)}`}>
      {feedback.text}
    </div>
  );
}

function EmptyOrdersState({ es, locale }: { readonly es: boolean; readonly locale: string }) {
  return (
    <div className="text-center py-20">
      <ShoppingBag size={32} className="text-pe-muted mx-auto mb-3" />
      <p className="font-display text-pe-charcoal/45 text-xl">{es ? 'Aun no tienes pedidos' : 'No orders yet'}</p>
      <a
        href={`/${locale}/products`}
        className="inline-block mt-4 font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-rose-ink hover:underline underline-offset-2"
      >
        {es ? 'Explorar productos' : 'Browse products'}
      </a>
    </div>
  );
}

function OrdersTab({
  es, locale, gatewayReturnFeedback, loadingOrders, orders, shippingZones, paymentsByOrder, loadingPayments,
  proofSubmittingByOrder, proofFeedbackByOrder, gatewayCheckoutLoadingByOrder, gatewaySimulatingByOrder,
  deliveryConfirmingByOrder, gatewayFeedbackByOrder, proofFilesByOrder, myReturns, effectiveToken,
  onSelectProofFile, onOpenOwnProof, onSubmitProof, onStartGatewayCheckout, onSimulateGateway,
  onConfirmDelivery, onReturnRequested,
}: OrdersTabProps) {
  if (loadingOrders) {
    return (
      <div className="mx-auto max-w-3xl">
        {gatewayReturnFeedback && <GatewayReturnFeedbackBanner feedback={gatewayReturnFeedback} />}
        <div className="flex justify-center py-16">
          <Loader2 size={24} className="animate-spin text-pe-rose-ink" />
        </div>
      </div>
    );
  }

  if (orders.length === 0) {
    return (
      <div className="mx-auto max-w-3xl">
        {gatewayReturnFeedback && <GatewayReturnFeedbackBanner feedback={gatewayReturnFeedback} />}
        <EmptyOrdersState es={es} locale={locale} />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      {gatewayReturnFeedback && <GatewayReturnFeedbackBanner feedback={gatewayReturnFeedback} />}
      <ul className="flex flex-col gap-4">
        {orders.map((order) => (
          <OrderListItem
            key={order.id}
            order={order}
            es={es}
            shippingZones={shippingZones}
            payment={paymentsByOrder[order.id]}
            loadingPayments={loadingPayments}
            isSubmittingProof={proofSubmittingByOrder[order.id] === true}
            proofFeedback={proofFeedbackByOrder[order.id]}
            isStartingGatewayCheckout={gatewayCheckoutLoadingByOrder[order.id] === true}
            isSimulatingGateway={gatewaySimulatingByOrder[order.id] === true}
            isConfirmingDelivery={deliveryConfirmingByOrder[order.id] === true}
            gatewayFeedback={gatewayFeedbackByOrder[order.id]}
            selectedFile={proofFilesByOrder[order.id]}
            existingReturn={myReturns.find((r) => r.orderId === order.id) ?? null}
            effectiveToken={effectiveToken}
            onSelectProofFile={(file) => onSelectProofFile(order.id, file)}
            onOpenOwnProof={() => onOpenOwnProof(order.id)}
            onSubmitProof={() => onSubmitProof(order.id)}
            onStartGatewayCheckout={() => onStartGatewayCheckout(order.id)}
            onSimulateGateway={(simulation) => onSimulateGateway(order.id, simulation)}
            onConfirmDelivery={() => onConfirmDelivery(order.id)}
            onReturnRequested={onReturnRequested}
          />
        ))}
      </ul>
    </div>
  );
}

export default function AccountPage({ locale }: Props) {
  const { user, token, clearAuth } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  /** Returns this customer already opened, so an order in progress does not offer the button twice. */
  const [myReturns, setMyReturns] = useState<ReturnRequestDto[]>([]);
  const [tab, setTab] = useState<Tab>('profile');
  const [reviews, setReviews] = useState<ReviewDto[]>([]);
  const [orders, setOrders] = useState<OrderDto[]>([]);
  const [shippingZones, setShippingZones] = useState<ShippingZoneConfig[]>([]);
  const [paymentsByOrder, setPaymentsByOrder] = useState<Record<string, PaymentDto>>({});
  const [proofFilesByOrder, setProofFilesByOrder] = useState<Record<string, File | null>>({});
  const [proofSubmittingByOrder, setProofSubmittingByOrder] = useState<Record<string, boolean>>({});
  const [proofFeedbackByOrder, setProofFeedbackByOrder] = useState<Record<string, ProofFeedback | undefined>>({});
  const [gatewayCheckoutLoadingByOrder, setGatewayCheckoutLoadingByOrder] = useState<Record<string, boolean>>({});
  const [gatewaySimulatingByOrder, setGatewaySimulatingByOrder] = useState<Record<string, boolean>>({});
  const [deliveryConfirmingByOrder, setDeliveryConfirmingByOrder] = useState<Record<string, boolean>>({});
  const [gatewayFeedbackByOrder, setGatewayFeedbackByOrder] = useState<Record<string, ProofFeedback | undefined>>({});
  const [gatewayReturnFeedback, setGatewayReturnFeedback] = useState<ProofFeedback | null>(null);
  const [loadingReviews, setLoadingReviews] = useState(false);
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [loadingPayments, setLoadingPayments] = useState(false);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileSaving, setProfileSaving] = useState(false);
  const [profileFeedback, setProfileFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [profile, setProfile] = useState<UserProfileDto | null>(null);
  const [profileName, setProfileName] = useState('');
  const [profilePhone, setProfilePhone] = useState('');
  const [profileNotificationChannel, setProfileNotificationChannel] = useState<NotificationChannelPreference>('AUTO');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordFeedback, setPasswordFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [ready, setReady] = useState(false);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [avatarDragging, setAvatarDragging] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [avatarFeedback, setAvatarFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [addresses, setAddresses] = useState<CustomerAddressDto[]>([]);
  const [loadingAddresses, setLoadingAddresses] = useState(false);
  const [addressModalOpen, setAddressModalOpen] = useState(false);
  const [addressSaving, setAddressSaving] = useState(false);
  const [addressDeletingId, setAddressDeletingId] = useState<string | null>(null);
  const [addressDefaultingId, setAddressDefaultingId] = useState<string | null>(null);
  const [editingAddressId, setEditingAddressId] = useState<string | null>(null);
  const [addressDraft, setAddressDraft] = useState<AddressDraft>(emptyAddressDraft());
  const [locationRegions, setLocationRegions] = useState<LocationRegionDto[]>([]);
  const [loadingLocations, setLoadingLocations] = useState(false);
  const [addressFeedback, setAddressFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const avatarInputRef = useRef<HTMLInputElement>(null);
  const es = locale === 'es';
  const displayName = profile?.fullName?.trim() ? profile.fullName : (user?.email ?? '');
  const selectedRegionId = addressDraft.regionId ? Number(addressDraft.regionId) : null;
  const selectedCityId = addressDraft.cityId ? Number(addressDraft.cityId) : null;
  const selectedComunaId = addressDraft.comunaId ? Number(addressDraft.comunaId) : null;
  const cityOptions = useCityOptions(locationRegions, addressDraft.regionId);
  const comunaOptions = useComunaOptions(cityOptions, addressDraft.cityId);

  useEffect(() => {
    setReady(true);
  }, []);

  useEffect(() => {
    if (typeof window !== 'undefined' && window.location.hash === '#notifications') {
      setTab('notifications');
    }
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const { tab: requestedTab, gatewayFeedback, cleanedUrl } = resolveQueryParamEffects(new URL(window.location.href), es);
    if (requestedTab) setTab(requestedTab);
    if (gatewayFeedback) setGatewayReturnFeedback(gatewayFeedback);
    if (cleanedUrl) window.history.replaceState(window.history.state, '', cleanedUrl);
  }, []);

  useEffect(() => {
    if (tab !== 'orders' || !effectiveToken) return;
    let cancelled = false;
    getMyReturns(effectiveToken).then((list) => {
      if (!cancelled) setMyReturns(list);
    });
    return () => {
      cancelled = true;
    };
  }, [tab, effectiveToken]);

  useEffect(() => {
    if (tab !== 'reviews' || !effectiveToken) return;
    setLoadingReviews(true);
    getMyReviews(effectiveToken)
      .then((r) => setReviews(r))
      .finally(() => setLoadingReviews(false));
  }, [tab, effectiveToken]);

  useEffect(() => {
    if (tab !== 'orders' || !effectiveToken) return;
    setLoadingOrders(true);
    getMyOrders(effectiveToken, 0, 20)
      .then((page) => setOrders(page.content ?? []))
      .finally(() => setLoadingOrders(false));
  }, [tab, effectiveToken]);

  /**
   * Fetches once, not per order: the zone stored on each order is only ever an internal code
   * (LOCAL/REGIONAL/NACIONAL) -- this config is what turns it into the ETA copy a customer
   * actually reads. A failure here is not fatal: OrderShippingInfo just omits that line.
   */
  useEffect(() => {
    if (tab !== 'orders' || shippingZones.length > 0) return;
    let cancelled = false;
    getPublicShippingConfig()
      .then((cfg) => {
        if (!cancelled) setShippingZones(cfg.zones);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [tab, shippingZones.length]);

  useEffect(() => {
    if (tab !== 'orders' || !effectiveToken) return;
    if (!orders.length) {
      setPaymentsByOrder({});
      return;
    }

    let cancelled = false;
    setLoadingPayments(true);

    Promise.all(
      orders.map(async (order) => {
        const payment = await getPaymentByOrder(order.id, effectiveToken);
        return [order.id, payment] as const;
      })
    )
      .then((rows) => {
        if (cancelled) return;
        const next: Record<string, PaymentDto> = {};
        rows.forEach(([orderId, payment]) => {
          if (payment) {
            next[orderId] = payment;
          }
        });
        setPaymentsByOrder(next);
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingPayments(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [tab, orders, effectiveToken]);

  useEffect(() => {
    if (!effectiveToken) return;
    let cancelled = false;
    setProfileLoading(true);
    getMyProfile(effectiveToken)
      .then((data) => {
        if (cancelled) return;
        setProfile(data);
        setProfileName(data.fullName ?? '');
        setProfilePhone(sanitizePhoneDraft(data.phone));
        setProfileNotificationChannel((data.notificationChannelPreference as NotificationChannelPreference) ?? 'AUTO');
        setAvatarUrl(data.avatarUrl ?? null);
      })
      .finally(() => {
        if (!cancelled) setProfileLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [effectiveToken]);

  const loadAddresses = useCallback(async () => {
    if (!effectiveToken) return;
    setLoadingAddresses(true);
    try {
      const rows = await getMyAddresses(effectiveToken);
      setAddresses(rows);
    } finally {
      setLoadingAddresses(false);
    }
  }, [effectiveToken]);

  const loadLocations = useCallback(async () => {
    setLoadingLocations(true);
    try {
      const regions = await getLocationTree();
      setLocationRegions(regions);
    } catch {
      setLocationRegions([]);
    } finally {
      setLoadingLocations(false);
    }
  }, []);

  useEffect(() => {
    if (tab !== 'addresses' || !effectiveToken) return;
    void loadAddresses();
  }, [tab, effectiveToken, loadAddresses]);

  useEffect(() => {
    if (tab !== 'addresses') return;
    void loadLocations();
  }, [tab, loadLocations]);

  useEffect(() => {
    if (!addressDraft.cityId) return;
    if (locationRegions.length === 0) return;
    const stillValid = cityOptions.some((city) => city.id === Number(addressDraft.cityId));
    if (!stillValid) {
      setAddressDraft((prev) => ({ ...prev, cityId: '', comunaId: '' }));
    }
  }, [addressDraft.cityId, cityOptions, locationRegions.length]);

  useEffect(() => {
    if (!addressDraft.comunaId) return;
    if (locationRegions.length === 0) return;
    const stillValid = comunaOptions.some((comuna) => comuna.id === Number(addressDraft.comunaId));
    if (!stillValid) {
      setAddressDraft((prev) => ({ ...prev, comunaId: '' }));
    }
  }, [addressDraft.comunaId, comunaOptions, locationRegions.length]);

  const handleAvatarFile = useCallback(async (file: File) => {
    if (!effectiveToken) return;
    if (!file.type.startsWith('image/')) {
      setAvatarFeedback({ type: 'error', text: es ? 'Solo se permiten imágenes.' : 'Only image files allowed.' });
      return;
    }
    setAvatarUploading(true);
    setAvatarFeedback(null);
    try {
      const res = await uploadMyAvatar(file, effectiveToken);
      setAvatarUrl(res.avatarUrl);
      const { setAuth, token: storeToken } = useAuthStore.getState();
      const storeUser = useAuthStore.getState().user;
      if (storeUser && storeToken) {
        setAuth(storeToken, { ...storeUser, avatarUrl: res.avatarUrl });
      }
      setAvatarFeedback({ type: 'success', text: es ? 'Foto actualizada.' : 'Photo updated.' });
    } catch {
      setAvatarFeedback({ type: 'error', text: es ? 'No se pudo subir la foto.' : 'Could not upload photo.' });
    } finally {
      setAvatarUploading(false);
    }
  }, [effectiveToken, es]);

  // Redirect if not logged in (after hydration)
  useEffect(() => {
    if (ready && !user) {
      window.location.href = `/${locale}/auth/login?redirect=/${locale}/account`;
    }
  }, [ready, user, locale]);

  if (!ready || !user) {
    return (
      <div className="flex items-center justify-center py-32">
        <Loader2 size={28} className="animate-spin text-pe-rose-ink" />
      </div>
    );
  }

  async function handleDeleteReview(reviewId: string) {
    if (!effectiveToken) return;
    try {
      await deleteReview(reviewId, effectiveToken);
      setReviews((prev) => prev.filter((r) => r.id !== reviewId));
    } catch {
      // no-op
    }
  }

  async function handleSaveProfile() {
    if (!effectiveToken || profileSaving) return;
    const fullName = profileName.trim();
    const rawPhone = profilePhone.trim();
    const validationError = validateProfileDraft(fullName, rawPhone, es);
    if (validationError) {
      setProfileFeedback({ type: 'error', text: validationError });
      return;
    }

    setProfileSaving(true);
    setProfileFeedback(null);
    try {
      const updated = await updateMyProfile(fullName, rawPhone, profileNotificationChannel, effectiveToken);
      setProfile(updated);
      setProfileName(updated.fullName);
      setProfilePhone(sanitizePhoneDraft(updated.phone));
      setProfileNotificationChannel((updated.notificationChannelPreference as NotificationChannelPreference) ?? 'AUTO');
      setProfileFeedback({ type: 'success', text: es ? 'Perfil actualizado.' : 'Profile updated.' });
    } catch (error) {
      setProfileFeedback({
        type: 'error',
        text: errorMessageOr(error, es ? 'No pudimos actualizar el perfil.' : 'Could not update profile.'),
      });
    } finally {
      setProfileSaving(false);
    }
  }

  async function handleChangePassword() {
    if (!effectiveToken || passwordSaving) return;
    const validationError = validatePasswordChange(currentPassword, newPassword, confirmPassword, es);
    if (validationError) {
      setPasswordFeedback({ type: 'error', text: validationError });
      return;
    }

    setPasswordSaving(true);
    setPasswordFeedback(null);
    try {
      await changeMyPassword(currentPassword, newPassword, effectiveToken);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setPasswordFeedback({
        type: 'success',
        text: es ? 'Contraseña actualizada correctamente.' : 'Password updated successfully.',
      });
    } catch (error) {
      setPasswordFeedback({ type: 'error', text: passwordChangeErrorMessage(error, es) });
    } finally {
      setPasswordSaving(false);
    }
  }

  function handleLogout() {
    clearAuth();
    window.location.href = `/${locale}/`;
  }


  async function handleStartGatewayCheckout(orderId: string) {
    if (!effectiveToken) return;
    const payment = paymentsByOrder[orderId];
    if (!payment) return;

    setGatewayCheckoutLoadingByOrder((prev) => ({ ...prev, [orderId]: true }));
    setGatewayFeedbackByOrder((prev) => ({ ...prev, [orderId]: undefined }));

    try {
      const session = await createGatewayCheckoutSession(payment.id, effectiveToken);
      const targetUrl = new URL(session.checkoutUrl, window.location.origin);
      const currentUrl = new URL(window.location.href);
      const isSameOrdersView =
        targetUrl.origin === currentUrl.origin &&
        targetUrl.pathname === currentUrl.pathname &&
        targetUrl.searchParams.get('tab') === 'orders';

      if (isSameOrdersView) {
        const reference = session.gatewayReference?.trim() ?? '';
        const referenceSuffix = reference ? ` (${reference})` : '';
        setGatewayFeedbackByOrder((prev) => ({
          ...prev,
          [orderId]: {
            type: 'success',
            text: es
              ? `Checkout simulado iniciado${referenceSuffix}. Puedes usar Simular aprobado/rechazado para cerrar el flujo.`
              : `Simulated checkout started${referenceSuffix}. Use Simulate approve/reject to complete the flow.`,
          },
        }));
        return;
      }

      setGatewayFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: {
          type: 'success',
          text: es ? 'Abriendo checkout seguro...' : 'Opening secure checkout...',
        },
      }));
      window.location.assign(targetUrl.toString());
    } catch (error) {
      const text = error instanceof Error ? error.message : '';
      setGatewayFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: {
          type: 'error',
          text: text || (es ? 'No se pudo iniciar el checkout.' : 'Could not start checkout.'),
        },
      }));
    } finally {
      setGatewayCheckoutLoadingByOrder((prev) => ({ ...prev, [orderId]: false }));
    }
  }

  function openCreateAddressModal() {
    setEditingAddressId(null);
    setAddressDraft(emptyAddressDraft());
    setAddressFeedback(null);
    setAddressModalOpen(true);
  }

  function openEditAddressModal(address: CustomerAddressDto) {
    setEditingAddressId(address.id);
    setAddressDraft(draftFromAddress(address));
    setAddressFeedback(null);
    setAddressModalOpen(true);
  }

  function normalizeAddressPayload(draft: AddressDraft): CreateCustomerAddressRequest {
    const selectedRegion = locationRegions.find((region) => region.id === selectedRegionId);
    const selectedCity = cityOptions.find((city) => city.id === selectedCityId);
    const selectedComuna = comunaOptions.find((comuna) => comuna.id === selectedComunaId);
    if (!selectedRegion || !selectedCity || !selectedComuna) {
      throw new Error(es ? 'Selecciona region, ciudad y comuna validas.' : 'Choose valid region, city, and comuna.');
    }
    return {
      label: draft.label.trim(),
      recipientName: draft.recipientName.trim(),
      phone: draft.phone.trim(),
      line1: draft.line1.trim(),
      line2: draft.line2.trim() || undefined,
      regionId: selectedRegion.id,
      cityId: selectedCity.id,
      comunaId: selectedComuna.id,
      comuna: selectedComuna.name,
      city: selectedCity.name,
      region: selectedRegion.name,
      reference: draft.reference.trim() || undefined,
      isDefault: draft.isDefault,
    };
  }

  async function handleSaveAddress() {
    if (!effectiveToken || addressSaving) return;
    const validationError = validateAddressDraft(addressDraft, es);
    if (validationError) {
      setAddressFeedback({ type: 'error', text: validationError });
      return;
    }
    setAddressSaving(true);
    setAddressFeedback(null);
    try {
      const payload = normalizeAddressPayload(addressDraft);
      await persistAddress(payload, editingAddressId, effectiveToken);
      await loadAddresses();
      setAddressModalOpen(false);
      setAddressFeedback({ type: 'success', text: es ? 'Dirección guardada.' : 'Address saved.' });
    } catch (error) {
      setAddressFeedback({
        type: 'error',
        text: errorMessageOr(error, es ? 'No se pudo guardar la dirección.' : 'Could not save address.'),
      });
    } finally {
      setAddressSaving(false);
    }
  }

  async function handleDeleteAddress(addressId: string) {
    if (!effectiveToken || addressDeletingId) return;
    setAddressDeletingId(addressId);
    setAddressFeedback(null);
    try {
      await deleteMyAddress(addressId, effectiveToken);
      await loadAddresses();
    } catch (error) {
      setAddressFeedback({
        type: 'error',
        text: errorMessageOr(error, es ? 'No se pudo eliminar la dirección.' : 'Could not delete address.'),
      });
    } finally {
      setAddressDeletingId(null);
    }
  }

  async function handleSetDefaultAddress(addressId: string) {
    if (!effectiveToken || addressDefaultingId) return;
    setAddressDefaultingId(addressId);
    setAddressFeedback(null);
    try {
      await setMyAddressAsDefault(addressId, effectiveToken);
      await loadAddresses();
    } catch (error) {
      setAddressFeedback({
        type: 'error',
        text: errorMessageOr(error, es ? 'No se pudo actualizar principal.' : 'Could not set default address.'),
      });
    } finally {
      setAddressDefaultingId(null);
    }
  }

  async function handleConfirmDelivery(orderId: string) {
    if (!effectiveToken) return;
    setDeliveryConfirmingByOrder((prev) => ({ ...prev, [orderId]: true }));
    setGatewayFeedbackByOrder((prev) => ({ ...prev, [orderId]: undefined }));
    try {
      await confirmOrderDelivery(orderId, effectiveToken);
      const page = await getMyOrders(effectiveToken, 0, 20);
      setOrders(page.content ?? []);
      setGatewayFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: {
          type: 'success',
          text: es ? 'Pedido marcado como recibido. Gracias por confirmar.' : 'Order marked as received. Thanks for confirming.',
        },
      }));
    } catch (error) {
      setGatewayFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: {
          type: 'error',
          text: errorMessageOr(error, es ? 'No se pudo confirmar la entrega.' : 'Could not confirm delivery.'),
        },
      }));
    } finally {
      setDeliveryConfirmingByOrder((prev) => ({ ...prev, [orderId]: false }));
    }
  }

  /**
   * Her own receipt, fetched with her token. It stopped being a link the day it moved out of the
   * public media root: the file shows her bank details.
   */
  async function openOwnProof(orderId: string, paymentId: string) {
    if (!effectiveToken) return;
    try {
      openBlobInNewTab(await fetchPaymentProof(paymentId, effectiveToken));
    } catch (error) {
      setProofFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: {
          type: 'error',
          text: errorMessageOr(error, es ? 'No se pudo abrir el comprobante.' : 'Could not open the receipt.'),
        },
      }));
    }
  }

  async function handleSubmitProof(orderId: string) {
    if (!effectiveToken) return;

    const payment = paymentsByOrder[orderId];
    const selectedFile = proofFilesByOrder[orderId];

    if (!payment) {
      setProofFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: { type: 'error', text: es ? 'No se encontro el pago para este pedido.' : 'Payment not found for this order.' },
      }));
      return;
    }

    if (!selectedFile) {
      setProofFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: {
          type: 'error',
          text: es ? 'Sube una imagen del comprobante.' : 'Upload a proof image.',
        },
      }));
      return;
    }

    setProofSubmittingByOrder((prev) => ({ ...prev, [orderId]: true }));
    setProofFeedbackByOrder((prev) => ({ ...prev, [orderId]: undefined }));

    try {
      const proofReference = await uploadPaymentProof(selectedFile, effectiveToken);

      const updatedPayment = await submitPaymentProof(payment.id, proofReference, effectiveToken);
      setPaymentsByOrder((prev) => ({ ...prev, [orderId]: updatedPayment }));
      setProofFilesByOrder((prev) => ({ ...prev, [orderId]: null }));
      setProofFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: {
          type: 'success',
          text: es ? 'Comprobante enviado. Lo revisaremos pronto.' : 'Proof submitted. We will review it soon.',
        },
      }));

      const page = await getMyOrders(effectiveToken, 0, 20);
      setOrders(page.content ?? []);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message.toLowerCase() : '';
      const isTooLarge = errorMessage.includes('too large') || errorMessage.includes('payload too large') || errorMessage.includes('413') || errorMessage.includes('10mb');
      setProofFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: {
          type: 'error',
          text: toggleLabel(
            isTooLarge, es,
            'La imagen supera el tamano maximo (10 MB). Intenta con una mas liviana.',
            'Image exceeds max size (10 MB). Please use a smaller file.',
            'No pudimos enviar el comprobante. Intenta nuevamente.',
            'Could not submit proof. Try again.',
          ),
        },
      }));
    } finally {
      setProofSubmittingByOrder((prev) => ({ ...prev, [orderId]: false }));
    }
  }

  async function handleSimulateGateway(orderId: string, simulation: 'APPROVED' | 'FAILED') {
    const payment = paymentsByOrder[orderId];
    if (!payment) return;

    setGatewaySimulatingByOrder((prev) => ({ ...prev, [orderId]: true }));
    setGatewayFeedbackByOrder((prev) => ({ ...prev, [orderId]: undefined }));

    try {
      await simulateGatewayPaymentStatus(payment.id, simulation);
      const refreshed = effectiveToken ? await getPaymentByOrder(orderId, effectiveToken) : null;
      if (refreshed) {
        setPaymentsByOrder((prev) => ({ ...prev, [orderId]: refreshed }));
      }
      setGatewayFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: {
          type: 'success',
          text: toggleLabel(
            simulation === 'APPROVED', es,
            'Simulacion aplicada: pago aprobado.',
            'Simulation applied: payment approved.',
            'Simulacion aplicada: pago rechazado.',
            'Simulation applied: payment rejected.',
          ),
        },
      }));
    } catch (error) {
      const text = error instanceof Error ? error.message : '';
      setGatewayFeedbackByOrder((prev) => ({
        ...prev,
        [orderId]: {
          type: 'error',
          text: text || (es ? 'No se pudo simular el pago.' : 'Could not simulate payment.'),
        },
      }));
    } finally {
      setGatewaySimulatingByOrder((prev) => ({ ...prev, [orderId]: false }));
    }
  }

  const tabs: { id: Tab; label: string; icon: React.ReactNode }[] = [
    { id: 'profile', label: es ? 'Perfil' : 'Profile', icon: <User size={14} /> },
    { id: 'reviews', label: es ? 'Mis resenas' : 'My reviews', icon: <Star size={14} /> },
    { id: 'orders', label: es ? 'Mis pedidos' : 'My orders', icon: <ShoppingBag size={14} /> },
    { id: 'addresses', label: es ? 'Direcciones' : 'Addresses', icon: <MapPin size={14} /> },
    { id: 'notifications', label: es ? 'Notificaciones' : 'Notifications', icon: null },
  ];

  return (
    <div className="min-h-[calc(100vh-180px)] bg-pe-offwhite">
      <div className="bg-pe-cream border-b border-pe-black/6 py-10">
        <div className="pe-container">
        <div className="mx-auto max-w-4xl flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <p className="pe-eyebrow text-pe-muted mb-1">{es ? 'Mi cuenta' : 'My account'}</p>
          <h1 className="font-display text-pe-black text-3xl font-light">{displayName}</h1>
          <p className="font-sans text-[0.78rem] text-pe-muted mt-1">{user.email}</p>
          <span className="inline-block mt-1.5 font-sans text-[0.65rem] tracking-wider uppercase bg-pe-rose/12 text-pe-rose-ink px-2 py-0.5">
            {roleLabel(user.role, es)}
          </span>
        </div>
          <button
            type="button"
            onClick={handleLogout}
            className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted hover:text-pe-rose-ink transition-colors duration-200"
          >
            {es ? 'Cerrar sesion' : 'Sign out'}
          </button>
        </div>
        </div>
      </div>

      <div className="pe-container py-10">
        <div className="mx-auto max-w-4xl">
        <nav className="flex gap-0 border-b border-pe-black/10 mb-8 overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0 [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
          {tabs.map((t) => (
            <button
              type="button"
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`flex shrink-0 items-center gap-2 whitespace-nowrap px-3.5 sm:px-5 py-3 font-sans text-[0.72rem] tracking-[0.18em] uppercase transition-colors duration-200 border-b-2 -mb-px ${
                tab === t.id
                  ? 'border-pe-rose text-pe-rose-ink'
                  : 'border-transparent text-pe-muted hover:text-pe-charcoal'
              }`}
            >
              {t.icon}
              {t.label}
            </button>
          ))}
        </nav>

        {tab === 'profile' && (
          <ProfileTab
            es={es}
            user={user}
            profile={profile}
            displayName={displayName}
            avatarUrl={avatarUrl}
            avatarDragging={avatarDragging}
            avatarUploading={avatarUploading}
            avatarFeedback={avatarFeedback}
            avatarInputRef={avatarInputRef}
            onAvatarFile={(file) => void handleAvatarFile(file)}
            onAvatarDraggingChange={setAvatarDragging}
            profileName={profileName}
            onProfileNameChange={setProfileName}
            profilePhone={profilePhone}
            onProfilePhoneChange={setProfilePhone}
            profileNotificationChannel={profileNotificationChannel}
            onProfileNotificationChannelChange={setProfileNotificationChannel}
            profileLoading={profileLoading}
            profileSaving={profileSaving}
            profileFeedback={profileFeedback}
            onSaveProfile={() => void handleSaveProfile()}
            currentPassword={currentPassword}
            onCurrentPasswordChange={setCurrentPassword}
            newPassword={newPassword}
            onNewPasswordChange={setNewPassword}
            confirmPassword={confirmPassword}
            onConfirmPasswordChange={setConfirmPassword}
            passwordSaving={passwordSaving}
            passwordFeedback={passwordFeedback}
            onChangePassword={() => void handleChangePassword()}
          />
        )}

        {tab === 'reviews' && (
          <ReviewsTab
            es={es}
            locale={locale}
            loadingReviews={loadingReviews}
            reviews={reviews}
            onDeleteReview={(reviewId) => void handleDeleteReview(reviewId)}
          />
        )}

        {tab === 'addresses' && (
          <AddressesTab
            es={es}
            addressFeedback={addressFeedback}
            loadingAddresses={loadingAddresses}
            addresses={addresses}
            addressDefaultingId={addressDefaultingId}
            addressDeletingId={addressDeletingId}
            onOpenCreateModal={openCreateAddressModal}
            onEditAddress={openEditAddressModal}
            onSetDefaultAddress={(addressId) => void handleSetDefaultAddress(addressId)}
            onDeleteAddress={(addressId) => void handleDeleteAddress(addressId)}
            addressModalOpen={addressModalOpen}
            editingAddressId={editingAddressId}
            addressDraft={addressDraft}
            onDraftChange={setAddressDraft}
            loadingLocations={loadingLocations}
            locationRegions={locationRegions}
            cityOptions={cityOptions}
            comunaOptions={comunaOptions}
            addressSaving={addressSaving}
            onSaveAddress={() => void handleSaveAddress()}
            onCloseModal={() => setAddressModalOpen(false)}
          />
        )}

        {tab === 'notifications' && (
          <div className="mx-auto max-w-3xl">
            <NotificationHistory locale={locale} />
          </div>
        )}

        {tab === 'orders' && (
          <OrdersTab
            es={es}
            locale={locale}
            gatewayReturnFeedback={gatewayReturnFeedback}
            loadingOrders={loadingOrders}
            orders={orders}
            shippingZones={shippingZones}
            paymentsByOrder={paymentsByOrder}
            loadingPayments={loadingPayments}
            proofSubmittingByOrder={proofSubmittingByOrder}
            proofFeedbackByOrder={proofFeedbackByOrder}
            gatewayCheckoutLoadingByOrder={gatewayCheckoutLoadingByOrder}
            gatewaySimulatingByOrder={gatewaySimulatingByOrder}
            deliveryConfirmingByOrder={deliveryConfirmingByOrder}
            gatewayFeedbackByOrder={gatewayFeedbackByOrder}
            proofFilesByOrder={proofFilesByOrder}
            myReturns={myReturns}
            effectiveToken={effectiveToken}
            onSelectProofFile={(orderId, file) => setProofFilesByOrder((prev) => ({ ...prev, [orderId]: file }))}
            onOpenOwnProof={(orderId) => void openOwnProof(orderId, paymentsByOrder[orderId]?.id ?? '')}
            onSubmitProof={(orderId) => void handleSubmitProof(orderId)}
            onStartGatewayCheckout={(orderId) => void handleStartGatewayCheckout(orderId)}
            onSimulateGateway={(orderId, simulation) => void handleSimulateGateway(orderId, simulation)}
            onConfirmDelivery={(orderId) => void handleConfirmDelivery(orderId)}
            onReturnRequested={(created) => setMyReturns((current) => [created, ...current])}
          />
        )}
        </div>
      </div>
    </div>
  );
}

