-- BE-36 (OPERION_BE_CHANGE_QUEUE.md): reset a forgotten password; a password change signs
-- out older sessions.
-- must_change_password: someone else chose the current password (a reset, or a new user);
-- the app sends the person to Change password after sign-in. Cleared by their own change.
-- password_changed_at: the last change or reset through the app, in whole seconds. Tokens
-- issued before it are refused. Null = never changed through the app (no cut-off).

ALTER TABLE public.users ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
ALTER TABLE public.users ADD COLUMN password_changed_at timestamp(6) without time zone;
