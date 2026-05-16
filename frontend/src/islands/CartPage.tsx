import { useEffect, useMemo, useState } from 'react';
import { Loader2, X } from 'lucide-react';
import { useCartStore } from '../lib/cartStore';
import { useAuthStore, readAuthTokenCookie } from '../lib/authStore';
import {
  createOrder,
  createMyAddress,
  getLocationTree,
  getMyAddresses,
  getProduct,
  getPublicShippingConfig,
  getPublicStoreSettings,
  setMyAddressAsDefault,
  updateMyAddress,
  validateDiscountCodeForUser,
  type CustomerAddressDto,
  type CourierConfig,
  type CreateCustomerAddressRequest,
  type DiscountCodeDto,
  type LocationCityDto,
  type LocationCommuneDto,
  type LocationRegionDto,
  type PaymentGatewayProvider,
  type ShippingPaymentMode,
  type ShippingZoneCode,
  type ShippingZoneConfig,
} from '../lib/api';
import type { Locale } from '../i18n/index';

interface Props {
  locale: Locale;
}

interface StockConflict {
  type: 'OUT_OF_STOCK' | 'INSUFFICIENT_STOCK';
  availableQty: number;
}

interface AddressDraft {
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
}

const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function resolveOrderProductId(item: { id: string; productId?: string }): string | null {
  const productId = item.productId?.trim() || item.id.split('::')[0]?.trim() || '';
  return UUID_REGEX.test(productId) ? productId : null;
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
    isDefault: address.isDefault,
  };
}

const labels = {
  es: {
    title: 'Carrito',
    empty: 'Tu carrito está vacío',
    emptyLink: 'Explorar productos',
    subtotal: 'Subtotal',
    workerDiscount: 'Descuento trabajador (10%)',
    total: 'Total',
    checkout: 'Finalizar Compra',
    checkoutLoading: 'Procesando...',
    paymentMethod: 'Metodo de pago',
    paymentMethodTransfer: 'Transferencia',
    paymentMethodGateway: 'Pasarela de pago',
    paymentMethodUnavailable: 'El metodo de pago seleccionado ya no esta disponible.',
    paymentProviderLabel: 'Proveedor',
    shippingSectionTitle: 'Metodo de envio',
    shippingZoneLabel: 'Zona de envio',
    shippingCourierLabel: 'Courier',
    shippingAddressTitle: 'Direccion de entrega',
    shippingAddressChoose: 'Elegir o cambiar direccion',
    shippingAddressMissing: 'Debes seleccionar una direccion para finalizar la compra.',
    shippingAddressCreate: 'Agregar direccion',
    shippingAddressNone: 'No tienes direcciones guardadas.',
    shippingAddressLoginRequired: 'Debes iniciar sesion para seleccionar direccion de entrega.',
    shippingAddressLoginAction: 'Iniciar sesion',
    shippingAddressSelectModalTitle: 'Selecciona direccion de entrega',
    shippingAddressEdit: 'Editar',
    shippingAddressSetDefault: 'Marcar principal',
    shippingAddressDefaultBadge: 'Principal',
    shippingAddressUse: 'Usar direccion',
    shippingAddressFormTitleNew: 'Nueva direccion',
    shippingAddressFormTitleEdit: 'Editar direccion',
    shippingAddressSave: 'Guardar direccion',
    shippingAddressCancel: 'Cancelar',
    shippingAddressLabel: 'Alias',
    shippingAddressRecipient: 'Destinatario',
    shippingAddressPhone: 'Telefono',
    shippingAddressLine1: 'Direccion',
    shippingAddressLine2: 'Departamento (opcional)',
    shippingAddressComuna: 'Comuna',
    shippingAddressCity: 'Ciudad',
    shippingAddressRegion: 'Region',
    shippingAddressRegionSelect: 'Selecciona region',
    shippingAddressCitySelect: 'Selecciona ciudad',
    shippingAddressComunaSelect: 'Selecciona comuna',
    shippingAddressLocationsLoading: 'Cargando ubicaciones...',
    shippingAddressReference: 'Referencia (opcional)',
    shippingAddressSetDefaultToggle: 'Dejar como principal',
    shippingAddressCloseModal: 'Cerrar modal',
    shippingPaymentModeLabel: 'Modalidad',
    shippingPaymentModePorPagar: 'Envio por pagar',
    shippingSelectionRequired: 'Debes seleccionar zona y courier antes de finalizar la compra.',
    shippingUnavailable: 'No hay zonas o couriers activos para despacho. Contacta soporte.',
    legacyCartItemError: 'Tu carrito contiene productos antiguos no sincronizados. Eliminalos y vuelve a agregarlos desde catalogo.',
    transferDetailsTitle: 'Datos para transferencia',
    transferHolder: 'Nombre',
    transferEmail: 'Correo',
    transferAccount: 'Numero de cuenta',
    transferBank: 'Banco',
    transferType: 'Tipo de cuenta',
    remove: 'Eliminar',
    quantity: 'Cantidad',
    checkoutError: 'No pudimos crear tu pedido. Inténtalo nuevamente.',
    checkoutStockAdjusted: 'Uno o más productos fueron comprados por otro cliente mientras navegabas.',
    checkoutStockReviewHint: 'Revisa los productos marcados, elimina los que están sin stock o ajusta la cantidad disponible.',
    checkoutBlockedByStock: 'No puedes finalizar la compra mientras existan conflictos de stock en el carrito.',
    outOfStockBadge: 'Sin stock',
    limitedStockBadge: 'Stock insuficiente',
    availableStockPrefix: 'Disponible',
    removeUnavailable: 'Eliminar',
    findReplacement: 'Buscar otro',
    checkoutSuccess: 'Pedido creado. Redirigiendo a tu cuenta...',
    continueShopping: 'Seguir comprando',
  },
  en: {
    title: 'Cart',
    empty: 'Your cart is empty',
    emptyLink: 'Explore products',
    subtotal: 'Subtotal',
    workerDiscount: 'Employee discount (10%)',
    total: 'Total',
    checkout: 'Checkout',
    checkoutLoading: 'Processing...',
    paymentMethod: 'Payment method',
    paymentMethodTransfer: 'Bank transfer',
    paymentMethodGateway: 'Payment gateway',
    paymentMethodUnavailable: 'The selected payment method is no longer available.',
    paymentProviderLabel: 'Provider',
    shippingSectionTitle: 'Shipping method',
    shippingZoneLabel: 'Shipping zone',
    shippingCourierLabel: 'Courier',
    shippingAddressTitle: 'Delivery address',
    shippingAddressChoose: 'Choose or change address',
    shippingAddressMissing: 'You must select a delivery address before checkout.',
    shippingAddressCreate: 'Add address',
    shippingAddressNone: 'You do not have saved addresses.',
    shippingAddressLoginRequired: 'You must sign in to select a delivery address.',
    shippingAddressLoginAction: 'Sign in',
    shippingAddressSelectModalTitle: 'Select delivery address',
    shippingAddressEdit: 'Edit',
    shippingAddressSetDefault: 'Set default',
    shippingAddressDefaultBadge: 'Default',
    shippingAddressUse: 'Use address',
    shippingAddressFormTitleNew: 'New address',
    shippingAddressFormTitleEdit: 'Edit address',
    shippingAddressSave: 'Save address',
    shippingAddressCancel: 'Cancel',
    shippingAddressLabel: 'Label',
    shippingAddressRecipient: 'Recipient',
    shippingAddressPhone: 'Phone',
    shippingAddressLine1: 'Address line 1',
    shippingAddressLine2: 'Address line 2 (optional)',
    shippingAddressComuna: 'Comuna',
    shippingAddressCity: 'City',
    shippingAddressRegion: 'Region',
    shippingAddressRegionSelect: 'Select region',
    shippingAddressCitySelect: 'Select city',
    shippingAddressComunaSelect: 'Select comuna',
    shippingAddressLocationsLoading: 'Loading locations...',
    shippingAddressReference: 'Reference (optional)',
    shippingAddressSetDefaultToggle: 'Set as default',
    shippingAddressCloseModal: 'Close modal',
    shippingPaymentModeLabel: 'Mode',
    shippingPaymentModePorPagar: 'Shipping paid on pickup',
    shippingSelectionRequired: 'You must select a shipping zone and courier before checkout.',
    shippingUnavailable: 'There are no active shipping zones or couriers. Contact support.',
    legacyCartItemError: 'Your cart contains legacy items that are no longer synced. Remove them and add them again from the catalog.',
    transferDetailsTitle: 'Bank transfer details',
    transferHolder: 'Name',
    transferEmail: 'Email',
    transferAccount: 'Account number',
    transferBank: 'Bank',
    transferType: 'Account type',
    remove: 'Remove',
    quantity: 'Quantity',
    checkoutError: 'We could not create your order. Please try again.',
    checkoutStockAdjusted: 'One or more products were purchased by another customer while you were browsing.',
    checkoutStockReviewHint: 'Review highlighted products, remove out-of-stock items, or adjust quantity before checkout.',
    checkoutBlockedByStock: 'You cannot complete checkout while stock conflicts remain in your cart.',
    outOfStockBadge: 'Out of stock',
    limitedStockBadge: 'Limited stock',
    availableStockPrefix: 'Available',
    removeUnavailable: 'Remove',
    findReplacement: 'Find another',
    checkoutSuccess: 'Order created. Redirecting to your account...',
    continueShopping: 'Continue shopping',
  },
} as const;

