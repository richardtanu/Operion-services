--
-- PostgreSQL database dump
--

-- Dumped from database version 18.3
-- Dumped by pg_dump version 18.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: movement_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.movement_type_enum AS ENUM (
    'IN',
    'OUT',
    'TRANSFER'
);


--
-- Name: reference_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.reference_type_enum AS ENUM (
    'SERVICE',
    'PURCHASE',
    'TRANSFER'
);


--
-- Name: service_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.service_status_enum AS ENUM (
    'OPEN',
    'IN_PROGRESS',
    'DONE'
);


--
-- Name: unit_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.unit_status_enum AS ENUM (
    'ACTIVE',
    'MAINTENANCE',
    'BROKEN'
);


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: airsoft_models; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.airsoft_models (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    brand text,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: airsoft_unit_parts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.airsoft_unit_parts (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    installed_date date,
    notes text,
    removed_date date,
    status character varying(255),
    updated_at timestamp(6) without time zone,
    airsoft_unit_id uuid NOT NULL,
    part_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    condition character varying(255),
    technician_assessment text,
    CONSTRAINT airsoft_unit_parts_status_check CHECK (((status)::text = ANY (ARRAY[('INSTALLED'::character varying)::text, ('REMOVED'::character varying)::text, ('BROKEN'::character varying)::text])))
);


--
-- Name: airsoft_units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.airsoft_units (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    model_id uuid,
    serial_number character varying(255),
    status character varying(255) DEFAULT 'ACTIVE'::public.unit_status_enum,
    purchase_date date,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp(6) without time zone,
    brand character varying(255),
    model character varying(255),
    name character varying(255) NOT NULL,
    notes text,
    type character varying(255),
    health_score integer,
    operational_severity character varying(255),
    CONSTRAINT airsoft_units_operational_severity_check CHECK (((operational_severity)::text = ANY ((ARRAY['NORMAL'::character varying, 'WARNING'::character varying, 'CRITICAL'::character varying])::text[])))
);


--
-- Name: barcode_allocations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.barcode_allocations (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    device_id character varying(255) NOT NULL,
    pad_width integer NOT NULL,
    prefix character varying(255) NOT NULL,
    range_end bigint NOT NULL,
    range_start bigint NOT NULL,
    issued_to uuid,
    tenant_id uuid NOT NULL
);


--
-- Name: barcode_counters; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.barcode_counters (
    id bigint NOT NULL,
    last_value bigint NOT NULL
);


--
-- Name: goods_receipt_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goods_receipt_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    receipt_id uuid,
    part_id uuid,
    quantity integer,
    realisasi_item_id uuid
);


--
-- Name: goods_receipts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goods_receipts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    po_id uuid,
    received_by uuid,
    received_date timestamp without time zone,
    notes text,
    realisasi_id uuid
);


--
-- Name: maintenance_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_rule (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    health_threshold integer DEFAULT 50 NOT NULL,
    max_worn_parts integer DEFAULT 2 NOT NULL,
    max_refurbished_parts integer DEFAULT 3 NOT NULL,
    max_removed_parts integer DEFAULT 5 NOT NULL,
    maintenance_interval_days integer DEFAULT 90 NOT NULL,
    low_stock_multiplier numeric(5,2) DEFAULT 1.00 NOT NULL,
    part_end_of_life_warning_days integer DEFAULT 30 NOT NULL,
    minimum_health_for_operation integer DEFAULT 60 NOT NULL,
    auto_create_work_order boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    burn_rate_min_events integer,
    burn_rate_min_observation_days integer,
    burn_rate_window_days integer,
    days_of_cover_target integer
);


--
-- Name: maintenance_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_schedules (
    id uuid NOT NULL,
    active boolean,
    auto_generate_work_order boolean,
    created_at timestamp(6) without time zone,
    description text,
    interval_days integer,
    last_maintenance_date date,
    next_due_date date,
    title character varying(255),
    updated_at timestamp(6) without time zone,
    airsoft_unit_id uuid,
    tenant_id uuid
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    message text,
    read boolean NOT NULL,
    read_at timestamp(6) without time zone,
    reference_id uuid,
    reference_type character varying(255),
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    recipient_user_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    CONSTRAINT notifications_reference_type_check CHECK (((reference_type)::text = ANY ((ARRAY['PURCHASE_REQUEST'::character varying, 'PART'::character varying, 'AIRSOFT_UNIT'::character varying, 'REALISASI'::character varying])::text[]))),
    CONSTRAINT notifications_type_check CHECK (((type)::text = ANY ((ARRAY['NEW_PURCHASE_REQUEST'::character varying, 'PURCHASE_REQUEST_ORDERED'::character varying, 'PART_END_OF_LIFE'::character varying, 'LOW_STOCK'::character varying, 'REALISASI_ESCALATED'::character varying])::text[])))
);


