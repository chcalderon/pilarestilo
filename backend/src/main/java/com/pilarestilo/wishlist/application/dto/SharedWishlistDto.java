package com.pilarestilo.wishlist.application.dto;

import java.util.List;
import java.util.UUID;

public record SharedWishlistDto(UUID token, List<UUID> productIds) {}
