-- BE-22 (OPERION_BE_CHANGE_QUEUE.md): automatic status changes name what triggered them.
--
-- Only two writers produce actor-less history rows: the PRA status refresh (after a
-- Realisasi is logged, approved, rejected or corrected) and the PO auto-status on goods
-- receipt. Both run inside a person's request, so each automatic row records that
-- person (triggered_by) and a short human reference ("SHP-123 approved",
-- "PO-… goods received"). changed_by stays null on those rows: the person didn't make
-- this change, the server derived it. PR and Realisasi history always record their
-- actor, so they get no columns. Existing actor-less rows (test data only) stay
-- automatic = false and read as NOT_RECORDED.

ALTER TABLE public.purchase_request_authorization_status_history
    ADD COLUMN automatic boolean NOT NULL DEFAULT false,
    ADD COLUMN triggered_by uuid,
    ADD COLUMN trigger_reference character varying(255);
ALTER TABLE public.purchase_request_authorization_status_history
    ADD CONSTRAINT pra_status_history_triggered_by_fkey FOREIGN KEY (triggered_by) REFERENCES public.users(id);

ALTER TABLE public.purchase_order_status_history
    ADD COLUMN automatic boolean NOT NULL DEFAULT false,
    ADD COLUMN triggered_by uuid,
    ADD COLUMN trigger_reference character varying(255);
ALTER TABLE public.purchase_order_status_history
    ADD CONSTRAINT po_status_history_triggered_by_fkey FOREIGN KEY (triggered_by) REFERENCES public.users(id);
