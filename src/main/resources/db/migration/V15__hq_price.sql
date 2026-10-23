-- BE-28 (OPERION_BE_CHANGE_QUEUE.md): HQ sets the price of what it sells.
-- parts.hq_price: Rupiah per unit (the unit an order's quantity counts), meaningful on HQ's
-- own catalog rows only. Orders to HQ copy it into each line when drafted and again when
-- the Owner sends them; after that a sent order's prices never change.

ALTER TABLE public.parts ADD COLUMN hq_price numeric(38,2);
ALTER TABLE public.parts
    ADD CONSTRAINT parts_hq_price_positive CHECK (hq_price IS NULL OR hq_price > 0);
