package com.pilarestilo.billing.infrastructure.persistence.repositories;

import com.pilarestilo.billing.domain.DocumentableSale;
import com.pilarestilo.billing.domain.model.SaleSummary;
import com.pilarestilo.billing.domain.ports.SalesQueryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sales list, as one query.
 *
 * <p>Native rather than JPA because the row spans four tables that belong to four modules, and no
 * aggregate owns that shape. The same reason {@code DashboardStatsRepositoryAdapter} is written this
 * way.
 */
@Repository
class SalesQueryRepositoryAdapter implements SalesQueryRepository {

    /**
     * Bound, not spliced. The statuses come from an enum and never from a request, so the old
     * string built into the query was safe — but "safe because of where the value came from" is a
     * property a reader has to reconstruct and a scanner cannot, and it stops being true the first
     * time somebody passes this a filter. Now there is no concatenation left to reason about.
     */
    private static final String[] DOCUMENTABLE_STATUSES = DocumentableSale.statusNames();

    /**
     * Whitelisted, not interpolated: the client's sort property name is only ever used as a map
     * key here, never spliced into the SQL string itself, so an unrecognized or hostile value
     * simply falls through to the default order rather than reaching the query.
     */
    private static final Map<String, String> SORTABLE_COLUMNS = Map.of(
            "createdAt", "o.created_at",
            "totalAmount", "o.total_amount"
    );

    /*
     * LEFT JOIN LATERAL for the payment: an order can carry several attempts and only the newest one
     * describes where it stands. A plain LEFT JOIN would multiply the row per attempt and quietly
     * inflate the list.
     *
     * The document join filters VOIDED, so the column is null exactly when the order has no live
     * document -- which is the whole question this screen exists to answer.
     */
    private static final String BASE_FROM = """
            FROM orders o
            LEFT JOIN users u ON u.id = o.customer_id
            LEFT JOIN LATERAL (
                SELECT pay.method, pay.status, pay.gateway_flag
                FROM payments pay
                WHERE pay.order_id = o.id
                ORDER BY pay.created_at DESC
                LIMIT 1
            ) p ON TRUE
            LEFT JOIN sales_documents d ON d.order_id = o.id AND d.status <> 'VOIDED'
            WHERE (CAST(:query AS text) IS NULL
                   OR o.public_reference ILIKE CAST(:query AS text)
                   OR u.full_name ILIKE CAST(:query AS text)
                   OR u.email ILIKE CAST(:query AS text)
                   OR o.buyer_name ILIKE CAST(:query AS text)
                   OR o.buyer_contact ILIKE CAST(:query AS text))
              AND (CAST(:orderStatus AS text) IS NULL OR o.status = CAST(:orderStatus AS text))
              AND (:missingOnly = FALSE
                   OR (d.id IS NULL AND o.status = ANY(CAST(:documentable AS text[]))))
            """;

    @PersistenceContext
    private EntityManager em;

    // java:S2077 — the only dynamic fragment is orderByClause(), which emits a column name taken
    // from the SORTABLE_COLUMNS whitelist (see its javadoc) plus a hardcoded ASC/DESC; every value
    // is a bound parameter.
    @SuppressWarnings("java:S2077")
    @Override
    public Page<SaleSummary> search(String query, String orderStatus, boolean missingOnly, Pageable pageable) {
        String like = query == null || query.isBlank() ? null : "%" + query.trim() + "%";
        String status = orderStatus == null || orderStatus.isBlank() ? null : orderStatus.trim();

        Query countQuery = em.createNativeQuery("SELECT COUNT(*) " + BASE_FROM);
        bind(countQuery, like, status, missingOnly);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        Query rowsQuery = em.createNativeQuery("""
                SELECT o.id, o.public_reference, o.created_at, o.status,
                       COALESCE(u.full_name, o.buyer_name), COALESCE(u.email, o.buyer_contact),
                       o.total_amount, o.net_amount, o.tax_amount, o.total_currency,
                       p.method, p.status, p.gateway_flag,
                       d.id, d.folio,
                       (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id),
                       (SELECT oi2.product_name FROM order_items oi2 WHERE oi2.order_id = o.id
                         ORDER BY oi2.product_name LIMIT 1)
                """ + BASE_FROM + orderByClause(pageable) + """
                LIMIT :size OFFSET :offset
                """);
        bind(rowsQuery, like, status, missingOnly);
        rowsQuery.setParameter("size", pageable.getPageSize());
        rowsQuery.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = rowsQuery.getResultList();
        return new PageImpl<>(rows.stream().map(this::toSummary).toList(), pageable, total);
    }

    @Override
    public long countMissingDocuments() {
        Query query = em.createNativeQuery("SELECT COUNT(*) " + BASE_FROM);
        bind(query, null, null, true);
        return ((Number) query.getSingleResult()).longValue();
    }

    /** The first sort order whose property is on the whitelist wins; a table like this shows one
     * sort column at a time, matching what `DataTable`'s header click sends. Falls back to the
     * newest-first order this screen has always used when the request carries none, or none that
     * resolve to a real column. */
    private String orderByClause(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            String column = SORTABLE_COLUMNS.get(order.getProperty());
            if (column != null) {
                return "ORDER BY " + column + " " + (order.isAscending() ? "ASC" : "DESC") + "\n";
            }
        }
        return "ORDER BY o.created_at DESC\n";
    }

    private void bind(Query query, String like, String status, boolean missingOnly) {
        query.setParameter("documentable", DOCUMENTABLE_STATUSES);
        query.setParameter("query", like);
        query.setParameter("orderStatus", status);
        query.setParameter("missingOnly", missingOnly);
    }

    private SaleSummary toSummary(Object[] row) {
        return new SaleSummary(
                toUuid(row[0]),
                asString(row[1]),
                toInstant(row[2]),
                asString(row[3]),
                asString(row[4]),
                asString(row[5]),
                toBigDecimal(row[6]),
                toBigDecimal(row[7]),
                toBigDecimal(row[8]),
                asString(row[9]),
                asString(row[10]),
                asString(row[11]),
                asString(row[12]),
                toUuid(row[13]),
                asString(row[14]),
                row[15] == null ? 0 : ((Number) row[15]).intValue(),
                asString(row[16])
        );
    }

    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return value instanceof Timestamp timestamp ? timestamp.toInstant() : null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
