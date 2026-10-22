-- BE-21 side effect 3 (OPERION_BE_CHANGE_QUEUE.md): HQ's first account, the "super admin":
-- ADMIN role + PRINCIPAL scope at HQ_BANDUNG, the top of the tree. Product owner's
-- decision 21 Oct 2026; person given 22 Oct 2026.
--
-- The account is created LOCKED: '!' is not a BCrypt hash, so no password matches it.
-- Never put a password in a migration. Its first password is set once at startup from
-- BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD (InitialPasswordBootstrap), which only
-- ever touches a locked account.

INSERT INTO public.users (id, email, password_hash, full_name, created_at, updated_at, role, tenant_id, scope)
SELECT gen_random_uuid(), 'tanugraha.richard@gmail.com', '!', 'Richard Tanugraha', now(), now(),
       'ADMIN', t.id, 'PRINCIPAL'
FROM public.tenants t
WHERE t.code = 'HQ_BANDUNG'
  AND NOT EXISTS (SELECT 1 FROM public.users u WHERE lower(u.email) = 'tanugraha.richard@gmail.com');
