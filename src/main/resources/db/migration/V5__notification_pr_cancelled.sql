-- BE-15 (OPERION_BE_CHANGE_QUEUE.md): a PR's requester is told when someone else cancels
-- it. notifications.type has a CHECK constraint that ddl-auto never updates (CLAUDE.md
-- rule 3), so the new value is added here, in the same change as the enum.
-- Full list = V3's list + PURCHASE_REQUEST_CANCELLED.

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
        'PURCHASE_REQUEST_CANCELLED'
    ]::text[]));
