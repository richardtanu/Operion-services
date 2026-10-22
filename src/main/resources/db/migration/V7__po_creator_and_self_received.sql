-- BE-20 (OPERION_BE_CHANGE_QUEUE.md): orders to HQ and who receives goods.
--
-- 1. purchase_orders.created_by: who drafted the order, so DRAFT cancel can allow
--    "the creator or OWNER+". Existing rows stay null (drafted before it was recorded).
-- 2. goods_receipts.self_received: the person who bought it also received it (allowed
--    since BE-20, only with a goods photo, and flagged so the Owner can see it).
-- 3. notifications.type: + GOODS_SELF_RECEIVED. Its CHECK constraint is never updated by
--    ddl-auto (CLAUDE.md rule 3); full list = V6's list + the new value.

ALTER TABLE public.purchase_orders ADD COLUMN created_by uuid;
ALTER TABLE public.purchase_orders
    ADD CONSTRAINT purchase_orders_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);

ALTER TABLE public.goods_receipts ADD COLUMN self_received boolean NOT NULL DEFAULT false;

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
        'REIMBURSEMENT_PAID',
        'GOODS_SELF_RECEIVED'
    ]::text[]));
