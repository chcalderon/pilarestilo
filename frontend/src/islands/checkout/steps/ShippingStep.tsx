import { useEffect, useMemo, useRef, useState } from 'react';
import { Check, Loader2, Plus, Truck } from 'lucide-react';
import type { CourierConfig, CustomerAddressDto, ShippingZoneConfig } from '../../../lib/api';
import {
  draftFromAddress,
  emptyAddressDraft,
  useAddressBook,
  useCityOptions,
  useComunaOptions,
  validateDraft,
  type AddressDraft,
  type AddressErrors,
} from '../useAddressBook';
import type { Locale } from '../../../i18n/index';

interface Props {
  readonly locale: Locale;
  readonly token: string | null;
  readonly zones: ShippingZoneConfig[];
  readonly couriers: CourierConfig[];
  readonly zoneCode: string;
  readonly courierId: string;
  readonly addressId: string;
  readonly onChange: (value: { zoneCode?: string; courierId?: string; addressId?: string }) => void;
  readonly onContinue: () => void;
}

const copy = {
  es: {
    heading: 'Datos de envío',
    zone: 'Zona de envío',
    courier: 'Courier',
    addresses: 'Dirección de entrega',
    noAddresses: 'Todavía no tienes direcciones guardadas. Agrega una para continuar.',
    addAddress: 'Agregar dirección',
    newAddress: 'Nueva dirección',
    editAddress: 'Editar dirección',
    defaultBadge: 'Principal',
    label: 'Alias',
    recipient: 'Destinatario',
    phone: 'Teléfono',
    line1: 'Dirección',
    line2: 'Departamento, casa u oficina (opcional)',
    region: 'Región',
    city: 'Ciudad',
    comuna: 'Comuna',
    reference: 'Referencia (opcional)',
    makeDefault: 'Usar como dirección principal',
    save: 'Guardar dirección',
    cancel: 'Cancelar',
    edit: 'Editar',
    continue: 'Continuar a pago',
    selectAddressFirst: 'Selecciona una dirección de entrega para continuar.',
    saveFailed: 'No pudimos guardar la dirección. Revisa los datos e inténtalo de nuevo.',
    chooseRegionFirst: 'Elige una región primero',
    chooseCityFirst: 'Elige una ciudad primero',
  },
  en: {
    heading: 'Shipping details',
    zone: 'Shipping zone',
    courier: 'Courier',
    addresses: 'Delivery address',
    noAddresses: 'You have no saved addresses yet. Add one to continue.',
    addAddress: 'Add address',
    newAddress: 'New address',
    editAddress: 'Edit address',
    defaultBadge: 'Default',
    label: 'Label',
    recipient: 'Recipient',
    phone: 'Phone',
    line1: 'Address',
    line2: 'Apartment, house or office (optional)',
    region: 'Region',
    city: 'City',
    comuna: 'Comuna',
    reference: 'Reference (optional)',
    makeDefault: 'Use as default address',
    save: 'Save address',
    cancel: 'Cancel',
    edit: 'Edit',
    continue: 'Continue to payment',
    selectAddressFirst: 'Select a delivery address to continue.',
    saveFailed: 'We could not save the address. Check the details and try again.',
    chooseRegionFirst: 'Choose a region first',
    chooseCityFirst: 'Choose a city first',
  },
} as const;

const FIELD_ORDER: (keyof AddressDraft)[] = [
  'label',
  'recipientName',
  'phone',
  'line1',
  'regionId',
  'cityId',
  'comunaId',
];

const inputClass =
  'w-full h-11 border border-pe-charcoal/25 bg-pe-white px-3 font-sans text-sm text-pe-black ' +
  'focus:outline-hidden focus:border-pe-black focus-visible:ring-1 focus-visible:ring-pe-rose/40';