--
-- Name: part_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.part_categories (
    id uuid NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    is_consumable boolean
);


--
-- Name: part_condition_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.part_condition_history (
    id uuid NOT NULL,
    changed_at timestamp(6) without time zone,
    new_condition character varying(255),
    previous_condition character varying(255),
    reason character varying(255),
    technician_assessment text,
    airsoft_unit_part_id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    CONSTRAINT part_condition_history_new_condition_check CHECK (((new_condition)::text = ANY ((ARRAY['NEW'::character varying, 'GOOD'::character varying, 'WORN'::character varying, 'FAILED'::character varying, 'RETIRED'::character varying, 'DONOR'::character varying, 'REFURBISHED'::character varying])::text[]))),
    CONSTRAINT part_condition_history_previous_condition_check CHECK (((previous_condition)::text = ANY ((ARRAY['NEW'::character varying, 'GOOD'::character varying, 'WORN'::character varying, 'FAILED'::character varying, 'RETIRED'::character varying, 'DONOR'::character varying, 'REFURBISHED'::character varying])::text[])))
);


--
-- Name: part_instances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.part_instances (
    id uuid NOT NULL,
    barcode character varying(255) NOT NULL,
    exhausted_at timestamp(6) without time zone,
    notes text,
    received_at timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    taken_at timestamp(6) without time zone,
    installed_unit_part_id uuid,
    part_id uuid NOT NULL,
    taken_by uuid,
    tenant_id uuid NOT NULL,
    landed_cost numeric(38,2),
    CONSTRAINT part_instances_status_check CHECK (((status)::text = ANY ((ARRAY['IN_STOCK'::character varying, 'TAKEN'::character varying, 'INSTALLED'::character varying, 'EXHAUSTED'::character varying])::text[])))
);


--
-- Name: part_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.part_type (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    category_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255),
    created_at timestamp without time zone
);


--
-- Name: parts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.parts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    category character varying(255),
    unit text,
    created_at timestamp without time zone DEFAULT now(),
    brand character varying(255),
    expected_lifespan_days integer,
    notes text,
    updated_at timestamp(6) without time zone,
    tenant_id uuid NOT NULL,
    current_stock integer DEFAULT 0 NOT NULL,
    minimum_stock integer DEFAULT 0 NOT NULL,
    reorder_quantity integer,
    category_id uuid,
    part_type_id uuid,
    active boolean DEFAULT true NOT NULL,
    retired boolean DEFAULT false NOT NULL,
    retired_at timestamp without time zone,
    retirement_reason text,
    manual_daily_usage numeric(10,4),
    manual_reorder_point integer
);


--
-- Name: purchase_order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    po_id uuid,
    part_id uuid,
    quantity integer,
    price numeric(38,2)
);


