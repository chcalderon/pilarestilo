package com.pilarestilo.wishlist.application.dto;

import java.util.UUID;

public record WishlistShareLinkDto(UUID token, boolean enabled) {}
