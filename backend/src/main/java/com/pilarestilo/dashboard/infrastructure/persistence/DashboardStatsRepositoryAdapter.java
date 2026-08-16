package com.pilarestilo.dashboard.infrastructure.persistence;

import com.pilarestilo.dashboard.application.port.out.DashboardStatsRepository;
import com.pilarestilo.dashboard.domain.model.DashboardStats;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
class DashboardStatsRepositoryAdapter implements DashboardStatsRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public DashboardStats.AdminStats getAdminStats() {
        Object[] daily = (Object[]) em.createNativeQuery(
                "SELECT COALESCE(SUM(total_amount), 0), COUNT(*) FROM orders WHERE status = 'PAID' AND DATE(updated_at) = CURRENT_DATE"
        ).getSingleResult();
        var dailySales = new DashboardStats.SalesTotal(toBigDecimal(daily[0]), ((Number) daily[1]).intValue());

        Object[] weekly = (Object[]) em.createNativeQuery(
                "SELECT COALESCE(SUM(total_amount), 0), COUNT(*) FROM orders WHERE status = 'PAID' AND updated_at >= DATE_TRUNC('week', CURRENT_DATE)"
        ).getSingleResult();
        var weeklySales = new DashboardStats.SalesTotal(toBigDecimal(weekly[0]), ((Number) weekly[1]).intValue());