--
-- Name: purchase_order_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order_status_history (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    new_status character varying(255) NOT NULL,
    previous_status character varying(255),
    reason text,
    changed_by uuid,
    purchase_order_id uuid NOT NULL,
    CONSTRAINT purchase_order_status_history_new_status_check CHECK (((new_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ORDERED'::character varying, 'PARTIALLY_RECEIVED'::character varying, 'RECEIVED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT purchase_order_status_history_previous_status_check CHECK (((previous_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ORDERED'::character varying, 'PARTIALLY_RECEIVED'::character varying, 'RECEIVED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: purchase_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_orders (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    supplier_id uuid,
    po_number character varying(255),
    order_date date,
    status character varying(255),
    total_amount numeric(38,2),
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: purchase_request_authorization_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_request_authorization_items (
    id uuid NOT NULL,
    authorized_qty integer,
    max_value numeric(38,2),
    part_id uuid,
    pra_id uuid
);


--
-- Name: purchase_request_authorization_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_request_authorization_status_history (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    new_status character varying(255) CONSTRAINT purchase_request_authorization_status_histo_new_status_not_null NOT NULL,
    previous_status character varying(255),
    reason text,
    changed_by uuid,
    purchase_request_authorization_id uuid CONSTRAINT purchase_request_authorizat_purchase_request_authoriza_not_null NOT NULL,
    CONSTRAINT purchase_request_authorization_status_his_previous_status_check CHECK (((previous_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'PARTIALLY_FULFILLED'::character varying, 'FULFILLED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT purchase_request_authorization_status_history_new_status_check CHECK (((new_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'PARTIALLY_FULFILLED'::character varying, 'FULFILLED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: purchase_request_authorizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_request_authorizations (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    status character varying(255) NOT NULL,
    approved_by uuid,
    tenant_id uuid NOT NULL,
    CONSTRAINT purchase_request_authorizations_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'PARTIALLY_FULFILLED'::character varying, 'FULFILLED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: purchase_request_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_request_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    pr_id uuid,
    part_id uuid,
    quantity integer
);


--
-- Name: purchase_request_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_request_status_history (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    new_status character varying(255) NOT NULL,
    previous_status character varying(255),
    reason text,
    changed_by uuid,
    purchase_request_id uuid NOT NULL,
    CONSTRAINT purchase_request_status_history_new_status_check CHECK (((new_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'ORDERED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT purchase_request_status_history_previous_status_check CHECK (((previous_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'ORDERED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: purchase_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    requested_by uuid,
    status character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    po_id uuid,
    pra_id uuid
);


--
-- Name: realisasi_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.realisasi_items (
    id uuid NOT NULL,
    actual_unit_price numeric(38,2),
    allocated_landed_cost numeric(38,2),
    conversion_factor numeric(38,2),
    purchase_uom character varying(255),
    purchased_qty integer,
    part_id uuid,
    pra_item_id uuid,
    realisasi_id uuid
);


--
-- Name: realisasi_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.realisasi_status_history (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    new_status character varying(255) NOT NULL,
    previous_status character varying(255),
    reason text,
    changed_by uuid,
    realisasi_id uuid NOT NULL,
    CONSTRAINT realisasi_status_history_new_status_check CHECK (((new_status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'FAILED'::character varying, 'SUPERSEDED'::character varying])::text[]))),
    CONSTRAINT realisasi_status_history_previous_status_check CHECK (((previous_status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'FAILED'::character varying, 'SUPERSEDED'::character varying])::text[])))
);


--
-- Name: realisasis; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.realisasis (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    attachments text,
    channel character varying(255),
    external_order_ref character varying(255) NOT NULL,
    insurance numeric(38,2),
    payment_method character varying(255),
    payment_ref character varying(255),
    platform_voucher numeric(38,2),
    purchased_at timestamp(6) without time zone,
    reimbursed_at timestamp(6) without time zone,
    reimbursement_status character varying(255),
    retro_purchase_flag boolean,
    seller_discount numeric(38,2),
    service_fee numeric(38,2),
    shipping numeric(38,2),
    status character varying(255) NOT NULL,
    subtotal numeric(38,2),
    superseded_reason text,
    total_cost numeric(38,2),
    variance_status character varying(255),
    approved_by uuid,
    created_by uuid,
    pra_id uuid NOT NULL,
    reimbursed_to uuid,
    supersedes_id uuid,
    supplier_id uuid,
    tenant_id uuid NOT NULL,
    CONSTRAINT realisasis_payment_method_check CHECK (((payment_method)::text = ANY ((ARRAY['COMPANY_ACCOUNT'::character varying, 'PERSONAL_REIMBURSABLE'::character varying])::text[]))),
    CONSTRAINT realisasis_reimbursement_status_check CHECK (((reimbursement_status)::text = ANY ((ARRAY['NOT_APPLICABLE'::character varying, 'PENDING'::character varying, 'REIMBURSED'::character varying])::text[]))),
    CONSTRAINT realisasis_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'FAILED'::character varying, 'SUPERSEDED'::character varying])::text[]))),
    CONSTRAINT realisasis_variance_status_check CHECK (((variance_status)::text = ANY ((ARRAY['WITHIN_CEILING'::character varying, 'ESCALATED'::character varying])::text[])))
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id integer NOT NULL,
    name character varying(50) NOT NULL
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: service_event_parts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_event_parts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    service_event_id uuid,
    part_id uuid,
    quantity integer,
    cost numeric(12,2)
);


--
-- Name: service_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    airsoft_unit_id uuid,
    technician_id uuid,
    status public.service_status_enum DEFAULT 'OPEN'::public.service_status_enum,
    description text,
    cost numeric(12,2),
    service_date date,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp(6) without time zone,
    action_taken text,
    event_type character varying(255) NOT NULL,
    issue text,
    next_check_date date,
    notes text,
    work_order_id uuid,
    CONSTRAINT service_events_event_type_check CHECK (((event_type)::text = ANY (ARRAY[('MAINTENANCE'::character varying)::text, ('REPAIR'::character varying)::text, ('UPGRADE'::character varying)::text])))
);


--
-- Name: stock_adjustment_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_adjustment_status_history (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    new_status character varying(255) NOT NULL,
    previous_status character varying(255),
    reason text,
    changed_by uuid,
    stock_adjustment_id uuid NOT NULL,
    CONSTRAINT stock_adjustment_status_history_new_status_check CHECK (((new_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT stock_adjustment_status_history_previous_status_check CHECK (((previous_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: stock_adjustments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_adjustments (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    quantity integer NOT NULL,
    reason text,
    status character varying(255) NOT NULL,
    part_id uuid NOT NULL,
    requested_by uuid,
    tenant_id uuid NOT NULL,
    CONSTRAINT stock_adjustments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: stock_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_movements (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    part_id uuid,
    movement_type character varying(255),
    quantity integer NOT NULL,
    reference_type character varying(255),
    reference_id uuid,
    created_at timestamp without time zone DEFAULT now(),
    before_stock integer DEFAULT 0 NOT NULL,
    after_stock integer DEFAULT 0 NOT NULL,
    notes text
);


--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suppliers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    contact character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    tenant_id uuid NOT NULL
);


--
-- Name: tenants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp(6) without time zone,
    code character varying(255) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    parent_id uuid
);


--
-- Name: user_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_profiles (
    user_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    full_name text,
    is_hq boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id uuid NOT NULL,
    role_id integer NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    full_name character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp(6) without time zone,
    role character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    scope character varying(255),
    CONSTRAINT users_scope_check CHECK (((scope)::text = ANY ((ARRAY['SUPERVISOR'::character varying, 'MANAGER'::character varying, 'OWNER'::character varying, 'PRINCIPAL'::character varying])::text[])))
);


--
-- Name: work_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_orders (
    id uuid NOT NULL,
    completed_date date,
    created_at timestamp(6) without time zone,
    description text,
    priority character varying(255),
    status character varying(255),
    target_date date,
    title character varying(255),
    updated_at timestamp(6) without time zone,
    airsoft_unit_id uuid,
    assigned_technician_id uuid,
    tenant_id uuid,
    assigned_at timestamp(6) without time zone,
    completed_at timestamp(6) without time zone,
    started_at timestamp(6) without time zone,
    airsoft_unit_part_id uuid,
    maintenance_schedule_id uuid,
    CONSTRAINT work_orders_priority_check CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT work_orders_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'ASSIGNED'::character varying, 'IN_PROGRESS'::character varying, 'WAITING_PARTS'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: airsoft_models airsoft_models_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airsoft_models
    ADD CONSTRAINT airsoft_models_pkey PRIMARY KEY (id);


--
-- Name: airsoft_unit_parts airsoft_unit_parts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airsoft_unit_parts
    ADD CONSTRAINT airsoft_unit_parts_pkey PRIMARY KEY (id);


--
-- Name: airsoft_units airsoft_units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airsoft_units
    ADD CONSTRAINT airsoft_units_pkey PRIMARY KEY (id);


--
-- Name: airsoft_units airsoft_units_serial_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airsoft_units
    ADD CONSTRAINT airsoft_units_serial_number_key UNIQUE (serial_number);


--
-- Name: barcode_allocations barcode_allocations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.barcode_allocations
    ADD CONSTRAINT barcode_allocations_pkey PRIMARY KEY (id);


--
-- Name: barcode_counters barcode_counters_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.barcode_counters
    ADD CONSTRAINT barcode_counters_pkey PRIMARY KEY (id);


--
-- Name: goods_receipt_items goods_receipt_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT goods_receipt_items_pkey PRIMARY KEY (id);


--
-- Name: goods_receipts goods_receipts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT goods_receipts_pkey PRIMARY KEY (id);


--
-- Name: maintenance_rule maintenance_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_rule
    ADD CONSTRAINT maintenance_rule_pkey PRIMARY KEY (id);


--
-- Name: maintenance_rule maintenance_rule_tenant_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_rule
    ADD CONSTRAINT maintenance_rule_tenant_id_key UNIQUE (tenant_id);


--
-- Name: maintenance_schedules maintenance_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_schedules
    ADD CONSTRAINT maintenance_schedules_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: part_categories part_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_categories
    ADD CONSTRAINT part_categories_pkey PRIMARY KEY (id);


--
-- Name: part_condition_history part_condition_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_condition_history
    ADD CONSTRAINT part_condition_history_pkey PRIMARY KEY (id);


--
-- Name: part_instances part_instances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT part_instances_pkey PRIMARY KEY (id);


--
-- Name: part_type part_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_type
    ADD CONSTRAINT part_type_pkey PRIMARY KEY (id);


--
-- Name: parts parts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parts
    ADD CONSTRAINT parts_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_items purchase_order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT purchase_order_items_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_status_history purchase_order_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_status_history
    ADD CONSTRAINT purchase_order_status_history_pkey PRIMARY KEY (id);


--
-- Name: purchase_orders purchase_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_pkey PRIMARY KEY (id);


--
-- Name: purchase_orders purchase_orders_po_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_po_number_key UNIQUE (po_number);


--
-- Name: purchase_request_authorization_items purchase_request_authorization_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_authorization_items
    ADD CONSTRAINT purchase_request_authorization_items_pkey PRIMARY KEY (id);


--
-- Name: purchase_request_authorization_status_history purchase_request_authorization_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_authorization_status_history
    ADD CONSTRAINT purchase_request_authorization_status_history_pkey PRIMARY KEY (id);


--
-- Name: purchase_request_authorizations purchase_request_authorizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_authorizations
    ADD CONSTRAINT purchase_request_authorizations_pkey PRIMARY KEY (id);


--
-- Name: purchase_request_items purchase_request_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_items
    ADD CONSTRAINT purchase_request_items_pkey PRIMARY KEY (id);


--
-- Name: purchase_request_status_history purchase_request_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_status_history
    ADD CONSTRAINT purchase_request_status_history_pkey PRIMARY KEY (id);


--
-- Name: purchase_requests purchase_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_pkey PRIMARY KEY (id);


--
-- Name: realisasi_items realisasi_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasi_items
    ADD CONSTRAINT realisasi_items_pkey PRIMARY KEY (id);


--
-- Name: realisasi_status_history realisasi_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasi_status_history
    ADD CONSTRAINT realisasi_status_history_pkey PRIMARY KEY (id);


--
-- Name: realisasis realisasis_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT realisasis_pkey PRIMARY KEY (id);


--
-- Name: roles roles_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_name_key UNIQUE (name);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: service_event_parts service_event_parts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_event_parts
    ADD CONSTRAINT service_event_parts_pkey PRIMARY KEY (id);


--
-- Name: service_events service_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_events
    ADD CONSTRAINT service_events_pkey PRIMARY KEY (id);


--
-- Name: stock_adjustment_status_history stock_adjustment_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustment_status_history
    ADD CONSTRAINT stock_adjustment_status_history_pkey PRIMARY KEY (id);


--
-- Name: stock_adjustments stock_adjustments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT stock_adjustments_pkey PRIMARY KEY (id);


--
-- Name: stock_movements stock_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_pkey PRIMARY KEY (id);


--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (id);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: part_instances uk7vaco1nuxw8mit69lrh43x3f0; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT uk7vaco1nuxw8mit69lrh43x3f0 UNIQUE (barcode);


--
-- Name: realisasis ukreeu382n59mscn02aqx7gumv9; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT ukreeu382n59mscn02aqx7gumv9 UNIQUE (external_order_ref);


--
-- Name: tenants uq_tenants_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT uq_tenants_code UNIQUE (code);


--
-- Name: user_profiles user_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_pkey PRIMARY KEY (user_id);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: work_orders work_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT work_orders_pkey PRIMARY KEY (id);



--
-- Name: airsoft_units airsoft_units_model_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airsoft_units
    ADD CONSTRAINT airsoft_units_model_id_fkey FOREIGN KEY (model_id) REFERENCES public.airsoft_models(id);


--
-- Name: airsoft_units airsoft_units_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airsoft_units
    ADD CONSTRAINT airsoft_units_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: parts fk13acitugkoo5g9ss0ngj15t14; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parts
    ADD CONSTRAINT fk13acitugkoo5g9ss0ngj15t14 FOREIGN KEY (category_id) REFERENCES public.part_categories(id);


--
-- Name: realisasis fk13h7g35yes0peocbk4uevo0ix; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fk13h7g35yes0peocbk4uevo0ix FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);


--
-- Name: airsoft_unit_parts fk26981xf284bogtjrssllnapsv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airsoft_unit_parts
    ADD CONSTRAINT fk26981xf284bogtjrssllnapsv FOREIGN KEY (airsoft_unit_id) REFERENCES public.airsoft_units(id);


--
-- Name: goods_receipts fk39swwl7dbtcofspprw5qd5kk6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT fk39swwl7dbtcofspprw5qd5kk6 FOREIGN KEY (realisasi_id) REFERENCES public.realisasis(id);


--
-- Name: stock_adjustment_status_history fk3klt5m29wubne4hru54wnd9yf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustment_status_history
    ADD CONSTRAINT fk3klt5m29wubne4hru54wnd9yf FOREIGN KEY (changed_by) REFERENCES public.users(id);


--
-- Name: purchase_order_status_history fk3n175i6n7wkefq1rlfgmqqcrc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_status_history
    ADD CONSTRAINT fk3n175i6n7wkefq1rlfgmqqcrc FOREIGN KEY (purchase_order_id) REFERENCES public.purchase_orders(id);


--
-- Name: maintenance_schedules fk50pih82e61w6ms8l73o7tyfwr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_schedules
    ADD CONSTRAINT fk50pih82e61w6ms8l73o7tyfwr FOREIGN KEY (airsoft_unit_id) REFERENCES public.airsoft_units(id);


--
-- Name: tenants fk6kkrj1ji0mc4ylqg5upgpi8wy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT fk6kkrj1ji0mc4ylqg5upgpi8wy FOREIGN KEY (parent_id) REFERENCES public.tenants(id);


--
-- Name: parts fk75haw46vt0ryrli40csd4etcm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parts
    ADD CONSTRAINT fk75haw46vt0ryrli40csd4etcm FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: stock_adjustment_status_history fk7gf0dkaox07gn89fvo76qfg8g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustment_status_history
    ADD CONSTRAINT fk7gf0dkaox07gn89fvo76qfg8g FOREIGN KEY (stock_adjustment_id) REFERENCES public.stock_adjustments(id);


--
-- Name: airsoft_unit_parts fk7gx66p2hgu9017gbqso63k0lk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airsoft_unit_parts
    ADD CONSTRAINT fk7gx66p2hgu9017gbqso63k0lk FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: stock_adjustments fk7oc03k8fxf5fnoyyyy9i93tex; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT fk7oc03k8fxf5fnoyyyy9i93tex FOREIGN KEY (requested_by) REFERENCES public.users(id);


--
-- Name: realisasis fk90tep8m7qbon8xcpfu3qxmpvx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fk90tep8m7qbon8xcpfu3qxmpvx FOREIGN KEY (supersedes_id) REFERENCES public.realisasis(id);


--
-- Name: realisasi_status_history fk97vec3ke62q7q55ddcfk2gydo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasi_status_history
    ADD CONSTRAINT fk97vec3ke62q7q55ddcfk2gydo FOREIGN KEY (changed_by) REFERENCES public.users(id);


--
-- Name: purchase_request_authorizations fk9b6mj4msv76dh045d58hibwi6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_authorizations
    ADD CONSTRAINT fk9b6mj4msv76dh045d58hibwi6 FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_request_authorization_items fk9bdtdg9ekxt54m7357rlj15a7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_authorization_items
    ADD CONSTRAINT fk9bdtdg9ekxt54m7357rlj15a7 FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: goods_receipt_items fk9untxautwqxlbcrkedk33lec6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT fk9untxautwqxlbcrkedk33lec6 FOREIGN KEY (realisasi_item_id) REFERENCES public.realisasi_items(id);


--
-- Name: maintenance_rule fk_maintenance_rule_tenants; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_rule
    ADD CONSTRAINT fk_maintenance_rule_tenants FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: part_type fk_part_type_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_type
    ADD CONSTRAINT fk_part_type_category FOREIGN KEY (category_id) REFERENCES public.part_categories(id);


--
-- Name: part_type fk_part_type_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_type
    ADD CONSTRAINT fk_part_type_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: users fk_users_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: work_orders fk_work_order_part; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT fk_work_order_part FOREIGN KEY (airsoft_unit_part_id) REFERENCES public.airsoft_unit_parts(id);


--
-- Name: purchase_requests fka2o5vo90i5q19ema982rh46vs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT fka2o5vo90i5q19ema982rh46vs FOREIGN KEY (po_id) REFERENCES public.purchase_orders(id);


--
-- Name: realisasi_status_history fka46pcn8l8kxsdoybttem9csoy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasi_status_history
    ADD CONSTRAINT fka46pcn8l8kxsdoybttem9csoy FOREIGN KEY (realisasi_id) REFERENCES public.realisasis(id);


--
-- Name: purchase_request_authorization_status_history fkar6extq5tpb347teftdydpjc2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_authorization_status_history
    ADD CONSTRAINT fkar6extq5tpb347teftdydpjc2 FOREIGN KEY (purchase_request_authorization_id) REFERENCES public.purchase_request_authorizations(id);


--
-- Name: realisasi_items fkb49toxl6bxuwwixwu2v4tij9s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasi_items
    ADD CONSTRAINT fkb49toxl6bxuwwixwu2v4tij9s FOREIGN KEY (pra_item_id) REFERENCES public.purchase_request_authorization_items(id);


--
-- Name: purchase_order_status_history fkcfnwnc5qu6jnpd19v1txdems9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_status_history
    ADD CONSTRAINT fkcfnwnc5qu6jnpd19v1txdems9 FOREIGN KEY (changed_by) REFERENCES public.users(id);


--
-- Name: parts fkcn1p10lsqimdl17dgmubk1m0w; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parts
    ADD CONSTRAINT fkcn1p10lsqimdl17dgmubk1m0w FOREIGN KEY (part_type_id) REFERENCES public.part_type(id);


--
-- Name: work_orders fkd9scw824lbjgyoeqro0kub419; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT fkd9scw824lbjgyoeqro0kub419 FOREIGN KEY (maintenance_schedule_id) REFERENCES public.maintenance_schedules(id);


--
-- Name: stock_adjustments fkdi22c1aybptbuh4yp02nro7e5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT fkdi22c1aybptbuh4yp02nro7e5 FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_request_status_history fke0lhcbetncj98ul5kv9w506yl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_status_history
    ADD CONSTRAINT fke0lhcbetncj98ul5kv9w506yl FOREIGN KEY (changed_by) REFERENCES public.users(id);


--
-- Name: stock_adjustments fkeoxthwcbjvh6w4uohtjnt5bb0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT fkeoxthwcbjvh6w4uohtjnt5bb0 FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: part_instances fkfsb0q829p1x6k451fqcchy6k4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT fkfsb0q829p1x6k451fqcchy6k4 FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: work_orders fkg1ne1vjc9mn32q0n4bippl31h; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT fkg1ne1vjc9mn32q0n4bippl31h FOREIGN KEY (airsoft_unit_id) REFERENCES public.airsoft_units(id);


--
-- Name: part_condition_history fkgcxfikqp60rq9phyuqnnij5mk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_condition_history
    ADD CONSTRAINT fkgcxfikqp60rq9phyuqnnij5mk FOREIGN KEY (airsoft_unit_part_id) REFERENCES public.airsoft_unit_parts(id);


--
-- Name: part_instances fkgn0do9i9ys7thu7p28h6skbf8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT fkgn0do9i9ys7thu7p28h6skbf8 FOREIGN KEY (taken_by) REFERENCES public.users(id);


--
-- Name: realisasis fkgsl5bis106cxc284xb8o72a2f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fkgsl5bis106cxc284xb8o72a2f FOREIGN KEY (pra_id) REFERENCES public.purchase_request_authorizations(id);


--
-- Name: work_orders fkh0d5ub1jwbp08sn44daqvhlky; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT fkh0d5ub1jwbp08sn44daqvhlky FOREIGN KEY (assigned_technician_id) REFERENCES public.users(id);


--
-- Name: suppliers fkh74geoqfe4ber25w1u7b1w70n; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT fkh74geoqfe4ber25w1u7b1w70n FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: barcode_allocations fkhjlj3o9w8i7ny5oeoevfi58ip; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.barcode_allocations
    ADD CONSTRAINT fkhjlj3o9w8i7ny5oeoevfi58ip FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_request_status_history fki9r4p6sm35b89criodrm0ehuw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_status_history
    ADD CONSTRAINT fki9r4p6sm35b89criodrm0ehuw FOREIGN KEY (purchase_request_id) REFERENCES public.purchase_requests(id);


--
-- Name: purchase_request_authorization_items fkixinp0te1c1hr9m7ymvh7hfrc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_authorization_items
    ADD CONSTRAINT fkixinp0te1c1hr9m7ymvh7hfrc FOREIGN KEY (pra_id) REFERENCES public.purchase_request_authorizations(id);


--
-- Name: barcode_allocations fkjpj0et4af4khjequcm7hppqpg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.barcode_allocations
    ADD CONSTRAINT fkjpj0et4af4khjequcm7hppqpg FOREIGN KEY (issued_to) REFERENCES public.users(id);


--
-- Name: realisasis fkmjggvsdgeto5rrnj55clxe8o6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fkmjggvsdgeto5rrnj55clxe8o6 FOREIGN KEY (approved_by) REFERENCES public.users(id);


--
-- Name: part_categories fkmsnbynj1f3nml6k5dxw19892r; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_categories
    ADD CONSTRAINT fkmsnbynj1f3nml6k5dxw19892r FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: realisasis fkmuvafrqnmfkg3kkh626kylofu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fkmuvafrqnmfkg3kkh626kylofu FOREIGN KEY (reimbursed_to) REFERENCES public.users(id);


--
-- Name: work_orders fko0w91m27jrono6u6lfrbnhgm4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT fko0w91m27jrono6u6lfrbnhgm4 FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_request_authorizations fko6vcr55nanfvvs51141mthare; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_authorizations
    ADD CONSTRAINT fko6vcr55nanfvvs51141mthare FOREIGN KEY (approved_by) REFERENCES public.users(id);


--
-- Name: realisasi_items fkppkdnx0934f6d824s3gvgpswa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasi_items
    ADD CONSTRAINT fkppkdnx0934f6d824s3gvgpswa FOREIGN KEY (realisasi_id) REFERENCES public.realisasis(id);


--
-- Name: realisasi_items fkpxydu09g3f90kyi0r17ou4nwe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasi_items
    ADD CONSTRAINT fkpxydu09g3f90kyi0r17ou4nwe FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: part_instances fkq5ptqyjx50eyfav7xju869j97; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT fkq5ptqyjx50eyfav7xju869j97 FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: purchase_request_authorization_status_history fkqdfgovp1qjcuumkglcgavrq2m; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_authorization_status_history
    ADD CONSTRAINT fkqdfgovp1qjcuumkglcgavrq2m FOREIGN KEY (changed_by) REFERENCES public.users(id);


--
-- Name: purchase_requests fkqdvlxlcyllarlkpynmqkc128k; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT fkqdvlxlcyllarlkpynmqkc128k FOREIGN KEY (pra_id) REFERENCES public.purchase_request_authorizations(id);


--
-- Name: notifications fkqjegl56prrnbxmjq7q8k5mav; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fkqjegl56prrnbxmjq7q8k5mav FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: part_instances fkrox47tibsqlfyymf40fqr3e6v; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT fkrox47tibsqlfyymf40fqr3e6v FOREIGN KEY (installed_unit_part_id) REFERENCES public.airsoft_unit_parts(id);


--
-- Name: realisasis fks0wolh6gyvs648o9c0uixie1f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fks0wolh6gyvs648o9c0uixie1f FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: airsoft_unit_parts fksb56o3tgxtp901gninci2wh4n; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airsoft_unit_parts
    ADD CONSTRAINT fksb56o3tgxtp901gninci2wh4n FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: realisasis fksbe7igucp4tx4ybgmlsfos3vj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fksbe7igucp4tx4ybgmlsfos3vj FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: service_events fksy234hpbjpgwi6rfhmmi2utde; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_events
    ADD CONSTRAINT fksy234hpbjpgwi6rfhmmi2utde FOREIGN KEY (work_order_id) REFERENCES public.work_orders(id);


--
-- Name: notifications fkt8ievafor22iuvg5sd4p7lhbk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fkt8ievafor22iuvg5sd4p7lhbk FOREIGN KEY (recipient_user_id) REFERENCES public.users(id);


--
-- Name: maintenance_schedules fktb243ksmtva1b5qfpca8lbplk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_schedules
    ADD CONSTRAINT fktb243ksmtva1b5qfpca8lbplk FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: goods_receipt_items goods_receipt_items_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT goods_receipt_items_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: goods_receipt_items goods_receipt_items_receipt_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT goods_receipt_items_receipt_id_fkey FOREIGN KEY (receipt_id) REFERENCES public.goods_receipts(id) ON DELETE CASCADE;


--
-- Name: goods_receipts goods_receipts_po_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT goods_receipts_po_id_fkey FOREIGN KEY (po_id) REFERENCES public.purchase_orders(id);


--
-- Name: goods_receipts goods_receipts_received_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT goods_receipts_received_by_fkey FOREIGN KEY (received_by) REFERENCES public.users(id);


--
-- Name: goods_receipts goods_receipts_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT goods_receipts_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_order_items purchase_order_items_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT purchase_order_items_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: purchase_order_items purchase_order_items_po_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT purchase_order_items_po_id_fkey FOREIGN KEY (po_id) REFERENCES public.purchase_orders(id) ON DELETE CASCADE;


--
-- Name: purchase_orders purchase_orders_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);


--
-- Name: purchase_orders purchase_orders_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_request_items purchase_request_items_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_items
    ADD CONSTRAINT purchase_request_items_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: purchase_request_items purchase_request_items_pr_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_items
    ADD CONSTRAINT purchase_request_items_pr_id_fkey FOREIGN KEY (pr_id) REFERENCES public.purchase_requests(id) ON DELETE CASCADE;


--
-- Name: purchase_requests purchase_requests_requested_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_requested_by_fkey FOREIGN KEY (requested_by) REFERENCES public.users(id);


--
-- Name: purchase_requests purchase_requests_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: service_event_parts service_event_parts_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_event_parts
    ADD CONSTRAINT service_event_parts_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: service_event_parts service_event_parts_service_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_event_parts
    ADD CONSTRAINT service_event_parts_service_event_id_fkey FOREIGN KEY (service_event_id) REFERENCES public.service_events(id) ON DELETE CASCADE;


--
-- Name: service_events service_events_airsoft_unit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_events
    ADD CONSTRAINT service_events_airsoft_unit_id_fkey FOREIGN KEY (airsoft_unit_id) REFERENCES public.airsoft_units(id);


--
-- Name: service_events service_events_technician_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_events
    ADD CONSTRAINT service_events_technician_id_fkey FOREIGN KEY (technician_id) REFERENCES public.users(id);


--
-- Name: service_events service_events_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_events
    ADD CONSTRAINT service_events_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: stock_movements stock_movements_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: stock_movements stock_movements_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: user_profiles user_profiles_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: user_profiles user_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_roles user_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

