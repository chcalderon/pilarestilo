package com.pilarestilo.shared.infrastructure.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

/**
 * Names every package the main EntityManagerFactory maps, rather than letting it scan
 * com.pilarestilo whole.
 *
 * <p>Notifications are moving to a database of their own, and two DataSources need two
 * EntityManagerFactories. Spring offers no "scan everything except this", and a scan root includes
 * by prefix and recursively, so com.pilarestilo.notifications cannot be left out by naming a root:
 * it sits inside com.pilarestilo whatever root is named. Listing the modules is what is left.
 *
 * <p>What a list costs is silence -- a module added and not listed simply stops being mapped, and
 * nothing says so until a query fails in production. {@code EntityScanCoversEveryModuleTest} is
 * what buys that back: it reads this annotation and compares it against the directories under
 * com/pilarestilo, so the omission is a red test on the machine of whoever added the module.
 *
 * <p>Packages with no entity of their own are listed too. They cost nothing to scan, and the day
 * one of them gains an entity it is already covered.
 *
 * <p>com.pilarestilo.notifications is deliberately absent: it belongs to
 * {@code NotificationsPersistenceConfig} and its own factory. That is the whole reason this list
 * exists, so the guard test checks the two lists together rather than this one alone.
 */
@Configuration
@EntityScan(basePackages = {
        "com.pilarestilo.billing",
        "com.pilarestilo.cashregister",
        "com.pilarestilo.category",
        "com.pilarestilo.customeraddress",
        "com.pilarestilo.customercredit",
        "com.pilarestilo.dashboard",
        "com.pilarestilo.discount",
        "com.pilarestilo.dispatch",
        "com.pilarestilo.inventory",
        "com.pilarestilo.location",
        "com.pilarestilo.navigation",
        "com.pilarestilo.notification",
        "com.pilarestilo.order",
        "com.pilarestilo.payment",
        "com.pilarestilo.privacy",
        "com.pilarestilo.product",
        "com.pilarestilo.productai",
        "com.pilarestilo.publication",
        "com.pilarestilo.returns",
        "com.pilarestilo.review",
        "com.pilarestilo.shared",
        "com.pilarestilo.systemsettings",
        "com.pilarestilo.user",
        "com.pilarestilo.varianttemplate",
        "com.pilarestilo.wishlist"
})
public class EntityScanConfig {
}
