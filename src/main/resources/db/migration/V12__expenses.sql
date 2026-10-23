-- BE-19 (OPERION_BE_CHANGE_QUEUE.md, screen PROC-10): expense claims — branch spending that
-- isn't stock (stationery, cleaning supplies, small items bought online).
--
-- 1. expenses: what, amount, date, category (HQ / BRANCH_SPECIFIC: a label, the branch pays
--    both), paid by (STORE / PERSONAL), receipts via uploads (attached_type 'EXPENSE').
--    No edit: a wrong entry is VOIDED with a reason and entered again. PERSONAL ones are paid
--    back with BE-18's rules (the reimbursement_* columns mirror realisasis').
--    Enum columns carry no CHECK constraint on purpose, like V6's reimbursement_method:
--    the Java enums validate them, and a new value can't silently break inserts (rule 3).
-- 2. notifications.reference_type: + EXPENSE (REIMBURSEMENT_PAID for an expense points at
--    it). This CHECK does exist; full list = V3's list + the new value.

CREATE TABLE public.expenses (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES public.tenants(id),
    description character varying(255) NOT NULL,
    amount numeric(38,2) NOT NULL CONSTRAINT expenses_amount_positive CHECK (amount > 0),
    expense_date date NOT NULL,
    category character varying(32) NOT NULL,
    paid_by character varying(32) NOT NULL,
    status character varying(32) NOT NULL DEFAULT 'ACTIVE',
    created_by uuid NOT NULL REFERENCES public.users(id),
    created_at timestamp without time zone NOT NULL DEFAULT now(),
    voided_by uuid REFERENCES public.users(id),
    voided_at timestamp without time zone,
    void_reason text,
    reimbursement_status character varying(32) NOT NULL,
    reimbursed_by uuid REFERENCES public.users(id),
    reimbursed_at timestamp without time zone,
    reimbursement_method character varying(20),
    reimbursement_reference character varying(255),
    -- the last "undo paid back" reason (expenses have no history table)
    reimbursement_undo_reason text
);

CREATE INDEX idx_expenses_tenant_date ON public.expenses (tenant_id, expense_date);

ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_reference_type_check;
ALTER TABLE public.notifications ADD CONSTRAINT notifications_reference_type_check CHECK (
    (reference_type)::text = ANY (ARRAY[
        'PURCHASE_REQUEST',
        'PART',
        'AIRSOFT_UNIT',
        'REALISASI',
        'PURCHASE_REQUEST_AUTHORIZATION',
        'EXPENSE'
    ]::text[]));
