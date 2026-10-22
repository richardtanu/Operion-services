-- BE-21 (OPERION_BE_CHANGE_QUEUE.md): HQ BANDUNG above BLITZ_BDG, and the HQ catalog.
-- Product owner's decisions, 21 Oct 2026.
--
-- 1. New root tenant HQ BANDUNG (HQ_BANDUNG); BLITZ_BDG becomes its child. Root tenants
--    are seed-only since BE-12, so this is a migration. FRANCHISE_HQ / OUTLET_2 (test
--    data) are left alone. HQ's part categories and types come from MasterDataSyncRunner
--    on the next boot, as for any tenant.
-- 2. Suppliers are HQ's (decision #7): BLITZ_BDG's suppliers move to HQ_BANDUNG.
--    Supplier lookups go through getRootTenantId(), which is HQ_BANDUNG from now on, so
--    without this move they would stop being found.
-- 3. suppliers.franchise_centre: the supplier that is HQ itself, at most one per tenant.
--    HQ_BANDUNG gets one ("HQ BANDUNG"). Orders to HQ use it automatically; buying a
--    mandatory item outside an order to HQ is allowed only with it as the seller.
-- 4. parts: the HQ catalog is the root tenant's own Part rows (decision (a), "Link").
--    catalog_part_id links a store's part to the HQ part it is (one per store);
--    sold_by_hq / mandatory_from_hq are meaningful on HQ rows only. Mandatory implies sold.
-- 5. realisasis.seller_name: where an outside purchase was made, as free text.
--
-- No user accounts here: the HQ super admin (ADMIN + PRINCIPAL) is created separately,
-- and never with a password in a migration.

INSERT INTO public.tenants (id, name, code, active, created_at, updated_at)
SELECT gen_random_uuid(), 'HQ BANDUNG', 'HQ_BANDUNG', true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM public.tenants WHERE code = 'HQ_BANDUNG');

UPDATE public.tenants
SET parent_id = (SELECT id FROM public.tenants WHERE code = 'HQ_BANDUNG'), updated_at = now()
WHERE code = 'BLITZ_BDG' AND parent_id IS NULL;

UPDATE public.suppliers
SET tenant_id = (SELECT id FROM public.tenants WHERE code = 'HQ_BANDUNG')
WHERE tenant_id = (SELECT id FROM public.tenants WHERE code = 'BLITZ_BDG');

ALTER TABLE public.suppliers ADD COLUMN franchise_centre boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX uq_suppliers_one_franchise_centre
    ON public.suppliers (tenant_id) WHERE franchise_centre;

INSERT INTO public.suppliers (id, name, contact, created_at, tenant_id, franchise_centre)
SELECT gen_random_uuid(), 'HQ BANDUNG', NULL, now(), t.id, true
FROM public.tenants t
WHERE t.code = 'HQ_BANDUNG'
  AND NOT EXISTS (SELECT 1 FROM public.suppliers s WHERE s.tenant_id = t.id AND s.franchise_centre);

ALTER TABLE public.parts ADD COLUMN catalog_part_id uuid;
ALTER TABLE public.parts
    ADD CONSTRAINT parts_catalog_part_id_fkey FOREIGN KEY (catalog_part_id) REFERENCES public.parts(id);
CREATE UNIQUE INDEX uq_parts_one_link_per_store
    ON public.parts (tenant_id, catalog_part_id) WHERE catalog_part_id IS NOT NULL;

ALTER TABLE public.parts ADD COLUMN sold_by_hq boolean NOT NULL DEFAULT false;
ALTER TABLE public.parts ADD COLUMN mandatory_from_hq boolean NOT NULL DEFAULT false;
ALTER TABLE public.parts
    ADD CONSTRAINT parts_mandatory_implies_sold CHECK (NOT mandatory_from_hq OR sold_by_hq);

ALTER TABLE public.realisasis ADD COLUMN seller_name character varying(255);
