package com.pilarestilo.productai.application.dto;

public record ProductAiInferenceDto(
        String title,
        String description,
        String imagePrompt,
        String engine,
        String fallbackReason
) {
}
