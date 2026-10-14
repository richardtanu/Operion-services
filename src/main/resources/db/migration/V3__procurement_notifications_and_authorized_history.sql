-- BE-07 (OPERION_BE_CHANGE_QUEUE.md). ddl-auto never alters CHECK constraints on
-- existing tables (CLAUDE.md rule 3), so every enum value added in the same change
-- is added here. Each constraint is dropped and recreated with the full value list.

-- 1. notifications.type: + PURCHASE_REQUEST_APPROVED, PURCHASE_REQUEST_REJECTED,
--    PURCHASE_REQUEST_AUTHORIZED (to the requester), PURCHASE_AUTHORIZATION_CREATED
--    (to PROCUREMENT users).
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
        'PURCHASE_AUTHORIZATION_CREATED'
    ]::text[]));

-- 2. notifications.reference_type: + PURCHASE_REQUEST_AUTHORIZATION.
ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_reference_type_check;
ALTER TABLE public.notifications ADD CONSTRAINT notifications_reference_type_check CHECK (
    (reference_type)::text = ANY (ARRAY[
        'PURCHASE_REQUEST',
        'PART',
        'AIRSOFT_UNIT',
        'REALISASI',
        'PURCHASE_REQUEST_AUTHORIZATION'
    ]::text[]));

-- 3. purchase_request_status_history: + AUTHORIZED. PurchaseRequestStatus has had
--    AUTHORIZED since PRAs were built, but PRA creation never wrote a PR history row,
--    so the gap never surfaced. BE-07 now records that transition (who authorized).
ALTER TABLE public.purchase_request_status_history
    DROP CONSTRAINT IF EXISTS purchase_request_status_history_new_status_check;
ALTER TABLE public.purchase_request_status_history
    ADD CONSTRAINT purchase_request_status_history_new_status_check CHECK (
        (new_status)::text = ANY (ARRAY[
            'PENDING', 'APPROVED', 'REJECTED', 'ORDERED', 'AUTHORIZED', 'CANCELLED'
        ]::text[]));

ALTER TABLE public.purchase_request_status_history
    DROP CONSTRAINT IF EXISTS purchase_request_status_history_previous_status_check;
ALTER TABLE public.purchase_request_status_history
    ADD CONSTRAINT purchase_request_status_history_previous_status_check CHECK (
        (previous_status)::text = ANY (ARRAY[
            'PENDING', 'APPROVED', 'REJECTED', 'ORDERED', 'AUTHORIZED', 'CANCELLED'
        ]::text[]));
