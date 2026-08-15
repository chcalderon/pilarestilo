-- Lets SUPERVISOR moderate reviews, so approving one is not a single person's job.
--
-- reviews.moderate has existed since the permission catalog in V63 and was granted to ADMIN alone.
-- Every written review in the shop therefore waited on one role to be published, and now that a
-- customer can replace a quick rating with a written review, that queue only gets longer.
--
-- SUPERVISOR is the role that already holds the oversight permissions — dashboard.read,
-- analytics.read, payments.read, inventory.read — without holding the ones that move money or
-- change records. Reading what customers wrote and deciding whether it goes on the storefront is
-- the same kind of responsibility.
--
-- ADMINISTRACION is deliberately left out. It is the back office: users, roles and payments. It
-- was given payments.review in V70 because approving a transfer is administration; judging what a
-- customer wrote about a product is not.
INSERT INTO role_permission_grants (role, permission_code)
VALUES ('SUPERVISOR', 'reviews.read'),
       ('SUPERVISOR', 'reviews.moderate')
ON CONFLICT DO NOTHING;