export default function CartPage({ locale }: Props) {
  const l = labels[locale];
  const items = useCartStore((s) => s.items);
  const removeItem = useCartStore((s) => s.removeItem);
  const updateQuantity = useCartStore((s) => s.updateQuantity);
  const clearCart = useCartStore((s) => s.clearCart);

  const authUser = useAuthStore((s) => s.user);
  const authToken = useAuthStore((s) => s.token);
  const effectiveToken = authToken ?? readAuthTokenCookie();
  const hasSession = Boolean(effectiveToken);

  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [checkingOut, setCheckingOut] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<'TRANSFER' | 'WEBPAY'>('TRANSFER');
  const [paymentMethodBankTransferEnabled, setPaymentMethodBankTransferEnabled] = useState(true);
  const [paymentMethodGatewayEnabled, setPaymentMethodGatewayEnabled] = useState(true);
  const [paymentGatewayProviders, setPaymentGatewayProviders] = useState<PaymentGatewayProvider[]>(['MERCADO_PAGO']);
  const [transferAccountHolder, setTransferAccountHolder] = useState('');
  const [transferContactEmail, setTransferContactEmail] = useState('');
  const [transferAccountNumber, setTransferAccountNumber] = useState('');
  const [transferBankName, setTransferBankName] = useState('');
  const [transferAccountType, setTransferAccountType] = useState('');
  const [shippingZones, setShippingZones] = useState<ShippingZoneConfig[]>([]);
  const [shippingCouriers, setShippingCouriers] = useState<CourierConfig[]>([]);
  const [shippingPaymentMode, setShippingPaymentMode] = useState<ShippingPaymentMode>('POR_PAGAR');
  const [shippingZoneCode, setShippingZoneCode] = useState<ShippingZoneCode>('LOCAL');
  const [shippingCourierId, setShippingCourierId] = useState('');
  const [addresses, setAddresses] = useState<CustomerAddressDto[]>([]);
  const [loadingAddresses, setLoadingAddresses] = useState(false);
  const [selectedShippingAddressId, setSelectedShippingAddressId] = useState<string>('');
  const [showAddressSelectModal, setShowAddressSelectModal] = useState(false);
  const [showAddressFormModal, setShowAddressFormModal] = useState(false);
  const [addressDraft, setAddressDraft] = useState<AddressDraft>(emptyAddressDraft());
  const [locationRegions, setLocationRegions] = useState<LocationRegionDto[]>([]);
  const [loadingLocations, setLoadingLocations] = useState(false);
  const [editingAddressId, setEditingAddressId] = useState<string | null>(null);
  const [savingAddress, setSavingAddress] = useState(false);
  const [settingDefaultAddressId, setSettingDefaultAddressId] = useState<string | null>(null);
  const [discountCode, setDiscountCode] = useState('');
  const [appliedDiscount, setAppliedDiscount] = useState<DiscountCodeDto | null>(null);
  const [discountApplying, setDiscountApplying] = useState(false);
  const [discountError, setDiscountError] = useState('');
  const [stockConflicts, setStockConflicts] = useState<Record<string, StockConflict>>({});

  const subtotal = items.reduce((sum, i) => sum + i.price.amount * i.quantity, 0);
  const isEmployee = authUser?.role === 'SELLER';
  const employeeDiscountAmount = isEmployee ? Math.round(subtotal * 0.1) : 0;
  const appliedDiscountAmount = appliedDiscount
    ? appliedDiscount.type === 'PERCENTAGE'
      ? Math.round(subtotal * appliedDiscount.value / 100)
      : Math.min(appliedDiscount.value, subtotal)
    : 0;
  const total = Math.max(0, subtotal - employeeDiscountAmount - appliedDiscountAmount);
  const stockConflictCount = Object.keys(stockConflicts).length;
  const activeShippingZones = useMemo(
    () => shippingZones.filter((zone) => zone.active),
    [shippingZones]
  );
  const activeShippingCouriers = useMemo(
    () => shippingCouriers.filter((courier) => courier.active),
    [shippingCouriers]
  );
  const shippingUnavailable = activeShippingZones.length === 0 || activeShippingCouriers.length === 0;

  const priceFormat = (amount: number, currency: string) =>
    new Intl.NumberFormat(locale === 'es' ? 'es-CL' : 'en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(amount);

  function shippingPaymentModeLabel(mode: ShippingPaymentMode): string {
    if (mode === 'POR_PAGAR') {
      return l.shippingPaymentModePorPagar;
    }
    return mode;
  }

  const selectedAddress = useMemo(
    () => addresses.find((address) => address.id === selectedShippingAddressId) ?? null,
    [addresses, selectedShippingAddressId]
  );

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

  function redirectToCartLogin() {
    window.location.href = `/${locale}/auth/login?redirect=${encodeURIComponent(`/${locale}/cart`)}`;
  }

  async function loadAddresses() {
    if (!effectiveToken) {
      setAddresses([]);
      setSelectedShippingAddressId('');
      return;
    }
    setLoadingAddresses(true);
    try {
      const rows = await getMyAddresses(effectiveToken);
      setAddresses(rows);
      const selectedStillExists = rows.some((row) => row.id === selectedShippingAddressId);
      if (selectedStillExists) return;
      const defaultAddress = rows.find((row) => row.isDefault) ?? rows[0];
      setSelectedShippingAddressId(defaultAddress?.id ?? '');
    } catch {
      setAddresses([]);
      setSelectedShippingAddressId('');
    } finally {
      setLoadingAddresses(false);
    }
  }

  async function loadLocations() {
    setLoadingLocations(true);
    try {
      const regions = await getLocationTree();
      setLocationRegions(regions);
    } catch {
      setLocationRegions([]);
    } finally {
      setLoadingLocations(false);
    }
  }

  function validateAddressDraft(draft: AddressDraft): string | null {
    if (!draft.label.trim()) return locale === 'es' ? 'Debes ingresar alias de dirección.' : 'Address label is required.';
    if (!draft.recipientName.trim()) return locale === 'es' ? 'Debes ingresar destinatario.' : 'Recipient is required.';
    const digits = draft.phone.replace(/\D/g, '');
    if (digits.length < 8 || digits.length > 15) {
      return locale === 'es' ? 'El teléfono debe tener entre 8 y 15 dígitos.' : 'Phone must contain between 8 and 15 digits.';
    }
    if (!draft.line1.trim()) return locale === 'es' ? 'Debes ingresar dirección.' : 'Address line is required.';
    if (!draft.regionId) return locale === 'es' ? 'Debes seleccionar region.' : 'Region selection is required.';
    if (!draft.cityId) return locale === 'es' ? 'Debes seleccionar ciudad.' : 'City selection is required.';
    if (!draft.comunaId) return locale === 'es' ? 'Debes seleccionar comuna.' : 'Comuna selection is required.';
    return null;
  }

  function openCreateAddressModal() {
    if (!hasSession) {
      redirectToCartLogin();
      return;
    }
    setEditingAddressId(null);
    setAddressDraft(emptyAddressDraft());
    setShowAddressSelectModal(false);
    setShowAddressFormModal(true);
  }

  function openEditAddressModal(address: CustomerAddressDto) {
    if (!hasSession) {
      redirectToCartLogin();
      return;
    }
    setEditingAddressId(address.id);
    setAddressDraft(draftFromAddress(address));
    setShowAddressSelectModal(false);
    setShowAddressFormModal(true);
  }

  async function handleSaveAddress() {
    if (!effectiveToken || savingAddress) return;
    const validationError = validateAddressDraft(addressDraft);
    if (validationError) {
      showToast('error', validationError);
      return;
    }
    setSavingAddress(true);
    const selectedRegion = locationRegions.find((region) => region.id === selectedRegionId);
    const selectedCity = cityOptions.find((city) => city.id === selectedCityId);
    const selectedComuna = comunaOptions.find((comuna) => comuna.id === selectedComunaId);
    if (!selectedRegion || !selectedCity || !selectedComuna) {
      showToast('error', locale === 'es' ? 'Selecciona region, ciudad y comuna validas.' : 'Choose valid region, city, and comuna.');
      setSavingAddress(false);
      return;
    }
    const payload: CreateCustomerAddressRequest = {
      label: addressDraft.label.trim(),
      recipientName: addressDraft.recipientName.trim(),
      phone: addressDraft.phone.trim(),
      line1: addressDraft.line1.trim(),
      line2: addressDraft.line2.trim() || undefined,
      regionId: selectedRegion.id,
      cityId: selectedCity.id,
      comunaId: selectedComuna.id,
      comuna: selectedComuna.name,
      city: selectedCity.name,
      region: selectedRegion.name,
      reference: addressDraft.reference.trim() || undefined,
      isDefault: addressDraft.isDefault,
    };
    try {
      let targetId = editingAddressId;
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
        targetId = editingAddressId;
      } else {
        const created = await createMyAddress(payload, effectiveToken);
        targetId = created.id;
      }
      if (payload.isDefault && targetId) {
        await setMyAddressAsDefault(targetId, effectiveToken);
      }
      await loadAddresses();
      if (targetId) {
        setSelectedShippingAddressId(targetId);
      }
      setShowAddressFormModal(false);
    } catch (error) {
      showToast('error', error instanceof Error ? error.message : l.checkoutError);
    } finally {
      setSavingAddress(false);
    }
  }

  async function handleSetDefaultAddress(addressId: string) {
    if (!effectiveToken || settingDefaultAddressId) return;
    setSettingDefaultAddressId(addressId);
    try {
      await setMyAddressAsDefault(addressId, effectiveToken);
      await loadAddresses();
      setSelectedShippingAddressId(addressId);
    } catch (error) {
      showToast('error', error instanceof Error ? error.message : l.checkoutError);
    } finally {
      setSettingDefaultAddressId(null);
    }
  }

  function showToast(type: 'success' | 'error', message: string) {
    setToast({ type, message });
    window.setTimeout(() => setToast(null), type === 'error' ? 6000 : 3200);
  }

  function clearItemConflict(itemId: string) {
    setStockConflicts((current) => {
      if (!current[itemId]) return current;
      const next = { ...current };
      delete next[itemId];
      return next;
    });
  }

  async function findStockConflicts(): Promise<Record<string, StockConflict>> {
    const checks = await Promise.all(items.map(async (item) => {
      const productId = resolveOrderProductId(item);
      if (!productId) {
        return {
          itemId: item.id,
          conflict: { type: 'OUT_OF_STOCK' as const, availableQty: 0 },
        };
      }
      try {
        const latest = await getProduct(productId);
        if (!latest.active || latest.stock <= 0) {
          return {
            itemId: item.id,
            conflict: { type: 'OUT_OF_STOCK' as const, availableQty: 0 },
          };
        }
        if (latest.stock < item.quantity) {
          return {
            itemId: item.id,
            conflict: { type: 'INSUFFICIENT_STOCK' as const, availableQty: latest.stock },
          };
        }
        return null;
      } catch {
        return {
          itemId: item.id,
          conflict: { type: 'OUT_OF_STOCK' as const, availableQty: 0 },
        };
      }
    }));

    return checks.reduce<Record<string, StockConflict>>((acc, entry) => {
      if (!entry) return acc;
      acc[entry.itemId] = entry.conflict;
      return acc;
    }, {});
  }

  useEffect(() => {
    let cancelled = false;
    async function loadPaymentConfig() {
      try {
        const [settings, shippingConfig] = await Promise.all([
          getPublicStoreSettings(),
          getPublicShippingConfig(),
        ]);
        if (cancelled) return;

        const providers = Array.isArray(settings?.paymentGatewayProviders)
          ? settings.paymentGatewayProviders
          : [];
        const gatewayEnabled = settings?.paymentMethodGatewayEnabled !== false && providers.length > 0;
        const bankTransferEnabled = settings?.paymentMethodBankTransferEnabled !== false || !gatewayEnabled;

        setPaymentGatewayProviders(providers.length ? providers : ['MERCADO_PAGO']);
        setPaymentMethodGatewayEnabled(gatewayEnabled);
        setPaymentMethodBankTransferEnabled(bankTransferEnabled);
        setTransferAccountHolder(settings?.bankTransferAccountHolder?.trim() ?? '');
        setTransferContactEmail(settings?.bankTransferContactEmail?.trim() ?? '');
        setTransferAccountNumber(settings?.bankTransferAccountNumber?.trim() ?? '');
        setTransferBankName(settings?.bankTransferBankName?.trim() ?? '');
        setTransferAccountType(settings?.bankTransferAccountType?.trim() ?? '');
        setShippingZones(shippingConfig.zones ?? []);
        setShippingCouriers(shippingConfig.couriers ?? []);
        setShippingPaymentMode(shippingConfig.paymentMode ?? 'POR_PAGAR');

        const firstActiveZone = (shippingConfig.zones ?? []).find((zone) => zone.active);
        if (firstActiveZone?.code) {
          setShippingZoneCode(firstActiveZone.code);
        }
        const firstActiveCourier = (shippingConfig.couriers ?? []).find((courier) => courier.active);
        if (firstActiveCourier?.id) {
          setShippingCourierId(firstActiveCourier.id);
        }

        setPaymentMethod((prev) => {
          if (prev === 'TRANSFER' && !bankTransferEnabled && gatewayEnabled) {
            return 'WEBPAY';
          }
          if (prev === 'WEBPAY' && !gatewayEnabled && bankTransferEnabled) {
            return 'TRANSFER';
          }
          return prev;
        });
      } catch {
        // Keep defaults if public settings are unavailable.
      }
    }

    void loadPaymentConfig();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    void loadAddresses();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [effectiveToken]);

  useEffect(() => {
    void loadLocations();
  }, []);

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

  useEffect(() => {
    setStockConflicts((current) => {
      const next = Object.entries(current).reduce<Record<string, StockConflict>>((acc, [itemId, conflict]) => {
        const item = items.find((candidate) => candidate.id === itemId);
        if (!item) return acc;
        if (conflict.type === 'INSUFFICIENT_STOCK' && item.quantity <= conflict.availableQty) {
          return acc;
        }
        acc[itemId] = conflict;
        return acc;
      }, {});

      if (Object.keys(next).length === Object.keys(current).length) {
        const sameEntries = Object.entries(next).every(([itemId, conflict]) => {
          const previous = current[itemId];
          return previous
            && previous.type === conflict.type
            && previous.availableQty === conflict.availableQty;
        });
        if (sameEntries) return current;
      }
      return next;
    });
  }, [items]);

  const selectedGatewayProviderLabel = useMemo(() => {
    if (!paymentGatewayProviders.length) return '';
    const first = paymentGatewayProviders[0];
    if (first === 'MERCADO_PAGO') {
      return 'Mercado Pago';
    }
    return first;
  }, [paymentGatewayProviders]);

  useEffect(() => {
    if (activeShippingZones.length === 0) return;
    const selectedZoneStillActive = activeShippingZones.some((zone) => zone.code === shippingZoneCode);
    if (!selectedZoneStillActive) {
      setShippingZoneCode(activeShippingZones[0].code);
    }
  }, [activeShippingZones, shippingZoneCode]);

  useEffect(() => {
    if (activeShippingCouriers.length === 0) return;
    const selectedCourierStillActive = activeShippingCouriers.some((courier) => courier.id === shippingCourierId);
    if (!selectedCourierStillActive) {
      setShippingCourierId(activeShippingCouriers[0].id);
    }
  }, [activeShippingCouriers, shippingCourierId]);

  useEffect(() => {
    const hasOpenModal = showAddressSelectModal || showAddressFormModal;
    if (!hasOpenModal) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [showAddressSelectModal, showAddressFormModal]);

  useEffect(() => {
    if (!showAddressSelectModal && !showAddressFormModal) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      setShowAddressFormModal(false);
      setShowAddressSelectModal(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [showAddressSelectModal, showAddressFormModal]);

  async function handleApplyDiscount() {
    if (!discountCode.trim()) return;
    if (!effectiveToken) {
      setDiscountError(locale === 'es' ? 'Debes iniciar sesión para usar un código.' : 'You must be logged in to apply a code.');
      return;
    }
    setDiscountApplying(true);
    setDiscountError('');
    try {
      const dto = await validateDiscountCodeForUser(discountCode.trim(), subtotal, effectiveToken);
      setAppliedDiscount(dto);
    } catch (e: unknown) {
      setDiscountError(e instanceof Error ? e.message : (locale === 'es' ? 'Código inválido o ya utilizado.' : 'Invalid or already used code.'));
      setAppliedDiscount(null);
    } finally {
      setDiscountApplying(false);
    }
  }

  async function handleCheckout() {
    if (!items.length || checkingOut) return;

    if (paymentMethod === 'TRANSFER' && !paymentMethodBankTransferEnabled) {
      showToast('error', l.paymentMethodUnavailable);
      return;
    }
    if (paymentMethod === 'WEBPAY' && !paymentMethodGatewayEnabled) {
      showToast('error', l.paymentMethodUnavailable);
      return;
    }
    if (activeShippingZones.length === 0 || activeShippingCouriers.length === 0) {
      showToast('error', l.shippingUnavailable);
      return;
    }
    if (!shippingZoneCode || !shippingCourierId) {
      showToast('error', l.shippingSelectionRequired);
      return;
    }
    if (!hasSession) {
      redirectToCartLogin();
      return;
    }
    if (!selectedShippingAddressId) {
      showToast('error', l.shippingAddressMissing);
      if (addresses.length === 0) {
        openCreateAddressModal();
      } else {
        setShowAddressSelectModal(true);
      }
      return;
    }

    if (items.some((item) => !resolveOrderProductId(item))) {
      showToast('error', l.legacyCartItemError);
      return;
    }

    if (!authUser || !effectiveToken) {
      redirectToCartLogin();
      return;
    }

    setCheckingOut(true);
    setToast(null);
    try {
      const conflicts = await findStockConflicts();
      if (Object.keys(conflicts).length > 0) {
        setStockConflicts(conflicts);
        showToast('error', l.checkoutStockAdjusted);
        return;
      }

      setStockConflicts({});

      const order = await createOrder(
        {
          customerId: authUser.id,
          items: items.map((item) => ({
            productId: resolveOrderProductId(item) as string,
            quantity: item.quantity,
            variantColor: item.variantColor,
            variantSize: item.variantSize,
          })),
          paymentMethod,
          shippingZoneCode,
          shippingCourierId,
          shippingAddressId: selectedShippingAddressId,
          discountCode: appliedDiscount?.code,
        },
        effectiveToken
      );

      clearCart();
      showToast('success', l.checkoutSuccess);
      window.setTimeout(() => {
        window.location.href = `/${locale}/account?tab=orders&order=${encodeURIComponent(order.id)}`;
      }, 500);
    } catch (error) {
      if (error instanceof Error && /insufficient stock/i.test(error.message)) {
        const conflicts = await findStockConflicts();
        if (Object.keys(conflicts).length > 0) {
          setStockConflicts(conflicts);
          showToast('error', l.checkoutBlockedByStock);
          return;
        }
      }
      showToast('error', error instanceof Error && error.message ? error.message : l.checkoutError);
    } finally {
      setCheckingOut(false);
    }
  }

  return (
    <div className="py-12 bg-pe-beige min-h-screen">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        <h1 className="font-display text-pe-black text-3xl md:text-4xl font-semibold mb-8">
          {l.title}
        </h1>

        {toast && (
          <div
            role="status"
            aria-live="polite"
            className={`fixed right-4 z-[99999] font-sans text-sm px-5 py-3 shadow-xl border max-w-[calc(100vw-2rem)] sm:max-w-sm ${
              toast.type === 'success'
                ? 'bg-pe-black text-pe-white border-pe-white/15'
                : 'bg-[#5f1e25] text-pe-white border-[#f1c3cb]/40'
            }`}
            style={{ top: 'calc(var(--pe-site-header-height, 64px) + 1rem)' }}
          >
            {toast.message}
          </div>
        )}

        {items.length === 0 ? (
          <div className="text-center py-24">
            <p className="font-display text-pe-charcoal/65 text-2xl mb-6">{l.empty}</p>
            <a
              href={`/${locale}/products`}
              className="font-sans text-sm tracking-widest uppercase text-pe-gold hover:underline underline-offset-4"
            >
              {l.emptyLink} →
            </a>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div className="lg:col-span-2 flex flex-col gap-4">
              {items.map((item) => {
                const conflict = stockConflicts[item.id];
                const hasStockConflict = Boolean(conflict);

                return (
                  <div
                    key={item.id}
                    className={`flex gap-4 p-4 border ${
                      hasStockConflict
                        ? 'bg-[#fff6f7] border-[#cb6070]/45'
                        : 'bg-pe-white border-transparent'
                    }`}
                  >
                    <img
                      src={item.imageUrl}
                      alt={item.name}
                      className="w-20 h-24 object-cover flex-shrink-0"
                      width="80"
                      height="96"
                    />
                    <div className="flex-1 min-w-0 flex flex-col gap-1">
                      <span className="font-sans text-[10px] tracking-[0.3em] uppercase text-pe-gold">
                        {item.brand}
                      </span>
                      <p className="font-display text-pe-black text-sm font-semibold truncate">
                        {item.name}
                      </p>
                      {item.variantLabel && (
                        <p className="font-sans text-[11px] tracking-wide text-pe-charcoal/55">
                          {item.variantLabel}
                        </p>
                      )}
                      <p className="font-sans text-pe-black text-sm">
                        {priceFormat(item.price.amount, item.price.currency)}
                      </p>
                      {conflict && (
                        <div
                          role="status"
                          aria-live="polite"
                          className="mt-2 border border-[#cb6070]/45 bg-[#ffe9ec] px-2.5 py-2"
                        >
                          <p className="font-sans text-[10px] tracking-[0.18em] uppercase text-[#8f2d3b]">
                            {conflict.type === 'OUT_OF_STOCK' ? l.outOfStockBadge : l.limitedStockBadge}
                          </p>
                          <p className="font-sans text-xs text-[#732731] mt-1">
                            {conflict.type === 'OUT_OF_STOCK'
                              ? l.checkoutStockAdjusted
                              : `${l.availableStockPrefix}: ${conflict.availableQty}`}
                          </p>
                          <div className="mt-2 flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() => {
                                clearItemConflict(item.id);
                                removeItem(item.id);
                              }}
                              className="font-sans text-[10px] tracking-[0.14em] uppercase px-2 py-1 border border-[#8f2d3b]/40 text-[#8f2d3b] hover:bg-[#8f2d3b] hover:text-white transition-colors"
                            >
                              {l.removeUnavailable}
                            </button>
                            <a
                              href={`/${locale}/products`}
                              className="font-sans text-[10px] tracking-[0.14em] uppercase px-2 py-1 border border-pe-black/20 text-pe-charcoal/70 hover:border-pe-gold hover:text-pe-gold transition-colors"
                            >
                              {l.findReplacement}
                            </a>
                          </div>
                        </div>
                      )}

                      <div className="mt-auto flex items-center justify-between gap-4">
                        <div className="flex items-center gap-2" aria-label={l.quantity}>
                          <button
                            onClick={() => {
                              clearItemConflict(item.id);
                              updateQuantity(item.id, item.quantity - 1);
                            }}
                            className="w-7 h-7 border border-pe-black/20 flex items-center justify-center font-sans text-sm hover:border-pe-gold hover:text-pe-gold transition-colors"
                            aria-label="Decrease quantity"
                          >
                            -
                          </button>
                          <span className="font-sans text-sm w-5 text-center">{item.quantity}</span>
                          <button
                            onClick={() => {
                              clearItemConflict(item.id);
                              updateQuantity(item.id, item.quantity + 1);
                            }}
                            className="w-7 h-7 border border-pe-black/20 flex items-center justify-center font-sans text-sm hover:border-pe-gold hover:text-pe-gold transition-colors"
                            aria-label="Increase quantity"
                          >
                            +
                          </button>
                        </div>

                        <span className="font-sans text-sm font-semibold">
                          {priceFormat(item.price.amount * item.quantity, item.price.currency)}
                        </span>

                        <button
                          onClick={() => {
                            clearItemConflict(item.id);
                            removeItem(item.id);
                          }}
                          className="font-sans text-[10px] tracking-widest uppercase text-pe-charcoal/65 hover:text-pe-gold transition-colors"
                          aria-label={`${l.remove} ${item.name}`}
                        >
                          {l.remove}
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="lg:col-span-1">
              <div
                className="bg-pe-white p-6 sticky"
                style={{ top: 'calc(var(--pe-site-header-height, 0px) + 1rem)' }}
              >
                <h2 className="font-display text-pe-black text-xl font-semibold mb-6">
                  {locale === 'es' ? 'Resumen' : 'Summary'}
                </h2>

                <div className="flex justify-between font-sans text-sm mb-4">
                  <span className="text-pe-charcoal/75">{l.subtotal}</span>
                  <span className="font-semibold">
                    {priceFormat(subtotal, items[0]?.price.currency ?? 'CLP')}
                  </span>
                </div>

                {stockConflictCount > 0 && (
                  <div className="mb-4 border border-[#cb6070]/45 bg-[#fff0f2] px-3 py-2">
                    <p className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-[#8f2d3b]">
                      {`${stockConflictCount} ${l.outOfStockBadge}`}
                    </p>
                    <p className="font-sans text-[0.72rem] text-[#732731] mt-1">
                      {l.checkoutStockReviewHint}
                    </p>
                  </div>
                )}

                {isEmployee && (
                  <div className="flex justify-between font-sans text-sm mb-4">
                    <span className="text-pe-charcoal/75">{l.workerDiscount}</span>
                    <span className="font-semibold text-green-700">
                      - {priceFormat(employeeDiscountAmount, items[0]?.price.currency ?? 'CLP')}
                    </span>
                  </div>
                )}

                {appliedDiscount && (
                  <div className="flex justify-between font-sans text-sm mb-4">
                    <span className="text-pe-charcoal/75 flex items-center gap-1.5">
                      <span>{locale === 'es' ? 'Descuento' : 'Discount'}</span>
                      <span className="font-mono text-[0.65rem] bg-pe-rose/8 text-pe-rose-deep px-1.5 py-0.5">{appliedDiscount.code}</span>
                    </span>
                    <span className="font-semibold text-green-700">
                      - {priceFormat(appliedDiscountAmount, items[0]?.price.currency ?? 'CLP')}
                    </span>
                  </div>
                )}

                {/* Discount code input */}
                <div className="mb-4">
                  <p className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal/75 mb-2">
                    {locale === 'es' ? 'Código de descuento' : 'Discount code'}
                  </p>
                  {appliedDiscount ? (
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-[0.78rem] text-green-700 border border-green-200 bg-green-50 px-2 py-1.5 flex-1 truncate">
                        {appliedDiscount.code}
                      </span>
                      <button
                        type="button"
                        onClick={() => { setAppliedDiscount(null); setDiscountCode(''); setDiscountError(''); }}
                        className="font-sans text-[0.68rem] uppercase tracking-wider px-2 py-1.5 border border-pe-black/12 text-pe-charcoal/50 hover:text-pe-rose hover:border-pe-rose/40 transition-colors"
                      >
                        {locale === 'es' ? 'Quitar' : 'Remove'}
                      </button>
                    </div>
                  ) : (
                    <div className="flex gap-1">
                      <input
                        type="text"
                        value={discountCode}
                        onChange={e => { setDiscountCode(e.target.value.toUpperCase()); setDiscountError(''); }}
                        onKeyDown={e => e.key === 'Enter' && handleApplyDiscount()}
                        placeholder={locale === 'es' ? 'PILAR_PE_ESTILO_...' : 'Enter code'}
                        className="flex-1 font-mono text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-none focus:border-pe-rose/50 transition-colors uppercase"
                      />
                      <button
                        type="button"
                        onClick={handleApplyDiscount}
                        disabled={discountApplying || !discountCode.trim()}
                        className="shrink-0 font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 bg-pe-charcoal/8 hover:bg-pe-charcoal/14 border border-pe-black/12 text-pe-charcoal/60 hover:text-pe-charcoal transition-colors disabled:opacity-40"
                      >
                        {discountApplying ? <Loader2 size={12} className="animate-spin" /> : (locale === 'es' ? 'Aplicar' : 'Apply')}
                      </button>
                    </div>
                  )}
                  {discountError && (
                    <p className="font-sans text-[0.68rem] text-pe-rose-deep mt-1">{discountError}</p>
                  )}
                </div>

                <div className="w-full h-px bg-pe-black/10 mb-6"></div>

                <div className="mb-6">
                  <p className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal/75 mb-2">
                    {l.shippingSectionTitle}
                  </p>
                  <div className="grid grid-cols-1 gap-2">
                    <label className="flex flex-col gap-1.5">
                      <span className="font-sans text-[0.64rem] uppercase tracking-[0.14em] text-pe-charcoal/60">
                        {l.shippingZoneLabel}
                      </span>
                      <select
                        value={shippingZoneCode}
                        onChange={(event) => setShippingZoneCode(event.target.value as ShippingZoneCode)}
                        className="w-full border border-pe-black/12 bg-pe-white px-2.5 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:outline-none focus:border-pe-rose/45"
                      >
                        {activeShippingZones.map((zone) => (
                          <option key={zone.code} value={zone.code}>
                            {locale === 'es' ? zone.titleEs : zone.titleEn}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="flex flex-col gap-1.5">
                      <span className="font-sans text-[0.64rem] uppercase tracking-[0.14em] text-pe-charcoal/60">
                        {l.shippingCourierLabel}
                      </span>
                      <select
                        value={shippingCourierId}
                        onChange={(event) => setShippingCourierId(event.target.value)}
                        className="w-full border border-pe-black/12 bg-pe-white px-2.5 py-2 font-sans text-[0.8rem] text-pe-charcoal focus:outline-none focus:border-pe-rose/45"
                      >
                        {activeShippingCouriers.map((courier) => (
                          <option key={courier.id} value={courier.id}>
                            {courier.name}
                          </option>
                        ))}
                      </select>
                    </label>
                    <div className="border border-pe-black/10 bg-pe-cream/30 px-3 py-2">
                      <p className="font-sans text-[0.64rem] uppercase tracking-[0.14em] text-pe-charcoal/60">
                        {l.shippingAddressTitle}
                      </p>
                      {!hasSession ? (
                        <div className="mt-1.5 flex flex-col gap-2">
                          <p className="font-sans text-[0.74rem] text-pe-rose-deep">
                            {l.shippingAddressLoginRequired}
                          </p>
                          <a
                            href={`/${locale}/auth/login?redirect=${encodeURIComponent(`/${locale}/cart`)}`}
                            className="w-fit px-2.5 py-1.5 border border-pe-rose/30 text-pe-rose-deep font-sans text-[0.62rem] tracking-wider uppercase hover:bg-pe-rose/10 transition-colors"
                          >
                            {l.shippingAddressLoginAction}
                          </a>
                        </div>
                      ) : loadingAddresses ? (
                        <p className="mt-1 font-sans text-[0.74rem] text-pe-charcoal/70">
                          {locale === 'es' ? 'Cargando direcciones...' : 'Loading addresses...'}
                        </p>
                      ) : selectedAddress ? (
                        <div className="mt-1.5">
                          <div className="flex items-center gap-2">
                            <p className="font-sans text-[0.82rem] text-pe-charcoal">{selectedAddress.label}</p>
                            {selectedAddress.isDefault && (
                              <span className="font-sans text-[0.58rem] tracking-wider uppercase px-1.5 py-0.5 bg-pe-rose/12 text-pe-rose-deep">
                                {l.shippingAddressDefaultBadge}
                              </span>
                            )}
                          </div>
                          <p className="font-sans text-[0.72rem] text-pe-charcoal/75">{selectedAddress.recipientName} · {selectedAddress.phone}</p>
                          <p className="font-sans text-[0.72rem] text-pe-charcoal/70">
                            {selectedAddress.line1}
                            {selectedAddress.line2 ? `, ${selectedAddress.line2}` : ''}
                          </p>
                          <p className="font-sans text-[0.72rem] text-pe-charcoal/70">
                            {selectedAddress.comuna}, {selectedAddress.city}, {selectedAddress.region}
                          </p>
                        </div>
                      ) : (
                        <p className="mt-1 font-sans text-[0.74rem] text-pe-rose-deep">
                          {l.shippingAddressNone}
                        </p>
                      )}
                      {hasSession && (
                      <div className="mt-2 flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => setShowAddressSelectModal(true)}
                          className="px-2.5 py-1.5 border border-pe-black/15 text-pe-charcoal/70 font-sans text-[0.62rem] tracking-wider uppercase hover:border-pe-black/25 transition-colors"
                        >
                          {l.shippingAddressChoose}
                        </button>
                        <button
                          type="button"
                          onClick={openCreateAddressModal}
                          className="px-2.5 py-1.5 border border-pe-rose/30 text-pe-rose-deep font-sans text-[0.62rem] tracking-wider uppercase hover:bg-pe-rose/10 transition-colors"
                        >
                          {l.shippingAddressCreate}
                        </button>
                        {selectedAddress && (
                          <button
                            type="button"
                            onClick={() => openEditAddressModal(selectedAddress)}
                            className="px-2.5 py-1.5 border border-pe-black/15 text-pe-charcoal/70 font-sans text-[0.62rem] tracking-wider uppercase hover:border-pe-black/25 transition-colors"
                          >
                            {l.shippingAddressEdit}
                          </button>
                        )}
                      </div>
                      )}
                    </div>
                  </div>
                  <p className="mt-2 font-sans text-[0.7rem] text-pe-charcoal/60">
                    {l.shippingPaymentModeLabel}: {shippingPaymentModeLabel(shippingPaymentMode)}
                  </p>
                  {(activeShippingZones.length === 0 || activeShippingCouriers.length === 0) && (
                    <p className="mt-2 font-sans text-[0.7rem] text-pe-rose-deep">
                      {l.shippingUnavailable}
                    </p>
                  )}
                </div>

                <div className="mb-6">
                  <p className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal/75 mb-2">
                    {l.paymentMethod}
                  </p>
                  <div className="flex flex-col gap-2">
                    {paymentMethodBankTransferEnabled && (
                      <label className="inline-flex items-center gap-2 font-sans text-sm text-pe-charcoal">
                        <input
                          type="radio"
                          name="paymentMethod"
                          value="TRANSFER"
                          checked={paymentMethod === 'TRANSFER'}
                          onChange={() => setPaymentMethod('TRANSFER')}
                          className="accent-pe-rose"
                        />
                        {l.paymentMethodTransfer}
                      </label>
                    )}
                    {paymentMethodGatewayEnabled && (
                      <label className="inline-flex items-start gap-2 font-sans text-sm text-pe-charcoal">
                        <input
                          type="radio"
                          name="paymentMethod"
                          value="WEBPAY"
                          checked={paymentMethod === 'WEBPAY'}
                          onChange={() => setPaymentMethod('WEBPAY')}
                          className="mt-1 accent-pe-rose"
                        />
                        <span>
                          {l.paymentMethodGateway}
                          {selectedGatewayProviderLabel && (
                            <span className="block text-[0.68rem] text-pe-charcoal/60">
                              {l.paymentProviderLabel}: {selectedGatewayProviderLabel}
                            </span>
                          )}
                        </span>
                      </label>
                    )}
                  </div>
                  {paymentMethod === 'TRANSFER' && (
                    <div className="mt-3 border border-pe-black/10 bg-pe-cream/35 px-3 py-2">
                      <p className="font-sans text-[0.65rem] uppercase tracking-[0.16em] text-pe-charcoal/55 mb-2">
                        {l.transferDetailsTitle}
                      </p>
                      <dl className="grid grid-cols-1 gap-1.5 font-sans text-[0.74rem] text-pe-charcoal/75">
                        <div className="flex items-center justify-between gap-3">
                          <dt className="text-pe-charcoal/55">{l.transferHolder}</dt>
                          <dd className="text-right">{transferAccountHolder || '-'}</dd>
                        </div>
                        <div className="flex items-center justify-between gap-3">
                          <dt className="text-pe-charcoal/55">{l.transferEmail}</dt>
                          <dd className="text-right">{transferContactEmail || '-'}</dd>
                        </div>
                        <div className="flex items-center justify-between gap-3">
                          <dt className="text-pe-charcoal/55">{l.transferAccount}</dt>
                          <dd className="text-right">{transferAccountNumber || '-'}</dd>
                        </div>
                        <div className="flex items-center justify-between gap-3">
                          <dt className="text-pe-charcoal/55">{l.transferBank}</dt>
                          <dd className="text-right">{transferBankName || '-'}</dd>
                        </div>
                        <div className="flex items-center justify-between gap-3">
                          <dt className="text-pe-charcoal/55">{l.transferType}</dt>
                          <dd className="text-right">{transferAccountType || '-'}</dd>
                        </div>
                      </dl>
                    </div>
                  )}
                </div>

                <div className="flex justify-between font-sans text-sm mb-6">
                  <span className="text-pe-charcoal">{l.total}</span>
                  <span className="font-semibold">
                    {priceFormat(total, items[0]?.price.currency ?? 'CLP')}
                  </span>
                </div>

                <button
                  onClick={handleCheckout}
                  disabled={checkingOut || stockConflictCount > 0 || shippingUnavailable || (!!authUser && !selectedShippingAddressId)}
                  className="w-full bg-pe-gold text-pe-black font-sans text-xs tracking-widest uppercase py-3 hover:bg-opacity-90 active:scale-95 transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-pe-gold disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {checkingOut ? (
                    <span className="inline-flex items-center justify-center gap-2">
                      <Loader2 size={13} className="animate-spin" />
                      {l.checkoutLoading}
                    </span>
                  ) : (
                    l.checkout
                  )}
                </button>

                {stockConflictCount > 0 && (
                  <p className="mt-2 font-sans text-[0.68rem] text-[#8f2d3b]">
                    {l.checkoutBlockedByStock}
                  </p>
                )}
                {shippingUnavailable && (
                  <p className="mt-2 font-sans text-[0.68rem] text-[#8f2d3b]">
                    {l.shippingUnavailable}
                  </p>
                )}
                {hasSession && !selectedShippingAddressId && (
                  <p className="mt-2 font-sans text-[0.68rem] text-[#8f2d3b]">
                    {l.shippingAddressMissing}
                  </p>
                )}

                <a
                  href={`/${locale}/products`}
                  className="block text-center font-sans text-[10px] tracking-widest uppercase text-pe-charcoal/65 hover:text-pe-gold transition-colors mt-4"
                >
                  {l.continueShopping}
                </a>
              </div>
            </div>
          </div>
        )}

        {showAddressSelectModal && (
          <div
            className="fixed inset-x-0 bottom-0 z-[120] bg-black/45 backdrop-blur-[1px] overflow-y-auto p-4 pb-6"
            style={{ top: 'calc(var(--pe-site-header-height, 0px) + 1.5rem)' }}
            onClick={() => setShowAddressSelectModal(false)}
          >
            <div className="min-h-full flex items-start justify-center">
            <div
              role="dialog"
              aria-modal="true"
              aria-label={l.shippingAddressSelectModalTitle}
              className="w-full max-w-2xl bg-pe-white border border-pe-black/12 px-5 pb-5 pt-4 flex flex-col gap-3 max-h-[calc(100dvh-var(--pe-site-header-height,0px)-3rem)] overflow-y-auto shadow-[0_30px_60px_-30px_rgba(26,26,26,0.45)]"
              onClick={(event) => event.stopPropagation()}
            >
              <div className="flex items-center justify-between">
                <p className="font-display text-pe-black text-xl">{l.shippingAddressSelectModalTitle}</p>
                <button
                  type="button"
                  onClick={() => setShowAddressSelectModal(false)}
                  aria-label={l.shippingAddressCloseModal}
                  className="inline-flex h-11 w-11 items-center justify-center border border-pe-black/15 text-pe-charcoal/65 transition-colors hover:border-pe-black/30 hover:text-pe-charcoal focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pe-rose/50"
                >
                  <X size={18} strokeWidth={1.9} />
                </button>
              </div>

              {addresses.length === 0 ? (
                <div className="border border-pe-black/10 bg-pe-cream/35 px-3 py-3">
                  <p className="font-sans text-sm text-pe-charcoal/70">{l.shippingAddressNone}</p>
                  <button
                    type="button"
                    onClick={() => {
                      setShowAddressSelectModal(false);
                      openCreateAddressModal();
                    }}
                    className="mt-2 px-3 py-1.5 border border-pe-rose/30 text-pe-rose-deep font-sans text-[0.66rem] tracking-wider uppercase hover:bg-pe-rose/10 transition-colors"
                  >
                    {l.shippingAddressCreate}
                  </button>
                </div>
              ) : (
                <ul className="flex flex-col gap-2">
                  {addresses.map((address) => (
                    <li key={address.id} className="border border-pe-black/10 bg-pe-cream/20 px-3 py-2">
                      <label className="flex items-start gap-2 cursor-pointer">
                        <input
                          type="radio"
                          name="shipping-address"
                          checked={selectedShippingAddressId === address.id}
                          onChange={() => setSelectedShippingAddressId(address.id)}
                          className="mt-1 accent-pe-rose"
                        />
                        <div className="flex-1">
                          <div className="flex items-center gap-2">
                            <p className="font-sans text-sm text-pe-charcoal">{address.label}</p>
                            {address.isDefault && (
                              <span className="font-sans text-[0.58rem] tracking-wider uppercase px-1.5 py-0.5 bg-pe-rose/12 text-pe-rose-deep">
                                {l.shippingAddressDefaultBadge}
                              </span>
                            )}
                          </div>
                          <p className="font-sans text-[0.74rem] text-pe-charcoal/75">{address.recipientName} · {address.phone}</p>
                          <p className="font-sans text-[0.72rem] text-pe-charcoal/65">
                            {address.line1}{address.line2 ? `, ${address.line2}` : ''}, {address.comuna}, {address.city}, {address.region}
                          </p>
                          <div className="mt-2 flex flex-wrap gap-2">
                            <button
                              type="button"
                              onClick={() => openEditAddressModal(address)}
                              className="px-2 py-1 border border-pe-black/15 text-pe-charcoal/70 font-sans text-[0.58rem] tracking-wider uppercase hover:border-pe-black/25 transition-colors"
                            >
                              {l.shippingAddressEdit}
                            </button>
                            {!address.isDefault && (
                              <button
                                type="button"
                                onClick={() => { void handleSetDefaultAddress(address.id); }}
                                disabled={settingDefaultAddressId === address.id}
                                className="px-2 py-1 border border-pe-rose/30 text-pe-rose-deep font-sans text-[0.58rem] tracking-wider uppercase hover:bg-pe-rose/10 transition-colors disabled:opacity-60"
                              >
                                {settingDefaultAddressId === address.id
                                  ? (locale === 'es' ? 'Guardando...' : 'Saving...')
                                  : l.shippingAddressSetDefault}
                              </button>
                            )}
                          </div>
                        </div>
                      </label>
                    </li>
                  ))}
                </ul>
              )}

              <div className="flex items-center gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddressSelectModal(false)}
                  className="px-4 py-2 bg-pe-rose text-white font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors"
                >
                  {l.shippingAddressUse}
                </button>
                <button
                  type="button"
                  onClick={openCreateAddressModal}
                  className="px-4 py-2 border border-pe-black/15 text-pe-charcoal/70 font-sans text-[0.68rem] tracking-wider uppercase hover:border-pe-black/25 transition-colors"
                >
                  {l.shippingAddressCreate}
                </button>
              </div>
            </div>
            </div>
          </div>
        )}

        {showAddressFormModal && (
          <div
            className="fixed inset-x-0 bottom-0 z-[130] bg-black/50 backdrop-blur-[1px] overflow-y-auto p-4 pb-6"
            style={{ top: 'calc(var(--pe-site-header-height, 0px) + 1.5rem)' }}
            onClick={() => setShowAddressFormModal(false)}
          >
            <div className="min-h-full flex items-start justify-center">
            <div
              role="dialog"
              aria-modal="true"
              aria-label={editingAddressId ? l.shippingAddressFormTitleEdit : l.shippingAddressFormTitleNew}
              className="w-full max-w-xl bg-pe-white border border-pe-black/12 px-5 pb-5 pt-4 flex flex-col gap-3 max-h-[calc(100dvh-var(--pe-site-header-height,0px)-3rem)] overflow-y-auto shadow-[0_30px_60px_-30px_rgba(26,26,26,0.45)]"
              onClick={(event) => event.stopPropagation()}
            >
              <div className="flex items-center justify-between">
                <p className="font-display text-pe-black text-xl">
                  {editingAddressId ? l.shippingAddressFormTitleEdit : l.shippingAddressFormTitleNew}
                </p>
                <button
                  type="button"
                  onClick={() => setShowAddressFormModal(false)}
                  aria-label={l.shippingAddressCloseModal}
                  className="inline-flex h-11 w-11 items-center justify-center border border-pe-black/15 text-pe-charcoal/65 transition-colors hover:border-pe-black/30 hover:text-pe-charcoal focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pe-rose/50"
                >
                  <X size={18} strokeWidth={1.9} />
                </button>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <input value={addressDraft.label} onChange={(e) => setAddressDraft((p) => ({ ...p, label: e.target.value }))} placeholder={l.shippingAddressLabel} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                <input value={addressDraft.recipientName} onChange={(e) => setAddressDraft((p) => ({ ...p, recipientName: e.target.value }))} placeholder={l.shippingAddressRecipient} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                <input value={addressDraft.phone} onChange={(e) => setAddressDraft((p) => ({ ...p, phone: e.target.value }))} placeholder={l.shippingAddressPhone} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                <input value={addressDraft.line1} onChange={(e) => setAddressDraft((p) => ({ ...p, line1: e.target.value }))} placeholder={l.shippingAddressLine1} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                <input value={addressDraft.line2} onChange={(e) => setAddressDraft((p) => ({ ...p, line2: e.target.value }))} placeholder={l.shippingAddressLine2} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
                <select
                  value={addressDraft.regionId}
                  onChange={(e) => setAddressDraft((p) => ({ ...p, regionId: e.target.value, cityId: '', comunaId: '', region: '', city: '', comuna: '' }))}
                  className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2 bg-white"
                >
                  <option value="">{loadingLocations ? l.shippingAddressLocationsLoading : l.shippingAddressRegionSelect}</option>
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
                  <option value="">{l.shippingAddressCitySelect}</option>
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
                  <option value="">{l.shippingAddressComunaSelect}</option>
                  {comunaOptions.map((comuna) => (
                    <option key={comuna.id} value={comuna.id}>{comuna.name}</option>
                  ))}
                </select>
                <input value={addressDraft.reference} onChange={(e) => setAddressDraft((p) => ({ ...p, reference: e.target.value }))} placeholder={l.shippingAddressReference} className="border border-pe-black/12 px-3 py-2 font-sans text-sm sm:col-span-2" />
              </div>
              <label className="inline-flex items-center gap-2 font-sans text-sm text-pe-charcoal/75">
                <input
                  type="checkbox"
                  checked={addressDraft.isDefault}
                  onChange={(e) => setAddressDraft((p) => ({ ...p, isDefault: e.target.checked }))}
                  className="accent-pe-rose"
                />
                {l.shippingAddressSetDefaultToggle}
              </label>
              <div className="flex items-center gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => { void handleSaveAddress(); }}
                  disabled={savingAddress}
                  className="px-4 py-2 bg-pe-rose text-white font-sans text-[0.68rem] tracking-wider uppercase hover:bg-pe-rose-deep transition-colors disabled:opacity-60"
                >
                  {savingAddress ? (locale === 'es' ? 'Guardando...' : 'Saving...') : l.shippingAddressSave}
                </button>
                <button
                  type="button"
                  onClick={() => setShowAddressFormModal(false)}
                  className="px-4 py-2 border border-pe-black/15 text-pe-charcoal/70 font-sans text-[0.68rem] tracking-wider uppercase hover:border-pe-black/25 transition-colors"
                >
                  {l.shippingAddressCancel}
                </button>
              </div>
            </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

