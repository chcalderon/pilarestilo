import { useEffect, useState } from 'react';
import {
  getPublicShippingConfig,
  getPublicStoreSettings,
  type CourierConfig,
  type ShippingPaymentMode,
  type ShippingZoneConfig,
} from '../../lib/api';

export interface BankTransferDetails {
  accountHolder: string;
  contactEmail: string;
  accountNumber: string;
  bankName: string;
  accountType: string;
}

export interface CheckoutConfig {
  loading: boolean;
  /** True once a load attempt finished but left the store unable to take an order. */
  unavailable: boolean;
  bankTransferEnabled: boolean;
  gatewayEnabled: boolean;
  gatewayLabel: string;
  /** The order's real payment method for whichever gateway is active -- never the UI's generic 'WEBPAY'. */
  gatewayMethod: 'MERCADOPAGO' | 'WEBPAY';
  transfer: BankTransferDetails;
  zones: ShippingZoneConfig[];
  couriers: CourierConfig[];
  shippingPaymentMode: ShippingPaymentMode;
  /** Minutes to upload a transfer receipt, or null when the auto-cancel sweep is off. */
  transferWindowMinutes: number | null;
}

const EMPTY_TRANSFER: BankTransferDetails = {
  accountHolder: '',
  contactEmail: '',
  accountNumber: '',
  bankName: '',
  accountType: '',
};

function providerLabel(providers: string[]): string {
  const first = providers[0];
  if (!first) return '';
  return first === 'MERCADO_PAGO' ? 'Mercado Pago' : first;
}

/**
 * The order's paymentMethod used to always be the generic 'WEBPAY' regardless of which gateway
 * actually processed it -- correct for the checkout radio label (already dynamic), wrong for
 * order history, which has its own 'MERCADOPAGO' label and showed 'WebPay' for a Mercado Pago
 * payment. 'WEBPAY' stays the fallback for a provider this doesn't recognise yet (e.g. once TUU
 * ships as a second option).
 */
function resolveGatewayMethod(providers: string[]): 'MERCADOPAGO' | 'WEBPAY' {
  return providers[0] === 'MERCADO_PAGO' ? 'MERCADOPAGO' : 'WEBPAY';
}

/**
 * Loads the store configuration the checkout depends on: which payment methods are on, the
 * bank details for a transfer, and the shipping zones and couriers.
 *
 * Inactive zones and couriers are filtered out here, so no step has to remember to do it.
 * Selecting one that the admin has since deactivated is the failure this prevents.
 */
export function useCheckoutConfig(): CheckoutConfig {
  const [state, setState] = useState<CheckoutConfig>({
    loading: true,
    unavailable: false,
    bankTransferEnabled: true,
    gatewayEnabled: false,
    gatewayLabel: '',
    gatewayMethod: 'WEBPAY',
    transfer: EMPTY_TRANSFER,
    zones: [],
    couriers: [],
    shippingPaymentMode: 'POR_PAGAR',
    transferWindowMinutes: null,
  });

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const [settings, shipping] = await Promise.all([
          getPublicStoreSettings(),
          getPublicShippingConfig(),
        ]);
        if (cancelled) return;

        const providers = Array.isArray(settings?.paymentGatewayProviders)
          ? settings.paymentGatewayProviders
          : [];
        const gatewayEnabled =
          settings?.paymentMethodGatewayEnabled !== false && providers.length > 0;
        /*
         * Transfer stays on when the gateway is off, even if the admin disabled it: a store
         * with no payment method at all can take no orders, and silently accepting none is
         * worse than honouring the fallback.
         */
        const bankTransferEnabled =
          settings?.paymentMethodBankTransferEnabled !== false || !gatewayEnabled;

        const zones = (shipping.zones ?? []).filter((zone) => zone.active);
        const couriers = (shipping.couriers ?? []).filter((courier) => courier.active);

        setState({
          loading: false,
          unavailable: zones.length === 0 || couriers.length === 0,
          bankTransferEnabled,
          gatewayEnabled,
          gatewayLabel: providerLabel(providers.length ? providers : ['MERCADO_PAGO']),
          gatewayMethod: resolveGatewayMethod(providers.length ? providers : ['MERCADO_PAGO']),
          transfer: {
            accountHolder: settings?.bankTransferAccountHolder?.trim() ?? '',
            contactEmail: settings?.bankTransferContactEmail?.trim() ?? '',
            accountNumber: settings?.bankTransferAccountNumber?.trim() ?? '',
            bankName: settings?.bankTransferBankName?.trim() ?? '',
            accountType: settings?.bankTransferAccountType?.trim() ?? '',
          },
          zones,
          couriers,
          shippingPaymentMode: shipping.paymentMode ?? 'POR_PAGAR',
          /*
           * Null when the sweep is disabled: there is no deadline then, and printing one
           * would be a promise the system does not keep.
           */
          transferWindowMinutes: settings?.bankTransferAutoCancelEnabled
            ? (settings.bankTransferAutoCancelTimeoutMinutes ?? null)
            : null,
        });
      } catch {
        if (cancelled) return;
        /*
         * The settings endpoint is unreachable. Report it rather than falling back to
         * defaults: the previous behaviour kept the optimistic defaults, so the customer
         * filled in a whole checkout that could not be submitted.
         */
        setState((prev) => ({ ...prev, loading: false, unavailable: true }));
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}
