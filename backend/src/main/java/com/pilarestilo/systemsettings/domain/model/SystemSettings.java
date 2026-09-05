package com.pilarestilo.systemsettings.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.enums.MediaStorageProvider;
import com.pilarestilo.systemsettings.domain.enums.NotificationProvider;
import com.pilarestilo.systemsettings.domain.enums.PaymentGatewayProvider;
import com.pilarestilo.systemsettings.domain.enums.ShippingPaymentMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SystemSettings {

    private static final String DEFAULT_STORE_NAME = "Pilar Estilo";
    private static final String DEFAULT_WHATSAPP_NUMBER = "+56900000000";

    private short id;
    private String whatsappNumber;
    private String instagramUrl;
    private String facebookUrl;
    private String bankTransferAccountHolder;
    private String bankTransferContactEmail;
    private String bankTransferAccountNumber;
    private String bankTransferBankName;
    private String bankTransferAccountType;
    private boolean paymentMethodBankTransferEnabled;
    private boolean paymentMethodGatewayEnabled;
    private Set<PaymentGatewayProvider> paymentGatewayProviders;
    private String paymentGatewayMpApiBaseUrl;
    private String paymentGatewayMpAccessTokenEncrypted;
    private String paymentGatewayMpSuccessUrl;
    private String paymentGatewayMpPendingUrl;
    private String paymentGatewayMpFailureUrl;
    private String paymentGatewayMpNotificationUrl;
    private String paymentGatewayMpWebhookTokenEncrypted;
    private MediaStorageProvider mediaStorageProvider;
    private String mediaS3Endpoint;
    private String mediaS3Region;
    private String mediaS3Bucket;
    private String mediaS3AccessKeyId;
    private String mediaS3SecretKeyEncrypted;
    private boolean mediaS3PathStyleEnabled;
    private String mediaS3PublicBaseUrl;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpFromEmail;
    private String smtpPasswordEncrypted;
    private boolean smtpAuthEnabled;
    private boolean smtpStarttlsEnabled;
    private Set<NotificationProvider> notificationProviders;
    private String n8nWebhookUrl;
    private String n8nApiKeyEncrypted;
    private String n8nTokenHeaderName;
    private String metaInstagramUserId;
    private String metaInstagramAccessTokenEncrypted;
    private String metaFacebookPageId;
    private String metaFacebookPageAccessTokenEncrypted;
    private String whatsappSimulatedTo;
    private String whatsappSimulatedSender;
    private String whatsappTwilioApiBaseUrl;
    private String whatsappTwilioAccountSid;
    private String whatsappTwilioAuthTokenEncrypted;
    private String whatsappTwilioFrom;
    private String whatsappTwilioToFallback;
    private String whatsappTwilioSenderAlias;
    private String sendgridApiBaseUrl;
    private String sendgridApiKeyEncrypted;
    private String sendgridFromEmail;
    private String sendgridSenderName;
    private String sendgridToFallback;
    private String productAiInferDefaultBrand;
    private String productAiInferDefaultCondition;
    private Integer productAiInferBasePrice;
    private BigDecimal productAiInferListPriceMultiplier;
    private String shippingZonesJson;
    private String shippingCouriersJson;
    private ShippingPaymentMode shippingPaymentMode;
    private boolean bankTransferAutoCancelEnabled;
    private int bankTransferAutoCancelTimeoutMinutes;
    private String bankTransferAutoCancelCron;
    private StoreTaxSettings tax;
    private WelcomeDiscountSettings welcomeDiscount;
    private PolicyVersions policyVersions;
    private Instant updatedAt;
    private String updatedBy;

    public static final String DEFAULT_SHIPPING_ZONES_JSON =
            "[" +
            "{\"code\":\"LOCAL\",\"titleEs\":\"Zona local\",\"titleEn\":\"Local zone\"," +
            "\"etaEs\":\"24-48 hs\",\"etaEn\":\"24-48h\"," +
            "\"comunas\":[\"Los Andes\",\"San Felipe\",\"Calle Larga\",\"Rinconada\"]," +
            "\"active\":true,\"sortOrder\":1}," +
            "{\"code\":\"REGIONAL\",\"titleEs\":\"V Region y RM\",\"titleEn\":\"Valparaiso Region and Metropolitan Region\"," +
            "\"etaEs\":\"2-4 dias habiles\",\"etaEn\":\"2-4 business days\"," +
            "\"comunas\":[],\"active\":true,\"sortOrder\":2}," +
            "{\"code\":\"NACIONAL\",\"titleEs\":\"Otras regiones\",\"titleEn\":\"Other Chilean regions\"," +
            "\"etaEs\":\"3-7 dias habiles\",\"etaEn\":\"3-7 business days\"," +
            "\"comunas\":[],\"active\":true,\"sortOrder\":3}" +
            "]";

    public static final String DEFAULT_SHIPPING_COURIERS_JSON =
            "[" +
            "{\"id\":\"starken\",\"name\":\"Starken\",\"logoUrl\":null,\"active\":true}," +
            "{\"id\":\"chilexpress\",\"name\":\"ChilExpress\",\"logoUrl\":null,\"active\":true}" +
            "]";

    private SystemSettings() {}

    public static SystemSettings createDefault() {
        SystemSettings settings = new SystemSettings();
        settings.id = 1;
        settings.whatsappNumber = DEFAULT_WHATSAPP_NUMBER;
        settings.instagramUrl = "https://instagram.com/pilarestilo";
        settings.facebookUrl = "https://facebook.com/pilarestilo";
        settings.bankTransferAccountHolder = DEFAULT_STORE_NAME;
        settings.bankTransferContactEmail = "admin@pilarestilo.com";
        settings.bankTransferAccountNumber = "0000000000";
        settings.bankTransferBankName = "Banco de Chile";
        settings.bankTransferAccountType = "Cuenta Corriente";
        settings.paymentMethodBankTransferEnabled = true;
        settings.paymentMethodGatewayEnabled = true;
        settings.paymentGatewayProviders = new LinkedHashSet<>();
        settings.paymentGatewayProviders.add(PaymentGatewayProvider.MERCADO_PAGO);
        settings.paymentGatewayMpApiBaseUrl = "https://api.mercadopago.com";
        settings.mediaStorageProvider = MediaStorageProvider.LOCAL;
        settings.mediaS3PathStyleEnabled = false;
        settings.smtpAuthEnabled = true;
        settings.smtpStarttlsEnabled = true;
        settings.notificationProviders = new LinkedHashSet<>(List.of(NotificationProvider.LOG));
        settings.n8nTokenHeaderName = "X-PE-N8N-TOKEN";
        settings.whatsappSimulatedTo = DEFAULT_WHATSAPP_NUMBER;
        settings.whatsappSimulatedSender = DEFAULT_STORE_NAME;
        settings.whatsappTwilioApiBaseUrl = "https://api.twilio.com";
        settings.whatsappTwilioToFallback = DEFAULT_WHATSAPP_NUMBER;
        settings.whatsappTwilioSenderAlias = DEFAULT_STORE_NAME;
        settings.sendgridApiBaseUrl = "https://api.sendgrid.com";
        settings.sendgridSenderName = DEFAULT_STORE_NAME;
        settings.productAiInferDefaultBrand = DEFAULT_STORE_NAME;
        settings.productAiInferDefaultCondition = "USED";
        settings.productAiInferBasePrice = 24990;
        settings.productAiInferListPriceMultiplier = new BigDecimal("1.35");
        settings.shippingZonesJson = DEFAULT_SHIPPING_ZONES_JSON;
        settings.shippingCouriersJson = DEFAULT_SHIPPING_COURIERS_JSON;
        settings.shippingPaymentMode = ShippingPaymentMode.POR_PAGAR;
        settings.bankTransferAutoCancelEnabled = true;
        settings.bankTransferAutoCancelTimeoutMinutes = 30;
        settings.bankTransferAutoCancelCron = "0 */15 * * * *";
        settings.tax = StoreTaxSettings.empty();
        settings.welcomeDiscount = WelcomeDiscountSettings.disabled();
        settings.policyVersions = PolicyVersions.initial();
        settings.updatedAt = Instant.now();
        settings.updatedBy = "system-default";
        return settings;
    }

    // One parameter per column the settings singleton actually carries; rehydration follows the
    // schema, and a builder would just move the same fields one level out, not reduce them.
    @SuppressWarnings("java:S107")
    public static SystemSettings reconstruct(
            short id,
            String whatsappNumber,
            String instagramUrl,
            String facebookUrl,
            String bankTransferAccountHolder,
            String bankTransferContactEmail,
            String bankTransferAccountNumber,
            String bankTransferBankName,
            String bankTransferAccountType,
            boolean paymentMethodBankTransferEnabled,
            boolean paymentMethodGatewayEnabled,
            String paymentGatewayProviders,
            String paymentGatewayMpApiBaseUrl,
            String paymentGatewayMpAccessTokenEncrypted,
            String paymentGatewayMpSuccessUrl,
            String paymentGatewayMpPendingUrl,
            String paymentGatewayMpFailureUrl,
            String paymentGatewayMpNotificationUrl,
            String paymentGatewayMpWebhookTokenEncrypted,
            String mediaStorageProvider,
            String mediaS3Endpoint,
            String mediaS3Region,
            String mediaS3Bucket,
            String mediaS3AccessKeyId,
            String mediaS3SecretKeyEncrypted,
            boolean mediaS3PathStyleEnabled,
            String mediaS3PublicBaseUrl,
            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            String smtpFromEmail,
            String smtpPasswordEncrypted,
            boolean smtpAuthEnabled,
            boolean smtpStarttlsEnabled,
            String notificationProviders,
            String n8nWebhookUrl,
            String n8nTokenHeaderName,
            String n8nApiKeyEncrypted,
            String metaInstagramUserId,
            String metaInstagramAccessTokenEncrypted,
            String metaFacebookPageId,
            String metaFacebookPageAccessTokenEncrypted,
            String whatsappSimulatedTo,
            String whatsappSimulatedSender,
            String whatsappTwilioApiBaseUrl,
            String whatsappTwilioAccountSid,
            String whatsappTwilioAuthTokenEncrypted,
            String whatsappTwilioFrom,
            String whatsappTwilioToFallback,
            String whatsappTwilioSenderAlias,
            String sendgridApiBaseUrl,
            String sendgridApiKeyEncrypted,
            String sendgridFromEmail,
            String sendgridSenderName,
            String sendgridToFallback,
            String productAiInferDefaultBrand,
            String productAiInferDefaultCondition,
            Integer productAiInferBasePrice,
            BigDecimal productAiInferListPriceMultiplier,
            String shippingZonesJson,
            String shippingCouriersJson,
            String shippingPaymentMode,
            Boolean bankTransferAutoCancelEnabled,
            Integer bankTransferAutoCancelTimeoutMinutes,
            String bankTransferAutoCancelCron,
            StoreTaxSettings tax,
            WelcomeDiscountSettings welcomeDiscount,
            PolicyVersions policyVersions,
            Instant updatedAt,
            String updatedBy
    ) {
        if (id != 1) {
            throw new DomainException("System settings id must be 1");
        }
        SystemSettings settings = createDefault();
        settings.whatsappNumber = normalizeRequired(whatsappNumber, "WhatsApp number");
        settings.instagramUrl = normalizeNullable(instagramUrl);
        settings.facebookUrl = normalizeNullable(facebookUrl);
        settings.bankTransferAccountHolder = normalizeNullable(bankTransferAccountHolder);
        settings.bankTransferContactEmail = normalizeNullable(bankTransferContactEmail);
        settings.bankTransferAccountNumber = normalizeNullable(bankTransferAccountNumber);
        settings.bankTransferBankName = normalizeNullable(bankTransferBankName);
        settings.bankTransferAccountType = normalizeNullable(bankTransferAccountType);
        settings.paymentMethodBankTransferEnabled = paymentMethodBankTransferEnabled;
        settings.paymentMethodGatewayEnabled = paymentMethodGatewayEnabled;
        settings.paymentGatewayProviders = normalizePaymentGatewayProviders(paymentGatewayProviders);
        settings.paymentGatewayMpApiBaseUrl = normalizeNullable(paymentGatewayMpApiBaseUrl);
        settings.paymentGatewayMpAccessTokenEncrypted = normalizeNullable(paymentGatewayMpAccessTokenEncrypted);
        settings.paymentGatewayMpSuccessUrl = normalizeNullable(paymentGatewayMpSuccessUrl);
        settings.paymentGatewayMpPendingUrl = normalizeNullable(paymentGatewayMpPendingUrl);
        settings.paymentGatewayMpFailureUrl = normalizeNullable(paymentGatewayMpFailureUrl);
        settings.paymentGatewayMpNotificationUrl = normalizeNullable(paymentGatewayMpNotificationUrl);
        settings.paymentGatewayMpWebhookTokenEncrypted = normalizeNullable(paymentGatewayMpWebhookTokenEncrypted);
        settings.mediaStorageProvider = normalizeMediaStorageProvider(mediaStorageProvider);
        settings.mediaS3Endpoint = normalizeNullable(mediaS3Endpoint);
        settings.mediaS3Region = normalizeNullable(mediaS3Region);
        settings.mediaS3Bucket = normalizeNullable(mediaS3Bucket);
        settings.mediaS3AccessKeyId = normalizeNullable(mediaS3AccessKeyId);
        settings.mediaS3SecretKeyEncrypted = normalizeNullable(mediaS3SecretKeyEncrypted);
        settings.mediaS3PathStyleEnabled = mediaS3PathStyleEnabled;
        settings.mediaS3PublicBaseUrl = normalizeNullable(mediaS3PublicBaseUrl);
        settings.smtpHost = normalizeNullable(smtpHost);
        settings.smtpPort = smtpPort;
        settings.smtpUsername = normalizeNullable(smtpUsername);
        settings.smtpFromEmail = normalizeNullable(smtpFromEmail);
        settings.smtpPasswordEncrypted = normalizeNullable(smtpPasswordEncrypted);
        settings.smtpAuthEnabled = smtpAuthEnabled;
        settings.smtpStarttlsEnabled = smtpStarttlsEnabled;
        settings.notificationProviders = normalizeNotificationProviders(notificationProviders);
        settings.n8nWebhookUrl = normalizeNullable(n8nWebhookUrl);
        settings.n8nTokenHeaderName = normalizeN8nTokenHeaderName(n8nTokenHeaderName);
        settings.n8nApiKeyEncrypted = normalizeNullable(n8nApiKeyEncrypted);
        settings.metaInstagramUserId = normalizeNullable(metaInstagramUserId);
        settings.metaInstagramAccessTokenEncrypted = normalizeNullable(metaInstagramAccessTokenEncrypted);
        settings.metaFacebookPageId = normalizeNullable(metaFacebookPageId);
        settings.metaFacebookPageAccessTokenEncrypted = normalizeNullable(metaFacebookPageAccessTokenEncrypted);
        settings.whatsappSimulatedTo = normalizeNullable(whatsappSimulatedTo);
        settings.whatsappSimulatedSender = normalizeNullable(whatsappSimulatedSender);
        settings.whatsappTwilioApiBaseUrl = normalizeNullable(whatsappTwilioApiBaseUrl);
        settings.whatsappTwilioAccountSid = normalizeNullable(whatsappTwilioAccountSid);
        settings.whatsappTwilioAuthTokenEncrypted = normalizeNullable(whatsappTwilioAuthTokenEncrypted);
        settings.whatsappTwilioFrom = normalizeNullable(whatsappTwilioFrom);
        settings.whatsappTwilioToFallback = normalizeNullable(whatsappTwilioToFallback);
        settings.whatsappTwilioSenderAlias = normalizeNullable(whatsappTwilioSenderAlias);
        settings.sendgridApiBaseUrl = normalizeNullable(sendgridApiBaseUrl);
        settings.sendgridApiKeyEncrypted = normalizeNullable(sendgridApiKeyEncrypted);
        settings.sendgridFromEmail = normalizeNullable(sendgridFromEmail);
        settings.sendgridSenderName = normalizeNullable(sendgridSenderName);
        settings.sendgridToFallback = normalizeNullable(sendgridToFallback);
        settings.productAiInferDefaultBrand = normalizeInferDefaultBrand(productAiInferDefaultBrand);
        settings.productAiInferDefaultCondition = normalizeInferDefaultCondition(productAiInferDefaultCondition);
        settings.productAiInferBasePrice = normalizeInferBasePrice(productAiInferBasePrice);
        settings.productAiInferListPriceMultiplier = normalizeInferListPriceMultiplier(productAiInferListPriceMultiplier);
        settings.shippingZonesJson = normalizeShippingZonesJson(shippingZonesJson);
        settings.shippingCouriersJson = normalizeShippingCouriersJson(shippingCouriersJson);
        settings.shippingPaymentMode = normalizeShippingPaymentMode(shippingPaymentMode);
        settings.bankTransferAutoCancelEnabled = bankTransferAutoCancelEnabled != null && bankTransferAutoCancelEnabled;
        settings.bankTransferAutoCancelTimeoutMinutes = normalizeCancelTimeout(bankTransferAutoCancelTimeoutMinutes);
        settings.bankTransferAutoCancelCron = normalizeCancelCron(bankTransferAutoCancelCron);
        settings.tax = tax == null ? StoreTaxSettings.empty() : tax;
        settings.welcomeDiscount = welcomeDiscount == null ? WelcomeDiscountSettings.disabled() : welcomeDiscount;
        settings.policyVersions = policyVersions == null ? PolicyVersions.initial() : policyVersions;
        settings.validateConfiguration();
        settings.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        settings.updatedBy = normalizeNullable(updatedBy);
        return settings;
    }

    @SuppressWarnings("java:S107")
    public void update(
            String whatsappNumber,
            String instagramUrl,
            String facebookUrl,
            String bankTransferAccountHolder,
            String bankTransferContactEmail,
            String bankTransferAccountNumber,
            String bankTransferBankName,
            String bankTransferAccountType,
            boolean paymentMethodBankTransferEnabled,
            boolean paymentMethodGatewayEnabled,
            String paymentGatewayProviders,
            String paymentGatewayMpApiBaseUrl,
            String paymentGatewayMpAccessTokenEncrypted,
            String paymentGatewayMpSuccessUrl,
            String paymentGatewayMpPendingUrl,
            String paymentGatewayMpFailureUrl,
            String paymentGatewayMpNotificationUrl,
            String paymentGatewayMpWebhookTokenEncrypted,
            String mediaStorageProvider,
            String mediaS3Endpoint,
            String mediaS3Region,
            String mediaS3Bucket,
            String mediaS3AccessKeyId,
            String mediaS3SecretKeyEncrypted,
            boolean mediaS3PathStyleEnabled,
            String mediaS3PublicBaseUrl,
            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            String smtpFromEmail,
            String smtpPasswordEncrypted,
            boolean smtpAuthEnabled,
            boolean smtpStarttlsEnabled,
            String notificationProviders,
            String n8nWebhookUrl,
            String n8nTokenHeaderName,
            String n8nApiKeyEncrypted,
            String metaInstagramUserId,
            String metaInstagramAccessTokenEncrypted,
            String metaFacebookPageId,
            String metaFacebookPageAccessTokenEncrypted,
            String whatsappSimulatedTo,
            String whatsappSimulatedSender,
            String whatsappTwilioApiBaseUrl,
            String whatsappTwilioAccountSid,
            String whatsappTwilioAuthTokenEncrypted,
            String whatsappTwilioFrom,
            String whatsappTwilioToFallback,
            String whatsappTwilioSenderAlias,
            String sendgridApiBaseUrl,
            String sendgridApiKeyEncrypted,
            String sendgridFromEmail,
            String sendgridSenderName,
            String sendgridToFallback,
            String productAiInferDefaultBrand,
            String productAiInferDefaultCondition,
            Integer productAiInferBasePrice,
            BigDecimal productAiInferListPriceMultiplier,
            String shippingZonesJson,
            String shippingCouriersJson,
            String shippingPaymentMode,
            Boolean bankTransferAutoCancelEnabled,
            Integer bankTransferAutoCancelTimeoutMinutes,
            String bankTransferAutoCancelCron,
            StoreTaxSettings tax,
            WelcomeDiscountSettings welcomeDiscount,
            PolicyVersions policyVersions,
            String updatedBy
    ) {
        this.whatsappNumber = normalizeRequired(whatsappNumber, "WhatsApp number");
        this.instagramUrl = normalizeNullable(instagramUrl);
        this.facebookUrl = normalizeNullable(facebookUrl);
        this.bankTransferAccountHolder = normalizeNullable(bankTransferAccountHolder);
        this.bankTransferContactEmail = normalizeNullable(bankTransferContactEmail);
        this.bankTransferAccountNumber = normalizeNullable(bankTransferAccountNumber);
        this.bankTransferBankName = normalizeNullable(bankTransferBankName);
        this.bankTransferAccountType = normalizeNullable(bankTransferAccountType);
        this.paymentMethodBankTransferEnabled = paymentMethodBankTransferEnabled;
        this.paymentMethodGatewayEnabled = paymentMethodGatewayEnabled;
        this.paymentGatewayProviders = normalizePaymentGatewayProviders(paymentGatewayProviders);
        this.paymentGatewayMpApiBaseUrl = normalizeNullable(paymentGatewayMpApiBaseUrl);
        this.paymentGatewayMpAccessTokenEncrypted = normalizeNullable(paymentGatewayMpAccessTokenEncrypted);
        this.paymentGatewayMpSuccessUrl = normalizeNullable(paymentGatewayMpSuccessUrl);
        this.paymentGatewayMpPendingUrl = normalizeNullable(paymentGatewayMpPendingUrl);
        this.paymentGatewayMpFailureUrl = normalizeNullable(paymentGatewayMpFailureUrl);
        this.paymentGatewayMpNotificationUrl = normalizeNullable(paymentGatewayMpNotificationUrl);
        this.paymentGatewayMpWebhookTokenEncrypted = normalizeNullable(paymentGatewayMpWebhookTokenEncrypted);
        this.mediaStorageProvider = normalizeMediaStorageProvider(mediaStorageProvider);
        this.mediaS3Endpoint = normalizeNullable(mediaS3Endpoint);
        this.mediaS3Region = normalizeNullable(mediaS3Region);
        this.mediaS3Bucket = normalizeNullable(mediaS3Bucket);
        this.mediaS3AccessKeyId = normalizeNullable(mediaS3AccessKeyId);
        this.mediaS3SecretKeyEncrypted = normalizeNullable(mediaS3SecretKeyEncrypted);
        this.mediaS3PathStyleEnabled = mediaS3PathStyleEnabled;
        this.mediaS3PublicBaseUrl = normalizeNullable(mediaS3PublicBaseUrl);
        this.smtpHost = normalizeNullable(smtpHost);
        this.smtpPort = smtpPort;
        this.smtpUsername = normalizeNullable(smtpUsername);
        this.smtpFromEmail = normalizeNullable(smtpFromEmail);
        this.smtpPasswordEncrypted = normalizeNullable(smtpPasswordEncrypted);
        this.smtpAuthEnabled = smtpAuthEnabled;
        this.smtpStarttlsEnabled = smtpStarttlsEnabled;
        this.notificationProviders = normalizeNotificationProviders(notificationProviders);
        this.n8nWebhookUrl = normalizeNullable(n8nWebhookUrl);
        this.n8nTokenHeaderName = normalizeN8nTokenHeaderName(n8nTokenHeaderName);
        this.n8nApiKeyEncrypted = normalizeNullable(n8nApiKeyEncrypted);
        this.metaInstagramUserId = normalizeNullable(metaInstagramUserId);
        this.metaInstagramAccessTokenEncrypted = normalizeNullable(metaInstagramAccessTokenEncrypted);
        this.metaFacebookPageId = normalizeNullable(metaFacebookPageId);
        this.metaFacebookPageAccessTokenEncrypted = normalizeNullable(metaFacebookPageAccessTokenEncrypted);
        this.whatsappSimulatedTo = normalizeNullable(whatsappSimulatedTo);
        this.whatsappSimulatedSender = normalizeNullable(whatsappSimulatedSender);
        this.whatsappTwilioApiBaseUrl = normalizeNullable(whatsappTwilioApiBaseUrl);
        this.whatsappTwilioAccountSid = normalizeNullable(whatsappTwilioAccountSid);
        this.whatsappTwilioAuthTokenEncrypted = normalizeNullable(whatsappTwilioAuthTokenEncrypted);
        this.whatsappTwilioFrom = normalizeNullable(whatsappTwilioFrom);
        this.whatsappTwilioToFallback = normalizeNullable(whatsappTwilioToFallback);
        this.whatsappTwilioSenderAlias = normalizeNullable(whatsappTwilioSenderAlias);
        this.sendgridApiBaseUrl = normalizeNullable(sendgridApiBaseUrl);
        this.sendgridApiKeyEncrypted = normalizeNullable(sendgridApiKeyEncrypted);
        this.sendgridFromEmail = normalizeNullable(sendgridFromEmail);
        this.sendgridSenderName = normalizeNullable(sendgridSenderName);
        this.sendgridToFallback = normalizeNullable(sendgridToFallback);
        this.productAiInferDefaultBrand = normalizeInferDefaultBrand(productAiInferDefaultBrand);
        this.productAiInferDefaultCondition = normalizeInferDefaultCondition(productAiInferDefaultCondition);
        this.productAiInferBasePrice = normalizeInferBasePrice(productAiInferBasePrice);
        this.productAiInferListPriceMultiplier = normalizeInferListPriceMultiplier(productAiInferListPriceMultiplier);
        this.shippingZonesJson = normalizeShippingZonesJson(shippingZonesJson);
        this.shippingCouriersJson = normalizeShippingCouriersJson(shippingCouriersJson);
        this.shippingPaymentMode = normalizeShippingPaymentMode(shippingPaymentMode);
        this.bankTransferAutoCancelEnabled = bankTransferAutoCancelEnabled != null && bankTransferAutoCancelEnabled;
        this.bankTransferAutoCancelTimeoutMinutes = normalizeCancelTimeout(bankTransferAutoCancelTimeoutMinutes);
        this.bankTransferAutoCancelCron = normalizeCancelCron(bankTransferAutoCancelCron);
        this.tax = tax == null ? StoreTaxSettings.empty() : tax;
        this.welcomeDiscount = welcomeDiscount == null ? WelcomeDiscountSettings.disabled() : welcomeDiscount;
        this.policyVersions = policyVersions == null ? PolicyVersions.initial() : policyVersions;
        validateConfiguration();
        this.updatedAt = Instant.now();
        this.updatedBy = normalizeNullable(updatedBy);
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new DomainException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    /** Package-private so {@link StoreTaxSettings} trims its fields by the same rule, not a copy of it. */
    static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeN8nTokenHeaderName(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "X-PE-N8N-TOKEN" : normalized;
    }

    private static MediaStorageProvider normalizeMediaStorageProvider(String rawProvider) {
        try {
            return MediaStorageProvider.fromRaw(rawProvider);
        } catch (IllegalArgumentException _) {
            throw new DomainException("Unsupported media storage provider: " + rawProvider);
        }
    }

    /**
     * Every channel the shop notifies over, from the comma-joined column.
     *
     * <p>A set, not a single value: a shop that sends WhatsApp still owes its customers the written
     * confirmation the Ley 21.398 asks for, and picking one used to silence the other. Empty falls
     * back to LOG rather than to nothing — a shop with no channel would send in silence, which is
     * the failure that hides itself.
     */
    private static Set<NotificationProvider> normalizeNotificationProviders(String rawProviders) {
        if (rawProviders == null || rawProviders.isBlank()) {
            return new LinkedHashSet<>(List.of(NotificationProvider.LOG));
        }
        LinkedHashSet<NotificationProvider> providers = new LinkedHashSet<>();
        for (String part : rawProviders.split(",")) {
            String normalized = part == null ? "" : part.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                providers.add(NotificationProvider.fromRaw(normalized));
            } catch (IllegalArgumentException _) {
                throw new DomainException("Unsupported notification provider: " + normalized);
            }
        }
        return providers.isEmpty()
                ? new LinkedHashSet<>(List.of(NotificationProvider.LOG))
                : providers;
    }

    private static Set<PaymentGatewayProvider> normalizePaymentGatewayProviders(String rawProviders) {
        if (rawProviders == null || rawProviders.isBlank()) {
            return new LinkedHashSet<>();
        }
        LinkedHashSet<PaymentGatewayProvider> providers = new LinkedHashSet<>();
        String[] split = rawProviders.split(",");
        for (String part : split) {
            String normalized = part == null ? "" : part.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                providers.add(PaymentGatewayProvider.fromRaw(normalized));
            } catch (IllegalArgumentException _) {
                throw new DomainException("Unsupported payment gateway provider: " + normalized);
            }
        }
        return providers;
    }

    private static String serializePaymentGatewayProviders(Set<PaymentGatewayProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            return "";
        }
        return providers.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private static String normalizeInferDefaultBrand(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return DEFAULT_STORE_NAME;
        }
        if (normalized.length() > 120) {
            return normalized.substring(0, 120).trim();
        }
        return normalized;
    }

    private static String normalizeInferDefaultCondition(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return "USED";
        }
        String upper = normalized.toUpperCase();
        if (!upper.equals("NEW") && !upper.equals("USED")) {
            throw new DomainException("Product AI default condition must be NEW or USED");
        }
        return upper;
    }

    private static Integer normalizeInferBasePrice(Integer value) {
        if (value == null) {
            return 24990;
        }
        if (value < 1000) {
            throw new DomainException("Product AI base price must be >= 1000");
        }
        return value;
    }

    private static String normalizeShippingZonesJson(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return DEFAULT_SHIPPING_ZONES_JSON;
        }
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
            throw new DomainException("Shipping zones must be a JSON array");
        }
        return normalized;
    }

    private static String normalizeShippingCouriersJson(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return DEFAULT_SHIPPING_COURIERS_JSON;
        }
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
            throw new DomainException("Shipping couriers must be a JSON array");
        }
        return normalized;
    }

    private static ShippingPaymentMode normalizeShippingPaymentMode(String value) {
        try {
            return ShippingPaymentMode.fromRaw(value);
        } catch (IllegalArgumentException _) {
            throw new DomainException("Unsupported shipping payment mode: " + value);
        }
    }

    private static int normalizeCancelTimeout(Integer value) {
        int v = value == null ? 30 : value;
        if (v < 5) throw new DomainException("Bank transfer auto-cancel timeout must be >= 5 minutes");
        if (v > 1440) throw new DomainException("Bank transfer auto-cancel timeout must be <= 1440 minutes");
        return v;
    }

    private static String normalizeCancelCron(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) return "0 */15 * * * *";
        try {
            org.springframework.scheduling.support.CronExpression.parse(normalized);
        } catch (IllegalArgumentException _) {
            throw new DomainException("Invalid cron expression: " + normalized);
        }
        return normalized;
    }

    private static BigDecimal normalizeInferListPriceMultiplier(BigDecimal value) {
        BigDecimal normalized = value == null ? new BigDecimal("1.35") : value;
        if (normalized.compareTo(new BigDecimal("1.00")) < 0) {
            throw new DomainException("Product AI list-price multiplier must be >= 1.00");
        }
        if (normalized.compareTo(new BigDecimal("5.00")) > 0) {
            throw new DomainException("Product AI list-price multiplier must be <= 5.00");
        }
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateConfiguration() {
        validateMediaConfiguration();
        validatePaymentConfiguration();
        validateProductAiInferConfiguration();
    }

    private void validateMediaConfiguration() {
        if (mediaStorageProvider == MediaStorageProvider.S3_COMPATIBLE
                && (mediaS3Bucket == null || mediaS3Bucket.isBlank())) {
            throw new DomainException("S3 bucket cannot be blank when S3-compatible storage is enabled");
        }
    }

    private void validatePaymentConfiguration() {
        if (!paymentMethodBankTransferEnabled && !paymentMethodGatewayEnabled) {
            throw new DomainException("At least one payment method must be enabled");
        }
        if (paymentMethodBankTransferEnabled) {
            validateBankTransferFields();
        }
        if (paymentMethodGatewayEnabled && (paymentGatewayProviders == null || paymentGatewayProviders.isEmpty())) {
            throw new DomainException("At least one payment gateway provider must be enabled");
        }
    }

    private void validateBankTransferFields() {
        if (bankTransferAccountHolder == null || bankTransferAccountHolder.isBlank()) {
            throw new DomainException("Bank transfer account holder cannot be blank when bank transfer is enabled");
        }
        if (bankTransferContactEmail == null || bankTransferContactEmail.isBlank()) {
            throw new DomainException("Bank transfer contact email cannot be blank when bank transfer is enabled");
        }
        if (bankTransferAccountNumber == null || bankTransferAccountNumber.isBlank()) {
            throw new DomainException("Bank transfer account number cannot be blank when bank transfer is enabled");
        }
        if (bankTransferBankName == null || bankTransferBankName.isBlank()) {
            throw new DomainException("Bank transfer bank name cannot be blank when bank transfer is enabled");
        }
        if (bankTransferAccountType == null || bankTransferAccountType.isBlank()) {
            throw new DomainException("Bank transfer account type cannot be blank when bank transfer is enabled");
        }
    }

    private void validateProductAiInferConfiguration() {
        if (productAiInferDefaultBrand == null || productAiInferDefaultBrand.isBlank()) {
            throw new DomainException("Product AI default brand cannot be blank");
        }
        if (!"NEW".equals(productAiInferDefaultCondition) && !"USED".equals(productAiInferDefaultCondition)) {
            throw new DomainException("Product AI default condition must be NEW or USED");
        }
        if (productAiInferBasePrice == null || productAiInferBasePrice < 1000) {
            throw new DomainException("Product AI base price must be >= 1000");
        }
        if (productAiInferListPriceMultiplier == null || productAiInferListPriceMultiplier.compareTo(new BigDecimal("1.00")) < 0) {
            throw new DomainException("Product AI list-price multiplier must be >= 1.00");
        }
    }

    public short getId() { return id; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getFacebookUrl() { return facebookUrl; }
    public String getBankTransferAccountHolder() { return bankTransferAccountHolder; }
    public String getBankTransferContactEmail() { return bankTransferContactEmail; }
    public String getBankTransferAccountNumber() { return bankTransferAccountNumber; }
    public String getBankTransferBankName() { return bankTransferBankName; }
    public String getBankTransferAccountType() { return bankTransferAccountType; }
    public boolean isPaymentMethodBankTransferEnabled() { return paymentMethodBankTransferEnabled; }
    public boolean isPaymentMethodGatewayEnabled() { return paymentMethodGatewayEnabled; }
    public Set<PaymentGatewayProvider> getPaymentGatewayProviders() { return Collections.unmodifiableSet(paymentGatewayProviders); }
    public String getPaymentGatewayProvidersSerialized() { return serializePaymentGatewayProviders(paymentGatewayProviders); }
    public String getPaymentGatewayMpApiBaseUrl() { return paymentGatewayMpApiBaseUrl; }
    public String getPaymentGatewayMpAccessTokenEncrypted() { return paymentGatewayMpAccessTokenEncrypted; }
    public String getPaymentGatewayMpSuccessUrl() { return paymentGatewayMpSuccessUrl; }
    public String getPaymentGatewayMpPendingUrl() { return paymentGatewayMpPendingUrl; }
    public String getPaymentGatewayMpFailureUrl() { return paymentGatewayMpFailureUrl; }
    public String getPaymentGatewayMpNotificationUrl() { return paymentGatewayMpNotificationUrl; }
    public String getPaymentGatewayMpWebhookTokenEncrypted() { return paymentGatewayMpWebhookTokenEncrypted; }
    public MediaStorageProvider getMediaStorageProvider() { return mediaStorageProvider; }
    public String getMediaS3Endpoint() { return mediaS3Endpoint; }
    public String getMediaS3Region() { return mediaS3Region; }
    public String getMediaS3Bucket() { return mediaS3Bucket; }
    public String getMediaS3AccessKeyId() { return mediaS3AccessKeyId; }
    public String getMediaS3SecretKeyEncrypted() { return mediaS3SecretKeyEncrypted; }
    public boolean isMediaS3PathStyleEnabled() { return mediaS3PathStyleEnabled; }
    public String getMediaS3PublicBaseUrl() { return mediaS3PublicBaseUrl; }
    public String getSmtpHost() { return smtpHost; }
    public Integer getSmtpPort() { return smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public String getSmtpFromEmail() { return smtpFromEmail; }
    public String getSmtpPasswordEncrypted() { return smtpPasswordEncrypted; }
    public boolean isSmtpAuthEnabled() { return smtpAuthEnabled; }
    public boolean isSmtpStarttlsEnabled() { return smtpStarttlsEnabled; }
    public Set<NotificationProvider> getNotificationProviders() { return notificationProviders; }
    public String getN8nWebhookUrl() { return n8nWebhookUrl; }
    public String getN8nApiKeyEncrypted() { return n8nApiKeyEncrypted; }
    public String getN8nTokenHeaderName() { return n8nTokenHeaderName; }
    public String getMetaInstagramUserId() { return metaInstagramUserId; }
    public String getMetaInstagramAccessTokenEncrypted() { return metaInstagramAccessTokenEncrypted; }
    public String getMetaFacebookPageId() { return metaFacebookPageId; }
    public String getMetaFacebookPageAccessTokenEncrypted() { return metaFacebookPageAccessTokenEncrypted; }
    public String getWhatsappSimulatedTo() { return whatsappSimulatedTo; }
    public String getWhatsappSimulatedSender() { return whatsappSimulatedSender; }
    public String getWhatsappTwilioApiBaseUrl() { return whatsappTwilioApiBaseUrl; }
    public String getWhatsappTwilioAccountSid() { return whatsappTwilioAccountSid; }
    public String getWhatsappTwilioAuthTokenEncrypted() { return whatsappTwilioAuthTokenEncrypted; }
    public String getWhatsappTwilioFrom() { return whatsappTwilioFrom; }
    public String getWhatsappTwilioToFallback() { return whatsappTwilioToFallback; }
    public String getWhatsappTwilioSenderAlias() { return whatsappTwilioSenderAlias; }
    public String getSendgridApiBaseUrl() { return sendgridApiBaseUrl; }
    public String getSendgridApiKeyEncrypted() { return sendgridApiKeyEncrypted; }
    public String getSendgridFromEmail() { return sendgridFromEmail; }
    public String getSendgridSenderName() { return sendgridSenderName; }
    public String getSendgridToFallback() { return sendgridToFallback; }
    public String getProductAiInferDefaultBrand() { return productAiInferDefaultBrand; }
    public String getProductAiInferDefaultCondition() { return productAiInferDefaultCondition; }
    public Integer getProductAiInferBasePrice() { return productAiInferBasePrice; }
    public BigDecimal getProductAiInferListPriceMultiplier() { return productAiInferListPriceMultiplier; }
    public String getShippingZonesJson() { return shippingZonesJson; }
    public String getShippingCouriersJson() { return shippingCouriersJson; }
    public ShippingPaymentMode getShippingPaymentMode() { return shippingPaymentMode; }
    public boolean isBankTransferAutoCancelEnabled() { return bankTransferAutoCancelEnabled; }
    public int getBankTransferAutoCancelTimeoutMinutes() { return bankTransferAutoCancelTimeoutMinutes; }
    public String getBankTransferAutoCancelCron() { return bankTransferAutoCancelCron; }
    public StoreTaxSettings getTax() { return tax; }
    public WelcomeDiscountSettings getWelcomeDiscount() { return welcomeDiscount; }
    public PolicyVersions getPolicyVersions() { return policyVersions; }
    /** The version a consent recorded now has to be stored against. */
    public String getPrivacyPolicyVersion() { return policyVersions.privacyPolicy(); }
    public String getTermsVersion() { return policyVersions.terms(); }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
