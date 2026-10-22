-- BE-18 (OPERION_BE_CHANGE_QUEUE.md): the store Owner marks the Store Manager paid back.
-- reimbursed_to / reimbursed_at already exist on realisasis; this adds how it was paid and
-- an optional reference, plus the REIMBURSEMENT_PAID notification type.
--
-- reimbursement_method has no CHECK constraint on purpose (the Java enum validates it).
-- notifications.type does have one, which ddl-auto never updates (CLAUDE.md rule 3), so it
-- is recreated with the full list = V5's list + REIMBURSEMENT_PAID.

ALTER TABLE public.realisasis ADD COLUMN reimbursement_method character varying(20);
ALTER TABLE public.realisasis ADD COLUMN reimbursement_reference character varying(255);

ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE public.notifications ADD CONSTRAINT notifications_type_check CHECK (
    (type)::text = ANY (ARRAY[
        'NEW_PURCHASE_REQUEST',
        'PURCHASE_REQUEST_ORDERED',
        'PART_END_OF_LIFE',
        'LOW_STOCK',
        'REALISASI_ESCALATED',
        'PURCHASE_REQUEST_APPROVED',
        'PURCHASE_REQUEST_REJECTED',
        'PURCHASE_REQUEST_AUTHORIZED',
        'PURCHASE_AUTHORIZATION_CREATED',
        'PURCHASE_REQUEST_CANCELLED',
        'REIMBURSEMENT_PAID'
    ]::text[]));
