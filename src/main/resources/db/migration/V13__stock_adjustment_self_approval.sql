-- BE-25 (OPERION_BE_CHANGE_QUEUE.md): who changes stock and item data.
--
-- 1. stock_adjustments.self_approved: the requester approved their own adjustment, which
--    is allowed only when they are the store's only Owner+ (decided 23 Oct 2026; the same
--    choice as BE-20's self-received goods: allowed, flagged, visible).
-- 2. notifications.type: + STOCK_ADJUSTMENT_REQUESTED (the store's Owner+ are told).
--    Full list = V7's list + the new value (CLAUDE.md rule 3).
-- 3. notifications.reference_type: + STOCK_ADJUSTMENT. Full list = V12's list + the new value.

ALTER TABLE public.stock_adjustments ADD COLUMN self_approved boolean NOT NULL DEFAULT false;

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
        'GOODS_SELF_RECEIVED',
        'STOCK_ADJUSTMENT_REQUESTED'
    ]::text[]));

ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_reference_type_check;
ALTER TABLE public.notifications ADD CONSTRAINT notifications_reference_type_check CHECK (
    (reference_type)::text = ANY (ARRAY[
        'PURCHASE_REQUEST',
        'PART',
        'AIRSOFT_UNIT',
        'REALISASI',
        'PURCHASE_REQUEST_AUTHORIZATION',
        'EXPENSE',
        'STOCK_ADJUSTMENT'
    ]::text[]));
