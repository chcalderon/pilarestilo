package com.pilarestilo.category.infrastructure.web.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderCategoriesRequest(
    @NotEmpty @Valid List<Item> items
) {
    public record Item(
        @NotNull UUID id,
        int sortOrder
    ) {}
}
