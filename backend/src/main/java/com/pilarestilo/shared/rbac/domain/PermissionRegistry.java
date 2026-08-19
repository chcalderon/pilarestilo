package com.pilarestilo.shared.rbac.domain;

import com.pilarestilo.shared.rbac.domain.model.PermissionCategory;
import com.pilarestilo.shared.rbac.domain.model.PermissionDefinition;
import com.pilarestilo.shared.rbac.domain.model.PermissionModule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PermissionRegistry {

    public static final PermissionDefinition DASHBOARD_READ = define(
            "dashboard.read", "Ver dashboard", "Acceso al dashboard administrativo",
            PermissionModule.ANALYTICS, PermissionCategory.READ);
    public static final PermissionDefinition ANALYTICS_READ = define(
            "analytics.read", "Ver analitica", "Acceso a metricas y resumenes",
            PermissionModule.ANALYTICS, PermissionCategory.READ);

    public static final PermissionDefinition PRODUCTS_READ = define(
            "products.read", "Ver productos", "Listar y ver productos",
            PermissionModule.PRODUCTS, PermissionCategory.READ);
    public static final PermissionDefinition PRODUCTS_CREATE = define(
            "products.create", "Crear productos", "Crear productos nuevos",
            PermissionModule.PRODUCTS, PermissionCategory.WRITE);
    public static final PermissionDefinition PRODUCTS_UPDATE = define(
            "products.update", "Editar productos", "Actualizar productos existentes",
            PermissionModule.PRODUCTS, PermissionCategory.WRITE);
    public static final PermissionDefinition PRODUCTS_DELETE = define(
            "products.delete", "Eliminar productos", "Eliminar productos",
            PermissionModule.PRODUCTS, PermissionCategory.ADMIN);

    public static final PermissionDefinition CATEGORIES_READ = define(
            "categories.read", "Ver categorias", "Listar y ver categorias",
            PermissionModule.PRODUCTS, PermissionCategory.READ);
    public static final PermissionDefinition CATEGORIES_UPDATE = define(
            "categories.update", "Editar categorias", "Crear y editar categorias",
            PermissionModule.PRODUCTS, PermissionCategory.WRITE);

    public static final PermissionDefinition NAVIGATION_READ = define(
            "navigation.read", "Ver navegacion", "Ver menu y estructura editorial",
            PermissionModule.PRODUCTS, PermissionCategory.READ);
    public static final PermissionDefinition NAVIGATION_UPDATE = define(
            "navigation.update", "Editar navegacion", "Editar menu y secciones",
            PermissionModule.PRODUCTS, PermissionCategory.WRITE);

    public static final PermissionDefinition REVIEWS_READ = define(
            "reviews.read", "Ver resenas", "Listar resenas",
            PermissionModule.PRODUCTS, PermissionCategory.READ);
    public static final PermissionDefinition REVIEWS_MODERATE = define(
            "reviews.moderate", "Moderar resenas", "Aprobar, rechazar o editar resenas",
            PermissionModule.PRODUCTS, PermissionCategory.WORKFLOW);

    public static final PermissionDefinition DISCOUNTS_READ = define(
            "discounts.read", "Ver descuentos", "Listar descuentos",
            PermissionModule.PRODUCTS, PermissionCategory.READ);
    public static final PermissionDefinition DISCOUNTS_UPDATE = define(
            "discounts.update", "Editar descuentos", "Crear y editar descuentos",
            PermissionModule.PRODUCTS, PermissionCategory.WRITE);

    public static final PermissionDefinition PUBLICATIONS_READ = define(
            "publications.read", "Ver publicaciones", "Listar publicaciones comerciales",
            PermissionModule.PRODUCTS, PermissionCategory.READ);
    public static final PermissionDefinition PUBLICATIONS_UPDATE = define(
            "publications.update", "Editar publicaciones", "Crear y editar publicaciones",
            PermissionModule.PRODUCTS, PermissionCategory.WRITE);

    public static final PermissionDefinition INVENTORY_READ = define(
            "inventory.read", "Ver inventario", "Consultar inventario",
            PermissionModule.INVENTORY, PermissionCategory.READ);
    public static final PermissionDefinition INVENTORY_ADJUST = define(
            "inventory.adjust", "Ajustar inventario", "Realizar movimientos de inventario",
            PermissionModule.INVENTORY, PermissionCategory.ADMIN);

    public static final PermissionDefinition ORDERS_READ = define(
            "orders.read", "Ver ordenes", "Consultar ordenes",
            PermissionModule.ORDERS, PermissionCategory.READ);
    public static final PermissionDefinition ORDERS_UPDATE = define(
            "orders.update", "Actualizar ordenes", "Actualizar estado o datos de orden",
            PermissionModule.ORDERS, PermissionCategory.WRITE);

    public static final PermissionDefinition PAYMENTS_READ = define(
            "payments.read", "Ver pagos", "Consultar pagos",
            PermissionModule.CASH, PermissionCategory.READ);
    public static final PermissionDefinition PAYMENTS_REVIEW = define(
            "payments.review", "Revisar pagos", "Revisar pagos manuales o pendientes",
            PermissionModule.CASH, PermissionCategory.WORKFLOW);

    public static final PermissionDefinition CASH_READ = define(
            "cash.read", "Ver caja", "Consultar caja",
            PermissionModule.CASH, PermissionCategory.READ);
    public static final PermissionDefinition CASH_OPEN = define(
            "cash.open", "Abrir caja", "Abrir caja operativa",
            PermissionModule.CASH, PermissionCategory.WORKFLOW);
    public static final PermissionDefinition CASH_CLOSE = define(
            "cash.close", "Cerrar caja", "Cerrar caja operativa",
            PermissionModule.CASH, PermissionCategory.WORKFLOW);
    public static final PermissionDefinition CASH_ADJUST = define(
            "cash.adjust", "Ajustar caja", "Realizar ajustes de caja",
            PermissionModule.CASH, PermissionCategory.ADMIN);

    public static final PermissionDefinition DOCUMENTS_READ = define(
            "documents.read", "Ver documentos tributarios", "Consultar boletas y facturas de una venta",
            PermissionModule.BILLING, PermissionCategory.READ);
    public static final PermissionDefinition DOCUMENTS_ISSUE = define(
            "documents.issue", "Registrar documentos", "Registrar la boleta o factura de una venta",
            PermissionModule.BILLING, PermissionCategory.WORKFLOW);
    public static final PermissionDefinition DOCUMENTS_VOID = define(
            "documents.void", "Anular documentos", "Anular un documento tributario emitido",
            PermissionModule.BILLING, PermissionCategory.WORKFLOW);

    /*
     * Deletion requests. Reading the queue is desk work; carrying one out is not, because
     * anonymising cannot be undone - the person stops being identifiable and no later correction
     * brings them back.
     */
    public static final PermissionDefinition PRIVACY_READ = define(
            "privacy.read", "Ver solicitudes de datos",
            "Consultar solicitudes de supresion de datos personales",
            PermissionModule.PRIVACY, PermissionCategory.READ);
    public static final PermissionDefinition PRIVACY_RESOLVE = define(
            "privacy.resolve", "Resolver supresiones",
            "Anonimizar a una persona o rechazar con motivo",
            PermissionModule.PRIVACY, PermissionCategory.ADMIN);

    public static final PermissionDefinition RETURNS_READ = define(
            "returns.read", "Ver devoluciones", "Consultar solicitudes de devolucion y retracto",
            PermissionModule.RETURNS, PermissionCategory.READ);
    public static final PermissionDefinition RETURNS_MANAGE = define(
            "returns.manage", "Gestionar devoluciones", "Aprobar, rechazar, recibir y disponer la prenda",
            PermissionModule.RETURNS, PermissionCategory.WORKFLOW);
    /** Separate from managing for the same reason cancelling is separate from issuing: it moves money. */
    public static final PermissionDefinition RETURNS_REFUND = define(
            "returns.refund", "Registrar reembolsos", "Registrar la devolucion del dinero",
            PermissionModule.RETURNS, PermissionCategory.ADMIN);

    public static final PermissionDefinition DISPATCH_READ = define(
            "dispatch.read", "Ver despachos", "Consultar cola e historial de despachos",
            PermissionModule.DISPATCH, PermissionCategory.READ);
    public static final PermissionDefinition DISPATCH_CLAIM = define(
            "dispatch.claim", "Tomar despachos", "Tomar despachos pendientes",
            PermissionModule.DISPATCH, PermissionCategory.WORKFLOW);
    public static final PermissionDefinition DISPATCH_MANAGE = define(
            "dispatch.manage", "Gestionar despachos", "Despachar, liberar y operar envios",
            PermissionModule.DISPATCH, PermissionCategory.WORKFLOW);
    public static final PermissionDefinition DISPATCH_ASSIGN = define(
            "dispatch.assign", "Asignar despachos", "Asignacion administrativa de despachos",
            PermissionModule.DISPATCH, PermissionCategory.ADMIN);

    public static final PermissionDefinition USERS_READ = define(
            "users.read", "Ver usuarios", "Consultar usuarios y trabajadores",
            PermissionModule.USERS, PermissionCategory.READ);
    public static final PermissionDefinition USERS_UPDATE = define(
            "users.update", "Editar usuarios", "Editar usuarios y su estado",
            PermissionModule.USERS, PermissionCategory.WRITE);
    public static final PermissionDefinition USERS_ASSIGN_ROLE = define(
            "users.assign_role", "Asignar roles", "Asignar o revocar rol laboral",
            PermissionModule.USERS, PermissionCategory.ADMIN);

    public static final PermissionDefinition ROLES_READ = define(
            "roles.read", "Ver roles y permisos", "Consultar matriz RBAC",
            PermissionModule.ROLES, PermissionCategory.READ);
    public static final PermissionDefinition ROLES_MANAGE = define(
            "roles.manage", "Gestionar roles y permisos", "Modificar grants de permisos",
            PermissionModule.ROLES, PermissionCategory.ADMIN);

    public static final PermissionDefinition SETTINGS_READ = define(
            "settings.read", "Ver configuracion", "Consultar configuracion de la tienda",
            PermissionModule.SETTINGS, PermissionCategory.READ);
    public static final PermissionDefinition SETTINGS_UPDATE = define(
            "settings.update", "Editar configuracion", "Modificar configuracion de la tienda",
            PermissionModule.SETTINGS, PermissionCategory.WRITE);

    public static final PermissionDefinition POS_READ = define(
            "pos.read", "Ver POS", "Acceso general a POS",
            PermissionModule.POS, PermissionCategory.READ);
    public static final PermissionDefinition POS_SALE_CREATE = define(
            "pos.sale.create", "Crear venta POS", "Registrar ventas en POS",
            PermissionModule.POS, PermissionCategory.WORKFLOW);
    public static final PermissionDefinition POS_REFUND_CREATE = define(
            "pos.refund.create", "Registrar devolucion POS", "Registrar devoluciones en POS",
            PermissionModule.POS, PermissionCategory.WORKFLOW);

    private static final List<PermissionDefinition> ALL = List.of(
            DASHBOARD_READ, ANALYTICS_READ,
            PRODUCTS_READ, PRODUCTS_CREATE, PRODUCTS_UPDATE, PRODUCTS_DELETE,
            CATEGORIES_READ, CATEGORIES_UPDATE,
            NAVIGATION_READ, NAVIGATION_UPDATE,
            REVIEWS_READ, REVIEWS_MODERATE,
            DISCOUNTS_READ, DISCOUNTS_UPDATE,
            PUBLICATIONS_READ, PUBLICATIONS_UPDATE,
            INVENTORY_READ, INVENTORY_ADJUST,
            ORDERS_READ, ORDERS_UPDATE,
            PAYMENTS_READ, PAYMENTS_REVIEW,
            CASH_READ, CASH_OPEN, CASH_CLOSE, CASH_ADJUST,
            DOCUMENTS_READ, DOCUMENTS_ISSUE, DOCUMENTS_VOID,
            RETURNS_READ, RETURNS_MANAGE, RETURNS_REFUND,
            PRIVACY_READ, PRIVACY_RESOLVE,
            DISPATCH_READ, DISPATCH_CLAIM, DISPATCH_MANAGE, DISPATCH_ASSIGN,
            USERS_READ, USERS_UPDATE, USERS_ASSIGN_ROLE,
            ROLES_READ, ROLES_MANAGE,
            SETTINGS_READ, SETTINGS_UPDATE,
            POS_READ, POS_SALE_CREATE, POS_REFUND_CREATE
    );

    private static final Map<String, PermissionDefinition> BY_CODE = indexByCode();

    private PermissionRegistry() {
    }

    public static List<PermissionDefinition> all() {
        return ALL;
    }

    public static PermissionDefinition require(String code) {
        PermissionDefinition definition = BY_CODE.get(code);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown permission code: " + code);
        }
        return definition;
    }

    private static PermissionDefinition define(
            String code,
            String name,
            String description,
            PermissionModule module,
            PermissionCategory category
    ) {
        return new PermissionDefinition(code, name, description, module, category);
    }

    private static Map<String, PermissionDefinition> indexByCode() {
        Map<String, PermissionDefinition> byCode = new LinkedHashMap<>();
        for (PermissionDefinition definition : ALL) {
            byCode.put(definition.code(), definition);
        }
        return Map.copyOf(byCode);
    }
}
