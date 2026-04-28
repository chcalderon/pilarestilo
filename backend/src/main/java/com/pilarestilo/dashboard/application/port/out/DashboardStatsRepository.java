package com.pilarestilo.dashboard.application.port.out;

import com.pilarestilo.dashboard.domain.model.DashboardStats;
import java.util.UUID;

public interface DashboardStatsRepository {
    DashboardStats.AdminStats getAdminStats();
    DashboardStats.SellerStats getSellerStats(UUID sellerId);
    DashboardStats.DespachadorStats getDespachadorStats(UUID despachadorId);
    DashboardStats.AdministracionStats getAdministracionStats();
}
