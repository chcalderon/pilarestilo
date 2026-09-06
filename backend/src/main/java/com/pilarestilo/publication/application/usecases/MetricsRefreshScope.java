package com.pilarestilo.publication.application.usecases;

public sealed interface MetricsRefreshScope
        permits MetricsRefreshScope.Campaign, MetricsRefreshScope.RecentDays {

    record Campaign(String label) implements MetricsRefreshScope {}

    record RecentDays(int days) implements MetricsRefreshScope {}
}