        int openCashRegisters = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM cash_registers WHERE status = 'OPEN'"
        ).getSingleResult()).intValue();

        int pendingDispatches = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM dispatches WHERE status = 'PENDING'"
        ).getSingleResult()).intValue();
        int inProgressDispatches = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM dispatches WHERE status = 'IN_PROGRESS'"
        ).getSingleResult()).intValue();

        int paymentsAwaitingReview = countPaymentsAwaitingReview();

        @SuppressWarnings("unchecked")
        List<Object[]> topRows = em.createNativeQuery(
                """
                SELECT oi.product_id::text, p.name, SUM(oi.quantity)::int as units
                FROM order_items oi
                JOIN products p ON p.id = oi.product_id
                JOIN orders o ON o.id = oi.order_id
                WHERE o.status = 'PAID'
                  AND o.updated_at >= DATE_TRUNC('week', CURRENT_DATE)
                GROUP BY oi.product_id, p.name
                ORDER BY units DESC
                LIMIT 5
                """
        ).getResultList();
        List<DashboardStats.TopProduct> topProducts = topRows.stream()
                .map(r -> new DashboardStats.TopProduct(r[0].toString(), r[1].toString(), ((Number) r[2]).intValue()))
                .toList();

        @SuppressWarnings("unchecked")
        List<Object[]> seriesRows = em.createNativeQuery(
                """
                SELECT DATE(updated_at) as day, COALESCE(SUM(total_amount), 0) as revenue
                FROM orders
                WHERE status = 'PAID'
                  AND updated_at >= CURRENT_DATE - INTERVAL '6 days'
                GROUP BY day
                ORDER BY day
                """
        ).getResultList();
        List<DashboardStats.DailyRevenue> dailyRevenueSeries = seriesRows.stream()
                .map(r -> new DashboardStats.DailyRevenue(toLocalDate(r[0]), toBigDecimal(r[1])))
                .toList();

        return new DashboardStats.AdminStats(dailySales, weeklySales, openCashRegisters,
                pendingDispatches, inProgressDispatches, paymentsAwaitingReview,
                topProducts, dailyRevenueSeries);
    }

    @Override
    public DashboardStats.SellerStats getSellerStats(UUID sellerId) {
        @SuppressWarnings("unchecked")
        List<Object[]> cajaRows = em.createNativeQuery(
                """
                SELECT cr.status, cr.opened_at, cr.opening_balance,
                       COALESCE(SUM(CASE WHEN cm.type = 'SALE' THEN cm.amount ELSE 0 END), 0) as sale_total,
                       COUNT(CASE WHEN cm.type = 'SALE' THEN 1 END)::int as sale_count
                FROM cash_registers cr
                LEFT JOIN cash_movements cm ON cm.cash_register_id = cr.id
                WHERE cr.seller_id = :sellerId AND cr.status = 'OPEN'
                GROUP BY cr.id, cr.status, cr.opened_at, cr.opening_balance
                LIMIT 1
                """
        ).setParameter("sellerId", sellerId).getResultList();

        DashboardStats.CajaSnapshot snapshot = cajaRows.isEmpty() ? null : buildSnapshot(cajaRows.get(0));

        @SuppressWarnings("unchecked")
        List<Object[]> lastSaleRows = em.createNativeQuery(
                """
                SELECT cm.amount, cm.recorded_at
                FROM cash_movements cm
                JOIN cash_registers cr ON cr.id = cm.cash_register_id
                WHERE cr.seller_id = :sellerId
                  AND cm.type = 'SALE'
                  AND DATE(cm.recorded_at) = CURRENT_DATE
                ORDER BY cm.recorded_at DESC
                LIMIT 1
                """
        ).setParameter("sellerId", sellerId).getResultList();

        DashboardStats.LastSale lastSale = lastSaleRows.isEmpty() ? null :
                new DashboardStats.LastSale(
                        toBigDecimal(lastSaleRows.get(0)[0]),
                        ((java.sql.Timestamp) lastSaleRows.get(0)[1]).toLocalDateTime());

        return new DashboardStats.SellerStats(snapshot, lastSale);
    }

    @Override
    public DashboardStats.DespachadorStats getDespachadorStats(UUID despachadorId) {
        int pending = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM dispatches WHERE status = 'PENDING'"
        ).getSingleResult()).intValue();

        int myToday = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM dispatches WHERE dispatcher_id = :id AND status = 'DISPATCHED' AND DATE(dispatched_at) = CURRENT_DATE"
        ).setParameter("id", despachadorId).getSingleResult()).intValue();

        int myInProgress = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM dispatches WHERE dispatcher_id = :id AND status = 'IN_PROGRESS'"
        ).setParameter("id", despachadorId).getSingleResult()).intValue();

        return new DashboardStats.DespachadorStats(pending, myToday, myInProgress);
    }

    @Override
    public DashboardStats.AdministracionStats getAdministracionStats() {
        int active = ((Number) em.createNativeQuery(
                """
                SELECT COUNT(*) FROM users
                WHERE role IN ('SUPERVISOR','ADMINISTRACION','DESPACHADOR','SELLER')
                  AND (worker_vigency_start IS NULL OR worker_vigency_start <= CURRENT_DATE)
                  AND (worker_vigency_end IS NULL OR worker_vigency_end >= CURRENT_DATE)
                """
        ).getSingleResult()).intValue();

        @SuppressWarnings("unchecked")
        List<Object[]> expiring = em.createNativeQuery(
                """
                SELECT id::text, full_name, worker_vigency_end
                FROM users
                WHERE role IN ('SUPERVISOR','ADMINISTRACION','DESPACHADOR','SELLER')
                  AND worker_vigency_end BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days'
                ORDER BY worker_vigency_end
                """
        ).getResultList();

        List<DashboardStats.ExpiringWorker> expiringWorkers = expiring.stream()
                .map(r -> new DashboardStats.ExpiringWorker(
                        r[0].toString(),
                        r[1] != null ? r[1].toString() : "",
                        toLocalDate(r[2])))
                .toList();

        return new DashboardStats.AdministracionStats(
                active, countPaymentsAwaitingReview(), expiringWorkers);
    }

    /**
     * Receipts a customer has uploaded and nobody has judged yet.
     *
     * <p>SUBMITTED is the state a proof upload leaves a payment in; UNDER_REVIEW covers a gateway
     * that reports the same thing. Both are money sitting still, which is why this belongs on a
     * dashboard rather than only inside the payments screen.
     */
    private int countPaymentsAwaitingReview() {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM payments WHERE status IN ('SUBMITTED', 'UNDER_REVIEW')"
        ).getSingleResult()).intValue();
    }

    /**
     * A native query's date column arrives as java.time.LocalDate from this driver and as
     * java.sql.Date from others, so neither cast is safe on its own. Casting to java.sql.Date threw
     * ClassCastException and took the whole dashboard down — invisibly, because the revenue series
     * has no rows until the shop's first paid order, and the expiring-workers list none until
     * somebody's vigency comes within a week. Both panels were broken from the day they were
     * written and only failed once real data arrived.
     */
    private LocalDate toLocalDate(Object val) {
        if (val instanceof LocalDate d) return d;
        if (val instanceof java.sql.Date d) return d.toLocalDate();
        if (val instanceof java.time.temporal.TemporalAccessor t) return LocalDate.from(t);
        throw new IllegalStateException("Unsupported date type from query: " + val.getClass());
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private DashboardStats.CajaSnapshot buildSnapshot(Object[] row) {
        BigDecimal openingBalance = toBigDecimal(row[2]);
        BigDecimal saleTotal = toBigDecimal(row[3]);
        int saleCount = ((Number) row[4]).intValue();
        BigDecimal expectedBalance = openingBalance.add(saleTotal);
        return new DashboardStats.CajaSnapshot(
                row[0].toString(),
                ((java.sql.Timestamp) row[1]).toLocalDateTime(),
                expectedBalance,
                saleCount,
                saleTotal);
    }
}
