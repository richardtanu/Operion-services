-- BE-33 (OPERION_BE_CHANGE_QUEUE.md): switch off a user who has left.
-- users.active: false = can't sign in, their tokens stop working, not counted as one of the
-- store's Owners, no new notifications, can't be named on a new record. Every existing user
-- stays on. deactivated_*: who switched them off, when and why; cleared when switched back on.

ALTER TABLE public.users ADD COLUMN active boolean NOT NULL DEFAULT true;
ALTER TABLE public.users ADD COLUMN deactivated_at timestamp(6) without time zone;
ALTER TABLE public.users ADD COLUMN deactivated_by uuid;
ALTER TABLE public.users ADD COLUMN deactivation_reason character varying(500);
ALTER TABLE public.users
    ADD CONSTRAINT fk_users_deactivated_by FOREIGN KEY (deactivated_by) REFERENCES public.users(id);
