package com.pilarestilo.shared.rbac.domain.model;

public enum PermissionModule {
    ANALYTICS("analytics"),
    PRODUCTS("products"),
    INVENTORY("inventory"),
    ORDERS("orders"),
    CASH("cash"),
    BILLING("billing"),
    RETURNS("returns"),
    DISPATCH("dispatch"),
    USERS("users"),
    ROLES("roles"),
    SETTINGS("settings"),
    PRIVACY("privacy"),
    POS("pos");

    private final String code;

    PermissionModule(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
