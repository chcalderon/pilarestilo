package com.pilarestilo.shared.rbac.domain.model;

public enum PermissionModule {
    ANALYTICS("analytics"),
    PRODUCTS("products"),
    INVENTORY("inventory"),
    ORDERS("orders"),
    CASH("cash"),
    BILLING("billing"),
    DISPATCH("dispatch"),
    USERS("users"),
    ROLES("roles"),
    SETTINGS("settings"),
    POS("pos");

    private final String code;

    PermissionModule(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
