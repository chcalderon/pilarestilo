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
        return switch (stats) {
            case DashboardStats.AdminStats(
                    DashboardStats.SalesTotal dailySales, DashboardStats.SalesTotal weeklySales,
                    int openCashRegisters, int pendingDispatches, int inProgressDispatches,
                    int paymentsAwaitingReview, List<DashboardStats.TopProduct> topProducts,
                    List<DashboardStats.DailyRevenue> dailyRevenueSeries) -> new DashboardStatsResponse(
                    "ADMIN",
                    new SalesTotal(dailySales.amount(), dailySales.orderCount()),
                    new SalesTotal(weeklySales.amount(), weeklySales.orderCount()),
                    openCashRegisters, pendingDispatches, inProgressDispatches,
                    paymentsAwaitingReview,
                    topProducts.stream().map(p -> new TopProduct(p.productId(), p.name(), p.unitsSold())).toList(),
                    dailyRevenueSeries.stream().map(d -> new DailyRevenue(d.date(), d.amount())).toList(),
                    null, null, null, null, null, null
            );
            case DashboardStats.SellerStats(
                    DashboardStats.CajaSnapshot currentCaja, DashboardStats.LastSale lastSale) -> new DashboardStatsResponse(
                    "SELLER", null, null, null, null, null, null, null, null,
                    currentCaja == null ? null : new CajaSnapshot(
                            currentCaja.status(), currentCaja.openedAt(),
                            currentCaja.expectedBalance(), currentCaja.saleCount(), currentCaja.saleTotal()),
                    lastSale == null ? null : new LastSale(lastSale.amount(), lastSale.recordedAt()),
                    null, null, null, null
            );
            case DashboardStats.DespachadorStats(
                    int pendingDispatches, int myDispatchedToday, int myInProgress) -> new DashboardStatsResponse(
                    "DESPACHADOR", null, null, null,
                    pendingDispatches, null, null, null, null, null, null,
                    myDispatchedToday, myInProgress, null, null
            );
            case DashboardStats.AdministracionStats(
                    int activeWorkers, int paymentsAwaitingReview,
                    List<DashboardStats.ExpiringWorker> expiringWorkers) -> new DashboardStatsResponse(
                    "ADMINISTRACION", null, null, null, null, null,
                    paymentsAwaitingReview,
                    null, null, null, null, null, null,
                    activeWorkers,
                    expiringWorkers.stream().map(w -> new ExpiringWorker(w.userId(), w.fullName(), w.vigencyEnd())).toList()
            );
        };
    }
}
