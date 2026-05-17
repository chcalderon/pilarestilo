package com.pilarestilo.shared.rbac.infrastructure.persistence.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "role_permission_grants")
public class RolePermissionGrantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "permission_code", nullable = false, length = 120)
    private String permissionCode;

    @Column(nullable = false, length = 30)
    private String source;

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public String getSource() {
        return source;
    }
}
