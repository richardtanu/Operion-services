-- BE-23 (OPERION_BE_CHANGE_QUEUE.md): one row per file in object storage (Cloudflare R2).
-- The file itself never touches Postgres; this records who uploaded what, for which
-- tenant and purpose, and what it was later attached to.
--
-- purpose / attached_type are plain text with no CHECK constraint on purpose: the Java
-- enum validates them, and a new purpose then needs no migration (CLAUDE.md rule 3).

CREATE TABLE public.uploads (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    tenant_id uuid NOT NULL,
    uploaded_by uuid NOT NULL,
    purpose character varying(50) NOT NULL,
    object_key character varying(255) NOT NULL,
    content_type character varying(100) NOT NULL,
    size_bytes bigint NOT NULL,
    attached_type character varying(50),
    attached_id uuid,
    attached_at timestamp(6) without time zone,
    CONSTRAINT uploads_pkey PRIMARY KEY (id),
    CONSTRAINT uploads_object_key_key UNIQUE (object_key),
    CONSTRAINT uploads_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id),
    CONSTRAINT uploads_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES public.users(id)
);

-- Look up what's attached to a record (goods receipt photos, expense receipts)
CREATE INDEX uploads_attached_idx ON public.uploads (attached_type, attached_id);

-- The hourly sweep only ever reads unattached rows by age
CREATE INDEX uploads_unattached_created_idx ON public.uploads (created_at) WHERE attached_type IS NULL;
