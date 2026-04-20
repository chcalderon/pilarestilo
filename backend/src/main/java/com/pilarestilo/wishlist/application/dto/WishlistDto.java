package com.pilarestilo.wishlist.application.dto;

import java.util.Set;
import java.util.UUID;

public record WishlistDto(UUID customerId, Set<UUID> productIds) {}
