---
title: Checkout Flow
date: 2026-04-20
status: superseded
superseded_by: a84aeb2
---

# Checkout Flow - Design Spec (Superseded)

## Summary

This spec proposed a dedicated `/{locale}/checkout` page and backend hardening in the same iteration.
The implementation that shipped in commit `a84aeb2` followed a simpler path in the cart flow.

## What Was Proposed

- New route: `/{locale}/checkout` with a dedicated `CheckoutPage` island.
- Auth redirect into checkout page when user is not logged in.
- Rich checkout UI with payment method selector, notes, and discount code fields.
- Backend change to derive `customerId` from JWT principal and remove it from request body.

## What Was Actually Implemented

- Checkout trigger remains in cart (`CartPage`) with real order creation against `POST /api/orders`.
- Drawer checkout now routes to full cart instead of showing "coming soon".
- `createOrder()` was added in frontend API layer.
- On successful checkout:
  - order is created
  - cart is cleared
  - user is redirected to `/{locale}/account?tab=orders`
- Account page now reads `tab` query param to open the Orders tab directly.

## Current Gap

- Backend still accepts `customerId` in `CreateOrderRequest` body.
- Ownership validation and principal-derived `customerId` hardening are still recommended follow-up work.

## Follow-Up Recommendation

Create a separate backend-focused spec/implementation for:

- `POST /api/orders` principal-based customer binding.
- Removing `customerId` from public order-creation request payload.
- Optional dedicated checkout page if product direction still requires it.
