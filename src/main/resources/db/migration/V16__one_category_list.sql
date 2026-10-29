-- BE-29 (OPERION_BE_CHANGE_QUEUE.md): one category list, owned by HQ.
-- Categories and part types now live only on each tree's root tenant. Every store item is
-- moved onto its root's same-named category and type (matched ignoring case; created at the
-- root when it has none; on a clash of is_consumable the root's row wins, because it is the
-- one kept), then the store-level copies are deleted. Only dev and test data exist; the
-- production database is wiped before go-live anyway.

-- each tenant's root
CREATE TEMP TABLE be29_root ON COMMIT DROP AS
WITH RECURSIVE up AS (
    SELECT id AS tenant_id, id AS cur, parent_id FROM public.tenants
    UNION ALL
    SELECT up.tenant_id, t.id, t.parent_id FROM up JOIN public.tenants t ON t.id = up.parent_id
)
SELECT tenant_id, cur AS root_id FROM up WHERE parent_id IS NULL;

-- 1. a root category for every store category's name, created where the root has none
INSERT INTO public.part_categories (id, tenant_id, name, description, is_consumable)
SELECT gen_random_uuid(), x.root_id, x.name, x.description, x.is_consumable
FROM (
    SELECT DISTINCT ON (r.root_id, lower(c.name)) r.root_id, c.name, c.description, c.is_consumable
    FROM public.part_categories c
    JOIN be29_root r ON r.tenant_id = c.tenant_id
    WHERE r.root_id <> c.tenant_id
    ORDER BY r.root_id, lower(c.name), c.name
) x
WHERE NOT EXISTS (
    SELECT 1 FROM public.part_categories h WHERE h.tenant_id = x.root_id AND lower(h.name) = lower(x.name));

CREATE TEMP TABLE be29_cat ON COMMIT DROP AS
SELECT c.id AS old_id,
       (SELECT h.id FROM public.part_categories h
         WHERE h.tenant_id = r.root_id AND lower(h.name) = lower(c.name)
         ORDER BY h.id LIMIT 1) AS new_id
FROM public.part_categories c
JOIN be29_root r ON r.tenant_id = c.tenant_id
WHERE r.root_id <> c.tenant_id;

-- 2. the same for part types, matched by (root category, name)
INSERT INTO public.part_type (id, tenant_id, category_id, name, description, created_at)
SELECT gen_random_uuid(), x.root_id, x.category_id, x.name, x.description, now()
FROM (
    SELECT DISTINCT ON (r.root_id, COALESCE(m.new_id, pt.category_id), lower(pt.name))
           r.root_id, COALESCE(m.new_id, pt.category_id) AS category_id, pt.name, pt.description
    FROM public.part_type pt
    JOIN be29_root r ON r.tenant_id = pt.tenant_id
    LEFT JOIN be29_cat m ON m.old_id = pt.category_id
    WHERE r.root_id <> pt.tenant_id
    ORDER BY r.root_id, COALESCE(m.new_id, pt.category_id), lower(pt.name), pt.name
) x
WHERE NOT EXISTS (
    SELECT 1 FROM public.part_type h
    WHERE h.tenant_id = x.root_id AND h.category_id = x.category_id AND lower(h.name) = lower(x.name));

CREATE TEMP TABLE be29_type ON COMMIT DROP AS
SELECT pt.id AS old_id,
       (SELECT h.id FROM public.part_type h
         WHERE h.tenant_id = r.root_id
           AND h.category_id = COALESCE(m.new_id, pt.category_id)
           AND lower(h.name) = lower(pt.name)
         ORDER BY h.id LIMIT 1) AS new_id
FROM public.part_type pt
JOIN be29_root r ON r.tenant_id = pt.tenant_id
LEFT JOIN be29_cat m ON m.old_id = pt.category_id
WHERE r.root_id <> pt.tenant_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM be29_cat WHERE new_id IS NULL)
       OR EXISTS (SELECT 1 FROM be29_type WHERE new_id IS NULL) THEN
        RAISE EXCEPTION 'BE-29: a store category or part type has no HQ row to move to';
    END IF;
END $$;

-- 3. every item points at HQ's rows; the legacy text column keeps HQ's name
UPDATE public.parts p SET category_id = m.new_id FROM be29_cat m WHERE p.category_id = m.old_id;
UPDATE public.parts p SET part_type_id = m.new_id FROM be29_type m WHERE p.part_type_id = m.old_id;
UPDATE public.part_type t SET category_id = m.new_id FROM be29_cat m WHERE t.category_id = m.old_id;
UPDATE public.parts p SET category = c.name
FROM public.part_categories c
WHERE c.id = p.category_id AND p.category IS DISTINCT FROM c.name;

-- 4. the store copies go
DELETE FROM public.part_type pt USING be29_type m WHERE pt.id = m.old_id;
DELETE FROM public.part_categories c USING be29_cat m WHERE c.id = m.old_id;
