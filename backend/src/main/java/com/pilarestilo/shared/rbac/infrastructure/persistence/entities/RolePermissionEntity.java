package com.pilarestilo.shared.rbac.infrastructure.persistence.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "role_permissions")
public class RolePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "view_key", nullable = false, length = 100)
    private String viewKey;

    public Long getId() { return id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getViewKey() { return viewKey; }
    public void setViewKey(String viewKey) { this.viewKey = viewKey; }
}
