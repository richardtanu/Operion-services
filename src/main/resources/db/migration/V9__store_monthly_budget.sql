-- BE-16 (OPERION_BE_CHANGE_QUEUE.md): an optional monthly purchase budget per store.
-- Set only by the store's own Owner; HQ reads it in the spending summary. Null = no
-- budget. It only ever warns, never blocks a purchase.

ALTER TABLE public.tenants ADD COLUMN monthly_purchase_budget numeric(38,2);
ALTER TABLE public.tenants
    ADD CONSTRAINT tenants_monthly_purchase_budget_check CHECK (monthly_purchase_budget >= 0);
