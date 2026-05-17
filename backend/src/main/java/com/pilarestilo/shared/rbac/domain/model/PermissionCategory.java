package com.pilarestilo.shared.rbac.domain.model;

public enum PermissionCategory {
    READ("read"),
    WRITE("write"),
    WORKFLOW("workflow"),
    ADMIN("admin");

    private final String code;

    PermissionCategory(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
