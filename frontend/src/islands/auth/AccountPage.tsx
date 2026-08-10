import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { User, Star, ShoppingBag, Trash2, Loader2, Camera, MapPin, X } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import NotificationHistory from '../NotificationHistory';
import {
  getMyReviews,
  getLocationTree,
  deleteReview,
  getMyOrders,
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
  uploadPaymentProofImage,
  uploadMyAvatar,
  createGatewayCheckoutSession,
  simulateGatewayPaymentStatus,
  type ReviewDto,
  type OrderDto,
  type PaymentDto,
  type UserProfileDto,
  type CustomerAddressDto,
  type CreateCustomerAddressRequest,
  type LocationCityDto,
  type LocationCommuneDto,
  type LocationRegionDto,
} from '../../lib/api';

interface Props {
  locale: 'es' | 'en';
}

type Tab = 'profile' | 'reviews' | 'orders' | 'addresses' | 'notifications';
type ProofFeedback = { type: 'success' | 'error'; text: string };
type TimelineState = 'done' | 'current' | 'todo';
type TimelineStepStatus = Exclude<OrderDto['status'], 'CANCELLED'>;
type NotificationChannelPreference = 'AUTO' | 'WHATSAPP' | 'EMAIL' | 'BOTH';
type AddressDraft = {
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string;
  regionId: string;
  cityId: string;
  comunaId: string;
  comuna: string;
  city: string;
  region: string;
  reference: string;
  isDefault: boolean;
};

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

function emptyAddressDraft(): AddressDraft {
  return {
    label: '',
    recipientName: '',
    phone: '',
    line1: '',
    line2: '',
    regionId: '',
    cityId: '',
    comunaId: '',
    comuna: '',
    city: '',
    region: '',
    reference: '',
    isDefault: false,
  };
}

function draftFromAddress(address: CustomerAddressDto): AddressDraft {
  return {
    label: address.label ?? '',
    recipientName: address.recipientName ?? '',
    phone: address.phone ?? '',
    line1: address.line1 ?? '',
    line2: address.line2 ?? '',
    regionId: address.regionId ? String(address.regionId) : '',
    cityId: address.cityId ? String(address.cityId) : '',
    comunaId: address.communeId ? String(address.communeId) : '',
    comuna: address.comuna ?? '',
    city: address.city ?? '',
    region: address.region ?? '',
    reference: address.reference ?? '',
    isDefault: Boolean(address.isDefault),
  };
}