export default function ShippingStep({
  locale,
  token,
  zones,
  couriers,
  zoneCode,
  courierId,
  addressId,
  onChange,
  onContinue,
}: Props) {
  const l = copy[locale === 'es' ? 'es' : 'en'];
  const book = useAddressBook(token, locale);

  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<AddressDraft>(emptyAddressDraft);
  const [errors, setErrors] = useState<AddressErrors>({});
  const [formError, setFormError] = useState('');
  const [continueError, setContinueError] = useState('');

  const fieldRefs = useRef<Partial<Record<keyof AddressDraft, HTMLElement | null>>>({});

  const cities = useCityOptions(book.regions, draft.regionId);
  const comunas = useComunaOptions(cities, draft.cityId);

  /** Defaults that keep a selection valid when the admin deactivates what was chosen. */
  useEffect(() => {
    if (zones.length && !zones.some((z) => z.code === zoneCode)) {
      onChange({ zoneCode: zones[0].code });
    }
  }, [zones, zoneCode, onChange]);

  useEffect(() => {
    if (couriers.length && !couriers.some((c) => c.id === courierId)) {
      onChange({ courierId: couriers[0].id });
    }
  }, [couriers, courierId, onChange]);

  /** Preselects the default address so the common case needs no interaction at all. */
  useEffect(() => {
    if (addressId || book.addresses.length === 0) return;
    const preferred = book.addresses.find((a) => a.isDefault) ?? book.addresses[0];
    onChange({ addressId: preferred.id });
  }, [book.addresses, addressId, onChange]);

  const selected = useMemo(
    () => book.addresses.find((a) => a.id === addressId) ?? null,
    [book.addresses, addressId]
  );

  function openCreate() {
    setEditingId(null);
    setDraft(emptyAddressDraft());
    setErrors({});
    setFormError('');
    setFormOpen(true);
  }

  function openEdit(address: CustomerAddressDto) {
    setEditingId(address.id);
    setDraft(draftFromAddress(address));
    setErrors({});
    setFormError('');
    setFormOpen(true);
  }

  async function submitAddress(event: React.FormEvent) {
    event.preventDefault();
    const found = validateDraft(draft, locale);
    setErrors(found);

    if (Object.keys(found).length > 0) {
      /* Send focus to the first problem so the fix does not require hunting for it. */
      const first = FIELD_ORDER.find((field) => found[field]);
      if (first) fieldRefs.current[first]?.focus();
      return;
    }

    try {
      const savedId = await book.save(draft, editingId);
      onChange({ addressId: savedId });
      if (draft.isDefault) await book.makeDefault(savedId);
      setFormOpen(false);
      setContinueError('');
    } catch (e) {
      setFormError(e instanceof Error && e.message ? e.message : l.saveFailed);
    }
  }

  function handleContinue() {
    if (!addressId) {
      setContinueError(l.selectAddressFirst);
      return;
    }
    setContinueError('');
    onContinue();
  }

  function field(name: keyof AddressDraft) {
    return {
      value: String(draft[name] ?? ''),
      onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
        setDraft((prev) => ({ ...prev, [name]: e.target.value })),
      'aria-invalid': errors[name] ? true : undefined,
      'aria-describedby': errors[name] ? `addr-${name}-error` : undefined,
    };
  }

  function fieldError(name: keyof AddressDraft) {
    if (!errors[name]) return null;
    return (
      <p id={`addr-${name}-error`} role="alert" className="font-sans text-[0.72rem] text-pe-danger-ink mt-1">
        {errors[name]}
      </p>
    );
  }

  return (
    <div className="bg-pe-white p-6">
      <h2 className="font-display text-pe-black text-xl font-semibold mb-6 flex items-center gap-2">
        <Truck size={18} className="text-pe-muted" aria-hidden="true" />
        {l.heading}
      </h2>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
        <div>
          <label
            htmlFor="checkout-zone"
            className="block font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal mb-2"
          >
            {l.zone}
          </label>
          <select
            id="checkout-zone"
            value={zoneCode}
            onChange={(e) => onChange({ zoneCode: e.target.value })}
            className={inputClass}
          >
            {zones.map((zone) => (
              <option key={zone.code} value={zone.code}>
                {locale === 'es' ? zone.titleEs : zone.titleEn}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label
            htmlFor="checkout-courier"
            className="block font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal mb-2"
          >
            {l.courier}
          </label>
          <select
            id="checkout-courier"
            value={courierId}
            onChange={(e) => onChange({ courierId: e.target.value })}
            className={inputClass}
          >
            {couriers.map((courier) => (
              <option key={courier.id} value={courier.id}>
                {courier.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <fieldset className="mb-6">
        <legend className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal mb-3">
          {l.addresses}
        </legend>

        {(() => {
        if (book.loading) { return (
          <div className="flex items-center gap-2 py-4">
            <Loader2 size={16} className="animate-spin text-pe-muted" />
          </div>
        ); }
        if (book.addresses.length === 0) { return (
          <p className="font-sans text-sm text-pe-muted py-2">{l.noAddresses}</p>
        ); }
        return (
          <div className="space-y-2">
            {book.addresses.map((address) => {
              const isSelected = address.id === addressId;
              return (
                <label
                  key={address.id}
                  className={`flex items-start gap-3 p-4 border cursor-pointer transition-colors ${
                    isSelected
                      ? 'border-pe-black bg-pe-cream/40'
                      : 'border-pe-charcoal/20 hover:border-pe-charcoal/40'
                  }`}
                >
                  <input
                    type="radio"
                    name="checkout-address"
                    value={address.id}
                    checked={isSelected}
                    onChange={() => {
                      onChange({ addressId: address.id });
                      setContinueError('');
                    }}
                    className="mt-1 accent-pe-rose w-4 h-4 shrink-0"
                  />
                  <span className="flex-1 min-w-0">
                    <span className="flex items-center gap-2 flex-wrap">
                      <span className="font-sans text-sm font-semibold text-pe-black">
                        {address.label}
                      </span>
                      {address.isDefault && (
                        <span className="font-sans text-[0.58rem] tracking-wider uppercase px-1.5 py-0.5 bg-pe-rose/12 text-pe-rose-ink">
                          {l.defaultBadge}
                        </span>
                      )}
                    </span>
                    <span className="block font-sans text-[0.78rem] text-pe-charcoal mt-0.5">
                      {address.recipientName} · {address.phone}
                    </span>
                    <span className="block font-sans text-[0.78rem] text-pe-muted">
                      {address.line1}
                      {address.line2 ? `, ${address.line2}` : ''} — {address.comuna}, {address.city}
                    </span>
                  </span>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.preventDefault();
                      openEdit(address);
                    }}
                    className="shrink-0 min-h-11 px-2 font-sans text-[0.62rem] tracking-wider
                      uppercase text-pe-muted hover:text-pe-black transition-colors
                      focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose"
                  >
                    {l.edit}
                  </button>
                </label>
              );
            })}
          </div>
        );
        })()}

        {/*
          * With no addresses saved, adding one is not a secondary option — it is the only way
          * forward, and a ghost button under a grey sentence did not read as one. It takes the
          * primary treatment in that case and returns to secondary once there is something to
          * choose between.
          */}
        {!formOpen && (
          <button
            type="button"
            onClick={openCreate}
            className={`mt-3 inline-flex items-center gap-2 min-h-11 px-4
              font-sans text-[0.68rem] tracking-[0.16em] uppercase transition-colors
              focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2
              ${book.addresses.length === 0 && !book.loading
                ? 'pe-btn-ink'
                : 'border border-pe-charcoal/25 text-pe-charcoal hover:border-pe-black hover:text-pe-black'}`}
          >
            <Plus size={14} />
            {l.addAddress}
          </button>
        )}
      </fieldset>

      {formOpen && (
        <form
          onSubmit={submitAddress}
          noValidate
          className="border border-pe-charcoal/20 p-4 sm:p-5 mb-6 bg-pe-cream/20"
        >
          <h3 className="font-display text-pe-black text-base font-semibold mb-4">
            {editingId ? l.editAddress : l.newAddress}
          </h3>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="addr-label" className="block font-sans text-[0.72rem] text-pe-charcoal mb-1.5">
                {l.label} *
              </label>
              <input
                id="addr-label"
                ref={(el) => { fieldRefs.current.label = el; }}
                autoComplete="off"
                className={inputClass}
                {...field('label')}
              />
              {fieldError('label')}
            </div>

            <div>
              <label htmlFor="addr-recipient" className="block font-sans text-[0.72rem] text-pe-charcoal mb-1.5">
                {l.recipient} *
              </label>
              <input
                id="addr-recipient"
                ref={(el) => { fieldRefs.current.recipientName = el; }}
                autoComplete="name"
                className={inputClass}
                {...field('recipientName')}
              />
              {fieldError('recipientName')}
            </div>

            <div>
              <label htmlFor="addr-phone" className="block font-sans text-[0.72rem] text-pe-charcoal mb-1.5">
                {l.phone} *
              </label>
              <input
                id="addr-phone"
                ref={(el) => { fieldRefs.current.phone = el; }}
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                className={inputClass}
                {...field('phone')}
              />
              {fieldError('phone')}
            </div>

            <div>
              <label htmlFor="addr-line1" className="block font-sans text-[0.72rem] text-pe-charcoal mb-1.5">
                {l.line1} *
              </label>
              <input
                id="addr-line1"
                ref={(el) => { fieldRefs.current.line1 = el; }}
                autoComplete="address-line1"
                className={inputClass}
                {...field('line1')}
              />
              {fieldError('line1')}
            </div>

            <div className="sm:col-span-2">
              <label htmlFor="addr-line2" className="block font-sans text-[0.72rem] text-pe-charcoal mb-1.5">
                {l.line2}
              </label>
              <input
                id="addr-line2"
                autoComplete="address-line2"
                className={inputClass}
                {...field('line2')}
              />
            </div>

            <div>
              <label htmlFor="addr-region" className="block font-sans text-[0.72rem] text-pe-charcoal mb-1.5">
                {l.region} *
              </label>
              <select
                id="addr-region"
                ref={(el) => { fieldRefs.current.regionId = el; }}
                className={inputClass}
                value={draft.regionId}
                onChange={(e) =>
                  /* Region drives city drives comuna: changing one invalidates the rest. */
                  setDraft((prev) => ({ ...prev, regionId: e.target.value, cityId: '', comunaId: '' }))
                }
                aria-invalid={errors.regionId ? true : undefined}
                aria-describedby={errors.regionId ? 'addr-regionId-error' : undefined}
              >
                <option value="">—</option>
                {book.regions.map((region) => (
                  <option key={region.id} value={region.id}>
                    {region.name}
                  </option>
                ))}
              </select>
              {fieldError('regionId')}
            </div>

            <div>
              <label htmlFor="addr-city" className="block font-sans text-[0.72rem] text-pe-charcoal mb-1.5">
                {l.city} *
              </label>
              <select
                id="addr-city"
                ref={(el) => { fieldRefs.current.cityId = el; }}
                className={inputClass}
                value={draft.cityId}
                disabled={!draft.regionId}
                onChange={(e) => setDraft((prev) => ({ ...prev, cityId: e.target.value, comunaId: '' }))}
                aria-invalid={errors.cityId ? true : undefined}
                aria-describedby={errors.cityId ? 'addr-cityId-error' : undefined}
              >
                <option value="">{draft.regionId ? '—' : l.chooseRegionFirst}</option>
                {cities.map((city) => (
                  <option key={city.id} value={city.id}>
                    {city.name}
                  </option>
                ))}
              </select>
              {fieldError('cityId')}
            </div>

            <div>
              <label htmlFor="addr-comuna" className="block font-sans text-[0.72rem] text-pe-charcoal mb-1.5">
                {l.comuna} *
              </label>
              <select
                id="addr-comuna"
                ref={(el) => { fieldRefs.current.comunaId = el; }}
                className={inputClass}
                value={draft.comunaId}
                disabled={!draft.cityId}
                onChange={(e) => setDraft((prev) => ({ ...prev, comunaId: e.target.value }))}
                aria-invalid={errors.comunaId ? true : undefined}
                aria-describedby={errors.comunaId ? 'addr-comunaId-error' : undefined}
              >
                <option value="">{draft.cityId ? '—' : l.chooseCityFirst}</option>
                {comunas.map((comuna) => (
                  <option key={comuna.id} value={comuna.id}>
                    {comuna.name}
                  </option>
                ))}
              </select>
              {fieldError('comunaId')}
            </div>

            <div>
              <label htmlFor="addr-reference" className="block font-sans text-[0.72rem] text-pe-charcoal mb-1.5">
                {l.reference}
              </label>
              <input id="addr-reference" className={inputClass} {...field('reference')} />
            </div>
          </div>

          <label className="flex items-center gap-2 mt-4 cursor-pointer">
            <input
              type="checkbox"
              checked={draft.isDefault}
              onChange={(e) => setDraft((prev) => ({ ...prev, isDefault: e.target.checked }))}
              className="accent-pe-rose w-4 h-4"
            />
            <span className="font-sans text-[0.78rem] text-pe-charcoal">{l.makeDefault}</span>
          </label>

          {formError && (
            <p role="alert" className="font-sans text-[0.75rem] text-pe-danger-ink mt-3">
              {formError}
            </p>
          )}

          <div className="flex flex-wrap gap-3 mt-5">
            <button
              type="submit"
              disabled={book.saving}
              className="inline-flex items-center gap-2 min-h-11 px-5 pe-btn-ink
                font-sans text-[0.68rem] tracking-[0.16em] uppercase disabled:opacity-40
                focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2"
            >
              {book.saving ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />}
              {l.save}
            </button>
            <button
              type="button"
              onClick={() => setFormOpen(false)}
              className="min-h-11 px-5 border border-pe-charcoal/25 font-sans text-[0.68rem]
                tracking-[0.16em] uppercase text-pe-charcoal hover:border-pe-black transition-colors
                focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2"
            >
              {l.cancel}
            </button>
          </div>
        </form>
      )}

      {continueError && (
        <p role="alert" className="font-sans text-[0.78rem] text-pe-danger-ink mb-3">
          {continueError}
        </p>
      )}

      <button
        type="button"
        onClick={handleContinue}
        disabled={!selected}
        className="w-full sm:w-auto inline-flex items-center justify-center min-h-12 px-8
          pe-btn-ink font-sans text-[0.7rem] tracking-[0.16em] uppercase
          transition-opacity disabled:opacity-40 disabled:cursor-not-allowed
          focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2"
      >
        {l.continue}
      </button>
    </div>
  );
}
