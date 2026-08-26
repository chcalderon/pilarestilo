-- V89__variant_templates.sql
-- Independent, admin-managed catalogue of variant field shapes (label, inputType,
-- options/min/max, allowMultiple/allowCustom for a primary+secondary pair), assigned
-- directly to a product -- see docs/superpowers/specs/2026-08-26-variant-templates-design.md.
-- Replaces the category-derived config from V87/V88, which stays in the categories table
-- unread (expand/contract) rather than being dropped here.

CREATE TABLE variant_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    field_config JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
