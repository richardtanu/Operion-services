-- BE-26 (OPERION_BE_CHANGE_QUEUE.md): who moved stock. Every stock movement records the
-- signed-in user whose action wrote it (a scan, an approval, a goods receipt, an admin's
-- data repair). Null on rows written before this, and on movements with no signed-in user.

ALTER TABLE public.stock_movements ADD COLUMN actor_id uuid;
ALTER TABLE public.stock_movements
    ADD CONSTRAINT stock_movements_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.users(id);
