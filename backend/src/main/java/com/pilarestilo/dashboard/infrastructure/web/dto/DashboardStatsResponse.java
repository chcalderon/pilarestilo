package com.pilarestilo.dashboard.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pilarestilo.dashboard.domain.model.DashboardStats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardStatsResponse(
        String role,
        // ADMIN / SUPERVISOR fields
        SalesTotal dailySales,
        SalesTotal weeklySales,
        Integer openCashRegisters,
        Integer pendingDispatches,
        Integer inProgressDispatches,
        /** Receipts uploaded and not yet judged. Shown to ADMIN, SUPERVISOR and ADMINISTRACION. */
        Integer paymentsAwaitingReview,
        List<TopProduct> topProducts,
        List<DailyRevenue> dailyRevenueSeries,
        // SELLER fields
        CajaSnapshot currentCaja,
        LastSale lastSale,
        // DESPACHADOR fields (pendingDispatches already above)
        Integer myDispatchedToday,
        Integer myInProgress,
        // ADMINISTRACION fields
        Integer activeWorkers,
        List<ExpiringWorker> expiringWorkers
) {
    public record SalesTotal(BigDecimal amount, int orderCount) {}
    public record TopProduct(String productId, String name, int unitsSold) {}
    public record DailyRevenue(LocalDate date, BigDecimal amount) {}
    public record CajaSnapshot(String status, LocalDateTime openedAt, BigDecimal expectedBalance, int saleCount, BigDecimal saleTotal) {}
    public record LastSale(BigDecimal amount, LocalDateTime recordedAt) {}
    public record ExpiringWorker(String userId, String fullName, LocalDate vigencyEnd) {}

    public static DashboardStatsResponse from(DashboardStats stats) {
        if (stats instanceof DashboardStats.AdminStats a) {
            return new DashboardStatsResponse(
                    "ADMIN",
                    new SalesTotal(a.dailySales().amount(), a.dailySales().orderCount()),
                    new SalesTotal(a.weeklySales().amount(), a.weeklySales().orderCount()),
                    a.openCashRegisters(), a.pendingDispatches(), a.inProgressDispatches(),
                    a.paymentsAwaitingReview(),
                    a.topProducts().stream().map(p -> new TopProduct(p.productId(), p.name(), p.unitsSold())).toList(),
                    a.dailyRevenueSeries().stream().map(d -> new DailyRevenue(d.date(), d.amount())).toList(),
                    null, null, null, null, null, null
            );
        } else if (stats instanceof DashboardStats.SellerStats s) {
            return new DashboardStatsResponse(
                    "SELLER", null, null, null, null, null, null, null, null,
                    s.currentCaja() == null ? null : new CajaSnapshot(
                            s.currentCaja().status(), s.currentCaja().openedAt(),
                            s.currentCaja().expectedBalance(), s.currentCaja().saleCount(), s.currentCaja().saleTotal()),
                    s.lastSale() == null ? null : new LastSale(s.lastSale().amount(), s.lastSale().recordedAt()),
                    null, null, null, null
            );
        } else if (stats instanceof DashboardStats.DespachadorStats d) {
            return new DashboardStatsResponse(
                    "DESPACHADOR", null, null, null,
                    d.pendingDispatches(), null, null, null, null, null, null,
                    d.myDispatchedToday(), d.myInProgress(), null, null
            );
        } else if (stats instanceof DashboardStats.AdministracionStats adm) {
            return new DashboardStatsResponse(
                    "ADMINISTRACION", null, null, null, null, null,
                    adm.paymentsAwaitingReview(),
                    null, null, null, null, null, null,
                    adm.activeWorkers(),
                    adm.expiringWorkers().stream().map(w -> new ExpiringWorker(w.userId(), w.fullName(), w.vigencyEnd())).toList()
            );
        } else {
            throw new IllegalArgumentException("Unknown DashboardStats type: " + stats.getClass());
        }
    }
}
