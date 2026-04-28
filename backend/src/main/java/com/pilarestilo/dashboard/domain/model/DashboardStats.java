package com.pilarestilo.dashboard.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public sealed interface DashboardStats
        permits DashboardStats.AdminStats, DashboardStats.SellerStats,
                DashboardStats.DespachadorStats, DashboardStats.AdministracionStats {

    record SalesTotal(BigDecimal amount, int orderCount) {}

    record TopProduct(String productId, String name, int unitsSold) {}

    record DailyRevenue(LocalDate date, BigDecimal amount) {}

    record CajaSnapshot(
            String status,
            LocalDateTime openedAt,
            BigDecimal expectedBalance,
            int saleCount,
            BigDecimal saleTotal
    ) {}

    record LastSale(BigDecimal amount, LocalDateTime recordedAt) {}

    record ExpiringWorker(String userId, String fullName, LocalDate vigencyEnd) {}

    record AdminStats(
            SalesTotal dailySales,
            SalesTotal weeklySales,
            int openCashRegisters,
            int pendingDispatches,
            int inProgressDispatches,
            List<TopProduct> topProducts,
            List<DailyRevenue> dailyRevenueSeries
    ) implements DashboardStats {}

    record SellerStats(
            CajaSnapshot currentCaja,
            LastSale lastSale
    ) implements DashboardStats {}

    record DespachadorStats(
            int pendingDispatches,
            int myDispatchedToday,
            int myInProgress
    ) implements DashboardStats {}

    record AdministracionStats(
            int activeWorkers,
            List<ExpiringWorker> expiringWorkers
    ) implements DashboardStats {}
}