export default function AccountPage({ locale }: Props) {
  const { user, token, clearAuth } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [tab, setTab] = useState<Tab>('profile');
  const [reviews, setReviews] = useState<ReviewDto[]>([]);
  const [orders, setOrders] = useState<OrderDto[]>([]);
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
  const cityOptions = useMemo<LocationCityDto[]>(() => {
    if (!selectedRegionId) return [];
    return locationRegions.find((region) => region.id === selectedRegionId)?.cities ?? [];
  }, [locationRegions, selectedRegionId]);
  const comunaOptions = useMemo<LocationCommuneDto[]>(() => {
    if (!selectedCityId) return [];
    return cityOptions.find((city) => city.id === selectedCityId)?.communes ?? [];
  }, [cityOptions, selectedCityId]);

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
    const url = new URL(window.location.href);
    const searchParams = url.searchParams;
    const requestedTab = searchParams.get('tab');
    if (
      requestedTab === 'profile'
      || requestedTab === 'reviews'
      || requestedTab === 'orders'
      || requestedTab === 'addresses'
      || requestedTab === 'notifications'
    ) {
      setTab(requestedTab);
    }

    const mpSignal = (searchParams.get('mp') ?? '').trim().toLowerCase();
    const collectionStatusSignal = (searchParams.get('collection_status') ?? '').trim().toLowerCase();
    const genericStatusSignal = (searchParams.get('status') ?? '').trim().toLowerCase();
    const normalizedSignal = mpSignal || collectionStatusSignal || genericStatusSignal;

    if (normalizedSignal) {
      if (normalizedSignal === 'success' || normalizedSignal === 'approved') {
        setGatewayReturnFeedback({
          type: 'success',
          text: es
            ? 'Pago confirmado por pasarela. Estamos actualizando tu pedido.'
            : 'Gateway payment confirmed. We are updating your order.',
        });
      } else if (normalizedSignal === 'pending' || normalizedSignal === 'in_process' || normalizedSignal === 'inprocess') {
        setGatewayReturnFeedback({
          type: 'success',
          text: es
            ? 'Pago recibido como pendiente. Te avisaremos cuando quede confirmado.'
            : 'Payment received as pending. We will notify you once it is confirmed.',
        });
      } else if (normalizedSignal === 'failure' || normalizedSignal === 'rejected' || normalizedSignal === 'cancelled') {
        setGatewayReturnFeedback({
          type: 'error',
          text: es
            ? 'El pago fue rechazado o cancelado. Puedes reintentar cuando quieras.'
            : 'Payment was rejected or cancelled. You can retry anytime.',
        });
      }

      setTab('orders');

      const mpReturnKeys = [
        'mp',
        'collection_status',
        'status',
        'payment_id',
        'payment_type',
        'merchant_order_id',
        'preference_id',
        'external_reference',
        'site_id',
        'processing_mode',
        'merchant_account_id',
      ];
      mpReturnKeys.forEach((key) => searchParams.delete(key));

      const nextQuery = searchParams.toString();
      const nextUrl = nextQuery ? `${url.pathname}?${nextQuery}` : url.pathname;
      window.history.replaceState(window.history.state, '', nextUrl);
    }
  }, []);

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
        <Loader2 size={28} className="animate-spin text-pe-rose/60" />
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
    if (!fullName) {
      setProfileFeedback({ type: 'error', text: es ? 'El nombre no puede estar vacio.' : 'Full name cannot be empty.' });
      return;
    }
    const rawPhone = profilePhone.trim();
    if (rawPhone.includes('@')) {
      setProfileFeedback({
        type: 'error',
        text: es ? 'Ingresa un telefono valido, no un correo.' : 'Enter a valid phone number, not an email.',
      });
      return;
    }
    const digits = rawPhone.replace(/\D/g, '');
    if (rawPhone && (digits.length < 8 || digits.length > 15)) {
      setProfileFeedback({
        type: 'error',
        text: es ? 'El telefono debe tener entre 8 y 15 digitos.' : 'Phone must contain between 8 and 15 digits.',
      });
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
      const text = error instanceof Error ? error.message : '';
      setProfileFeedback({
        type: 'error',
        text: text || (es ? 'No pudimos actualizar el perfil.' : 'Could not update profile.'),
      });
    } finally {
      setProfileSaving(false);
    }
  }

  async function handleChangePassword() {
    if (!effectiveToken || passwordSaving) return;
    if (!currentPassword.trim()) {
      setPasswordFeedback({
        type: 'error',
        text: es ? 'Ingresa tu contraseña actual.' : 'Enter your current password.',
      });
      return;
    }
    if (newPassword.length < 8) {
      setPasswordFeedback({
        type: 'error',
        text: es ? 'La nueva contraseña debe tener al menos 8 caracteres.' : 'New password must have at least 8 characters.',
      });
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordFeedback({
        type: 'error',
        text: es ? 'Las contraseñas nuevas no coinciden.' : 'New passwords do not match.',
      });
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
      const raw = error instanceof Error ? error.message.toLowerCase() : '';
      const message = raw.includes('current password is invalid')
        ? (es ? 'La contraseña actual no es correcta.' : 'Current password is incorrect.')
        : (error instanceof Error ? error.message : (es ? 'No pudimos cambiar la contraseña.' : 'Could not change password.'));
      setPasswordFeedback({ type: 'error', text: message });
    } finally {
      setPasswordSaving(false);
    }
  }

  function handleLogout() {
    clearAuth();
    window.location.href = `/${locale}/`;
  }

  function paymentStatusLabel(status: string) {
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
        setGatewayFeedbackByOrder((prev) => ({
          ...prev,
          [orderId]: {
            type: 'success',
            text: es
              ? `Checkout simulado iniciado${reference ? ` (${reference})` : ''}. Puedes usar Simular aprobado/rechazado para cerrar el flujo.`
              : `Simulated checkout started${reference ? ` (${reference})` : ''}. Use Simulate approve/reject to complete the flow.`,
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

  function validateAddressDraft(draft: AddressDraft): string | null {
    if (!draft.label.trim()) return es ? 'Debes ingresar un alias de dirección.' : 'Address alias is required.';
    if (!draft.recipientName.trim()) return es ? 'Debes ingresar destinatario.' : 'Recipient name is required.';
    if (!draft.phone.trim()) return es ? 'Debes ingresar teléfono.' : 'Phone is required.';
    const digits = draft.phone.replace(/\D/g, '');
    if (digits.length < 8 || digits.length > 15) {
      return es ? 'El teléfono debe tener entre 8 y 15 dígitos.' : 'Phone must contain between 8 and 15 digits.';
    }
    if (!draft.line1.trim()) return es ? 'Debes ingresar dirección.' : 'Address line is required.';
    if (!draft.regionId) return es ? 'Debes seleccionar region.' : 'Region selection is required.';
    if (!draft.cityId) return es ? 'Debes seleccionar ciudad.' : 'City selection is required.';
    if (!draft.comunaId) return es ? 'Debes seleccionar comuna.' : 'Comuna selection is required.';
    return null;
  }

  async function handleSaveAddress() {
    if (!effectiveToken || addressSaving) return;
    const validationError = validateAddressDraft(addressDraft);
    if (validationError) {
      setAddressFeedback({ type: 'error', text: validationError });
      return;
    }
    setAddressSaving(true);
    setAddressFeedback(null);
    try {
      const payload = normalizeAddressPayload(addressDraft);
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
        }, effectiveToken);
        if (payload.isDefault) {
          await setMyAddressAsDefault(editingAddressId, effectiveToken);
        }
      } else {
        const created = await createMyAddress(payload, effectiveToken);
        if (payload.isDefault && !created.isDefault) {
          await setMyAddressAsDefault(created.id, effectiveToken);
        }
      }
      await loadAddresses();
      setAddressModalOpen(false);
      setAddressFeedback({ type: 'success', text: es ? 'Dirección guardada.' : 'Address saved.' });
    } catch (error) {
      setAddressFeedback({
        type: 'error',
        text: error instanceof Error ? error.message : (es ? 'No se pudo guardar la dirección.' : 'Could not save address.'),
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
        text: error instanceof Error ? error.message : (es ? 'No se pudo eliminar la dirección.' : 'Could not delete address.'),
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
        text: error instanceof Error ? error.message : (es ? 'No se pudo actualizar principal.' : 'Could not set default address.'),
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
          text: error instanceof Error
            ? error.message
            : (es ? 'No se pudo confirmar la entrega.' : 'Could not confirm delivery.'),
        },
      }));
    } finally {
      setDeliveryConfirmingByOrder((prev) => ({ ...prev, [orderId]: false }));
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
      const upload = await uploadPaymentProofImage(selectedFile, effectiveToken);
      const proofReference = upload.url;

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
          text: isTooLarge
            ? (es ? 'La imagen supera el tamano maximo (10 MB). Intenta con una mas liviana.' : 'Image exceeds max size (10 MB). Please use a smaller file.')
            : (es ? 'No pudimos enviar el comprobante. Intenta nuevamente.' : 'Could not submit proof. Try again.'),
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
          text: simulation === 'APPROVED'
            ? (es ? 'Simulacion aplicada: pago aprobado.' : 'Simulation applied: payment approved.')
            : (es ? 'Simulacion aplicada: pago rechazado.' : 'Simulation applied: payment rejected.'),
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

  function formatMoney(amount: number, currency: string) {
    return new Intl.NumberFormat(es ? 'es-CL' : 'en-US', {
      style: 'currency',
      currency: currency || 'CLP',
      maximumFractionDigits: 0,
    }).format(amount ?? 0);
  }

  function orderStatusLabel(status: OrderDto['status']) {
    const labelsEs: Record<OrderDto['status'], string> = {
      CREATED: 'Creado',
      PENDING_PAYMENT: 'Pendiente de pago',
      PAYMENT_UNDER_REVIEW: 'Pago en revision',
      PAID: 'Pagado',
      PREPARING_ORDER: 'Preparando pedido',
      SHIPPED: 'Enviado',
      DELIVERED: 'Entregado',
      CANCELLED: 'Cancelado',
    };
    const labelsEn: Record<OrderDto['status'], string> = {
      CREATED: 'Created',
      PENDING_PAYMENT: 'Pending payment',
      PAYMENT_UNDER_REVIEW: 'Payment under review',
      PAID: 'Paid',
      PREPARING_ORDER: 'Preparing order',
      SHIPPED: 'Shipped',
      DELIVERED: 'Delivered',
      CANCELLED: 'Cancelled',
    };
    return (es ? labelsEs : labelsEn)[status] ?? status;
  }

  function orderTimelineLabel(status: TimelineStepStatus) {
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
      return {
        cancelled: true,
        steps: ORDER_TIMELINE_FLOW.map((step, index) => ({
          step,
          state: (index === 0 ? 'done' : 'todo') as TimelineState,
        })),
      };
    }

    const currentIndex = ORDER_TIMELINE_FLOW.indexOf(status as TimelineStepStatus);
    const normalizedIndex = currentIndex >= 0 ? currentIndex : 0;

    return {
      cancelled: false,
      steps: ORDER_TIMELINE_FLOW.map((step, index) => ({
        step,
        state: (
          index < normalizedIndex
            ? 'done'
            : index === normalizedIndex
              ? 'current'
              : 'todo'
        ) as TimelineState,
      })),
    };
  }

  function paymentMethodLabel(method: OrderDto['paymentMethod']) {
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

  function shippingPaymentModeLabel(mode: string | null | undefined) {
    const normalized = (mode ?? '').trim().toUpperCase();
    const labelsEs: Record<string, string> = {
      POR_PAGAR: 'Envio por pagar',
    };
    const labelsEn: Record<string, string> = {
      POR_PAGAR: 'Shipping paid on pickup',
    };
    return (es ? labelsEs : labelsEn)[normalized] ?? normalized;
  }

  function notificationChannelLabel(value: string | null | undefined) {
    const normalized = (value ?? 'AUTO').toUpperCase();
    const labelsEs: Record<string, string> = {
      AUTO: 'Automatico',
      WHATSAPP: 'WhatsApp',
      EMAIL: 'Correo',
      BOTH: 'Ambos',
    };
    const labelsEn: Record<string, string> = {
      AUTO: 'Automatic',
      WHATSAPP: 'WhatsApp',
      EMAIL: 'Email',
      BOTH: 'Both',
    };
    return (es ? labelsEs : labelsEn)[normalized] ?? normalized;
  }

  function maskAccountNumber(accountNumber: string | null | undefined) {
    const normalized = (accountNumber ?? '').trim();
    if (!normalized) return '-';
    if (normalized.length <= 4) return normalized;
    return `${'*'.repeat(Math.max(0, normalized.length - 4))}${normalized.slice(-4)}`;
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
        <div className="pe-container flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <p className="pe-eyebrow text-pe-charcoal/40 mb-1">{es ? 'Mi cuenta' : 'My account'}</p>
          <h1 className="font-display text-pe-black text-3xl font-light">{displayName}</h1>
          <p className="font-sans text-[0.78rem] text-pe-charcoal/50 mt-1">{user.email}</p>
          <span className="inline-block mt-1.5 font-sans text-[0.65rem] tracking-wider uppercase bg-pe-rose/12 text-pe-rose-deep px-2 py-0.5">
            {user.role === 'ADMIN' ? 'Admin' : user.role === 'SELLER' ? (es ? 'Vendedor/a' : 'Seller') : (es ? 'Cliente' : 'Customer')}
          </span>
        </div>
          <button
            onClick={handleLogout}
            className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-charcoal/40 hover:text-pe-rose-deep transition-colors duration-200"
          >
            {es ? 'Cerrar sesion' : 'Sign out'}
          </button>
        </div>
      </div>

      <div className="pe-container py-10">
        <nav className="flex gap-0 border-b border-pe-black/10 mb-8">
          {tabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`flex items-center gap-2 px-5 py-3 font-sans text-[0.72rem] tracking-[0.18em] uppercase transition-colors duration-200 border-b-2 -mb-px ${
                tab === t.id
                  ? 'border-pe-rose text-pe-rose-deep'
                  : 'border-transparent text-pe-charcoal/50 hover:text-pe-charcoal'
              }`}
            >
              {t.icon}
              {t.label}
            </button>
          ))}
        </nav>

        {tab === 'profile' && (
          <div className="max-w-2xl flex flex-col gap-5">
            {/* Avatar */}
            <div
              className={`bg-pe-white p-6 border transition-colors duration-200 flex flex-col gap-4 ${avatarDragging ? 'border-pe-rose bg-pe-rose/5' : 'border-pe-black/6'}`}
              onDragOver={(e) => { e.preventDefault(); setAvatarDragging(true); }}
              onDragLeave={(e) => {
                if (!e.currentTarget.contains(e.relatedTarget as Node)) setAvatarDragging(false);
              }}
              onDrop={(e) => {
                e.preventDefault();
                setAvatarDragging(false);
                const file = e.dataTransfer.files[0];
                if (file) void handleAvatarFile(file);
              }}
            >
              <p className="pe-eyebrow text-pe-charcoal/40">{es ? 'Foto de perfil' : 'Profile photo'}</p>
              <div className="flex items-center gap-5">
                <div className={`relative w-20 h-20 rounded-full shrink-0 overflow-hidden border-2 transition-colors duration-200 ${avatarDragging ? 'border-pe-rose' : 'border-pe-black/10'}`}>
                  {avatarUrl ? (
                    <img src={avatarUrl} alt={displayName} className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full bg-pe-rose flex items-center justify-center text-pe-offwhite font-display text-2xl font-light">
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
                  <p className="font-sans text-[0.72rem] text-pe-charcoal/50">
                    {avatarDragging
                      ? (es ? 'Suelta la imagen aquí' : 'Drop the image here')
                      : (es ? 'Arrastra una foto aquí o' : 'Drag a photo here or')}
                  </p>
                  <label className="inline-flex items-center gap-1.5 cursor-pointer px-3 py-1.5 border border-pe-black/15 font-sans text-[0.68rem] tracking-wider uppercase text-pe-charcoal/60 hover:border-pe-rose hover:text-pe-rose-deep transition-colors duration-200">
                    <Camera size={12} />
                    {es ? 'Elegir archivo' : 'Choose file'}
                    <input
                      ref={avatarInputRef}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) void handleAvatarFile(file);
                        e.target.value = '';
                      }}
                    />
                  </label>
                  {avatarFeedback && (
                    <span className={`font-sans text-[0.7rem] ${avatarFeedback.type === 'success' ? 'text-green-700' : 'text-red-500'}`}>
                      {avatarFeedback.text}
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div className="bg-pe-white p-6 border border-pe-black/6 flex flex-col gap-3">
              <p className="pe-eyebrow text-pe-charcoal/40">{es ? 'Datos de perfil' : 'Profile details'}</p>
              <label className="font-sans text-[0.72rem] text-pe-charcoal/70">{es ? 'Nombre completo' : 'Full name'}</label>
              <input
                type="text"
                value={profileName}
                onChange={(event) => setProfileName(event.target.value)}
                disabled={profileLoading || profileSaving}
                className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-none focus:border-pe-rose disabled:opacity-60"
                placeholder={es ? 'Tu nombre completo' : 'Your full name'}
              />
              <label className="font-sans text-[0.72rem] text-pe-charcoal/70">{es ? 'Telefono WhatsApp' : 'WhatsApp phone'}</label>
              <input
                type="tel"
                value={profilePhone}
                onChange={(event) => setProfilePhone(event.target.value)}
                disabled={profileLoading || profileSaving}
                autoComplete="tel"
                inputMode="tel"
                name="whatsappPhone"
                className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-none focus:border-pe-rose disabled:opacity-60"
                placeholder={es ? '+56912345678' : '+14155550123'}
              />
              <label className="font-sans text-[0.72rem] text-pe-charcoal/70">
                {es ? 'Canal de notificaciones' : 'Notification channel'}
              </label>
              <select
                value={profileNotificationChannel}
                onChange={(event) => setProfileNotificationChannel(event.target.value as NotificationChannelPreference)}
                disabled={profileLoading || profileSaving}
                className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-none focus:border-pe-rose disabled:opacity-60"
              >
                <option value="AUTO">{es ? 'Automatico (recomendado)' : 'Automatic (recommended)'}</option>
                <option value="WHATSAPP">WhatsApp</option>
                <option value="EMAIL">{es ? 'Correo' : 'Email'}</option>
                <option value="BOTH">{es ? 'Ambos' : 'Both'}</option>
              </select>
              <p className="font-sans text-[0.68rem] text-pe-charcoal/60">
                {es
                  ? 'Si completas tu WhatsApp y eliges un canal, enviaremos notificaciones de pedido segun tu preferencia.'
                  : 'If you provide WhatsApp and choose a channel, order notifications will follow your preference.'}
              </p>
              <div className="flex items-center gap-3">
                <button
                  onClick={() => {
                    void handleSaveProfile();
                  }}
                  disabled={profileLoading || profileSaving}
                  className="inline-flex items-center justify-center px-4 py-2 bg-pe-rose text-white font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors disabled:opacity-60"
                >
                  {profileSaving ? (es ? 'Guardando...' : 'Saving...') : (es ? 'Guardar perfil' : 'Save profile')}
                </button>
                {profileFeedback && (
                  <span className={`font-sans text-[0.72rem] ${profileFeedback.type === 'success' ? 'text-green-700' : 'text-red-500'}`}>
                    {profileFeedback.text}
                  </span>
                )}
              </div>
            </div>

            <div className="bg-pe-white p-6 border border-pe-black/6 flex flex-col gap-3">
              <p className="pe-eyebrow text-pe-charcoal/40">{es ? 'Cambiar contraseña' : 'Change password'}</p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <input
                  type="password"
                  value={currentPassword}
                  onChange={(event) => setCurrentPassword(event.target.value)}
                  className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-none focus:border-pe-rose"
                  placeholder={es ? 'Contraseña actual' : 'Current password'}
                />
                <input
                  type="password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-none focus:border-pe-rose"
                  placeholder={es ? 'Nueva contraseña' : 'New password'}
                />
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  className="border border-pe-black/10 px-3 py-2 font-sans text-sm text-pe-charcoal focus:outline-none focus:border-pe-rose sm:col-span-2"
                  placeholder={es ? 'Confirmar nueva contraseña' : 'Confirm new password'}
                />
              </div>
              <div className="flex items-center gap-3">
                <button
                  onClick={() => {
                    void handleChangePassword();
                  }}
                  disabled={passwordSaving}
                  className="inline-flex items-center justify-center px-4 py-2 bg-pe-black text-pe-offwhite font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-charcoal transition-colors disabled:opacity-60"
                >
                  {passwordSaving ? (es ? 'Actualizando...' : 'Updating...') : (es ? 'Actualizar contraseña' : 'Update password')}
                </button>
                {passwordFeedback && (
                  <span className={`font-sans text-[0.72rem] ${passwordFeedback.type === 'success' ? 'text-green-700' : 'text-red-500'}`}>
                    {passwordFeedback.text}
                  </span>
                )}
              </div>
            </div>

            <div className="bg-pe-white p-6 flex flex-col gap-3 border border-pe-black/6">
              <p className="pe-eyebrow text-pe-charcoal/40">Email</p>
              <p className="font-sans text-pe-charcoal">{profile?.email ?? user.email}</p>
            </div>
            <div className="bg-pe-white p-6 flex flex-col gap-3 border border-pe-black/6">
              <p className="pe-eyebrow text-pe-charcoal/40">{es ? 'Telefono WhatsApp' : 'WhatsApp phone'}</p>
              <p className="font-sans text-pe-charcoal">{profile?.phone ?? (es ? 'No configurado' : 'Not configured')}</p>
            </div>
            <div className="bg-pe-white p-6 flex flex-col gap-3 border border-pe-black/6">
              <p className="pe-eyebrow text-pe-charcoal/40">{es ? 'Canal de notificaciones' : 'Notification channel'}</p>
              <p className="font-sans text-pe-charcoal">{notificationChannelLabel(profile?.notificationChannelPreference)}</p>
            </div>
            <div className="bg-pe-white p-6 flex flex-col gap-3 border border-pe-black/6">
              <p className="pe-eyebrow text-pe-charcoal/40">{es ? 'Rol' : 'Role'}</p>
              <p className="font-sans text-pe-charcoal">
                {user.role === 'ADMIN' ? 'Admin' : user.role === 'SELLER' ? (es ? 'Vendedor/a' : 'Seller') : (es ? 'Cliente' : 'Customer')}
              </p>
            </div>
            <p className="font-sans text-[0.72rem] text-pe-charcoal/40">
              {es ? 'ID de cuenta: ' : 'Account ID: '}
              {user.id}
            </p>
          </div>
        )}

        {tab === 'reviews' && (
          <div className="max-w-2xl">
            {loadingReviews ? (
              <div className="flex justify-center py-16">
                <Loader2 size={24} className="animate-spin text-pe-rose/60" />
              </div>
            ) : reviews.length === 0 ? (
              <div className="text-center py-20">
                <Star size={32} className="text-pe-charcoal/20 mx-auto mb-3" />
                <p className="font-display text-pe-black/30 text-xl">{es ? 'Aun no escribiste resenas' : 'No reviews yet'}</p>
                <a
                  href={`/${locale}/products`}
                  className="inline-block mt-4 font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-rose-deep hover:underline underline-offset-2"
                >
                  {es ? 'Explorar productos' : 'Browse products'}
                </a>
              </div>
            ) : (
              <ul className="flex flex-col gap-4">
                {reviews.map((review) => (
                  <li key={review.id} className="bg-pe-white border border-pe-black/6 p-5 flex flex-col gap-2">
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex gap-0.5">
                        {Array.from({ length: 5 }).map((_, i) => (
                          <Star
                            key={i}
                            size={13}
                            className={i < review.rating ? 'text-pe-rose fill-pe-rose' : 'text-pe-charcoal/20'}
                          />
                        ))}
                      </div>
                      <button
                        onClick={() => handleDeleteReview(review.id)}
                        className="text-pe-charcoal/30 hover:text-pe-rose-deep transition-colors duration-200 p-0.5"
                        aria-label={es ? 'Eliminar resena' : 'Delete review'}
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                    {review.title && <p className="font-display text-pe-black font-medium text-[1rem]">{review.title}</p>}
                    {review.comment && <p className="font-sans text-sm text-pe-charcoal/70 leading-relaxed">{review.comment}</p>}
                    <div className="flex items-center gap-3 mt-1">
                      <span
                        className={`font-sans text-[0.65rem] tracking-wider uppercase px-2 py-0.5 ${
                          review.approved ? 'bg-green-50 text-green-700' : 'bg-pe-cream text-pe-charcoal/40'
                        }`}
                      >
                        {review.approved ? (es ? 'Aprobada' : 'Approved') : (es ? 'Pendiente' : 'Pending')}
                      </span>
                      <span className="font-sans text-[0.68rem] text-pe-charcoal/70">
                        {new Date(review.createdAt).toLocaleDateString(es ? 'es-CL' : 'en-US')}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {tab === 'addresses' && (
          <div className="max-w-4xl flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <p className="font-display text-pe-black text-xl">
                {es ? 'Libreta de direcciones' : 'Address book'}
              </p>
              <button
                type="button"
                onClick={openCreateAddressModal}
                className="inline-flex items-center justify-center px-4 py-2 bg-pe-rose text-white font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors"
              >
                {es ? 'Agregar dirección' : 'Add address'}
              </button>
            </div>

            {addressFeedback && (
              <p className={`font-sans text-[0.74rem] ${addressFeedback.type === 'success' ? 'text-green-700' : 'text-red-600'}`}>
                {addressFeedback.text}
              </p>
            )}

            {loadingAddresses ? (
              <div className="flex justify-center py-16">
                <Loader2 size={24} className="animate-spin text-pe-rose/60" />
              </div>
            ) : addresses.length === 0 ? (
              <div className="bg-pe-white border border-pe-black/8 p-6">
                <p className="font-sans text-sm text-pe-charcoal/70">
                  {es
                    ? 'Aún no tienes direcciones. Agrega una para usarla en el carrito.'
                    : 'You have no addresses yet. Add one to use it at checkout.'}
                </p>
              </div>
            ) : (
              <ul className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {addresses.map((address) => (
                  <li key={address.id} className="bg-pe-white border border-pe-black/8 p-4 flex flex-col gap-2">
                    <div className="flex items-center justify-between gap-3">
                      <p className="font-display text-pe-black text-lg">{address.label}</p>
                      {address.isDefault && (
                        <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-rose/12 text-pe-rose-deep">
                          {es ? 'Principal' : 'Default'}
                        </span>
                      )}
                    </div>
                    <p className="font-sans text-sm text-pe-charcoal/80">{address.recipientName}</p>
                    <p className="font-sans text-sm text-pe-charcoal/65">{address.phone}</p>
                    <p className="font-sans text-sm text-pe-charcoal/70">
                      {address.line1}
                      {address.line2 ? `, ${address.line2}` : ''}
                    </p>
                    <p className="font-sans text-sm text-pe-charcoal/70">
                      {address.comuna}, {address.city}, {address.region}
                    </p>
                    {address.reference && (
                      <p className="font-sans text-[0.72rem] text-pe-charcoal/60">
                        {es ? 'Referencia:' : 'Reference:'} {address.reference}
                      </p>
                    )}
                    <div className="flex flex-wrap gap-2 pt-2">
                      <button
                        type="button"
                        onClick={() => openEditAddressModal(address)}
                        className="px-3 py-1.5 border border-pe-black/15 text-pe-charcoal/75 font-sans text-[0.66rem] tracking-wider uppercase hover:border-pe-charcoal/30 transition-colors"
                      >
                        {es ? 'Editar' : 'Edit'}
                      </button>
                      {!address.isDefault && (
                        <button
                          type="button"
                          onClick={() => {
                            void handleSetDefaultAddress(address.id);
                          }}
                          disabled={addressDefaultingId === address.id}
                          className="px-3 py-1.5 border border-pe-rose/30 text-pe-rose-deep font-sans text-[0.66rem] tracking-wider uppercase hover:bg-pe-rose/10 transition-colors disabled:opacity-60"
                        >
                          {addressDefaultingId === address.id
                            ? (es ? 'Guardando...' : 'Saving...')
                            : (es ? 'Marcar principal' : 'Set default')}
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => {
                          void handleDeleteAddress(address.id);
                        }}
                        disabled={addressDeletingId === address.id}
                        className="px-3 py-1.5 border border-red-200 text-red-600 font-sans text-[0.66rem] tracking-wider uppercase hover:bg-red-50 transition-colors disabled:opacity-60"
                      >
                        {addressDeletingId === address.id
                          ? (es ? 'Eliminando...' : 'Deleting...')
                          : (es ? 'Eliminar' : 'Delete')}
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )}

            {addressModalOpen && (
              <div className="fixed inset-0 z-[90] bg-black/45 flex items-center justify-center p-4">
                <div className="w-full max-w-xl bg-pe-white border border-pe-black/10 p-5 flex flex-col gap-3 max-h-[90vh] overflow-y-auto">
                  <div className="flex items-center justify-between">
                    <p className="font-display text-pe-black text-xl">
                      {editingAddressId
                        ? (es ? 'Editar dirección' : 'Edit address')
                        : (es ? 'Nueva dirección' : 'New address')}
                    </p>
                    <button
                      type="button"
                      onClick={() => setAddressModalOpen(false)}
                      aria-label={es ? 'Cerrar modal' : 'Close modal'}
                      className="inline-flex h-10 w-10 items-center justify-center border border-pe-black/15 text-pe-charcoal/65 transition-colors hover:border-pe-black/30 hover:text-pe-charcoal focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pe-rose/45"
                    >
                      <X size={17} strokeWidth={1.9} />
                    </button>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <input value={addressDraft.label} onChange={(e) => setAddressDraft((p) => ({ ...p, label: e.target.value }))} placeholder={es ? 'Alias (Casa, Oficina)' : 'Label (Home, Office)'} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                    <input value={addressDraft.recipientName} onChange={(e) => setAddressDraft((p) => ({ ...p, recipientName: e.target.value }))} placeholder={es ? 'Destinatario' : 'Recipient'} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                    <input value={addressDraft.phone} onChange={(e) => setAddressDraft((p) => ({ ...p, phone: e.target.value }))} placeholder={es ? 'Teléfono' : 'Phone'} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                    <input value={addressDraft.line1} onChange={(e) => setAddressDraft((p) => ({ ...p, line1: e.target.value }))} placeholder={es ? 'Dirección (línea 1)' : 'Address line 1'} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                    <input value={addressDraft.line2} onChange={(e) => setAddressDraft((p) => ({ ...p, line2: e.target.value }))} placeholder={es ? 'Dirección (línea 2, opcional)' : 'Address line 2 (optional)'} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                    <select
                      value={addressDraft.regionId}
                      onChange={(e) => setAddressDraft((p) => ({ ...p, regionId: e.target.value, cityId: '', comunaId: '', region: '', city: '', comuna: '' }))}
                      className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2 bg-white"
                    >
                      <option value="">{loadingLocations ? (es ? 'Cargando ubicaciones...' : 'Loading locations...') : (es ? 'Selecciona region' : 'Select region')}</option>
                      {locationRegions.map((region) => (
                        <option key={region.id} value={region.id}>{region.name}</option>
                      ))}
                    </select>
                    <select
                      value={addressDraft.cityId}
                      onChange={(e) => setAddressDraft((p) => ({ ...p, cityId: e.target.value, comunaId: '', city: '', comuna: '' }))}
                      disabled={!addressDraft.regionId}
                      className="border border-pe-black/12 px-3 py-2 font-sans text-sm bg-white disabled:bg-pe-cream/25"
                    >
                      <option value="">{es ? 'Selecciona ciudad' : 'Select city'}</option>
                      {cityOptions.map((city) => (
                        <option key={city.id} value={city.id}>{city.name}</option>
                      ))}
                    </select>
                    <select
                      value={addressDraft.comunaId}
                      onChange={(e) => setAddressDraft((p) => ({ ...p, comunaId: e.target.value }))}
                      disabled={!addressDraft.cityId}
                      className="border border-pe-black/12 px-3 py-2 font-sans text-sm bg-white disabled:bg-pe-cream/25"
                    >
                      <option value="">{es ? 'Selecciona comuna' : 'Select comuna'}</option>
                      {comunaOptions.map((comuna) => (
                        <option key={comuna.id} value={comuna.id}>{comuna.name}</option>
                      ))}
                    </select>
                    <input value={addressDraft.reference} onChange={(e) => setAddressDraft((p) => ({ ...p, reference: e.target.value }))} placeholder={es ? 'Referencia (opcional)' : 'Reference (optional)'} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                  </div>
                  <label className="inline-flex items-center gap-2 font-sans text-sm text-pe-charcoal/75">
                    <input
                      type="checkbox"
                      checked={addressDraft.isDefault}
                      onChange={(e) => setAddressDraft((p) => ({ ...p, isDefault: e.target.checked }))}
                      className="accent-pe-rose"
                    />
                    {es ? 'Dejar como principal' : 'Set as default'}
                  </label>
                  <div className="flex items-center gap-2 pt-2">
                    <button
                      type="button"
                      onClick={() => {
                        void handleSaveAddress();
                      }}
                      disabled={addressSaving}
                      className="px-4 py-2 bg-pe-rose text-white font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors disabled:opacity-60"
                    >
                      {addressSaving ? (es ? 'Guardando...' : 'Saving...') : (es ? 'Guardar dirección' : 'Save address')}
                    </button>
                    <button
                      type="button"
                      onClick={() => setAddressModalOpen(false)}
                      className="px-4 py-2 border border-pe-black/15 text-pe-charcoal/70 font-sans text-[0.68rem] tracking-wider uppercase hover:border-pe-black/25 transition-colors"
                    >
                      {es ? 'Cancelar' : 'Cancel'}
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {tab === 'notifications' && <NotificationHistory locale={locale} />}

        {tab === 'orders' && (
          <div className="max-w-3xl">
            {gatewayReturnFeedback && (
              <div
                className={[
                  'mb-4 border px-3 py-2 font-sans text-[0.74rem]',
                  gatewayReturnFeedback.type === 'success'
                    ? 'border-green-200 bg-green-50 text-green-700'
                    : 'border-red-200 bg-red-50 text-red-700',
                ].join(' ')}
              >
                {gatewayReturnFeedback.text}
              </div>
            )}
            {loadingOrders ? (
              <div className="flex justify-center py-16">
                <Loader2 size={24} className="animate-spin text-pe-rose/60" />
              </div>
            ) : orders.length === 0 ? (
              <div className="text-center py-20">
                <ShoppingBag size={32} className="text-pe-charcoal/20 mx-auto mb-3" />
                <p className="font-display text-pe-black/30 text-xl">{es ? 'Aun no tienes pedidos' : 'No orders yet'}</p>
                <a
                  href={`/${locale}/products`}
                  className="inline-block mt-4 font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-rose-deep hover:underline underline-offset-2"
                >
                  {es ? 'Explorar productos' : 'Browse products'}
                </a>
              </div>
            ) : (
              <ul className="flex flex-col gap-4">
                {orders.map((order) => {
                  const payment = paymentsByOrder[order.id];
                  const timeline = getOrderTimeline(order.status);
                  const canUploadProof = canSubmitProof(order, payment);
                  const canSimulate = canSimulateGateway(order, payment);
                  const isSubmittingProof = proofSubmittingByOrder[order.id] === true;
                  const proofFeedback = proofFeedbackByOrder[order.id];
                  const isStartingGatewayCheckout = gatewayCheckoutLoadingByOrder[order.id] === true;
                  const isSimulatingGateway = gatewaySimulatingByOrder[order.id] === true;
                  const isConfirmingDelivery = deliveryConfirmingByOrder[order.id] === true;
                  const gatewayFeedback = gatewayFeedbackByOrder[order.id];
                  const selectedFile = proofFilesByOrder[order.id];
                  const shippingZone = order.shippingZoneCode?.trim();
                  const shippingCourier = order.shippingCourierName?.trim() || order.shippingCourierId?.trim();
                  const shippingMode = shippingPaymentModeLabel(order.shippingPaymentMode);
                  const shippingReference = order.shippingAddressReference?.trim();

                  return (
                    <li key={order.id} className="bg-pe-white border border-pe-black/6 p-5 flex flex-col gap-3">
                      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
                        <div>
                          <p className="font-sans text-[0.65rem] tracking-[0.16em] uppercase text-pe-charcoal/70">
                            {es ? 'Pedido' : 'Order'}
                          </p>
                          <p className="font-mono text-[0.82rem] text-pe-charcoal/65 mt-0.5">{order.id}</p>
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-cream text-pe-charcoal/60">
                            {paymentMethodLabel(order.paymentMethod)}
                          </span>
                          <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-rose/10 text-pe-rose-deep">
                            {orderStatusLabel(order.status)}
                          </span>
                        </div>
                      </div>

                      {(shippingZone || shippingCourier || shippingMode || shippingReference) && (
                        <div className="border border-pe-black/8 bg-pe-cream/25 px-3 py-2">
                          <p className="font-sans text-[0.62rem] tracking-[0.14em] uppercase text-pe-charcoal/60 mb-1">
                            {es ? 'Envio' : 'Shipping'}
                          </p>
                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1 font-sans text-[0.72rem] text-pe-charcoal/70">
                            {shippingZone && (
                              <p><span className="text-pe-charcoal/55">{es ? 'Zona:' : 'Zone:'}</span> {shippingZone}</p>
                            )}
                            {shippingCourier && (
                              <p><span className="text-pe-charcoal/55">{es ? 'Courier:' : 'Courier:'}</span> {shippingCourier}</p>
                            )}
                            {shippingMode && (
                              <p><span className="text-pe-charcoal/55">{es ? 'Modalidad:' : 'Mode:'}</span> {shippingMode}</p>
                            )}
                            {shippingReference && (
                              <p><span className="text-pe-charcoal/55">{es ? 'Referencia:' : 'Reference:'}</span> {shippingReference}</p>
                            )}
                          </div>
                        </div>
                      )}

                      <div className="border border-pe-black/8 bg-pe-cream/35 px-3 py-3 overflow-x-auto">
                        <div className="flex items-center gap-2 min-w-[680px]">
                          {timeline.steps.map((node, index) => (
                            <div key={node.step} className="flex items-center gap-2">
                              <span
                                className={[
                                  'inline-flex h-2.5 w-2.5 rounded-full border',
                                  node.state === 'done'
                                    ? 'bg-emerald-600 border-emerald-600'
                                    : node.state === 'current'
                                      ? 'bg-pe-rose border-pe-rose'
                                      : 'bg-transparent border-pe-black/20',
                                ].join(' ')}
                              />
                              <span
                                className={[
                                  'font-sans text-[0.62rem] tracking-[0.08em] uppercase whitespace-nowrap',
                                  node.state === 'done'
                                    ? 'text-emerald-700'
                                    : node.state === 'current'
                                      ? 'text-pe-rose-deep'
                                      : 'text-pe-charcoal/60',
                                ].join(' ')}
                              >
                                {orderTimelineLabel(node.step)}
                              </span>
                              {index < timeline.steps.length - 1 && (
                                <span
                                  className={[
                                    'block h-px w-5',
                                    node.state === 'done' || node.state === 'current'
                                      ? 'bg-pe-rose/45'
                                      : 'bg-pe-black/12',
                                  ].join(' ')}
                                  aria-hidden="true"
                                />
                              )}
                            </div>
                          ))}
                        </div>
                        {timeline.cancelled && (
                          <p className="font-sans text-[0.68rem] text-red-600 mt-2">
                            {es ? 'Pedido cancelado por administracion o cliente.' : 'Order cancelled by admin or customer.'}
                          </p>
                        )}
                      </div>

                      <ul className="flex flex-col gap-1.5 border-t border-pe-black/7 pt-3">
                        {order.items.map((item) => (
                          <li key={item.id} className="flex items-center justify-between gap-3">
                            <span className="font-sans text-sm text-pe-charcoal/75">
                              {item.productName} x{item.quantity}
                            </span>
                            <span className="font-sans text-sm text-pe-charcoal/70">
                              {formatMoney(item.unitPrice.amount, item.unitPrice.currency)}
                            </span>
                          </li>
                        ))}
                      </ul>

                      {order.paymentMethod === 'TRANSFER' && (
                        <div className="border-t border-pe-black/7 pt-3 flex flex-col gap-3">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <p className="font-sans text-[0.66rem] tracking-[0.16em] uppercase text-pe-charcoal/60">
                              {es ? 'Comprobante de transferencia' : 'Transfer proof'}
                            </p>
                            {payment ? (
                              <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-cream text-pe-charcoal/65">
                                {paymentStatusLabel(payment.status)}
                              </span>
                            ) : (
                              <span className="font-sans text-[0.62rem] tracking-wider uppercase text-pe-charcoal/70">
                                {loadingPayments ? (es ? 'Cargando...' : 'Loading...') : (es ? 'Sin pago asociado' : 'No linked payment')}
                              </span>
                            )}
                          </div>

                          {(payment?.transferAccountHolderName
                            || payment?.transferAccountEmail
                            || payment?.transferAccountNumber
                            || payment?.transferBankName
                            || payment?.transferAccountType) && (
                              <div className="border border-pe-black/8 bg-pe-cream/35 px-3 py-2">
                                <p className="font-sans text-[0.62rem] tracking-[0.14em] uppercase text-pe-charcoal/60 mb-1.5">
                                  {es ? 'Datos transferencia (snapshot)' : 'Transfer details (snapshot)'}
                                </p>
                                <dl className="grid grid-cols-1 gap-1 font-sans text-[0.72rem] text-pe-charcoal/70">
                                  <div className="flex items-center justify-between gap-3">
                                    <dt className="text-pe-charcoal/60">{es ? 'Nombre' : 'Name'}</dt>
                                    <dd className="text-right">{payment?.transferAccountHolderName || '-'}</dd>
                                  </div>
                                  <div className="flex items-center justify-between gap-3">
                                    <dt className="text-pe-charcoal/60">{es ? 'Correo' : 'Email'}</dt>
                                    <dd className="text-right">{payment?.transferAccountEmail || '-'}</dd>
                                  </div>
                                  <div className="flex items-center justify-between gap-3">
                                    <dt className="text-pe-charcoal/60">{es ? 'Cuenta' : 'Account'}</dt>
                                    <dd className="text-right">{maskAccountNumber(payment?.transferAccountNumber)}</dd>
                                  </div>
                                  <div className="flex items-center justify-between gap-3">
                                    <dt className="text-pe-charcoal/60">{es ? 'Banco' : 'Bank'}</dt>
                                    <dd className="text-right">{payment?.transferBankName || '-'}</dd>
                                  </div>
                                  <div className="flex items-center justify-between gap-3">
                                    <dt className="text-pe-charcoal/60">{es ? 'Tipo' : 'Type'}</dt>
                                    <dd className="text-right">{payment?.transferAccountType || '-'}</dd>
                                  </div>
                                </dl>
                              </div>
                            )}

                          {payment?.proofReference && (
                            <a
                              href={payment.proofReference}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="font-sans text-[0.72rem] text-pe-rose-deep hover:underline underline-offset-2"
                            >
                              {es ? 'Ver comprobante enviado' : 'View submitted proof'}
                            </a>
                          )}

                          {canUploadProof && (
                            <div className="flex flex-col gap-2">
                              <div className="flex flex-col lg:flex-row lg:items-center gap-2">
                                <label className="inline-flex items-center justify-center px-3 py-2 border border-pe-black/12 text-pe-charcoal/70 hover:text-pe-charcoal hover:border-pe-black/20 transition-colors cursor-pointer font-sans text-[0.68rem] tracking-wider uppercase">
                                  {selectedFile ? (es ? 'Cambiar imagen' : 'Change image') : (es ? 'Seleccionar imagen' : 'Select image')}
                                  <input
                                    type="file"
                                    accept="image/*"
                                    className="hidden"
                                    onChange={(event) => {
                                      const file = event.target.files?.[0] ?? null;
                                      setProofFilesByOrder((prev) => ({ ...prev, [order.id]: file }));
                                    }}
                                  />
                                </label>

                                <button
                                  onClick={() => {
                                    void handleSubmitProof(order.id);
                                  }}
                                  disabled={isSubmittingProof}
                                  className="inline-flex items-center justify-center px-4 py-2 bg-pe-rose text-white font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors disabled:opacity-60"
                                >
                                  {isSubmittingProof ? (es ? 'Enviando...' : 'Submitting...') : (es ? 'Enviar comprobante' : 'Submit proof')}
                                </button>
                              </div>

                              {selectedFile && (
                                <p className="font-sans text-[0.7rem] text-pe-charcoal/60">
                                  {es ? 'Archivo:' : 'File:'} {selectedFile.name}
                                </p>
                              )}
                            </div>
                          )}

                          {payment?.status === 'UNDER_REVIEW' && (
                            <p className="font-sans text-[0.72rem] text-pe-charcoal/60">
                              {es ? 'Tu comprobante esta en revision del equipo.' : 'Your proof is being reviewed by our team.'}
                            </p>
                          )}

                          {proofFeedback && (
                            <p className={`font-sans text-[0.72rem] ${proofFeedback.type === 'success' ? 'text-green-700' : 'text-red-500'}`}>
                              {proofFeedback.text}
                            </p>
                          )}
                        </div>
                      )}

                      {(order.paymentMethod === 'WEBPAY' || order.paymentMethod === 'MERCADOPAGO') && (
                        <div className="border-t border-pe-black/7 pt-3 flex flex-col gap-3">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <p className="font-sans text-[0.66rem] tracking-[0.16em] uppercase text-pe-charcoal/60">
                              {es ? 'Estado pasarela' : 'Gateway status'}
                            </p>
                            {payment ? (
                              <span className="font-sans text-[0.62rem] tracking-wider uppercase px-2 py-0.5 bg-pe-cream text-pe-charcoal/65">
                                {paymentStatusLabel(payment.status)}
                              </span>
                            ) : (
                              <span className="font-sans text-[0.62rem] tracking-wider uppercase text-pe-charcoal/70">
                                {loadingPayments ? (es ? 'Cargando...' : 'Loading...') : (es ? 'Sin pago asociado' : 'No linked payment')}
                              </span>
                            )}
                          </div>

                          {canSimulate && (
                            <div className="flex flex-wrap gap-2">
                              <button
                                onClick={() => {
                                  void handleStartGatewayCheckout(order.id);
                                }}
                                disabled={isSimulatingGateway || isStartingGatewayCheckout}
                                className="inline-flex items-center justify-center px-3 py-2 bg-pe-rose text-white font-sans text-[0.66rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors disabled:opacity-60"
                              >
                                {isStartingGatewayCheckout
                                  ? (es ? 'Abriendo...' : 'Opening...')
                                  : (es ? 'Ir a pagar' : 'Pay now')}
                              </button>
                              <button
                                onClick={() => {
                                  void handleSimulateGateway(order.id, 'APPROVED');
                                }}
                                disabled={isSimulatingGateway || isStartingGatewayCheckout}
                                className="inline-flex items-center justify-center px-3 py-2 bg-green-600 text-white font-sans text-[0.66rem] tracking-wider uppercase hover:bg-green-700 transition-colors disabled:opacity-60"
                              >
                                {isSimulatingGateway ? (es ? 'Simulando...' : 'Simulating...') : (es ? 'Simular aprobado' : 'Simulate approve')}
                              </button>
                              <button
                                onClick={() => {
                                  void handleSimulateGateway(order.id, 'FAILED');
                                }}
                                disabled={isSimulatingGateway || isStartingGatewayCheckout}
                                className="inline-flex items-center justify-center px-3 py-2 border border-red-300 text-red-600 font-sans text-[0.66rem] tracking-wider uppercase hover:bg-red-50 transition-colors disabled:opacity-60"
                              >
                                {isSimulatingGateway ? (es ? 'Simulando...' : 'Simulating...') : (es ? 'Simular rechazado' : 'Simulate reject')}
                              </button>
                            </div>
                          )}

                          {gatewayFeedback && (
                            <p className={`font-sans text-[0.72rem] ${gatewayFeedback.type === 'success' ? 'text-green-700' : 'text-red-500'}`}>
                              {gatewayFeedback.text}
                            </p>
                          )}
                        </div>
                      )}

                      <div className="border-t border-pe-black/7 pt-3 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                        {order.status === 'SHIPPED' && (
                          <button
                            onClick={() => {
                              void handleConfirmDelivery(order.id);
                            }}
                            disabled={isConfirmingDelivery}
                            className="inline-flex items-center justify-center px-3 py-2 bg-emerald-700 text-white font-sans text-[0.66rem] tracking-wider uppercase hover:bg-emerald-800 transition-colors disabled:opacity-60"
                          >
                            {isConfirmingDelivery
                              ? (es ? 'Confirmando...' : 'Confirming...')
                              : (es ? 'Marcar como recibido' : 'Mark as received')}
                          </button>
                        )}
                        <span className="font-sans text-[0.72rem] text-pe-charcoal/60">
                          {new Date(order.createdAt).toLocaleDateString(es ? 'es-CL' : 'en-US', {
                            day: '2-digit',
                            month: '2-digit',
                            year: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </span>
                        <p className="font-display text-[1.05rem] text-pe-black">
                          {es ? 'Total: ' : 'Total: '}
                          {formatMoney(order.totalAmount.amount, order.totalAmount.currency)}
                        </p>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

