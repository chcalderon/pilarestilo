package com.pilarestilo.privacy.application.dto;

import java.util.List;
import java.util.Map;

/**
 * What the shop holds about one customer, as she is entitled to receive it.
 *
 * <p>Maps rather than typed records on purpose: this is a copy handed to a person, not an interface
 * another part of the system consumes, and a typed mirror of six aggregates would be six more
 * things to keep in step with the models they copy.
 */
public record PersonalDataExportDto(
        Map<String, Object> account,
        List<Map<String, Object>> orders,
        List<Map<String, Object>> addresses,
        List<Map<String, Object>> reviews,
        List<Map<String, Object>> returns,
        List<Map<String, Object>> consents
) {}
