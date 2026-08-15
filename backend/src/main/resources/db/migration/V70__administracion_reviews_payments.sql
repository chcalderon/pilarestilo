-- Lets ADMINISTRACION see and approve payments.
--
-- The role has existed since the RBAC work and held no payment permission at all, despite the
-- name: payments.read was granted to ADMIN, SELLER and SUPERVISOR, and payments.review only to
-- ADMIN. So every bank transfer in the shop waited on a single role to approve it, and the one
-- role named after the back office could not even look.
--
-- SUPERVISOR is deliberately left with read only. Seeing a payment and releasing the money it
-- represents are different responsibilities, and nobody asked for the second to be widened.
INSERT INTO role_permission_grants (role, permission_code)
VALUES ('ADMINISTRACION', 'payments.read'),
       ('ADMINISTRACION', 'payments.review')
ON CONFLICT DO NOTHING;
