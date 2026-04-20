package com.pilarestilo.review.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReviewSummaryDto(UUID productId, BigDecimal avgRating, long count) {}
