--
-- PostgreSQL database dump
--

\restrict pyqr5jtZS3M3KW6SmtH13MBycGr6C4r3aWDMwxXdtUyolxPQxgVhzhp6RR3mRK6

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
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: movement_type_enum; Type: TYPE; Schema: public; Owner: postgres
--

CREATE TYPE public.movement_type_enum AS ENUM (
    'IN',
    'OUT',
    'TRANSFER'
);


ALTER TYPE public.movement_type_enum OWNER TO postgres;

--
-- Name: reference_type_enum; Type: TYPE; Schema: public; Owner: postgres
--

CREATE TYPE public.reference_type_enum AS ENUM (
    'SERVICE',
    'PURCHASE',
    'TRANSFER'
);


ALTER TYPE public.reference_type_enum OWNER TO postgres;

--
-- Name: service_status_enum; Type: TYPE; Schema: public; Owner: postgres
--

CREATE TYPE public.service_status_enum AS ENUM (
    'OPEN',
    'IN_PROGRESS',
    'DONE'
);


ALTER TYPE public.service_status_enum OWNER TO postgres;

--
-- Name: unit_status_enum; Type: TYPE; Schema: public; Owner: postgres
--

CREATE TYPE public.unit_status_enum AS ENUM (
    'ACTIVE',
    'MAINTENANCE',
    'BROKEN'
);


ALTER TYPE public.unit_status_enum OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: airsoft_models; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.airsoft_models (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    brand text,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.airsoft_models OWNER TO postgres;

--
-- Name: airsoft_unit_parts; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.airsoft_unit_parts OWNER TO postgres;

--
-- Name: airsoft_units; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.airsoft_units OWNER TO postgres;

--
-- Name: barcode_allocations; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.barcode_allocations OWNER TO postgres;

--
-- Name: barcode_counters; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.barcode_counters (
    id bigint NOT NULL,
    last_value bigint NOT NULL
);


ALTER TABLE public.barcode_counters OWNER TO postgres;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO postgres;

--
-- Name: goods_receipt_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.goods_receipt_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    receipt_id uuid,
    part_id uuid,
    quantity integer,
    realisasi_item_id uuid
);


ALTER TABLE public.goods_receipt_items OWNER TO postgres;

--
-- Name: goods_receipts; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.goods_receipts OWNER TO postgres;

--
-- Name: maintenance_rule; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.maintenance_rule OWNER TO postgres;

--
-- Name: maintenance_schedules; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.maintenance_schedules OWNER TO postgres;

--
-- Name: notifications; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.notifications OWNER TO postgres;

--
-- Name: part_categories; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.part_categories (
    id uuid NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    is_consumable boolean
);


ALTER TABLE public.part_categories OWNER TO postgres;

--
-- Name: part_condition_history; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.part_condition_history OWNER TO postgres;

--
-- Name: part_instances; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.part_instances OWNER TO postgres;

--
-- Name: part_type; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.part_type (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    category_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255),
    created_at timestamp without time zone
);


ALTER TABLE public.part_type OWNER TO postgres;

--
-- Name: parts; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.parts OWNER TO postgres;

--
-- Name: purchase_order_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.purchase_order_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    po_id uuid,
    part_id uuid,
    quantity integer,
    price numeric(38,2)
);


ALTER TABLE public.purchase_order_items OWNER TO postgres;

--
-- Name: purchase_order_status_history; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.purchase_order_status_history OWNER TO postgres;

--
-- Name: purchase_orders; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.purchase_orders OWNER TO postgres;

--
-- Name: purchase_request_authorization_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.purchase_request_authorization_items (
    id uuid NOT NULL,
    authorized_qty integer,
    max_value numeric(38,2),
    part_id uuid,
    pra_id uuid
);


ALTER TABLE public.purchase_request_authorization_items OWNER TO postgres;

--
-- Name: purchase_request_authorization_status_history; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.purchase_request_authorization_status_history OWNER TO postgres;

--
-- Name: purchase_request_authorizations; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.purchase_request_authorizations OWNER TO postgres;

--
-- Name: purchase_request_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.purchase_request_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    pr_id uuid,
    part_id uuid,
    quantity integer
);


ALTER TABLE public.purchase_request_items OWNER TO postgres;

--
-- Name: purchase_request_status_history; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.purchase_request_status_history OWNER TO postgres;

--
-- Name: purchase_requests; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.purchase_requests OWNER TO postgres;

--
-- Name: realisasi_items; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.realisasi_items OWNER TO postgres;

--
-- Name: realisasi_status_history; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.realisasi_status_history OWNER TO postgres;

--
-- Name: realisasis; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.realisasis OWNER TO postgres;

--
-- Name: roles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.roles (
    id integer NOT NULL,
    name character varying(50) NOT NULL
);


ALTER TABLE public.roles OWNER TO postgres;

--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.roles_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.roles_id_seq OWNER TO postgres;

--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: service_event_parts; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.service_event_parts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    service_event_id uuid,
    part_id uuid,
    quantity integer,
    cost numeric(12,2)
);


ALTER TABLE public.service_event_parts OWNER TO postgres;

--
-- Name: service_events; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.service_events OWNER TO postgres;

--
-- Name: stock_adjustment_status_history; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.stock_adjustment_status_history OWNER TO postgres;

--
-- Name: stock_adjustments; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.stock_adjustments OWNER TO postgres;

--
-- Name: stock_movements; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.stock_movements OWNER TO postgres;

--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.suppliers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    contact character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    tenant_id uuid NOT NULL
);


ALTER TABLE public.suppliers OWNER TO postgres;

--
-- Name: tenants; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.tenants OWNER TO postgres;

--
-- Name: user_profiles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_profiles (
    user_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    full_name text,
    is_hq boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.user_profiles OWNER TO postgres;

--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_roles (
    user_id uuid NOT NULL,
    role_id integer NOT NULL
);


ALTER TABLE public.user_roles OWNER TO postgres;

--
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.users OWNER TO postgres;

--
-- Name: work_orders; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.work_orders OWNER TO postgres;

--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Data for Name: airsoft_models; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.airsoft_models (id, name, brand, created_at) FROM stdin;
\.


--
-- Data for Name: airsoft_unit_parts; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.airsoft_unit_parts (id, created_at, installed_date, notes, removed_date, status, updated_at, airsoft_unit_id, part_id, tenant_id, condition, technician_assessment) FROM stdin;
deefe985-bbd6-4bed-80ed-f7a963239e28	2026-05-16 19:50:36.576292	2026-05-16	Initial installation	2026-05-20	REMOVED	2026-05-20 16:07:54.795163	30273c0e-40b9-4cbe-af69-0943fa3e70ec	6da6cf1c-a8d0-40be-9d86-c5edec443670	a720623c-28d9-4d6b-b06b-059397718d18	WORN	HOP up rubber worn out after heavy milsim usage
9d9ee04e-d84a-4e85-99a6-26137dc5fa81	2026-05-20 16:07:54.827231	2026-05-20	Replaced during scheduled maintenance	\N	INSTALLED	2026-05-28 20:55:39.838441	30273c0e-40b9-4cbe-af69-0943fa3e70ec	691ffd3c-ba81-41fa-ba3e-705f00512c2e	a720623c-28d9-4d6b-b06b-059397718d18	WORN	Still usable but replacement recommended soon
66b79322-2b0a-4b9c-842a-4a60ecd23547	2026-05-16 19:50:08.762209	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	42af7a38-00f7-4803-987d-b593d8eed52e	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
57714a20-b26c-446d-9531-9f775f8742e8	2026-05-16 19:51:37.912448	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	8a2d4326-c54e-4d9d-bff0-7423d9bf3597	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
df35d860-ee67-47cb-9c8c-797ac2a0fd97	2026-06-15 20:31:41.132856	2026-06-15	TEST installation	\N	INSTALLED	\N	2d994854-913e-4dcf-b836-f03c846a86aa	24ba1d61-9bd6-47c0-96f9-fe24f92a2b9b	a720623c-28d9-4d6b-b06b-059397718d18	\N	\N
fb60e2f0-1c34-48e5-a3b5-3c5d8667bcab	2026-06-18 15:53:27.649851	2026-06-18	Installed replacement cylinder head	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	9f32205b-f64f-443c-b15f-8388614d387f	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
d92b4a2f-f53f-4967-b668-1be4bbaf1865	2026-05-16 19:52:44.809814	2026-05-16	Initial installation	2026-06-18	REMOVED	2026-06-18 15:53:27.666088	30273c0e-40b9-4cbe-af69-0943fa3e70ec	9f32205b-f64f-443c-b15f-8388614d387f	a720623c-28d9-4d6b-b06b-059397718d18	FAILED	Part unsafe for operation
632b55cf-5099-4216-b489-c50768003e1b	2026-05-16 19:54:42.787971	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	80517433-57e1-4b4f-8dc1-218232e1d807	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
b54f7a58-6517-4992-8c87-415f158a6999	2026-05-16 19:55:01.720935	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	567330fb-5b64-4d3c-b818-a4819e541204	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
8959e2de-04e6-4443-846f-a140aa281a2d	2026-05-16 19:55:22.822111	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	87b10bd9-c598-4a93-9ba3-0800b72d1f77	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
434dc31a-d1de-4ab6-ab31-7fc18b70abde	2026-05-16 19:55:43.14647	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	9c7b633b-5918-41e8-a29b-a7131129fad2	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
cd78ee2a-10e0-4ca6-af61-004e230bffd0	2026-05-16 19:56:08.553833	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	9adfb219-3f23-4377-bddb-844d54977fe7	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
8309a148-6e0d-4464-bd61-659d7b9bc4eb	2026-05-16 19:56:27.453932	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	a17282cf-e43d-4f5e-ae99-a3cda6a0c832	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
c9d7529f-f8cd-4977-ac6d-93490b0bff70	2026-05-16 19:56:50.378592	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	60c1fb01-765a-442e-b315-4ceeb2e0b777	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
a47e5c57-9052-41cb-8c84-c777d3cdff1f	2026-05-16 19:57:10.71844	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	f6b11de1-df0d-4d08-a29d-bb3585d24da0	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
36b184c9-7829-41b8-929b-bfc1336480e2	2026-05-16 19:57:30.635841	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	edf73423-d3de-4e78-be28-2da4dac6c2ea	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
bcd813f5-f200-4abd-9c22-e02449114f56	2026-05-16 19:57:52.086235	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	ba950c28-9454-4f64-9797-62cbe560009c	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
2efd6043-b030-41de-8399-88c31ba3aac9	2026-05-16 19:58:09.093338	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	a5da2586-ced7-4017-a1ad-ac561e9efe0e	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
5e9f527e-0fff-42d3-a765-04b8ad902f8c	2026-07-02 12:07:14.773377	2026-07-02	Change spareparts	\N	INSTALLED	\N	2d994854-913e-4dcf-b836-f03c846a86aa	8a0f9d15-2674-4470-9384-e1f8694af039	a720623c-28d9-4d6b-b06b-059397718d18	\N	\N
106d05ae-6740-4bb1-9113-f547f978fe6f	2026-05-16 19:53:40.384579	2026-05-16	Initial installation	\N	INSTALLED	2026-06-03 11:22:29.529411	30273c0e-40b9-4cbe-af69-0943fa3e70ec	f8b74d56-f200-4000-b7b2-54758460d3b2	a720623c-28d9-4d6b-b06b-059397718d18	FAILED	Part is failing
63ba8324-07dc-4116-aed8-efa55fcd9108	2026-05-16 19:52:21.665569	2026-05-16	Initial installation	\N	INSTALLED	2026-06-04 15:57:55.670494	30273c0e-40b9-4cbe-af69-0943fa3e70ec	30c816b0-6944-4652-a127-a72a8341b52f	a720623c-28d9-4d6b-b06b-059397718d18	FAILED	Unsafe for operation
69afa710-cd63-40a5-95f3-49c999dbdd5b	2026-06-10 14:20:15.946224	2026-06-10	Installed upgraded replacement shell	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	8f56faea-f30f-4458-9bbb-d623d518665c	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
9b613960-2711-48d0-859c-73ca364fe4c4	2026-05-16 19:51:59.816242	2026-05-16	Initial installation	2026-06-10	REMOVED	2026-06-10 14:20:15.968219	30273c0e-40b9-4cbe-af69-0943fa3e70ec	8f56faea-f30f-4458-9bbb-d623d518665c	a720623c-28d9-4d6b-b06b-059397718d18	FAILED	Part unsafe for operation
2e37f8ac-4fb8-40a3-8f41-df3064c9076f	2026-07-03 00:20:00.57103	2026-07-03	Installed replacement Piston Head with the flat type. Most of the Mushroom type had that spacer problem	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	8a0f9d15-2674-4470-9384-e1f8694af039	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
cd5e9f20-d66e-4b12-9d3d-88f5cb635283	2026-05-16 19:54:23.718032	2026-05-16	Initial installation	2026-07-03	REMOVED	2026-07-03 00:20:00.59629	30273c0e-40b9-4cbe-af69-0943fa3e70ec	578402c0-463a-4716-9368-69b324c3eb9f	a720623c-28d9-4d6b-b06b-059397718d18	FAILED	Part unsafe for operation
6ff87d93-acaa-4d26-a2f7-97a50af8e0b1	2026-05-16 19:49:18.911062	2026-05-16	Initial installation	\N	INSTALLED	\N	30273c0e-40b9-4cbe-af69-0943fa3e70ec	1d1f6fdf-43cb-4fd6-bc2e-5a9d8991100c	a720623c-28d9-4d6b-b06b-059397718d18	GOOD	\N
\.


--
-- Data for Name: airsoft_units; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.airsoft_units (id, tenant_id, model_id, serial_number, status, purchase_date, created_at, updated_at, brand, model, name, notes, type, health_score, operational_severity) FROM stdin;
30273c0e-40b9-4cbe-af69-0943fa3e70ec	a720623c-28d9-4d6b-b06b-059397718d18	\N	JBR1209C	MAINTENANCE	2026-05-07	2026-05-13 18:37:46.755383	2026-07-03 00:20:00.664406	King Arms	AK 47 classic	AK 47 AEG	Primary rental rifle	AEG	83	CRITICAL
2d994854-913e-4dcf-b836-f03c846a86aa	a720623c-28d9-4d6b-b06b-059397718d18	\N	JBR9999	ACTIVE	2026-06-15	2026-06-15 20:28:45.13544	2026-06-15 20:28:45.13544	King Arms	AK TESTING	AK TESTING	TESTING DATA	AEG	100	\N
\.


--
-- Data for Name: barcode_allocations; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.barcode_allocations (id, created_at, updated_at, device_id, pad_width, prefix, range_end, range_start, issued_to, tenant_id) FROM stdin;
882e7ffe-dbfc-4c85-bef6-557b2dd23ee3	2026-07-27 14:51:53.8656	2026-07-27 14:51:53.8656	device-A	10	OFF-	500	1	8118bfca-0404-44e0-984b-36653ec6557c	a720623c-28d9-4d6b-b06b-059397718d18
0f4bd9c0-102b-460c-b126-b407f6adbef7	2026-07-27 14:51:54.251291	2026-07-27 14:51:54.251291	device-B	10	OFF-	520	501	8118bfca-0404-44e0-984b-36653ec6557c	a720623c-28d9-4d6b-b06b-059397718d18
8ee9a235-519b-4149-b80d-a2ffef4f1c12	2026-07-28 12:08:18.015205	2026-07-28 12:08:18.015205	concurrent-device-1	10	OFF-	620	521	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
19c6d752-00c8-4526-829c-fcf0090bb191	2026-07-28 12:08:18.041615	2026-07-28 12:08:18.041615	concurrent-device-2	10	OFF-	720	621	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
dfcf21a3-ec08-49c4-acbf-284d6be60158	2026-07-28 12:08:18.0577	2026-07-28 12:08:18.0577	concurrent-device-3	10	OFF-	820	721	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
ad47158e-24a4-4e4d-88f4-b4020f0428bc	2026-07-28 12:08:18.113169	2026-07-28 12:08:18.113169	concurrent-device-4	10	OFF-	920	821	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
ee03c0d6-3343-42d3-8503-216e92948131	2026-07-28 12:08:18.16292	2026-07-28 12:08:18.16292	concurrent-device-5	10	OFF-	1020	921	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
af6f4fda-f1eb-4593-bc23-6a16031c5549	2026-07-28 12:08:18.220455	2026-07-28 12:08:18.220455	concurrent-device-6	10	OFF-	1120	1021	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
60fc39cd-1d40-4179-ba6c-d6dba3e33908	2026-07-28 12:08:18.275369	2026-07-28 12:08:18.275369	concurrent-device-7	10	OFF-	1220	1121	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
dbe78a04-54a2-4504-a47b-4adb095300ce	2026-07-28 12:08:18.331509	2026-07-28 12:08:18.331509	concurrent-device-8	10	OFF-	1320	1221	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
38b6df1e-2bfb-49a9-90a4-8c84364c693b	2026-07-28 12:08:18.39094	2026-07-28 12:08:18.39094	concurrent-device-9	10	OFF-	1420	1321	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
0443ddff-757d-46ff-8ff7-9efaf1990c4d	2026-07-28 12:08:18.443303	2026-07-28 12:08:18.443303	concurrent-device-10	10	OFF-	1520	1421	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
\.


--
-- Data for Name: barcode_counters; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.barcode_counters (id, last_value) FROM stdin;
1	1520
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	<< Flyway Baseline >>	BASELINE	<< Flyway Baseline >>	\N	postgres	2026-08-11 01:07:18.146439	0	t
\.


--
-- Data for Name: goods_receipt_items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.goods_receipt_items (id, receipt_id, part_id, quantity, realisasi_item_id) FROM stdin;
c6a3e69c-e9f9-4ea6-a4b1-deec746508b0	8cce1259-aca4-4f2e-afde-6953f756626c	60c1fb01-765a-442e-b315-4ceeb2e0b777	10	\N
4a3d8341-e146-42db-8908-a4d4e12c79a1	81a85975-08cd-4efa-befd-611a2d7ea876	60c1fb01-765a-442e-b315-4ceeb2e0b777	3	251fce59-19f7-471d-8963-88681cbd03dd
f3f5f0eb-d500-4b71-82a4-59c8b3839f08	4048cc8b-5505-4d53-9598-02c05b889736	60c1fb01-765a-442e-b315-4ceeb2e0b777	2	413edbbd-a60f-4e4a-9b33-f8f901b93d7f
d77bd558-deb5-4485-bd4d-2995ba8b82af	05cd06f2-7172-4860-a496-d1224fe77b6a	60c1fb01-765a-442e-b315-4ceeb2e0b777	1	ae89f761-161d-4cce-9e8a-65674076ab5d
ec4b3e62-59cf-4017-85ac-60fe8c742ab2	17b09ecd-30c1-44c4-a864-5cbddbf9e722	42af7a38-00f7-4803-987d-b593d8eed52e	6	80208857-7dcb-4bab-86f9-4f96928ab961
57eb5f85-8347-43a8-8988-f6414535ff35	470a76f2-965e-42ef-b6fa-739f46debeda	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	6	fdbd7b90-8d84-4e71-8fee-ef7ad72997e8
\.


--
-- Data for Name: goods_receipts; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.goods_receipts (id, tenant_id, po_id, received_by, received_date, notes, realisasi_id) FROM stdin;
8cce1259-aca4-4f2e-afde-6953f756626c	a720623c-28d9-4d6b-b06b-059397718d18	6e3ed986-6464-4df5-8a34-54cf4f61c81f	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	2026-07-22 17:13:28.873306	Delivered in good condition	\N
81a85975-08cd-4efa-befd-611a2d7ea876	a720623c-28d9-4d6b-b06b-059397718d18	\N	d0a4f811-81ce-4342-9366-0245635b41ab	2026-07-27 16:45:08.829458	\N	5247421a-8533-4ee3-9f12-368603aa4d92
4048cc8b-5505-4d53-9598-02c05b889736	a720623c-28d9-4d6b-b06b-059397718d18	\N	d0a4f811-81ce-4342-9366-0245635b41ab	2026-07-28 12:30:39.090468	\N	6d617d66-18ca-4554-82b3-27f326b3d85b
05cd06f2-7172-4860-a496-d1224fe77b6a	a720623c-28d9-4d6b-b06b-059397718d18	\N	d0a4f811-81ce-4342-9366-0245635b41ab	2026-07-28 13:48:01.624935	\N	13974068-5861-4ef6-80d3-22f3c794e8fe
17b09ecd-30c1-44c4-a864-5cbddbf9e722	a720623c-28d9-4d6b-b06b-059397718d18	\N	f214a3fd-8a02-4999-b8c1-f77ae229479f	2026-07-28 15:46:44.283306	\N	98a97fcb-68da-40a8-aff2-248890e1a91a
470a76f2-965e-42ef-b6fa-739f46debeda	a720623c-28d9-4d6b-b06b-059397718d18	\N	f61772db-2411-4c27-8424-00eb883ad1c5	2026-07-29 03:14:38.750172	\N	3f247932-6b0a-4344-a242-44f39e2696bf
\.


--
-- Data for Name: maintenance_rule; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.maintenance_rule (id, tenant_id, health_threshold, max_worn_parts, max_refurbished_parts, max_removed_parts, maintenance_interval_days, low_stock_multiplier, part_end_of_life_warning_days, minimum_health_for_operation, auto_create_work_order, created_at, updated_at, burn_rate_min_events, burn_rate_min_observation_days, burn_rate_window_days, days_of_cover_target) FROM stdin;
978df87c-0282-4b8d-ab69-a8f42411597a	a720623c-28d9-4d6b-b06b-059397718d18	50	2	3	5	90	1.00	30	60	t	2026-07-06 19:23:20.177555	2026-07-06 19:23:20.177555	\N	\N	\N	\N
\.


--
-- Data for Name: maintenance_schedules; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.maintenance_schedules (id, active, auto_generate_work_order, created_at, description, interval_days, last_maintenance_date, next_due_date, title, updated_at, airsoft_unit_id, tenant_id) FROM stdin;
579227cf-47bf-4fab-bd7b-7b3e956eb25c	t	t	2026-06-24 16:11:42.539069	Routine inspection of gearbox and compression system	30	2026-06-26	2026-07-26	Monthly Inspection	2026-06-26 14:52:21.298089	30273c0e-40b9-4cbe-af69-0943fa3e70ec	a720623c-28d9-4d6b-b06b-059397718d18
\.


--
-- Data for Name: notifications; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.notifications (id, created_at, updated_at, message, read, read_at, reference_id, reference_type, title, type, recipient_user_id, tenant_id) FROM stdin;
13c78b42-c448-41e2-a338-06c0e1b5ae2b	2026-07-27 16:38:07.548777	2026-07-27 16:38:07.548777	New purchase request submitted by Field PIC at Blitz Tactical	f	\N	77819b5e-68e4-4f37-bc7f-307275246778	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	a720623c-28d9-4d6b-b06b-059397718d18
7426bd55-b59e-40f2-b284-b32ebe565d29	2026-07-27 16:38:07.552788	2026-07-27 16:38:07.552788	New purchase request submitted by Field PIC at Blitz Tactical	f	\N	77819b5e-68e4-4f37-bc7f-307275246778	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
b55a500e-265a-418b-8cee-e11c48115e9b	2026-07-27 16:38:08.256851	2026-07-27 16:38:08.256851	New purchase request submitted by Field PIC at Blitz Tactical	f	\N	2d23c808-971f-4666-b662-b9e0431b937f	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	a720623c-28d9-4d6b-b06b-059397718d18
88f85458-3198-4c69-80ec-d32b8ad3797d	2026-07-27 16:38:08.256851	2026-07-27 16:38:08.256851	New purchase request submitted by Field PIC at Blitz Tactical	f	\N	2d23c808-971f-4666-b662-b9e0431b937f	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
1222b8e1-10dc-4810-abd6-3c132e026943	2026-07-27 16:38:08.827292	2026-07-27 16:38:08.827292	New purchase request submitted by Field PIC at Blitz Tactical	f	\N	e48a3aa2-4435-44ea-918d-45f87b288df9	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	a720623c-28d9-4d6b-b06b-059397718d18
aa8f1f5f-9c0b-45aa-8e0e-3e7bb88de466	2026-07-27 16:38:08.827292	2026-07-27 16:38:08.827292	New purchase request submitted by Field PIC at Blitz Tactical	f	\N	e48a3aa2-4435-44ea-918d-45f87b288df9	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
4d79ed3a-ceb5-43c9-889b-5c589ce4716f	2026-07-27 16:44:02.513529	2026-07-27 16:44:02.513529	Realisasi A2-ESCALATED exceeds its PRA ceiling and needs approval	f	\N	00b03b33-3eb4-41b2-9cf4-a26ec9fdbe88	REALISASI	Realisasi Pembelian Exceeds Authorization Ceiling	REALISASI_ESCALATED	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	a720623c-28d9-4d6b-b06b-059397718d18
be579925-8758-4302-b2e2-98e05dd7e4e4	2026-07-27 16:44:02.514532	2026-07-27 16:44:02.514532	Realisasi A2-ESCALATED exceeds its PRA ceiling and needs approval	f	\N	00b03b33-3eb4-41b2-9cf4-a26ec9fdbe88	REALISASI	Realisasi Pembelian Exceeds Authorization Ceiling	REALISASI_ESCALATED	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
110f78ca-a018-444b-ab3e-a28a9747ac5d	2026-07-27 16:44:46.638197	2026-07-27 16:44:46.638197	Realisasi B2-WILL-FAIL exceeds its PRA ceiling and needs approval	f	\N	9aa060e9-7da2-4b7c-bcf9-727829ae4702	REALISASI	Realisasi Pembelian Exceeds Authorization Ceiling	REALISASI_ESCALATED	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	a720623c-28d9-4d6b-b06b-059397718d18
10a33cf0-885c-4347-9d66-843c45c6000b	2026-07-27 16:44:46.638197	2026-07-27 16:44:46.638197	Realisasi B2-WILL-FAIL exceeds its PRA ceiling and needs approval	f	\N	9aa060e9-7da2-4b7c-bcf9-727829ae4702	REALISASI	Realisasi Pembelian Exceeds Authorization Ceiling	REALISASI_ESCALATED	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
82697678-4229-4cd0-bcae-b65e220da8e8	2026-07-28 15:46:41.193322	2026-07-28 15:46:41.193322	New purchase request submitted by Conv Creator2 at Blitz Tactical	f	\N	6ccb5178-c123-4808-8af8-3f8761cf8c33	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	a720623c-28d9-4d6b-b06b-059397718d18
9bed5dc9-b76e-499d-a351-2ea45a364374	2026-07-28 15:46:41.197405	2026-07-28 15:46:41.197405	New purchase request submitted by Conv Creator2 at Blitz Tactical	f	\N	6ccb5178-c123-4808-8af8-3f8761cf8c33	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
edad679e-6d86-418e-b1df-da4494cf7b69	2026-07-28 15:46:41.197405	2026-07-28 15:46:41.197405	New purchase request submitted by Conv Creator2 at Blitz Tactical	f	\N	6ccb5178-c123-4808-8af8-3f8761cf8c33	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	b21f5996-8310-41f8-8d9e-e111e4cd5d43	a720623c-28d9-4d6b-b06b-059397718d18
ded740fc-1c5b-4d04-a29d-45fe10f4891d	2026-07-28 15:46:41.197405	2026-07-28 15:46:41.197405	New purchase request submitted by Conv Creator2 at Blitz Tactical	f	\N	6ccb5178-c123-4808-8af8-3f8761cf8c33	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	134b31e0-b277-4bfc-b810-5614f4773684	a720623c-28d9-4d6b-b06b-059397718d18
8d672955-d181-4f66-a9b7-6b7c0a0d956c	2026-07-29 03:14:36.757949	2026-07-29 03:14:36.757949	New purchase request submitted by Case9 Creator at Blitz Tactical	f	\N	6dcea281-438e-4872-9287-52336754dc54	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	a720623c-28d9-4d6b-b06b-059397718d18
aa0a90a9-05ce-4aba-b74e-6a38d4f2a835	2026-07-29 03:14:36.759506	2026-07-29 03:14:36.759506	New purchase request submitted by Case9 Creator at Blitz Tactical	f	\N	6dcea281-438e-4872-9287-52336754dc54	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
d18ad836-0cf0-4328-b41a-fc5ef309be44	2026-07-29 03:14:36.760225	2026-07-29 03:14:36.760225	New purchase request submitted by Case9 Creator at Blitz Tactical	f	\N	6dcea281-438e-4872-9287-52336754dc54	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	b21f5996-8310-41f8-8d9e-e111e4cd5d43	a720623c-28d9-4d6b-b06b-059397718d18
3249421d-a82a-48bb-8777-47b1e8fc8ae6	2026-07-29 03:14:36.760225	2026-07-29 03:14:36.760225	New purchase request submitted by Case9 Creator at Blitz Tactical	f	\N	6dcea281-438e-4872-9287-52336754dc54	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	134b31e0-b277-4bfc-b810-5614f4773684	a720623c-28d9-4d6b-b06b-059397718d18
d26219f1-9cd9-45df-8298-24377920fc39	2026-07-29 03:14:36.760225	2026-07-29 03:14:36.760225	New purchase request submitted by Case9 Creator at Blitz Tactical	f	\N	6dcea281-438e-4872-9287-52336754dc54	PURCHASE_REQUEST	New Purchase Request	NEW_PURCHASE_REQUEST	1af5b908-3a5b-4e98-a2e3-a382bf204c89	a720623c-28d9-4d6b-b06b-059397718d18
f31467e2-ef4a-4886-ae74-ef5c2f527616	2026-07-29 03:15:06.096148	2026-07-29 03:15:06.096148	Case9 Gas ConvFactor has dropped to or below its minimum stock level	f	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	PART	Low Stock	LOW_STOCK	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	a720623c-28d9-4d6b-b06b-059397718d18
04a6da2c-a584-4793-b473-283424642281	2026-07-29 03:15:06.096148	2026-07-29 03:15:06.096148	Case9 Gas ConvFactor has dropped to or below its minimum stock level	f	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	PART	Low Stock	LOW_STOCK	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
c7439d7b-1f14-42b4-9060-94313127ecb4	2026-07-29 03:15:06.096148	2026-07-29 03:15:06.096148	Case9 Gas ConvFactor has dropped to or below its minimum stock level	f	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	PART	Low Stock	LOW_STOCK	b21f5996-8310-41f8-8d9e-e111e4cd5d43	a720623c-28d9-4d6b-b06b-059397718d18
e16e7367-00c0-4af9-b5ca-a5588421aef5	2026-07-29 03:15:06.096148	2026-07-29 03:15:06.096148	Case9 Gas ConvFactor has dropped to or below its minimum stock level	f	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	PART	Low Stock	LOW_STOCK	134b31e0-b277-4bfc-b810-5614f4773684	a720623c-28d9-4d6b-b06b-059397718d18
68d0b9da-72d5-40ae-9ac5-37b1199be216	2026-07-29 03:15:06.096148	2026-07-29 03:15:06.096148	Case9 Gas ConvFactor has dropped to or below its minimum stock level	f	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	PART	Low Stock	LOW_STOCK	1af5b908-3a5b-4e98-a2e3-a382bf204c89	a720623c-28d9-4d6b-b06b-059397718d18
\.


--
-- Data for Name: part_categories; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.part_categories (id, description, name, tenant_id, is_consumable) FROM stdin;
246c8338-c71e-4570-ade3-3dff42b6f3bb	\N	Consumable	a720623c-28d9-4d6b-b06b-059397718d18	t
631cba2b-eb31-45ca-9230-35b8bfd2f392	Gas, BB and other consumable items	Consumable	8417979f-b99b-4df6-b1a8-368f791e81fb	t
83f25150-4908-4a33-ad72-7a46d7a52bbb	Gas, BB and other consumable items	Consumable	aa950bb6-cb01-41ca-b134-56bb42b730bc	t
26606ced-0f86-4842-ac6e-7c78665d47a5	\N	Internal Mechanical	a720623c-28d9-4d6b-b06b-059397718d18	f
709b51a3-6618-48ab-9537-2f0d2b502316	\N	External Mechanical	a720623c-28d9-4d6b-b06b-059397718d18	f
fc4c9498-d50c-4a91-aa0d-b245d7ae11d2	\N	Electrical & Electronic	a720623c-28d9-4d6b-b06b-059397718d18	f
570f4d39-de7b-4adb-86e9-d7c38a0c9f64	\N	Magazine	a720623c-28d9-4d6b-b06b-059397718d18	f
d5ba9680-2be4-4685-8e4c-961c20282ba8	\N	Optics & Accessories	a720623c-28d9-4d6b-b06b-059397718d18	f
21103111-18d9-4af4-95ee-cd498c376851	Gearbox, compression, hop-up, motor and trigger group components	Internal Mechanical	8417979f-b99b-4df6-b1a8-368f791e81fb	f
2211c88e-8b5d-4c22-97f9-a9207a898a30	Barrel, receiver, handguard, stock and other body components	External Mechanical	8417979f-b99b-4df6-b1a8-368f791e81fb	f
b2cac7a0-67d4-44b0-b110-4ab7d9430419	MOSFET, ETU, wiring and other electrical components	Electrical & Electronic	8417979f-b99b-4df6-b1a8-368f791e81fb	f
16b336a4-2831-450f-9b11-b36b44e81392	Magazine body and internal components	Magazine	8417979f-b99b-4df6-b1a8-368f791e81fb	f
9e84abef-8a2d-4d77-8dee-b09adbae584f	Optics, rails, sling, flashlight and other accessories	Optics & Accessories	8417979f-b99b-4df6-b1a8-368f791e81fb	f
00a23f4e-cd81-44c4-8ef6-3f7a95887327	Gearbox, compression, hop-up, motor and trigger group components	Internal Mechanical	aa950bb6-cb01-41ca-b134-56bb42b730bc	f
03aaf61c-18eb-40a4-96a6-3c7918878845	Barrel, receiver, handguard, stock and other body components	External Mechanical	aa950bb6-cb01-41ca-b134-56bb42b730bc	f
4fa52756-b5fc-486a-9613-073546d0b79c	MOSFET, ETU, wiring and other electrical components	Electrical & Electronic	aa950bb6-cb01-41ca-b134-56bb42b730bc	f
82c2d44f-b5ca-4e7e-abe8-9fea934c53bc	Magazine body and internal components	Magazine	aa950bb6-cb01-41ca-b134-56bb42b730bc	f
16a1f51c-31d3-45f9-abac-8a35951bd51a	Optics, rails, sling, flashlight and other accessories	Optics & Accessories	aa950bb6-cb01-41ca-b134-56bb42b730bc	f
\.


--
-- Data for Name: part_condition_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.part_condition_history (id, changed_at, new_condition, previous_condition, reason, technician_assessment, airsoft_unit_part_id, created_at, updated_at) FROM stdin;
1ee0b58c-c2c7-4519-90d2-5a409ca7151e	\N	FAILED	FAILED	Gearbox shell cracked during inspection	Part unsafe for operation	9b613960-2711-48d0-859c-73ca364fe4c4	2026-06-10 14:20:15.921068	2026-06-10 14:20:15.921068
f5d64290-1586-407d-a8fa-32d4a1f130a2	2026-05-28 20:55:39.864326	WORN	GOOD	Compression instability detected	Still usable but replacement recommended soon	9d9ee04e-d84a-4e85-99a6-26137dc5fa81	2026-06-10 15:14:14.937986	\N
aea99d07-0c9d-49fe-b09b-b3aa9a11f0f9	2026-05-28 21:55:27.818488	FAILED	\N	Gearbox jam	Unit unsafe for operation	9b613960-2711-48d0-859c-73ca364fe4c4	2026-06-10 15:14:14.937986	\N
a60bbbb2-193a-4650-b825-4b9efd915b3e	2026-05-28 21:57:26.115439	FAILED	FAILED	Gearbox jam	Unit unsafe for operation	9b613960-2711-48d0-859c-73ca364fe4c4	2026-06-10 15:14:14.937986	\N
dec864aa-b721-47d5-a3dc-b18383d0cc3f	2026-05-28 22:00:15.149473	FAILED	FAILED	Gearbox jam	Unit unsafe for operation	9b613960-2711-48d0-859c-73ca364fe4c4	2026-06-10 15:14:14.937986	\N
22d1b665-bbf9-49c8-84ff-9686b760db05	2026-06-03 11:04:15.201673	FAILED	GOOD	Part decprecated	Need to change part with the same type	d92b4a2f-f53f-4967-b668-1be4bbaf1865	2026-06-10 15:14:14.937986	\N
c58bfb5b-25af-45ea-9906-5018eaa6d858	2026-06-03 11:18:46.500944	FAILED	FAILED	Part destroyed	Part is failing	d92b4a2f-f53f-4967-b668-1be4bbaf1865	2026-06-10 15:14:14.937986	\N
8e5d7c1c-6fa3-420d-9a14-297328b1ec9f	2026-06-03 11:22:29.57942	FAILED	GOOD	Part destroyed	Part is failing	106d05ae-6740-4bb1-9113-f547f978fe6f	2026-06-10 15:14:14.937986	\N
552a6c2a-e7e2-4fe6-b825-43470e29f4d6	2026-06-04 15:57:55.701784	FAILED	GOOD	Cracked during inspection	Unsafe for operation	63ba8324-07dc-4116-aed8-efa55fcd9108	2026-06-10 15:14:14.937986	\N
9caa01b4-3ddb-4bd2-a6f2-6b8dacdb1a67	\N	FAILED	FAILED	Cylinder head cracked	Part unsafe for operation	d92b4a2f-f53f-4967-b668-1be4bbaf1865	2026-06-18 15:53:27.615913	2026-06-18 15:53:27.615913
811a33e4-ce1d-4005-afb3-7da2ae236d07	\N	FAILED	GOOD	Spacer is broken due to full auto	Unsafe for operation	cd5e9f20-d66e-4b12-9d3d-88f5cb635283	2026-07-02 14:26:59.83192	2026-07-02 14:26:59.83192
6d31f75a-3b18-4f3e-bb1f-8118241b902b	\N	FAILED	FAILED	Destroyed internal part	Part unsafe for operation	cd5e9f20-d66e-4b12-9d3d-88f5cb635283	2026-07-03 00:20:00.570429	2026-07-03 00:20:00.570429
\.


--
-- Data for Name: part_instances; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.part_instances (id, barcode, exhausted_at, notes, received_at, status, taken_at, installed_unit_part_id, part_id, taken_by, tenant_id, landed_cost) FROM stdin;
e4809959-8bfc-405f-81d8-25f2c192244c	PI-D39805C7A5A4	\N	tenant leak test	2026-07-25 19:31:12.03633	IN_STOCK	\N	\N	2264292d-62cc-4c45-9e0d-10db18668417	\N	8417979f-b99b-4df6-b1a8-368f791e81fb	\N
f3366340-a5b4-41a4-b39a-27bdc28c5d35	PI-1FD46073648F	\N	tenant leak test	2026-07-25 19:31:12.03633	IN_STOCK	\N	\N	2264292d-62cc-4c45-9e0d-10db18668417	\N	8417979f-b99b-4df6-b1a8-368f791e81fb	\N
aa23a0fc-270c-4bdd-b97d-598002a9be4d	PI-1AC4C8165AA0	\N	\N	2026-07-25 19:31:12.03633	IN_STOCK	\N	\N	2264292d-62cc-4c45-9e0d-10db18668417	\N	8417979f-b99b-4df6-b1a8-368f791e81fb	\N
734aa87a-b7ab-47a9-b54f-c27ad7b09bff	PI-F0A6FF4C9F6F	\N	Goods receipt for Realisasi B1-OK	2026-07-27 16:45:08.83793	IN_STOCK	\N	\N	60c1fb01-765a-442e-b315-4ceeb2e0b777	\N	a720623c-28d9-4d6b-b06b-059397718d18	\N
a18a7f21-e45e-4380-b436-b80aecbad1bf	PI-C3BFCA8A6E1C	\N	Goods receipt for Realisasi B1-OK	2026-07-27 16:45:08.83793	IN_STOCK	\N	\N	60c1fb01-765a-442e-b315-4ceeb2e0b777	\N	a720623c-28d9-4d6b-b06b-059397718d18	\N
82551bee-48fe-4ea3-aaf2-4c3f845fca0d	PI-2C5500F69701	\N	Goods receipt for Realisasi B1-OK	2026-07-27 16:45:08.83793	IN_STOCK	\N	\N	60c1fb01-765a-442e-b315-4ceeb2e0b777	\N	a720623c-28d9-4d6b-b06b-059397718d18	\N
e6f4a6d4-aeb4-437c-9db9-7e85c285a8bb	PI-A4C75A1AC8CF	\N	Goods receipt for Realisasi LANDEDCOST-TEST	2026-07-28 12:30:39.114471	IN_STOCK	\N	\N	60c1fb01-765a-442e-b315-4ceeb2e0b777	\N	a720623c-28d9-4d6b-b06b-059397718d18	60000.00
34efc721-13cc-492f-a7d8-b29140b67206	PI-631ABEC305B8	\N	Goods receipt for Realisasi LANDEDCOST-TEST	2026-07-28 12:30:39.114471	IN_STOCK	\N	\N	60c1fb01-765a-442e-b315-4ceeb2e0b777	\N	a720623c-28d9-4d6b-b06b-059397718d18	60000.00
f211958f-ccd4-49d6-a552-8c262cd53273	PI-D29715AAD69B	\N	Goods receipt for Realisasi WITHATTACH-TEST	2026-07-28 13:48:01.641953	IN_STOCK	\N	\N	60c1fb01-765a-442e-b315-4ceeb2e0b777	\N	a720623c-28d9-4d6b-b06b-059397718d18	50000.00
262a105c-6a1f-4421-8947-06e2f08f3e7d	PI-82F787FC6322	\N	Goods receipt for Realisasi CONVTEST-6-1785228402	2026-07-28 15:46:44.29314	IN_STOCK	\N	\N	42af7a38-00f7-4803-987d-b593d8eed52e	\N	a720623c-28d9-4d6b-b06b-059397718d18	23700.00
2450da56-378d-4698-80b4-caa0c0fd6e20	PI-09DD03A3453F	\N	Goods receipt for Realisasi CONVTEST-6-1785228402	2026-07-28 15:46:44.29314	IN_STOCK	\N	\N	42af7a38-00f7-4803-987d-b593d8eed52e	\N	a720623c-28d9-4d6b-b06b-059397718d18	23700.00
b28ef328-b5ce-4637-9206-30a692b068a5	PI-EE9B1615E35F	\N	Goods receipt for Realisasi CONVTEST-6-1785228402	2026-07-28 15:46:44.29314	IN_STOCK	\N	\N	42af7a38-00f7-4803-987d-b593d8eed52e	\N	a720623c-28d9-4d6b-b06b-059397718d18	23700.00
340640d3-e091-4632-acee-4f351fc8ca35	PI-22975BFC42B0	\N	Goods receipt for Realisasi CONVTEST-6-1785228402	2026-07-28 15:46:44.29314	IN_STOCK	\N	\N	42af7a38-00f7-4803-987d-b593d8eed52e	\N	a720623c-28d9-4d6b-b06b-059397718d18	23700.00
ddbf3e94-2afc-4bc6-a330-5ca72f6e76af	PI-AFF60F2B2AC7	\N	Goods receipt for Realisasi CONVTEST-6-1785228402	2026-07-28 15:46:44.29314	IN_STOCK	\N	\N	42af7a38-00f7-4803-987d-b593d8eed52e	\N	a720623c-28d9-4d6b-b06b-059397718d18	23700.00
529dcd30-ef08-47fc-9cbd-252abe5243e6	PI-EF1C841F794B	\N	Goods receipt for Realisasi CONVTEST-6-1785228402	2026-07-28 15:46:44.29314	IN_STOCK	\N	\N	42af7a38-00f7-4803-987d-b593d8eed52e	\N	a720623c-28d9-4d6b-b06b-059397718d18	23700.00
6030b099-2d2d-4d08-b611-78409759d55f	TESTBURN-1785269096	\N	burn rate test	2026-07-29 03:04:56.970065	TAKEN	2026-07-29 03:04:57.100695	\N	12aa2765-7620-4c75-a0a3-728f72a3c743	1d05f4e5-bf04-4f9b-8596-38caf45812a7	a720623c-28d9-4d6b-b06b-059397718d18	\N
61dd6557-0f4a-4ff4-96b4-c4ed13eb107f	CASE2-1785269341-1	\N	case2	2026-07-29 03:09:01.951638	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
0472ac70-3a38-475e-b790-04d0c5d3c69f	CASE2-1785269342-2	\N	case2	2026-07-29 03:09:02.231688	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
5defcd8d-56db-43b9-b29a-15a244f903a7	CASE2-1785269342-3	\N	case2	2026-07-29 03:09:02.519347	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
f18e2a7f-9277-4992-8219-5bed5a88e320	CASE2-1785269342-4	\N	case2	2026-07-29 03:09:02.813034	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
364b4983-e722-48c4-805d-edb2c68e4ffe	CASE2-1785269342-5	\N	case2	2026-07-29 03:09:03.084504	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
f26ce9ad-7b0d-4b9c-8ade-2177ba232a15	CASE2-1785269343-6	\N	case2	2026-07-29 03:09:03.385355	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
2770ec5d-7efe-4c68-8ffb-b14dad26224d	CASE2-1785269343-7	\N	case2	2026-07-29 03:09:03.702143	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
4544377d-1615-49aa-b622-a2b9171e2aa3	CASE2-1785269343-8	\N	case2	2026-07-29 03:09:03.987164	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
19ef2e75-99f3-4096-98ab-4e9f9adb8ab2	CASE2-1785269344-9	\N	case2	2026-07-29 03:09:04.303345	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
5099956a-c6c1-4570-97a7-aa5b1974172e	CASE2-1785269344-10	\N	case2	2026-07-29 03:09:04.597193	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
7b96cd21-dee9-4d78-a711-12f1bacf3509	CASE2-1785269344-11	\N	case2	2026-07-29 03:09:04.86977	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
f3e17847-471a-4899-83e2-5b828bbd53ae	CASE2-1785269345-12	\N	case2	2026-07-29 03:09:05.16475	TAKEN	2026-07-23 03:11:00.696254	\N	8b6608d4-12f4-4163-a9aa-db8398022fc0	6f010108-3575-4bbf-814c-f8d0d10d0e8c	a720623c-28d9-4d6b-b06b-059397718d18	\N
162a646a-27f8-4ca4-90bd-a272f54c6e32	CASE9SCAN-1785269704-1	\N	case9	2026-07-29 03:15:05.062309	TAKEN	2026-07-29 03:15:05.174363	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	f61772db-2411-4c27-8424-00eb883ad1c5	a720623c-28d9-4d6b-b06b-059397718d18	\N
08a1ca49-9239-4249-aae6-4fa45d47161f	CASE9SCAN-1785269705-2	\N	case9	2026-07-29 03:15:05.365926	TAKEN	2026-07-29 03:15:05.479405	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	f61772db-2411-4c27-8424-00eb883ad1c5	a720623c-28d9-4d6b-b06b-059397718d18	\N
1034c71d-0cc0-4c4b-a133-05c2e1e00c11	CASE9SCAN-1785269705-3	\N	case9	2026-07-29 03:15:05.670141	TAKEN	2026-07-29 03:15:05.779466	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	f61772db-2411-4c27-8424-00eb883ad1c5	a720623c-28d9-4d6b-b06b-059397718d18	\N
dd366b7e-5d03-4deb-84fa-83e7faa134e2	CASE9SCAN-1785269705-4	\N	case9	2026-07-29 03:15:05.969986	TAKEN	2026-07-29 03:15:06.096148	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	f61772db-2411-4c27-8424-00eb883ad1c5	a720623c-28d9-4d6b-b06b-059397718d18	\N
f13eb6e4-e21d-4e0c-acb9-5afcb68b1634	CASE9SCAN-1785269706-5	\N	case9	2026-07-29 03:15:06.29558	TAKEN	2026-07-29 03:15:06.412487	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	f61772db-2411-4c27-8424-00eb883ad1c5	a720623c-28d9-4d6b-b06b-059397718d18	\N
e8b31a1c-6a12-4e42-bb3e-8c001cd7a64b	CASE9SCAN-1785269706-6	\N	case9	2026-07-29 03:15:06.614089	TAKEN	2026-07-29 03:15:06.727492	\N	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	f61772db-2411-4c27-8424-00eb883ad1c5	a720623c-28d9-4d6b-b06b-059397718d18	\N
\.


--
-- Data for Name: part_type; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.part_type (id, tenant_id, category_id, name, description, created_at) FROM stdin;
f40b6eb9-51e8-417d-b835-3edc82d0a1c9	a720623c-28d9-4d6b-b06b-059397718d18	246c8338-c71e-4570-ade3-3dff42b6f3bb	Green Gas	\N	\N
062aae59-033b-4740-9552-724aa546fdcd	a720623c-28d9-4d6b-b06b-059397718d18	246c8338-c71e-4570-ade3-3dff42b6f3bb	CO2 Cartridge 12g	\N	\N
7b1915bb-b45c-471c-b30f-f82d8efa3227	a720623c-28d9-4d6b-b06b-059397718d18	246c8338-c71e-4570-ade3-3dff42b6f3bb	BB 0.20g	\N	\N
c5b53079-448f-4f0b-b4aa-7d41b7f7a3a2	a720623c-28d9-4d6b-b06b-059397718d18	246c8338-c71e-4570-ade3-3dff42b6f3bb	BB 0.25g	\N	\N
b1df8312-63d3-49cc-b89e-08a98af52a62	a720623c-28d9-4d6b-b06b-059397718d18	246c8338-c71e-4570-ade3-3dff42b6f3bb	BB 0.28g	\N	\N
d6cfb770-02c5-47c0-8285-12394f11d649	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Piston	\N	\N
d4ac593d-562a-4c0e-8b65-fabb4756d381	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Piston Head	\N	\N
dcabc3d7-227c-4ddb-a30b-a7fadfbc89c9	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Cylinder	\N	\N
575084aa-39c7-48dd-b674-25d013d85c72	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Cylinder Head	\N	\N
ca3b957c-6798-441c-b220-7b6ef92a5e63	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Spring	\N	\N
4fe4869b-047e-4c99-9b3f-5b955ac73b66	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Spring Guide	\N	\N
bc8493a6-04bc-45d2-8a49-08b2f9050cac	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Gearbox Shell	\N	\N
93e59828-10d1-40a7-9bad-dd2c0c1b221f	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Gear Set	\N	\N
1e13682a-da98-4ca4-9af0-4d7d61eb3959	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Tappet Plate	\N	\N
d6301162-0d37-4957-aa9e-4ddc7d95ef82	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Nozzle	\N	\N
00d6d6a0-8ed8-4bc6-889e-fb7cbe22c9e1	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Trigger Assembly	\N	\N
8f00df05-ac92-4821-aa57-4492993e7632	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Anti-Reversal Latch	\N	\N
0988a8ee-caec-4234-92dd-f96b6702bf12	a720623c-28d9-4d6b-b06b-059397718d18	709b51a3-6618-48ab-9537-2f0d2b502316	Inner Barrel	\N	\N
3a5ff198-853f-464c-95ee-542228a9af90	a720623c-28d9-4d6b-b06b-059397718d18	709b51a3-6618-48ab-9537-2f0d2b502316	Outer Barrel	\N	\N
fc973265-933e-4c9c-b562-1cb10f38e2d5	a720623c-28d9-4d6b-b06b-059397718d18	709b51a3-6618-48ab-9537-2f0d2b502316	Hop-up Unit	\N	\N
a5de0f4d-8187-4115-b10b-c29086966c1a	a720623c-28d9-4d6b-b06b-059397718d18	709b51a3-6618-48ab-9537-2f0d2b502316	Hop-up Bucking	\N	\N
94adbf47-6cf2-4758-8529-d87a5aca8e78	a720623c-28d9-4d6b-b06b-059397718d18	709b51a3-6618-48ab-9537-2f0d2b502316	Stock	\N	\N
2e9eecbc-b94f-45e1-92d1-921e1e090bca	a720623c-28d9-4d6b-b06b-059397718d18	709b51a3-6618-48ab-9537-2f0d2b502316	Grip	\N	\N
80e7660c-7b7a-455a-afd1-faa76a328bf8	a720623c-28d9-4d6b-b06b-059397718d18	709b51a3-6618-48ab-9537-2f0d2b502316	Receiver (Upper)	\N	\N
cfea247f-adec-456d-b410-549c23af8437	a720623c-28d9-4d6b-b06b-059397718d18	709b51a3-6618-48ab-9537-2f0d2b502316	Receiver (Lower)	\N	\N
80ed91a7-5668-4b17-8954-1387b8da7dd0	a720623c-28d9-4d6b-b06b-059397718d18	709b51a3-6618-48ab-9537-2f0d2b502316	Rail System	\N	\N
04832de1-d334-4b3b-853d-ff0f8410cfd1	a720623c-28d9-4d6b-b06b-059397718d18	fc4c9498-d50c-4a91-aa0d-b245d7ae11d2	Motor	\N	\N
39d8ab2c-0d51-4a18-8b86-3617bea76c75	a720623c-28d9-4d6b-b06b-059397718d18	fc4c9498-d50c-4a91-aa0d-b245d7ae11d2	Battery (LiPo)	\N	\N
c4d7c6ad-0bbf-41a1-b5df-0b8a1fbdc31a	a720623c-28d9-4d6b-b06b-059397718d18	fc4c9498-d50c-4a91-aa0d-b245d7ae11d2	Battery (NiMH)	\N	\N
6832ad66-7c83-4073-8c81-b48127c25336	a720623c-28d9-4d6b-b06b-059397718d18	fc4c9498-d50c-4a91-aa0d-b245d7ae11d2	MOSFET	\N	\N
0b025c44-6068-4962-bf02-8bc7c8166bf1	a720623c-28d9-4d6b-b06b-059397718d18	fc4c9498-d50c-4a91-aa0d-b245d7ae11d2	Wiring Harness	\N	\N
cbfbdd38-1d0a-43e3-bd3b-0ef21219541e	a720623c-28d9-4d6b-b06b-059397718d18	fc4c9498-d50c-4a91-aa0d-b245d7ae11d2	Trigger Switch	\N	\N
f31e979c-b6e0-493f-8bf0-8b2672526c16	a720623c-28d9-4d6b-b06b-059397718d18	fc4c9498-d50c-4a91-aa0d-b245d7ae11d2	Charger	\N	\N
b23bceb1-b0de-45b5-97ef-6723afc503cc	a720623c-28d9-4d6b-b06b-059397718d18	d5ba9680-2be4-4685-8e4c-961c20282ba8	Red Dot Sight	\N	\N
bf939f8e-f9ec-4d16-8bbf-01509ab3db94	a720623c-28d9-4d6b-b06b-059397718d18	d5ba9680-2be4-4685-8e4c-961c20282ba8	Magnified Scope	\N	\N
48e74356-a16b-45a5-b7df-4a7c0bb89dcc	a720623c-28d9-4d6b-b06b-059397718d18	d5ba9680-2be4-4685-8e4c-961c20282ba8	Sling	\N	\N
f2978a6c-a9d2-4caf-bcc6-967fd59b13f3	a720623c-28d9-4d6b-b06b-059397718d18	d5ba9680-2be4-4685-8e4c-961c20282ba8	Foregrip	\N	\N
a6d8bcf1-3ee2-4e4b-bcb2-ae04d358f2cf	a720623c-28d9-4d6b-b06b-059397718d18	d5ba9680-2be4-4685-8e4c-961c20282ba8	Flashlight	\N	\N
3a7a6290-e9ee-4785-8350-0605e7dc9ee1	a720623c-28d9-4d6b-b06b-059397718d18	570f4d39-de7b-4adb-86e9-d7c38a0c9f64	Low-cap Magazine	\N	\N
e72746bc-079a-4643-991b-ee3c2d70f9d9	a720623c-28d9-4d6b-b06b-059397718d18	570f4d39-de7b-4adb-86e9-d7c38a0c9f64	Mid-cap Magazine	\N	\N
52fd4a14-4d8a-457d-a39d-9645ad0facd3	a720623c-28d9-4d6b-b06b-059397718d18	570f4d39-de7b-4adb-86e9-d7c38a0c9f64	Hi-cap Magazine	\N	\N
694d7ad6-7de5-4e48-bf88-b4dd92836712	a720623c-28d9-4d6b-b06b-059397718d18	570f4d39-de7b-4adb-86e9-d7c38a0c9f64	Drum Magazine	\N	\N
2e13a552-a3d9-4af3-a87f-57c9acf0f5be	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Test Gear	test	\N
b637ed5c-35ee-4896-95e6-a75d045e64d3	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Bushing	Gearbox shaft bushing/bearing	\N
51e17518-1040-4359-89f6-32d392564fac	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Pinion Gear	Motor pinion gear	\N
04ebc0ba-bd4d-4d91-a966-47504ec6a77e	a720623c-28d9-4d6b-b06b-059397718d18	26606ced-0f86-4842-ac6e-7c78665d47a5	Motor Cage	Motor mount/cage	\N
be6d09fd-40ea-4252-968d-7156bc39a0ae	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Anti-Reversal Latch	\N	\N
29fce8a9-a986-4cf5-aaae-b48809cd2b8c	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Bushing	\N	\N
704f30af-2707-4d87-815a-d77895949594	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Cylinder	\N	\N
b49b79c5-d1ab-4172-9f7c-c78717c6f9b6	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Cylinder Head	\N	\N
dbfe0030-d844-456c-8d5b-5b5d1614647c	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Gear Set	\N	\N
257c65be-75a6-477b-9fd3-6de7ec793fae	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Gearbox Shell	\N	\N
3b317c71-4bd9-4c12-9d73-70bce9d25578	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Motor Cage	\N	\N
91439b5e-fcbb-4ade-8282-1bcafd968019	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Nozzle	\N	\N
11d0b7a9-e493-4262-826a-9587afb3c829	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Pinion Gear	\N	\N
14290baa-e528-4f35-aa4c-bbb7677b0105	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Piston	\N	\N
6d3eb37e-739f-486a-91ff-40bb44e5ab75	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Piston Head	\N	\N
a2303ed9-d556-47bf-8b83-d2012ebd9b73	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Spring	\N	\N
9a2975e0-aceb-4ace-b62a-5dfd3434f5c5	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Spring Guide	\N	\N
c234b713-f8f2-4429-8f21-dfc0c667570b	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Tappet Plate	\N	\N
b0d3f9a7-f03a-49ef-a110-864c0df8b277	aa950bb6-cb01-41ca-b134-56bb42b730bc	00a23f4e-cd81-44c4-8ef6-3f7a95887327	Trigger Assembly	\N	\N
2cad9764-65d2-4e62-8d0e-6ee77895dc40	aa950bb6-cb01-41ca-b134-56bb42b730bc	03aaf61c-18eb-40a4-96a6-3c7918878845	Grip	\N	\N
447df97b-d41e-414f-93c3-b493a652ed8f	aa950bb6-cb01-41ca-b134-56bb42b730bc	03aaf61c-18eb-40a4-96a6-3c7918878845	Hop-up Bucking	\N	\N
99f67703-6e3f-47e7-8752-f5f38a931c47	aa950bb6-cb01-41ca-b134-56bb42b730bc	03aaf61c-18eb-40a4-96a6-3c7918878845	Hop-up Unit	\N	\N
abe774f4-5121-4a6f-8b1f-72caaae95ef2	aa950bb6-cb01-41ca-b134-56bb42b730bc	03aaf61c-18eb-40a4-96a6-3c7918878845	Inner Barrel	\N	\N
d7e5e8cf-4103-4a0b-bcc7-ddffedf4128a	aa950bb6-cb01-41ca-b134-56bb42b730bc	03aaf61c-18eb-40a4-96a6-3c7918878845	Outer Barrel	\N	\N
29d9d916-b693-47ad-937e-3ec45a7c4a95	aa950bb6-cb01-41ca-b134-56bb42b730bc	03aaf61c-18eb-40a4-96a6-3c7918878845	Rail System	\N	\N
8a508a0a-f149-4847-872f-d39254e74cdc	aa950bb6-cb01-41ca-b134-56bb42b730bc	03aaf61c-18eb-40a4-96a6-3c7918878845	Receiver (Lower)	\N	\N
801a6007-b55f-4153-b48a-c27e6d9b0188	aa950bb6-cb01-41ca-b134-56bb42b730bc	03aaf61c-18eb-40a4-96a6-3c7918878845	Receiver (Upper)	\N	\N
fa1679de-3bcd-4e7f-b710-75d0c1e9ab3e	aa950bb6-cb01-41ca-b134-56bb42b730bc	03aaf61c-18eb-40a4-96a6-3c7918878845	Stock	\N	\N
c35d3e38-2246-4469-98e1-4051f2fad562	aa950bb6-cb01-41ca-b134-56bb42b730bc	4fa52756-b5fc-486a-9613-073546d0b79c	Battery (LiPo)	\N	\N
833f07fb-e0dd-4eee-a99d-9e504dadec31	aa950bb6-cb01-41ca-b134-56bb42b730bc	4fa52756-b5fc-486a-9613-073546d0b79c	Battery (NiMH)	\N	\N
ecc450f9-3325-4792-826d-cc1f80a0e7d9	aa950bb6-cb01-41ca-b134-56bb42b730bc	4fa52756-b5fc-486a-9613-073546d0b79c	Charger	\N	\N
dd15b1f0-92f3-4dbd-9838-c8a161d9fbd2	aa950bb6-cb01-41ca-b134-56bb42b730bc	4fa52756-b5fc-486a-9613-073546d0b79c	MOSFET	\N	\N
f451abd1-a10d-465b-a3c4-44a8a27203f7	aa950bb6-cb01-41ca-b134-56bb42b730bc	4fa52756-b5fc-486a-9613-073546d0b79c	Motor	\N	\N
02137e20-4b1c-4e6e-aa75-277d3a463648	aa950bb6-cb01-41ca-b134-56bb42b730bc	4fa52756-b5fc-486a-9613-073546d0b79c	Trigger Switch	\N	\N
cec1c054-7394-4601-92b5-66876fbfcbff	aa950bb6-cb01-41ca-b134-56bb42b730bc	4fa52756-b5fc-486a-9613-073546d0b79c	Wiring Harness	\N	\N
eeda6501-5f12-4e85-bcb1-c80101694eea	aa950bb6-cb01-41ca-b134-56bb42b730bc	82c2d44f-b5ca-4e7e-abe8-9fea934c53bc	Drum Magazine	\N	\N
f81f1f5b-d336-430a-a128-23c404bab1bd	aa950bb6-cb01-41ca-b134-56bb42b730bc	82c2d44f-b5ca-4e7e-abe8-9fea934c53bc	Hi-cap Magazine	\N	\N
aa1ef213-5d18-423e-b8bc-ed6ef6ffe52f	aa950bb6-cb01-41ca-b134-56bb42b730bc	82c2d44f-b5ca-4e7e-abe8-9fea934c53bc	Low-cap Magazine	\N	\N
a050722c-8a2c-4e51-bbbe-34629ddd65ae	aa950bb6-cb01-41ca-b134-56bb42b730bc	82c2d44f-b5ca-4e7e-abe8-9fea934c53bc	Mid-cap Magazine	\N	\N
7eec7299-c7b7-4636-8f10-e91a1a133e21	aa950bb6-cb01-41ca-b134-56bb42b730bc	83f25150-4908-4a33-ad72-7a46d7a52bbb	BB 0.20g	\N	\N
ba20245b-ea04-438a-814f-22538fcbaae2	aa950bb6-cb01-41ca-b134-56bb42b730bc	83f25150-4908-4a33-ad72-7a46d7a52bbb	BB 0.25g	\N	\N
b61f0aaa-6010-4d0f-8900-d57ea14d0bc3	aa950bb6-cb01-41ca-b134-56bb42b730bc	83f25150-4908-4a33-ad72-7a46d7a52bbb	BB 0.28g	\N	\N
181cbe28-2a98-4b85-8981-820d02f530da	aa950bb6-cb01-41ca-b134-56bb42b730bc	83f25150-4908-4a33-ad72-7a46d7a52bbb	CO2 Cartridge 12g	\N	\N
65caaffa-a147-4334-be8d-268c6d7f5d16	aa950bb6-cb01-41ca-b134-56bb42b730bc	83f25150-4908-4a33-ad72-7a46d7a52bbb	Green Gas	\N	\N
7aa90b54-702b-4a18-b433-d2716d6e2f1f	aa950bb6-cb01-41ca-b134-56bb42b730bc	16a1f51c-31d3-45f9-abac-8a35951bd51a	Flashlight	\N	\N
6d3a3915-a590-469f-8c03-0dc2c1ebc74a	aa950bb6-cb01-41ca-b134-56bb42b730bc	16a1f51c-31d3-45f9-abac-8a35951bd51a	Foregrip	\N	\N
359162ef-5e7b-4c2a-a757-b1f5b7f4c640	aa950bb6-cb01-41ca-b134-56bb42b730bc	16a1f51c-31d3-45f9-abac-8a35951bd51a	Magnified Scope	\N	\N
35f93c4e-794b-42a2-a1a9-c1be73943268	aa950bb6-cb01-41ca-b134-56bb42b730bc	16a1f51c-31d3-45f9-abac-8a35951bd51a	Red Dot Sight	\N	\N
554df931-757f-4038-8a5d-965c8682981e	aa950bb6-cb01-41ca-b134-56bb42b730bc	16a1f51c-31d3-45f9-abac-8a35951bd51a	Sling	\N	\N
86f10656-b38e-405b-b483-3928d4118fe3	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Anti-Reversal Latch	\N	\N
beeef579-c4db-421a-815f-ef71e738cb10	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Bushing	\N	\N
b2ee9dfa-72fd-4419-975f-0d022d995849	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Cylinder	\N	\N
b88d4705-a591-4102-96ba-243f22bb91d8	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Cylinder Head	\N	\N
7c38dd23-95ce-4cc0-ae2b-f25396bf0dd7	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Gear Set	\N	\N
7c925952-0cb2-478d-8e0b-be5dc4a24c7d	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Gearbox Shell	\N	\N
974efd62-4ae1-4c89-b1bd-fc6b91d4e15e	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Motor Cage	\N	\N
20d997d7-ef50-433e-a2e3-05e2ba52e283	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Nozzle	\N	\N
4ef7b652-711c-4b79-bbec-6a206fe0e7d2	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Pinion Gear	\N	\N
74f6ca90-7dc6-40ef-b3fd-f9d511e16b95	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Piston	\N	\N
3c83c3af-99fb-47a5-b856-fc4d16eb638c	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Piston Head	\N	\N
be2cd917-e6b1-40b0-a184-6f3195407263	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Spring	\N	\N
27c41134-70b1-4a10-8824-398df08678f8	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Spring Guide	\N	\N
89bbef20-1ab3-4512-889c-a90ad3609bf1	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Tappet Plate	\N	\N
780a8c9e-733d-4840-a7f6-3be6c4074bcf	8417979f-b99b-4df6-b1a8-368f791e81fb	21103111-18d9-4af4-95ee-cd498c376851	Trigger Assembly	\N	\N
1e71db45-a77d-4613-a63d-7579a023b0d4	8417979f-b99b-4df6-b1a8-368f791e81fb	2211c88e-8b5d-4c22-97f9-a9207a898a30	Grip	\N	\N
29950ce4-918d-4687-ab60-a60f044bb17e	8417979f-b99b-4df6-b1a8-368f791e81fb	2211c88e-8b5d-4c22-97f9-a9207a898a30	Hop-up Bucking	\N	\N
46462f12-2716-49f9-8a44-1644b21773d2	8417979f-b99b-4df6-b1a8-368f791e81fb	2211c88e-8b5d-4c22-97f9-a9207a898a30	Hop-up Unit	\N	\N
2c843031-d2da-4934-9c2c-0d6e9b3c5445	8417979f-b99b-4df6-b1a8-368f791e81fb	2211c88e-8b5d-4c22-97f9-a9207a898a30	Inner Barrel	\N	\N
f4948a0e-1d9f-4ad8-868a-e75fd81d7fab	8417979f-b99b-4df6-b1a8-368f791e81fb	2211c88e-8b5d-4c22-97f9-a9207a898a30	Outer Barrel	\N	\N
fa31f2ad-52d5-4c54-8526-749222e7d727	8417979f-b99b-4df6-b1a8-368f791e81fb	2211c88e-8b5d-4c22-97f9-a9207a898a30	Rail System	\N	\N
45512b43-a201-45c1-b670-566ed1839224	8417979f-b99b-4df6-b1a8-368f791e81fb	2211c88e-8b5d-4c22-97f9-a9207a898a30	Receiver (Lower)	\N	\N
f5a79196-eda1-425d-8af5-80c1ef17bd30	8417979f-b99b-4df6-b1a8-368f791e81fb	2211c88e-8b5d-4c22-97f9-a9207a898a30	Receiver (Upper)	\N	\N
14f3aa80-9e69-4565-8a61-e2fc8df7e0f6	8417979f-b99b-4df6-b1a8-368f791e81fb	2211c88e-8b5d-4c22-97f9-a9207a898a30	Stock	\N	\N
a68bc340-231f-463c-acb8-ef0be00defdb	8417979f-b99b-4df6-b1a8-368f791e81fb	b2cac7a0-67d4-44b0-b110-4ab7d9430419	Battery (LiPo)	\N	\N
1762c66c-cb8d-4d43-aff5-80508e4bf9f3	8417979f-b99b-4df6-b1a8-368f791e81fb	b2cac7a0-67d4-44b0-b110-4ab7d9430419	Battery (NiMH)	\N	\N
3dbaac0a-d4b7-4a48-8bc2-ca2e3725f31c	8417979f-b99b-4df6-b1a8-368f791e81fb	b2cac7a0-67d4-44b0-b110-4ab7d9430419	Charger	\N	\N
a4545631-0287-4b62-9a43-ed7d3b9cbb68	8417979f-b99b-4df6-b1a8-368f791e81fb	b2cac7a0-67d4-44b0-b110-4ab7d9430419	MOSFET	\N	\N
849d0896-b520-4c9b-9f0f-f6a392948ea4	8417979f-b99b-4df6-b1a8-368f791e81fb	b2cac7a0-67d4-44b0-b110-4ab7d9430419	Motor	\N	\N
fa02f663-820e-4f9d-8d0e-b6f86b66fff2	8417979f-b99b-4df6-b1a8-368f791e81fb	b2cac7a0-67d4-44b0-b110-4ab7d9430419	Trigger Switch	\N	\N
c0844c70-2fa2-4e65-b5bd-bab234e5c067	8417979f-b99b-4df6-b1a8-368f791e81fb	b2cac7a0-67d4-44b0-b110-4ab7d9430419	Wiring Harness	\N	\N
17c2011e-0442-4581-a8e9-6bc335859bd8	8417979f-b99b-4df6-b1a8-368f791e81fb	16b336a4-2831-450f-9b11-b36b44e81392	Drum Magazine	\N	\N
7c9c2130-51c3-4f9e-a1ae-b1add1b13f14	8417979f-b99b-4df6-b1a8-368f791e81fb	16b336a4-2831-450f-9b11-b36b44e81392	Hi-cap Magazine	\N	\N
b40c9b33-bf53-4720-8a36-ef1b1fc589be	8417979f-b99b-4df6-b1a8-368f791e81fb	16b336a4-2831-450f-9b11-b36b44e81392	Low-cap Magazine	\N	\N
8898b68e-c5dc-4f05-971d-22748e70c159	8417979f-b99b-4df6-b1a8-368f791e81fb	16b336a4-2831-450f-9b11-b36b44e81392	Mid-cap Magazine	\N	\N
3bba74b6-af39-461b-a47e-00b0e4b1019e	8417979f-b99b-4df6-b1a8-368f791e81fb	631cba2b-eb31-45ca-9230-35b8bfd2f392	BB 0.20g	\N	\N
eeac8f0c-64fb-44aa-9daa-763108f50b6a	8417979f-b99b-4df6-b1a8-368f791e81fb	631cba2b-eb31-45ca-9230-35b8bfd2f392	BB 0.25g	\N	\N
f387e72f-43da-49d1-b3ca-e753a3d6b6b2	8417979f-b99b-4df6-b1a8-368f791e81fb	631cba2b-eb31-45ca-9230-35b8bfd2f392	BB 0.28g	\N	\N
a5bb80e5-af1f-4c35-b98d-138a5935a063	8417979f-b99b-4df6-b1a8-368f791e81fb	631cba2b-eb31-45ca-9230-35b8bfd2f392	CO2 Cartridge 12g	\N	\N
b219d996-a2a5-414f-b7a0-b201e3a75e7b	8417979f-b99b-4df6-b1a8-368f791e81fb	631cba2b-eb31-45ca-9230-35b8bfd2f392	Green Gas	\N	\N
89d0c52c-a8a4-45e4-bd31-f1eae8174ef4	8417979f-b99b-4df6-b1a8-368f791e81fb	9e84abef-8a2d-4d77-8dee-b09adbae584f	Flashlight	\N	\N
d12ef745-781f-40ba-9f4b-eb1dadbd3772	8417979f-b99b-4df6-b1a8-368f791e81fb	9e84abef-8a2d-4d77-8dee-b09adbae584f	Foregrip	\N	\N
ac213622-1d86-4a75-b407-1336dc867194	8417979f-b99b-4df6-b1a8-368f791e81fb	9e84abef-8a2d-4d77-8dee-b09adbae584f	Magnified Scope	\N	\N
8bb8cdd3-c445-4795-8850-fceff6971ba6	8417979f-b99b-4df6-b1a8-368f791e81fb	9e84abef-8a2d-4d77-8dee-b09adbae584f	Red Dot Sight	\N	\N
fab3a72c-e1de-4213-bd76-a127d873cca7	8417979f-b99b-4df6-b1a8-368f791e81fb	9e84abef-8a2d-4d77-8dee-b09adbae584f	Sling	\N	\N
\.


--
-- Data for Name: parts; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.parts (id, name, category, unit, created_at, brand, expected_lifespan_days, notes, updated_at, tenant_id, current_stock, minimum_stock, reorder_quantity, category_id, part_type_id, active, retired, retired_at, retirement_reason, manual_daily_usage, manual_reorder_point) FROM stdin;
8a2d4326-c54e-4d9d-bff0-7423d9bf3597	RHOP Custom	Hop-Up Rubber	\N	2026-05-16 15:59:38.925835	Custom	365	Custom RHOP by Puji	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	709b51a3-6618-48ab-9537-2f0d2b502316	a5de0f4d-8187-4115-b10b-c29086966c1a	t	f	\N	\N	\N	\N
1d1f6fdf-43cb-4fd6-bc2e-5a9d8991100c	Tight Bore Inner Barrel 50cm	Inner Barrel	\N	2026-05-16 15:58:40.392484	PDI	720	Silver tight bore barrel for AK platform	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	709b51a3-6618-48ab-9537-2f0d2b502316	0988a8ee-caec-4234-92dd-f96b6702bf12	t	f	\N	\N	\N	\N
30c816b0-6944-4652-a127-a72a8341b52f	AK Nozzle	Nozzle	\N	2026-05-16 15:59:50.500386	SHS	365	Standard SHS nozzle	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	d6301162-0d37-4957-aa9e-4ddc7d95ef82	t	f	\N	\N	\N	\N
f8b74d56-f200-4000-b7b2-54758460d3b2	Enhanced Cylinder 100%	Cylinder	\N	2026-05-16 16:00:02.701912	SHS	900	Silver enhanced cylinder	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	dcabc3d7-227c-4ddb-a30b-a7fadfbc89c9	t	f	\N	\N	\N	\N
8f56faea-f30f-4458-9bbb-d623d518665c	V3 Gearbox Shell QD Silver	Gearbox Shell	\N	2026-05-16 15:59:45.052456	CYMA	1460	Quick detach spring gearbox shell	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	bc8493a6-04bc-45d2-8a49-08b2f9050cac	t	f	\N	\N	\N	\N
6da6cf1c-a8d0-40be-9d86-c5edec443670	Hop-Up Rubber Blue Transparent	Hop-Up Rubber	\N	2026-05-16 15:59:26.737927	SHS	180	Standard bucking	2026-07-25 20:17:55.656674	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	709b51a3-6618-48ab-9537-2f0d2b502316	a5de0f4d-8187-4115-b10b-c29086966c1a	t	f	\N	\N	\N	\N
60c1fb01-765a-442e-b315-4ceeb2e0b777	Kestrel MOSFET	MOSFET	\N	2026-05-16 16:02:17.729149	E-Shooter	720	Electronic trigger MOSFET	2026-07-28 13:48:01.641953	a720623c-28d9-4d6b-b06b-059397718d18	16	0	\N	fc4c9498-d50c-4a91-aa0d-b245d7ae11d2	6832ad66-7c83-4073-8c81-b48127c25336	t	f	\N	\N	\N	\N
42af7a38-00f7-4803-987d-b593d8eed52e	AK Hop-Up Chamber Standard Nylon	Hop-Up Chamber	\N	2026-05-16 15:59:20.449324	King Arms	900	Default stock chamber	2026-07-28 15:46:44.29314	a720623c-28d9-4d6b-b06b-059397718d18	6	0	\N	709b51a3-6618-48ab-9537-2f0d2b502316	fc973265-933e-4c9c-b562-1cb10f38e2d5	t	f	\N	\N	\N	\N
5923a73d-f915-4be8-b60b-3018df0460bc	a test1	Compression	\N	2026-07-02 02:39:03.771415	a brand	11	testing category string insert data	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	10	26606ced-0f86-4842-ac6e-7c78665d47a5	\N	t	f	\N	\N	\N	\N
24ba1d61-9bd6-47c0-96f9-fe24f92a2b9b	PART1	Hopup	\N	2026-06-15 20:29:54.826226	a test	999	Karet gelang	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	\N	t	f	\N	\N	\N	\N
567330fb-5b64-4d3c-b818-a4819e541204	Tappet Plate Blue	Tappet Plate	\N	2026-05-16 16:00:32.468326	SHS	365	Blue reinforced tappet plate	2026-06-29 13:18:04.7152	a720623c-28d9-4d6b-b06b-059397718d18	5	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	1e13682a-da98-4ca4-9af0-4d7d61eb3959	t	f	\N	\N	\N	\N
80517433-57e1-4b4f-8dc1-218232e1d807	Piston 14 Teeth	Piston	\N	2026-05-16 16:00:25.826025	SHS	300	14 steel teeth piston	2026-07-25 20:17:38.65929	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	d6cfb770-02c5-47c0-8285-12394f11d649	t	f	\N	\N	\N	\N
2264292d-62cc-4c45-9e0d-10db18668417	Outlet2 Test Gear	Internal Mechanical	\N	2026-07-23 01:58:03.543553	TestBrand	\N	\N	2026-07-25 19:31:12.036862	8417979f-b99b-4df6-b1a8-368f791e81fb	6	1	1	21103111-18d9-4af4-95ee-cd498c376851	2e13a552-a3d9-4af3-a87f-57c9acf0f5be	t	f	\N	\N	\N	\N
f6b11de1-df0d-4d08-a29d-bb3585d24da0	Brushless Motor Blue	Motor	\N	2026-05-16 16:02:25.731316	SoLink	720	Brushless AEG motor	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	fc4c9498-d50c-4a91-aa0d-b245d7ae11d2	04832de1-d334-4b3b-853d-ff0f8410cfd1	t	f	\N	\N	\N	\N
691ffd3c-ba81-41fa-ba3e-705f00512c2e	Karet Hop-up	Hopup	\N	2026-05-20 14:50:49.116263	Mapple Leaf	180	Karet Hop-up Mapple Leaf warna hitam 80 derajat	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	709b51a3-6618-48ab-9537-2f0d2b502316	a5de0f4d-8187-4115-b10b-c29086966c1a	t	f	\N	\N	\N	\N
a17282cf-e43d-4f5e-ae99-a3cda6a0c832	7mm Bushing	Bushing	\N	2026-05-16 16:02:12.330687	Generic	900	7mm gearbox bushing	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	b637ed5c-35ee-4896-95e6-a75d045e64d3	t	f	\N	\N	\N	\N
a5da2586-ced7-4017-a1ad-ac561e9efe0e	Pinion Gear D-Shape	Pinion Gear	\N	2026-05-16 16:00:54.499132	SHS	540	D-shape motor pinion	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	51e17518-1040-4359-89f6-32d392564fac	t	f	\N	\N	\N	\N
edf73423-d3de-4e78-be28-2da4dac6c2ea	V3 CNC Motor Cage	Motor Cage	\N	2026-05-16 16:02:31.689262	Generic	1460	Aluminum CNC motor cage	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	04ebc0ba-bd4d-4d91-a966-47504ec6a77e	t	f	\N	\N	\N	\N
9f32205b-f64f-443c-b15f-8388614d387f	Cylinder Head Mushroom	Cylinder Head	\N	2026-05-16 15:59:56.117256	SHS	720	Mushroom dampening head	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	575084aa-39c7-48dd-b674-25d013d85c72	t	f	\N	\N	\N	\N
7185d422-f179-4961-8f2b-4fe53ddca6e5	Cylinder Head Mushroom Test 2	Compression	\N	2026-07-02 02:22:49.67873	ES H ES	120000	Updated by admin	2026-07-02 04:02:12.300714	a720623c-28d9-4d6b-b06b-059397718d18	0	5	20	26606ced-0f86-4842-ac6e-7c78665d47a5	575084aa-39c7-48dd-b674-25d013d85c72	t	f	\N	\N	\N	\N
2759fad0-6c63-4f44-b85a-84b1da81db1f	Cylinder Head Mushroom Test 2	Compression	\N	2026-07-02 02:36:13.855307	ES H ES	120000	Updated by admin	2026-07-02 04:02:37.070595	a720623c-28d9-4d6b-b06b-059397718d18	0	5	20	26606ced-0f86-4842-ac6e-7c78665d47a5	575084aa-39c7-48dd-b674-25d013d85c72	t	f	\N	\N	\N	\N
effdc7d8-c961-46e8-9b63-a74fb54f179b	Cylinder Head Mushroom Test 2	Compression	\N	2026-07-02 02:23:50.446918	ES H ES	120000	Updated by admin	2026-07-16 16:04:03.769773	a720623c-28d9-4d6b-b06b-059397718d18	0	5	20	26606ced-0f86-4842-ac6e-7c78665d47a5	575084aa-39c7-48dd-b674-25d013d85c72	t	f	\N	\N	\N	\N
9adfb219-3f23-4377-bddb-844d54977fe7	Gearset 13:1	Gearset	\N	2026-05-16 16:00:48.620938	SHS	720	High speed gear ratio	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	93e59828-10d1-40a7-9bad-dd2c0c1b221f	t	f	\N	\N	\N	\N
87b10bd9-c598-4a93-9ba3-0800b72d1f77	M110 Spring	Spring	\N	2026-05-16 16:00:36.512967	SHS	240	Main gearbox spring	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	ca3b957c-6798-441c-b220-7b6ef92a5e63	t	f	\N	\N	\N	\N
578402c0-463a-4716-9368-69b324c3eb9f	Piston Head Mushroom	Piston Head	\N	2026-05-16 16:00:20.66949	SHS	365	Mushroom piston head	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	d4ac593d-562a-4c0e-8b65-fabb4756d381	t	f	\N	\N	\N	\N
8a0f9d15-2674-4470-9384-e1f8694af039	Piston Head Flat Type	Compression	\N	2026-07-02 12:02:47.417101	SHS	720	testing category string insert data	2026-07-03 00:20:00.690273	a720623c-28d9-4d6b-b06b-059397718d18	1	0	10	26606ced-0f86-4842-ac6e-7c78665d47a5	d4ac593d-562a-4c0e-8b65-fabb4756d381	t	f	\N	\N	\N	\N
9c7b633b-5918-41e8-a29b-a7131129fad2	Spring Guide X-Type	Spring Guide	\N	2026-05-16 16:00:43.585725	CYMA	900	X-shape spring guide	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	4fe4869b-047e-4c99-9b3f-5b955ac73b66	t	f	\N	\N	\N	\N
ba950c28-9454-4f64-9797-62cbe560009c	Standard Trigger	Trigger	\N	2026-05-16 16:02:36.750191	King Arms	900	Default stock trigger	2026-06-30 13:37:50.95838	a720623c-28d9-4d6b-b06b-059397718d18	10	0	\N	26606ced-0f86-4842-ac6e-7c78665d47a5	00d6d6a0-8ed8-4bc6-889e-fb7cbe22c9e1	t	f	\N	\N	\N	\N
12aa2765-7620-4c75-a0a3-728f72a3c743	Green Gas Test Can	Consumable	\N	2026-07-29 03:04:55.919799	\N	\N	\N	2026-07-29 03:04:57.100695	a720623c-28d9-4d6b-b06b-059397718d18	9	5	100	246c8338-c71e-4570-ade3-3dff42b6f3bb	f40b6eb9-51e8-417d-b835-3edc82d0a1c9	t	f	\N	\N	\N	\N
7cd76a8a-f8e5-4a08-8241-9e77e352dc44	Case9 Gas ConvFactor	Consumable	\N	2026-07-29 03:14:36.348235	\N	\N	\N	2026-07-29 03:15:24.570015	a720623c-28d9-4d6b-b06b-059397718d18	0	2	50	246c8338-c71e-4570-ade3-3dff42b6f3bb	f40b6eb9-51e8-417d-b835-3edc82d0a1c9	t	f	\N	\N	0.5000	\N
8b6608d4-12f4-4163-a9aa-db8398022fc0	Case2 Clamp Test	Consumable	\N	2026-07-29 03:09:01.346988	\N	\N	\N	2026-07-29 03:09:05.275071	a720623c-28d9-4d6b-b06b-059397718d18	4	2	100	246c8338-c71e-4570-ade3-3dff42b6f3bb	f40b6eb9-51e8-417d-b835-3edc82d0a1c9	t	f	\N	\N	\N	\N
44a1f28e-0e90-44b4-aeb1-c073d859ab61	Case5 No Data Test	Consumable	\N	2026-07-29 03:13:40.667105	\N	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18	0	2	50	246c8338-c71e-4570-ade3-3dff42b6f3bb	f40b6eb9-51e8-417d-b835-3edc82d0a1c9	t	f	\N	\N	\N	\N
\.


--
-- Data for Name: purchase_order_items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.purchase_order_items (id, po_id, part_id, quantity, price) FROM stdin;
82b6828b-359f-4eb3-90d5-2d110efbed5f	6e3ed986-6464-4df5-8a34-54cf4f61c81f	60c1fb01-765a-442e-b315-4ceeb2e0b777	10	1000000.00
\.


--
-- Data for Name: purchase_order_status_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.purchase_order_status_history (id, created_at, updated_at, new_status, previous_status, reason, changed_by, purchase_order_id) FROM stdin;
2e69a256-cd48-41bb-b39c-aa6d7ca64304	2026-07-22 17:08:43.924221	2026-07-22 17:08:43.924221	DRAFT	\N	\N	\N	6e3ed986-6464-4df5-8a34-54cf4f61c81f
c93b7194-2609-4f99-adb2-1a6636df8d20	2026-07-22 17:10:54.316682	2026-07-22 17:10:54.316682	ORDERED	DRAFT	\N	\N	6e3ed986-6464-4df5-8a34-54cf4f61c81f
944ae102-589c-4daa-b24e-bd1e1763ede7	2026-07-22 17:13:28.893032	2026-07-22 17:13:28.893032	RECEIVED	ORDERED	Goods receipt 8cce1259-aca4-4f2e-afde-6953f756626c	\N	6e3ed986-6464-4df5-8a34-54cf4f61c81f
\.


--
-- Data for Name: purchase_orders; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.purchase_orders (id, tenant_id, supplier_id, po_number, order_date, status, total_amount, created_at) FROM stdin;
6e3ed986-6464-4df5-8a34-54cf4f61c81f	a720623c-28d9-4d6b-b06b-059397718d18	a848fd39-cbfc-47f8-a1f4-3771b8556a45	PO-1784714923910	2026-07-22	RECEIVED	10000000.00	2026-07-22 17:08:43.911265
\.


--
-- Data for Name: purchase_request_authorization_items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.purchase_request_authorization_items (id, authorized_qty, max_value, part_id, pra_id) FROM stdin;
650eaf2e-cded-4dbb-967a-67f67ee8dc7a	100	600000.00	60c1fb01-765a-442e-b315-4ceeb2e0b777	d0ffbfc0-4779-455b-8a0c-38b67dcdad50
87aef6d2-e76d-4f94-9f39-f919bb012139	5	100000000.00	60c1fb01-765a-442e-b315-4ceeb2e0b777	7ef28536-d8b6-43ba-83b3-bde8aa2e6628
b5c86f2d-82ee-4e62-8b6e-7ab456d0b58a	20	100000000.00	60c1fb01-765a-442e-b315-4ceeb2e0b777	cceb6ff3-a586-4795-aabc-00e6bfc36e07
eddc4a42-12af-46ed-aae5-0bb70d57de1d	6	200000.00	42af7a38-00f7-4803-987d-b593d8eed52e	4acc0dfa-37a4-43f2-8331-c70865c616a8
ba4b315d-1b02-4c1e-9390-662524fab034	6	200000.00	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	8ca7fca9-6af3-44a6-a604-df3410663992
\.


--
-- Data for Name: purchase_request_authorization_status_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.purchase_request_authorization_status_history (id, created_at, updated_at, new_status, previous_status, reason, changed_by, purchase_request_authorization_id) FROM stdin;
3c74b7b9-8048-4ef3-b59c-2598573a7fe2	2026-07-27 16:41:55.69691	2026-07-27 16:41:55.69691	ACTIVE	\N	\N	87526b10-4357-4d4b-a578-de4e1b317d75	d0ffbfc0-4779-455b-8a0c-38b67dcdad50
da5080fc-57ab-4fa3-9442-6e3901886a44	2026-07-27 16:41:55.848789	2026-07-27 16:41:55.848789	ACTIVE	\N	\N	87526b10-4357-4d4b-a578-de4e1b317d75	7ef28536-d8b6-43ba-83b3-bde8aa2e6628
21e7bf92-7d75-4502-863f-2b370459d0fe	2026-07-27 16:41:55.986225	2026-07-27 16:41:55.986225	ACTIVE	\N	\N	3a6a229b-9797-499c-ba1a-9f48dad36695	cceb6ff3-a586-4795-aabc-00e6bfc36e07
305829a4-0077-47a7-96fd-21e789487042	2026-07-27 16:44:47.360465	2026-07-27 16:44:47.360465	FULFILLED	ACTIVE	Recomputed from Realisasi fulfillment	\N	7ef28536-d8b6-43ba-83b3-bde8aa2e6628
3e2d3f4d-fcf7-41ff-b047-dbeef0bdd0e4	2026-07-28 15:46:42.098274	2026-07-28 15:46:42.098274	ACTIVE	\N	\N	134b31e0-b277-4bfc-b810-5614f4773684	4acc0dfa-37a4-43f2-8331-c70865c616a8
84f8312c-944e-4ca0-83a3-dad47c203642	2026-07-28 15:46:43.179963	2026-07-28 15:46:43.179963	FULFILLED	ACTIVE	Recomputed from Realisasi fulfillment	\N	4acc0dfa-37a4-43f2-8331-c70865c616a8
5870e202-0362-4fea-9efd-a4b18b5a5bc3	2026-07-29 03:14:37.2583	2026-07-29 03:14:37.2583	ACTIVE	\N	\N	1af5b908-3a5b-4e98-a2e3-a382bf204c89	8ca7fca9-6af3-44a6-a604-df3410663992
2a816fe8-1320-4498-b61e-a9705419d897	2026-07-29 03:14:37.947025	2026-07-29 03:14:37.947025	FULFILLED	ACTIVE	Recomputed from Realisasi fulfillment	\N	8ca7fca9-6af3-44a6-a604-df3410663992
\.


--
-- Data for Name: purchase_request_authorizations; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.purchase_request_authorizations (id, created_at, updated_at, status, approved_by, tenant_id) FROM stdin;
d0ffbfc0-4779-455b-8a0c-38b67dcdad50	2026-07-27 16:41:55.686653	2026-07-27 16:41:55.686653	ACTIVE	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
cceb6ff3-a586-4795-aabc-00e6bfc36e07	2026-07-27 16:41:55.986225	2026-07-27 16:41:55.986225	ACTIVE	3a6a229b-9797-499c-ba1a-9f48dad36695	a720623c-28d9-4d6b-b06b-059397718d18
7ef28536-d8b6-43ba-83b3-bde8aa2e6628	2026-07-27 16:41:55.848789	2026-07-27 16:44:47.360465	FULFILLED	87526b10-4357-4d4b-a578-de4e1b317d75	a720623c-28d9-4d6b-b06b-059397718d18
4acc0dfa-37a4-43f2-8331-c70865c616a8	2026-07-28 15:46:42.093952	2026-07-28 15:46:43.184811	FULFILLED	134b31e0-b277-4bfc-b810-5614f4773684	a720623c-28d9-4d6b-b06b-059397718d18
8ca7fca9-6af3-44a6-a604-df3410663992	2026-07-29 03:14:37.250001	2026-07-29 03:14:37.951602	FULFILLED	1af5b908-3a5b-4e98-a2e3-a382bf204c89	a720623c-28d9-4d6b-b06b-059397718d18
\.


--
-- Data for Name: purchase_request_items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.purchase_request_items (id, pr_id, part_id, quantity) FROM stdin;
9455109a-0e4f-407d-95dc-c88c2093dcf0	462d1e0d-d5f1-4b3c-8880-af3cc6f20629	60c1fb01-765a-442e-b315-4ceeb2e0b777	2
d6770a7d-c290-4680-b223-9202559ce9cb	77819b5e-68e4-4f37-bc7f-307275246778	60c1fb01-765a-442e-b315-4ceeb2e0b777	100
c06e5160-a86b-40f4-8e06-e0f6a726fca9	2d23c808-971f-4666-b662-b9e0431b937f	60c1fb01-765a-442e-b315-4ceeb2e0b777	5
c43daa42-5ef0-4522-805b-99b4b67774f7	e48a3aa2-4435-44ea-918d-45f87b288df9	60c1fb01-765a-442e-b315-4ceeb2e0b777	20
2b6bd5a3-1352-4d2b-b864-37e9be0124c5	6ccb5178-c123-4808-8af8-3f8761cf8c33	42af7a38-00f7-4803-987d-b593d8eed52e	1
f21618e7-0cd6-493e-8e7d-36722049c986	6dcea281-438e-4872-9287-52336754dc54	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	1
\.


--
-- Data for Name: purchase_request_status_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.purchase_request_status_history (id, created_at, updated_at, new_status, previous_status, reason, changed_by, purchase_request_id) FROM stdin;
21cb33d1-9f95-4dbb-92ea-f02fdcb0f8f3	2026-07-22 16:46:36.473908	2026-07-22 16:46:36.473908	PENDING	\N	\N	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	462d1e0d-d5f1-4b3c-8880-af3cc6f20629
d16bc521-0c0c-4f5d-b406-563f4afa7907	2026-07-22 16:46:56.683454	2026-07-22 16:46:56.683454	APPROVED	PENDING	\N	\N	462d1e0d-d5f1-4b3c-8880-af3cc6f20629
d8d3c54e-d8f0-4f29-a9a3-530f01e6909e	2026-07-22 17:08:43.922578	2026-07-22 17:08:43.922578	ORDERED	APPROVED	Ordered via purchase order PO-1784714923910	\N	462d1e0d-d5f1-4b3c-8880-af3cc6f20629
93f8ca43-c91d-4be5-9826-fc6d92ddd427	2026-07-27 16:38:07.539186	2026-07-27 16:38:07.539186	PENDING	\N	\N	4c15fa8d-df65-49ff-8669-c0bfa2f351cf	77819b5e-68e4-4f37-bc7f-307275246778
de65f0e6-4db2-409c-be97-62e27488e8bf	2026-07-27 16:38:08.021386	2026-07-27 16:38:08.021386	APPROVED	PENDING	\N	\N	77819b5e-68e4-4f37-bc7f-307275246778
edc64201-d534-4c0c-b656-c9f5b6e91950	2026-07-27 16:38:08.253593	2026-07-27 16:38:08.253593	PENDING	\N	\N	4c15fa8d-df65-49ff-8669-c0bfa2f351cf	2d23c808-971f-4666-b662-b9e0431b937f
bcb57720-4e16-4dcd-9e61-7d103f85c8f0	2026-07-27 16:38:08.632625	2026-07-27 16:38:08.632625	APPROVED	PENDING	\N	\N	2d23c808-971f-4666-b662-b9e0431b937f
2c4b1b6f-2001-401f-b697-787c8f7cec89	2026-07-27 16:38:08.824711	2026-07-27 16:38:08.824711	PENDING	\N	\N	4c15fa8d-df65-49ff-8669-c0bfa2f351cf	e48a3aa2-4435-44ea-918d-45f87b288df9
242d9ff8-8d38-4b74-b4b9-96fda9b5e29e	2026-07-27 16:38:09.29506	2026-07-27 16:38:09.29506	APPROVED	PENDING	\N	\N	e48a3aa2-4435-44ea-918d-45f87b288df9
3be8ea27-c14b-4334-ad0c-c58726c761ea	2026-07-28 15:46:41.178835	2026-07-28 15:46:41.178835	PENDING	\N	\N	e8c905e4-a07e-4183-ab47-7a18d0ef64ae	6ccb5178-c123-4808-8af8-3f8761cf8c33
e3f09de2-3268-4bfe-8d1c-4a811d453280	2026-07-28 15:46:41.890992	2026-07-28 15:46:41.890992	APPROVED	PENDING	\N	\N	6ccb5178-c123-4808-8af8-3f8761cf8c33
993c57b7-baf1-4fd3-979c-455daddf352e	2026-07-29 03:14:36.747735	2026-07-29 03:14:36.747735	PENDING	\N	\N	34191b8e-4292-478c-8aa9-92fd41968bd5	6dcea281-438e-4872-9287-52336754dc54
a6961dc0-2a7a-4151-b366-e21546f7fc75	2026-07-29 03:14:37.133649	2026-07-29 03:14:37.133649	APPROVED	PENDING	\N	\N	6dcea281-438e-4872-9287-52336754dc54
\.


--
-- Data for Name: purchase_requests; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.purchase_requests (id, tenant_id, requested_by, status, created_at, po_id, pra_id) FROM stdin;
462d1e0d-d5f1-4b3c-8880-af3cc6f20629	a720623c-28d9-4d6b-b06b-059397718d18	7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	ORDERED	2026-07-22 16:46:36.458589	6e3ed986-6464-4df5-8a34-54cf4f61c81f	\N
77819b5e-68e4-4f37-bc7f-307275246778	a720623c-28d9-4d6b-b06b-059397718d18	4c15fa8d-df65-49ff-8669-c0bfa2f351cf	AUTHORIZED	2026-07-27 16:38:07.532413	\N	d0ffbfc0-4779-455b-8a0c-38b67dcdad50
2d23c808-971f-4666-b662-b9e0431b937f	a720623c-28d9-4d6b-b06b-059397718d18	4c15fa8d-df65-49ff-8669-c0bfa2f351cf	AUTHORIZED	2026-07-27 16:38:08.25142	\N	7ef28536-d8b6-43ba-83b3-bde8aa2e6628
e48a3aa2-4435-44ea-918d-45f87b288df9	a720623c-28d9-4d6b-b06b-059397718d18	4c15fa8d-df65-49ff-8669-c0bfa2f351cf	AUTHORIZED	2026-07-27 16:38:08.822313	\N	cceb6ff3-a586-4795-aabc-00e6bfc36e07
6ccb5178-c123-4808-8af8-3f8761cf8c33	a720623c-28d9-4d6b-b06b-059397718d18	e8c905e4-a07e-4183-ab47-7a18d0ef64ae	AUTHORIZED	2026-07-28 15:46:41.171623	\N	4acc0dfa-37a4-43f2-8331-c70865c616a8
6dcea281-438e-4872-9287-52336754dc54	a720623c-28d9-4d6b-b06b-059397718d18	34191b8e-4292-478c-8aa9-92fd41968bd5	AUTHORIZED	2026-07-29 03:14:36.746347	\N	8ca7fca9-6af3-44a6-a604-df3410663992
\.


--
-- Data for Name: realisasi_items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.realisasi_items (id, actual_unit_price, allocated_landed_cost, conversion_factor, purchase_uom, purchased_qty, part_id, pra_item_id, realisasi_id) FROM stdin;
13c84202-caac-426e-b565-a6b26ec29aba	100000.00	100000.00	1.00	pcs	1	60c1fb01-765a-442e-b315-4ceeb2e0b777	b5c86f2d-82ee-4e62-8b6e-7ab456d0b58a	90df729d-d6d6-437f-b98c-bae1fc6d6097
2fcea47f-a705-4055-a20c-f1bcd2ed6b60	100000.00	500000.00	1.00	pcs	5	60c1fb01-765a-442e-b315-4ceeb2e0b777	650eaf2e-cded-4dbb-967a-67f67ee8dc7a	c84f3748-374b-470b-b05f-94e2f7583838
64913e52-1bbd-4fb7-8027-9f39339f250e	100000.00	300000.00	1.00	pcs	3	60c1fb01-765a-442e-b315-4ceeb2e0b777	650eaf2e-cded-4dbb-967a-67f67ee8dc7a	00b03b33-3eb4-41b2-9cf4-a26ec9fdbe88
251fce59-19f7-471d-8963-88681cbd03dd	100000.00	300000.00	1.00	pcs	3	60c1fb01-765a-442e-b315-4ceeb2e0b777	87aef6d2-e76d-4f94-9f39-f919bb012139	5247421a-8533-4ee3-9f12-368603aa4d92
1b9f0883-b49d-4bf9-b807-d91fb8a10441	100000.00	300000.00	1.00	pcs	3	60c1fb01-765a-442e-b315-4ceeb2e0b777	87aef6d2-e76d-4f94-9f39-f919bb012139	9aa060e9-7da2-4b7c-bcf9-727829ae4702
02a2fea5-dcbc-4224-b073-a122a0c71952	100000.00	200000.00	1.00	pcs	2	60c1fb01-765a-442e-b315-4ceeb2e0b777	87aef6d2-e76d-4f94-9f39-f919bb012139	2a7f9efb-17bd-44e8-ab35-fc85e407de74
439fb00f-4e18-431d-a7d0-98a6a853da56	100000.00	100000.00	1.00	pcs	1	60c1fb01-765a-442e-b315-4ceeb2e0b777	b5c86f2d-82ee-4e62-8b6e-7ab456d0b58a	094faa88-a9d3-46dd-a331-5f9b26de8edf
00361917-9b8d-442b-9757-c6f88789a933	100000.00	115000.00	1.00	pcs	1	60c1fb01-765a-442e-b315-4ceeb2e0b777	b5c86f2d-82ee-4e62-8b6e-7ab456d0b58a	872b8a5f-a450-4589-9b1d-9dca44e0e731
413edbbd-a60f-4e4a-9b33-f8f901b93d7f	50000.00	120000.00	1.00	pcs	2	60c1fb01-765a-442e-b315-4ceeb2e0b777	b5c86f2d-82ee-4e62-8b6e-7ab456d0b58a	6d617d66-18ca-4554-82b3-27f326b3d85b
9e05f3d4-d222-420a-bd97-2f98713b4244	50000.00	50000.00	1.00	pcs	1	60c1fb01-765a-442e-b315-4ceeb2e0b777	b5c86f2d-82ee-4e62-8b6e-7ab456d0b58a	acd3bf7a-c597-424b-8636-4d019971c3fa
ae89f761-161d-4cce-9e8a-65674076ab5d	50000.00	50000.00	1.00	pcs	1	60c1fb01-765a-442e-b315-4ceeb2e0b777	b5c86f2d-82ee-4e62-8b6e-7ab456d0b58a	13974068-5861-4ef6-80d3-22f3c794e8fe
80208857-7dcb-4bab-86f9-4f96928ab961	142200.00	142200.00	6.00	paket	1	42af7a38-00f7-4803-987d-b593d8eed52e	eddc4a42-12af-46ed-aae5-0bb70d57de1d	98a97fcb-68da-40a8-aff2-248890e1a91a
fdbd7b90-8d84-4e71-8fee-ef7ad72997e8	90000.00	90000.00	6.00	paket	1	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	ba4b315d-1b02-4c1e-9390-662524fab034	3f247932-6b0a-4344-a242-44f39e2696bf
\.


--
-- Data for Name: realisasi_status_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.realisasi_status_history (id, created_at, updated_at, new_status, previous_status, reason, changed_by, realisasi_id) FROM stdin;
68e472fd-8151-4693-897c-3046e84cd99b	2026-07-27 16:42:14.933017	2026-07-27 16:42:14.933017	APPROVED	\N	\N	87526b10-4357-4d4b-a578-de4e1b317d75	90df729d-d6d6-437f-b98c-bae1fc6d6097
23f66616-ab0d-435c-ad3c-cd16248ab93b	2026-07-27 16:42:15.095754	2026-07-27 16:42:15.095754	APPROVED	\N	\N	87526b10-4357-4d4b-a578-de4e1b317d75	c84f3748-374b-470b-b05f-94e2f7583838
32ea0d97-9d40-4ca0-bd4e-6dd3ec3e86b6	2026-07-27 16:44:02.512533	2026-07-27 16:44:02.512533	PENDING_APPROVAL	\N	Exceeds PRA ceiling, escalated for Owner approval	3a6a229b-9797-499c-ba1a-9f48dad36695	00b03b33-3eb4-41b2-9cf4-a26ec9fdbe88
f59c8d4f-002d-4e4f-bced-4bc3a8a76c0d	2026-07-27 16:44:03.015506	2026-07-27 16:44:03.015506	APPROVED	PENDING_APPROVAL	\N	87526b10-4357-4d4b-a578-de4e1b317d75	00b03b33-3eb4-41b2-9cf4-a26ec9fdbe88
f10f568a-7620-42bb-a8aa-37c9399d9673	2026-07-27 16:44:46.472559	2026-07-27 16:44:46.472559	APPROVED	\N	\N	3a6a229b-9797-499c-ba1a-9f48dad36695	5247421a-8533-4ee3-9f12-368603aa4d92
2457fa4b-28ea-42b2-943f-424f1c19c980	2026-07-27 16:44:46.638197	2026-07-27 16:44:46.638197	PENDING_APPROVAL	\N	Exceeds PRA ceiling, escalated for Owner approval	3a6a229b-9797-499c-ba1a-9f48dad36695	9aa060e9-7da2-4b7c-bcf9-727829ae4702
0eeef51a-622f-402c-bf90-4faedc693155	2026-07-27 16:44:47.054803	2026-07-27 16:44:47.054803	FAILED	PENDING_APPROVAL	Seller cancelled the order, stock ran out	3a6a229b-9797-499c-ba1a-9f48dad36695	9aa060e9-7da2-4b7c-bcf9-727829ae4702
9763464d-4bd9-4d31-933a-71aef287de90	2026-07-27 16:44:47.360465	2026-07-27 16:44:47.360465	APPROVED	\N	\N	3a6a229b-9797-499c-ba1a-9f48dad36695	2a7f9efb-17bd-44e8-ab35-fc85e407de74
1030dc2d-8adb-4f79-9d2b-1fcc337b1763	2026-07-27 16:45:52.448697	2026-07-27 16:45:52.448697	APPROVED	\N	\N	87526b10-4357-4d4b-a578-de4e1b317d75	094faa88-a9d3-46dd-a331-5f9b26de8edf
07eeb71a-f183-4d80-9101-7cfb3251e460	2026-07-27 16:45:52.842618	2026-07-27 16:45:52.842618	SUPERSEDED	APPROVED	Forgot to include shipping cost	87526b10-4357-4d4b-a578-de4e1b317d75	094faa88-a9d3-46dd-a331-5f9b26de8edf
4a685885-be78-462a-9b1f-19bc0cb03d29	2026-07-27 16:45:52.851147	2026-07-27 16:45:52.851147	APPROVED	\N	\N	87526b10-4357-4d4b-a578-de4e1b317d75	872b8a5f-a450-4589-9b1d-9dca44e0e731
b6444e43-5f2f-4255-8225-ff8063a1f169	2026-07-28 12:30:38.274978	2026-07-28 12:30:38.274978	APPROVED	\N	\N	87526b10-4357-4d4b-a578-de4e1b317d75	6d617d66-18ca-4554-82b3-27f326b3d85b
8096aaa8-e7c7-4c78-b836-0593e778ed5c	2026-07-28 13:47:59.684701	2026-07-28 13:47:59.684701	APPROVED	\N	\N	87526b10-4357-4d4b-a578-de4e1b317d75	acd3bf7a-c597-424b-8636-4d019971c3fa
93fe0458-da0a-4279-9828-633f995296ca	2026-07-28 13:48:00.858049	2026-07-28 13:48:00.858049	APPROVED	\N	\N	87526b10-4357-4d4b-a578-de4e1b317d75	13974068-5861-4ef6-80d3-22f3c794e8fe
753acd2f-4096-46d9-b904-c5dd5a2e1e17	2026-07-28 15:46:43.170275	2026-07-28 15:46:43.170275	APPROVED	\N	\N	e8c905e4-a07e-4183-ab47-7a18d0ef64ae	98a97fcb-68da-40a8-aff2-248890e1a91a
07c632aa-0b65-4697-8f12-d7dbbbd99272	2026-07-29 03:14:37.934527	2026-07-29 03:14:37.934527	APPROVED	\N	\N	34191b8e-4292-478c-8aa9-92fd41968bd5	3f247932-6b0a-4344-a242-44f39e2696bf
\.


--
-- Data for Name: realisasis; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.realisasis (id, created_at, updated_at, attachments, channel, external_order_ref, insurance, payment_method, payment_ref, platform_voucher, purchased_at, reimbursed_at, reimbursement_status, retro_purchase_flag, seller_discount, service_fee, shipping, status, subtotal, superseded_reason, total_cost, variance_status, approved_by, created_by, pra_id, reimbursed_to, supersedes_id, supplier_id, tenant_id) FROM stdin;
90df729d-d6d6-437f-b98c-bae1fc6d6097	2026-07-27 16:42:14.925837	2026-07-27 16:42:14.925837	\N	Tokopedia	SEG-TEST-2	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-27 16:42:14.925234	\N	NOT_APPLICABLE	f	0.00	0.00	0.00	APPROVED	100000.00	\N	100000.00	WITHIN_CEILING	\N	87526b10-4357-4d4b-a578-de4e1b317d75	cceb6ff3-a586-4795-aabc-00e6bfc36e07	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
c84f3748-374b-470b-b05f-94e2f7583838	2026-07-27 16:42:15.095754	2026-07-27 16:42:15.095754	\N	Tokopedia	A1-WITHIN-CEILING	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-27 16:42:15.095754	\N	NOT_APPLICABLE	f	0.00	0.00	0.00	APPROVED	500000.00	\N	500000.00	WITHIN_CEILING	\N	87526b10-4357-4d4b-a578-de4e1b317d75	d0ffbfc0-4779-455b-8a0c-38b67dcdad50	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
00b03b33-3eb4-41b2-9cf4-a26ec9fdbe88	2026-07-27 16:44:02.511529	2026-07-27 16:44:03.018215	\N	Shopee	A2-ESCALATED	0.00	PERSONAL_REIMBURSABLE	\N	0.00	2026-07-27 16:44:02.510441	\N	PENDING	f	0.00	0.00	0.00	APPROVED	300000.00	\N	300000.00	ESCALATED	87526b10-4357-4d4b-a578-de4e1b317d75	3a6a229b-9797-499c-ba1a-9f48dad36695	d0ffbfc0-4779-455b-8a0c-38b67dcdad50	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
5247421a-8533-4ee3-9f12-368603aa4d92	2026-07-27 16:44:46.472559	2026-07-27 16:44:46.472559	\N	Tokopedia	B1-OK	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-27 16:44:46.472559	\N	NOT_APPLICABLE	f	0.00	0.00	0.00	APPROVED	300000.00	\N	300000.00	WITHIN_CEILING	\N	3a6a229b-9797-499c-ba1a-9f48dad36695	7ef28536-d8b6-43ba-83b3-bde8aa2e6628	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
9aa060e9-7da2-4b7c-bcf9-727829ae4702	2026-07-27 16:44:46.638197	2026-07-27 16:44:47.060557	\N	Tokopedia	B2-WILL-FAIL	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-27 16:44:46.638197	\N	NOT_APPLICABLE	f	0.00	0.00	0.00	FAILED	300000.00	\N	300000.00	ESCALATED	\N	3a6a229b-9797-499c-ba1a-9f48dad36695	7ef28536-d8b6-43ba-83b3-bde8aa2e6628	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
2a7f9efb-17bd-44e8-ab35-fc85e407de74	2026-07-27 16:44:47.360465	2026-07-27 16:44:47.360465	\N	Tokopedia	B3-RETRY-OK	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-27 16:44:47.360465	\N	NOT_APPLICABLE	f	0.00	0.00	0.00	APPROVED	200000.00	\N	200000.00	WITHIN_CEILING	\N	3a6a229b-9797-499c-ba1a-9f48dad36695	7ef28536-d8b6-43ba-83b3-bde8aa2e6628	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
094faa88-a9d3-46dd-a331-5f9b26de8edf	2026-07-27 16:45:52.448697	2026-07-27 16:45:52.842618	\N	Tokopedia	SUPERSEDE-ORIG	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-27 16:45:52.448697	\N	NOT_APPLICABLE	f	0.00	0.00	0.00	SUPERSEDED	100000.00	Forgot to include shipping cost	100000.00	WITHIN_CEILING	\N	87526b10-4357-4d4b-a578-de4e1b317d75	cceb6ff3-a586-4795-aabc-00e6bfc36e07	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
872b8a5f-a450-4589-9b1d-9dca44e0e731	2026-07-27 16:45:52.849108	2026-07-27 16:45:52.851985	\N	Tokopedia	SUPERSEDE-ORIG-V2	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-27 16:45:52.849108	\N	NOT_APPLICABLE	f	0.00	0.00	15000.00	APPROVED	100000.00	\N	115000.00	WITHIN_CEILING	\N	87526b10-4357-4d4b-a578-de4e1b317d75	cceb6ff3-a586-4795-aabc-00e6bfc36e07	\N	094faa88-a9d3-46dd-a331-5f9b26de8edf	\N	a720623c-28d9-4d6b-b06b-059397718d18
6d617d66-18ca-4554-82b3-27f326b3d85b	2026-07-28 12:30:38.263104	2026-07-28 12:30:38.263104	\N	Tokopedia	LANDEDCOST-TEST	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-28 12:30:38.258852	\N	NOT_APPLICABLE	f	0.00	0.00	20000.00	APPROVED	100000.00	\N	120000.00	WITHIN_CEILING	\N	87526b10-4357-4d4b-a578-de4e1b317d75	cceb6ff3-a586-4795-aabc-00e6bfc36e07	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
acd3bf7a-c597-424b-8636-4d019971c3fa	2026-07-28 13:47:59.663082	2026-07-28 13:47:59.663082	\N	Tokopedia	NOATTACH-TEST	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-28 13:47:59.641676	\N	NOT_APPLICABLE	f	0.00	0.00	0.00	APPROVED	50000.00	\N	50000.00	WITHIN_CEILING	\N	87526b10-4357-4d4b-a578-de4e1b317d75	cceb6ff3-a586-4795-aabc-00e6bfc36e07	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
13974068-5861-4ef6-80d3-22f3c794e8fe	2026-07-28 13:48:00.858049	2026-07-28 13:48:00.858049	https://example.com/receipt.pdf	Tokopedia	WITHATTACH-TEST	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-28 13:48:00.858049	\N	NOT_APPLICABLE	f	0.00	0.00	0.00	APPROVED	50000.00	\N	50000.00	WITHIN_CEILING	\N	87526b10-4357-4d4b-a578-de4e1b317d75	cceb6ff3-a586-4795-aabc-00e6bfc36e07	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
98a97fcb-68da-40a8-aff2-248890e1a91a	2026-07-28 15:46:43.143009	2026-07-28 15:46:43.143009	http://example.com/receipt.jpg	Tokopedia	CONVTEST-6-1785228402	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-28 15:46:43.143009	\N	NOT_APPLICABLE	f	0.00	0.00	0.00	APPROVED	142200.00	\N	142200.00	WITHIN_CEILING	\N	e8c905e4-a07e-4183-ab47-7a18d0ef64ae	4acc0dfa-37a4-43f2-8331-c70865c616a8	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
3f247932-6b0a-4344-a242-44f39e2696bf	2026-07-29 03:14:37.934527	2026-07-29 03:14:37.934527	http://example.com/r.jpg	Tokopedia	CASE9-1785269677	0.00	COMPANY_ACCOUNT	\N	0.00	2026-07-29 03:14:37.934527	\N	NOT_APPLICABLE	f	0.00	0.00	0.00	APPROVED	90000.00	\N	90000.00	WITHIN_CEILING	\N	34191b8e-4292-478c-8aa9-92fd41968bd5	8ca7fca9-6af3-44a6-a604-df3410663992	\N	\N	\N	a720623c-28d9-4d6b-b06b-059397718d18
\.


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.roles (id, name) FROM stdin;
1	admin
2	technician
3	finance
\.


--
-- Data for Name: service_event_parts; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.service_event_parts (id, service_event_id, part_id, quantity, cost) FROM stdin;
\.


--
-- Data for Name: service_events; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.service_events (id, tenant_id, airsoft_unit_id, technician_id, status, description, cost, service_date, created_at, updated_at, action_taken, event_type, issue, next_check_date, notes, work_order_id) FROM stdin;
a8224696-4d2b-4894-bb86-db8678564a51	a720623c-28d9-4d6b-b06b-059397718d18	30273c0e-40b9-4cbe-af69-0943fa3e70ec	\N	OPEN	\N	250000.00	2026-05-13	2026-05-13 19:19:02.790165	2026-05-13 19:19:02.790165	Replaced motor	REPAIR	Motor jammed	\N	Tested stable	\N
b199a3ea-1325-46cf-8cd4-7824c8a9d7fa	a720623c-28d9-4d6b-b06b-059397718d18	30273c0e-40b9-4cbe-af69-0943fa3e70ec	\N	OPEN	\N	250000.00	2026-05-13	2026-05-13 19:30:44.489146	2026-05-13 19:30:44.489146	Replaced motor	REPAIR	Motor jammed	\N	Tested stable	\N
0137bf78-a663-408a-b8b7-6bad2fad7b9a	a720623c-28d9-4d6b-b06b-059397718d18	30273c0e-40b9-4cbe-af69-0943fa3e70ec	\N	OPEN	\N	\N	2026-06-10	2026-06-10 14:20:15.947996	2026-06-10 14:20:15.947996	Replaced V3 Gearbox Shell QD Silver with V3 Gearbox Shell QD Silver	REPAIR	Part replacement	\N	\N	\N
94ca0265-b847-4028-953b-d1ed0cf388ae	a720623c-28d9-4d6b-b06b-059397718d18	30273c0e-40b9-4cbe-af69-0943fa3e70ec	\N	OPEN	\N	\N	2026-06-18	2026-06-18 15:53:27.654098	2026-06-18 15:53:27.654098	Replaced Cylinder Head Mushroom with Cylinder Head Mushroom	REPAIR	Part replacement	\N	\N	\N
61ed5eb3-8e7f-4154-9a74-d488e5911daf	a720623c-28d9-4d6b-b06b-059397718d18	30273c0e-40b9-4cbe-af69-0943fa3e70ec	\N	OPEN	\N	\N	2026-07-03	2026-07-03 00:20:00.581546	2026-07-03 00:20:00.581546	Replaced Piston Head Mushroom with Piston Head Flat Type	REPAIR	Part replacement	\N	\N	\N
\.


--
-- Data for Name: stock_adjustment_status_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.stock_adjustment_status_history (id, created_at, updated_at, new_status, previous_status, reason, changed_by, stock_adjustment_id) FROM stdin;
f5fb17b6-624d-446b-83ae-c4de86b966b0	2026-07-23 18:14:46.273266	2026-07-23 18:14:46.273266	PENDING	\N	\N	8c14cc96-e8e4-47f4-aadf-92ca2ad7d361	686a7956-7df4-4793-a1f7-76965e90a00b
266278fa-bfa1-4c78-bc84-e8b1386b45a9	2026-07-23 18:14:59.019241	2026-07-23 18:14:59.019241	APPROVED	PENDING	\N	2eafb813-ea2f-481f-9e58-9f6b479b3aa2	686a7956-7df4-4793-a1f7-76965e90a00b
f23f32c5-cd1f-45a5-9e45-08d4b8c673c8	2026-07-23 18:15:23.084652	2026-07-23 18:15:23.084652	PENDING	\N	\N	8c14cc96-e8e4-47f4-aadf-92ca2ad7d361	e1f5fd7b-c0b4-495b-bcbf-5632081fda41
1572773b-843d-41a8-9d9c-e16bd9ec73ec	2026-07-23 18:15:23.849894	2026-07-23 18:15:23.849894	REJECTED	PENDING	Recount confirmed original count was correct	2eafb813-ea2f-481f-9e58-9f6b479b3aa2	e1f5fd7b-c0b4-495b-bcbf-5632081fda41
\.


--
-- Data for Name: stock_adjustments; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.stock_adjustments (id, created_at, updated_at, quantity, reason, status, part_id, requested_by, tenant_id) FROM stdin;
686a7956-7df4-4793-a1f7-76965e90a00b	2026-07-23 18:14:46.266992	2026-07-23 18:14:59.019241	5	Employee forgot to scan 5 units on receipt	APPROVED	2264292d-62cc-4c45-9e0d-10db18668417	8c14cc96-e8e4-47f4-aadf-92ca2ad7d361	8417979f-b99b-4df6-b1a8-368f791e81fb
e1f5fd7b-c0b4-495b-bcbf-5632081fda41	2026-07-23 18:15:23.084652	2026-07-23 18:15:23.849894	-3	Miscounted, think 3 fewer	REJECTED	2264292d-62cc-4c45-9e0d-10db18668417	8c14cc96-e8e4-47f4-aadf-92ca2ad7d361	8417979f-b99b-4df6-b1a8-368f791e81fb
\.


--
-- Data for Name: stock_movements; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.stock_movements (id, tenant_id, part_id, movement_type, quantity, reference_type, reference_id, created_at, before_stock, after_stock, notes) FROM stdin;
0a34c0ea-acf8-4c1d-a673-38af6c5fb7ac	a720623c-28d9-4d6b-b06b-059397718d18	567330fb-5b64-4d3c-b818-a4819e541204	ADJUSTMENT	5	MANUAL	\N	2026-06-29 13:18:04.761572	0	5	restock
9eb4ef6c-0106-4f08-842b-72b316770eb0	a720623c-28d9-4d6b-b06b-059397718d18	ba950c28-9454-4f64-9797-62cbe560009c	PURCHASE	10	MANUAL	\N	2026-06-30 13:37:50.94753	0	10	Purchase from online shope
1cc3dc23-2f39-462f-bc77-97fcaa58ab4d	a720623c-28d9-4d6b-b06b-059397718d18	8a0f9d15-2674-4470-9384-e1f8694af039	PURCHASE	2	MANUAL	\N	2026-07-02 14:57:14.923384	0	2	Purchase from online shope
933dc6e9-c675-4dfb-971c-3921c1e5331f	a720623c-28d9-4d6b-b06b-059397718d18	8a0f9d15-2674-4470-9384-e1f8694af039	MAINTENANCE_USAGE	-1	WORK_ORDER	\N	2026-07-03 00:20:00.689269	2	1	Replacement during maintenance
90b4447b-32b8-425e-a03e-8f8f079d115f	a720623c-28d9-4d6b-b06b-059397718d18	60c1fb01-765a-442e-b315-4ceeb2e0b777	PURCHASE	10	PURCHASE	8cce1259-aca4-4f2e-afde-6953f756626c	2026-07-22 17:13:28.886713	0	10	Goods receipt for PO PO-1784714923910
7a522101-b9e5-4e7a-9d43-47ccff094743	a720623c-28d9-4d6b-b06b-059397718d18	60c1fb01-765a-442e-b315-4ceeb2e0b777	ADJUSTMENT	-10	MANUAL	\N	2026-07-25 17:58:34.966892	10	0	test low stock crossing
598b7446-132e-4a4f-b39c-4a26a1118e42	a720623c-28d9-4d6b-b06b-059397718d18	60c1fb01-765a-442e-b315-4ceeb2e0b777	ADJUSTMENT	0	MANUAL	\N	2026-07-25 17:58:53.917043	0	0	no-op, still at 0
5172851d-c8d9-4ce8-afd9-9124daffbb1c	a720623c-28d9-4d6b-b06b-059397718d18	60c1fb01-765a-442e-b315-4ceeb2e0b777	ADJUSTMENT	3	MANUAL	\N	2026-07-25 17:58:54.067019	0	3	restock a bit but still <= min? min is 0 so 3 crosses back above
eab426c9-df4f-49c4-82bb-ae891f0ce236	a720623c-28d9-4d6b-b06b-059397718d18	60c1fb01-765a-442e-b315-4ceeb2e0b777	PURCHASE	3	PURCHASE	81a85975-08cd-4efa-befd-611a2d7ea876	2026-07-27 16:45:08.836277	10	13	Goods receipt for Realisasi B1-OK
1d3206ed-ed67-46a0-b2b8-69a2e0466338	a720623c-28d9-4d6b-b06b-059397718d18	60c1fb01-765a-442e-b315-4ceeb2e0b777	PURCHASE	2	PURCHASE	4048cc8b-5505-4d53-9598-02c05b889736	2026-07-28 12:30:39.114471	13	15	Goods receipt for Realisasi LANDEDCOST-TEST
3594797f-3d5c-4c39-b246-58398d2ff574	a720623c-28d9-4d6b-b06b-059397718d18	60c1fb01-765a-442e-b315-4ceeb2e0b777	PURCHASE	1	PURCHASE	05cd06f2-7172-4860-a496-d1224fe77b6a	2026-07-28 13:48:01.641049	15	16	Goods receipt for Realisasi WITHATTACH-TEST
5634fa62-ab9f-4f17-9385-2966840f1407	a720623c-28d9-4d6b-b06b-059397718d18	42af7a38-00f7-4803-987d-b593d8eed52e	PURCHASE	6	PURCHASE	17b09ecd-30c1-44c4-a864-5cbddbf9e722	2026-07-28 15:46:44.29314	0	6	Goods receipt for Realisasi CONVTEST-6-1785228402
de9dadf5-e5b9-453c-a568-1feb312282f0	a720623c-28d9-4d6b-b06b-059397718d18	12aa2765-7620-4c75-a0a3-728f72a3c743	ADJUSTMENT	10	MANUAL	\N	2026-07-29 03:04:56.276512	0	10	seed for burn rate test
ab8fed13-b80c-404b-9e7d-7b9b7a4b32f8	a720623c-28d9-4d6b-b06b-059397718d18	12aa2765-7620-4c75-a0a3-728f72a3c743	CONSUMABLE_TAKE	-1	PART_INSTANCE	6030b099-2d2d-4d08-b611-78409759d55f	2026-07-29 03:04:57.100695	10	9	Taken via barcode TESTBURN-1785269096
bc9c6821-534d-4233-9575-ff44162f6dda	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	ADJUSTMENT	16	MANUAL	\N	2026-07-29 03:09:01.694802	0	16	seed
aa9590e6-2006-4751-b89f-de4e0885b11a	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	61dd6557-0f4a-4ff4-96b4-c4ed13eb107f	2026-07-29 03:09:02.062684	16	15	Taken via barcode CASE2-1785269341-1
b396d5f6-0b1a-44c6-bc3a-8043ca839177	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	0472ac70-3a38-475e-b790-04d0c5d3c69f	2026-07-29 03:09:02.341057	15	14	Taken via barcode CASE2-1785269342-2
76b6b19c-33ed-480b-a97d-5d7c1d6a5e4b	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	5defcd8d-56db-43b9-b29a-15a244f903a7	2026-07-29 03:09:02.635115	14	13	Taken via barcode CASE2-1785269342-3
f9abc581-6a88-4282-9d86-a6815a26f2e0	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	f18e2a7f-9277-4992-8219-5bed5a88e320	2026-07-29 03:09:02.918879	13	12	Taken via barcode CASE2-1785269342-4
873c6703-922f-457c-82fc-08737d7a3657	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	364b4983-e722-48c4-805d-edb2c68e4ffe	2026-07-29 03:09:03.198174	12	11	Taken via barcode CASE2-1785269342-5
93f28503-3d98-4a45-a989-3d7358b50054	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	f26ce9ad-7b0d-4b9c-8ade-2177ba232a15	2026-07-29 03:09:03.497301	11	10	Taken via barcode CASE2-1785269343-6
6c9b5b27-b25a-49fc-91e2-dfd06e17f86b	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	2770ec5d-7efe-4c68-8ffb-b14dad26224d	2026-07-29 03:09:03.813977	10	9	Taken via barcode CASE2-1785269343-7
7adfe98b-73f9-4723-9d50-61d3f1731347	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	4544377d-1615-49aa-b622-a2b9171e2aa3	2026-07-29 03:09:04.098435	9	8	Taken via barcode CASE2-1785269343-8
02b79843-4372-4a2f-9200-80c5ec97787b	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	19ef2e75-99f3-4096-98ab-4e9f9adb8ab2	2026-07-29 03:09:04.414011	8	7	Taken via barcode CASE2-1785269344-9
f241d69a-4635-4523-bc15-6f974cfb58d2	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	5099956a-c6c1-4570-97a7-aa5b1974172e	2026-07-29 03:09:04.697253	7	6	Taken via barcode CASE2-1785269344-10
62628fec-743a-4f07-82ff-e7685bf49fee	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	7b96cd21-dee9-4d78-a711-12f1bacf3509	2026-07-29 03:09:04.974752	6	5	Taken via barcode CASE2-1785269344-11
03460cd8-48c0-441e-b293-a0d5d6618b07	a720623c-28d9-4d6b-b06b-059397718d18	8b6608d4-12f4-4163-a9aa-db8398022fc0	CONSUMABLE_TAKE	-1	PART_INSTANCE	f3e17847-471a-4899-83e2-5b828bbd53ae	2026-07-29 03:09:05.275071	5	4	Taken via barcode CASE2-1785269345-12
ffbdbcb9-aa4c-4ba1-aded-a4e7886b7014	a720623c-28d9-4d6b-b06b-059397718d18	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	PURCHASE	6	PURCHASE	470a76f2-965e-42ef-b6fa-739f46debeda	2026-07-29 03:14:38.760436	0	6	Goods receipt for Realisasi CASE9-1785269677
367ef045-a773-4e63-aca8-71ecf885c96b	a720623c-28d9-4d6b-b06b-059397718d18	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	CONSUMABLE_TAKE	-1	PART_INSTANCE	162a646a-27f8-4ca4-90bd-a272f54c6e32	2026-07-29 03:15:05.178937	6	5	Taken via barcode CASE9SCAN-1785269704-1
8eb3dede-674e-4187-a33c-0a5075f0ebc0	a720623c-28d9-4d6b-b06b-059397718d18	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	CONSUMABLE_TAKE	-1	PART_INSTANCE	08a1ca49-9239-4249-aae6-4fa45d47161f	2026-07-29 03:15:05.479405	5	4	Taken via barcode CASE9SCAN-1785269705-2
b143f748-d521-4058-9183-af7fc16b944b	a720623c-28d9-4d6b-b06b-059397718d18	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	CONSUMABLE_TAKE	-1	PART_INSTANCE	1034c71d-0cc0-4c4b-a133-05c2e1e00c11	2026-07-29 03:15:05.779466	4	3	Taken via barcode CASE9SCAN-1785269705-3
c5bacad7-75bf-42c5-9ac6-8c744db7d2c2	a720623c-28d9-4d6b-b06b-059397718d18	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	CONSUMABLE_TAKE	-1	PART_INSTANCE	dd366b7e-5d03-4deb-84fa-83e7faa134e2	2026-07-29 03:15:06.096148	3	2	Taken via barcode CASE9SCAN-1785269705-4
62cfeab1-7669-4af9-a256-15f07327b9ea	a720623c-28d9-4d6b-b06b-059397718d18	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	CONSUMABLE_TAKE	-1	PART_INSTANCE	f13eb6e4-e21d-4e0c-acb9-5afcb68b1634	2026-07-29 03:15:06.413426	2	1	Taken via barcode CASE9SCAN-1785269706-5
13445d06-5e86-4d58-bc22-7e8d51b16637	a720623c-28d9-4d6b-b06b-059397718d18	7cd76a8a-f8e5-4a08-8241-9e77e352dc44	CONSUMABLE_TAKE	-1	PART_INSTANCE	e8b31a1c-6a12-4e42-bb3e-8c001cd7a64b	2026-07-29 03:15:06.72807	1	0	Taken via barcode CASE9SCAN-1785269706-6
\.


--
-- Data for Name: suppliers; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.suppliers (id, name, contact, created_at, tenant_id) FROM stdin;
7489e73a-5322-4c79-a62a-5664bc11e2f1	Airsoft Parts Wholesale Testing	tanugraha.richard@gmail.com	2026-07-22 16:40:00.60652	a720623c-28d9-4d6b-b06b-059397718d18
92e738e4-6472-4e1f-b506-c3f363eb8335	Megah Sport Jalang	tanugraha.richard@gmail.com	2026-07-22 16:40:21.311716	a720623c-28d9-4d6b-b06b-059397718d18
b21c060c-d948-43ae-a53c-b602fe4f6a14	Firly Similikiti	tanugraha.richard@gmail.com	2026-07-22 16:40:39.147172	a720623c-28d9-4d6b-b06b-059397718d18
aa23366f-5536-4962-962b-ccffdbcdbb6e	Hosana Halleluyah Airsoft	tanugraha.richard@gmail.com	2026-07-22 16:40:58.942557	a720623c-28d9-4d6b-b06b-059397718d18
c99d08f7-7774-482e-8537-eeb734a4f4ca	Logan tot	tanugraha.richard@gmail.com	2026-07-22 16:41:20.305128	a720623c-28d9-4d6b-b06b-059397718d18
a848fd39-cbfc-47f8-a1f4-3771b8556a45	Tokopedia	tanugraha.richard@gmail.com	2026-07-22 17:06:08.597423	a720623c-28d9-4d6b-b06b-059397718d18
\.


--
-- Data for Name: tenants; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.tenants (id, name, created_at, updated_at, code, active, parent_id) FROM stdin;
a720623c-28d9-4d6b-b06b-059397718d18	Blitz Tactical	2026-05-06 22:29:27.571254	\N	BLITZ_BDG	t	\N
aa950bb6-cb01-41ca-b134-56bb42b730bc	Franchise HQ	2026-07-23 01:57:11.510081	2026-07-23 01:57:11.510081	FRANCHISE_HQ	t	\N
8417979f-b99b-4df6-b1a8-368f791e81fb	Outlet 2	2026-07-23 01:57:23.362166	2026-07-23 01:57:23.362166	OUTLET_2	t	aa950bb6-cb01-41ca-b134-56bb42b730bc
\.


--
-- Data for Name: user_profiles; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.user_profiles (user_id, tenant_id, full_name, is_hq, created_at) FROM stdin;
\.


--
-- Data for Name: user_roles; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.user_roles (user_id, role_id) FROM stdin;
9be6bef2-2d9c-4cf6-870a-0fde06951139	2
00b23ffe-f73e-4299-893c-7f43213d6c90	2
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.users (id, email, password_hash, full_name, created_at, updated_at, role, tenant_id, scope) FROM stdin;
9be6bef2-2d9c-4cf6-870a-0fde06951139	puji@test.com	$2a$10$VMsqFX.C.Gk9ujTfZSah3.wfxIwNKn2hhEXyWem8gtwXOhWiu6pgy	Puji	2026-06-04 14:14:05.016785	2026-06-04 14:14:05.016785	TECHNICIAN	a720623c-28d9-4d6b-b06b-059397718d18	\N
00b23ffe-f73e-4299-893c-7f43213d6c90	dian@test.com	$2a$10$YwrU1kNsFlYgkktH0fRUY.pE2e5HWPH1NwnJHXqmaTfgVPj4pFPe2	Dian	2026-06-04 14:28:15.496131	2026-06-04 14:28:15.496131	TECHNICIAN	a720623c-28d9-4d6b-b06b-059397718d18	\N
fcfd3668-3ce8-447f-bc26-7fc2ebbd6055	outlet2@test.com	$2a$10$n6K97KtdJk82Yo416KnvwemalEfOgZgwuEt4SKc2n0DK4CHLnzfwi	Outlet Two User	2026-07-23 01:57:31.383262	2026-07-23 01:57:31.383262	OPERATOR	8417979f-b99b-4df6-b1a8-368f791e81fb	\N
8c14cc96-e8e4-47f4-aadf-92ca2ad7d361	operator_test@test.com	$2a$10$yOkPWCSTnS8RNoLvfn8XNuJGA1Y6DOLM7r19x0Bu661Mb.JPnmz0C	Operator Test	2026-07-23 18:08:30.288626	2026-07-23 18:08:30.288626	OPERATOR	8417979f-b99b-4df6-b1a8-368f791e81fb	\N
0712b260-ab30-4f13-a8ef-2245041d6472	blitz_regress_test@test.com	$2a$10$YlBnZgQ8ZZsX/pLd2pmRjOvSQzP3ynLWdiZN7Mtj7c/sTaTAkcmIy	Blitz Regress	2026-07-23 18:16:26.877666	2026-07-23 18:16:26.877666	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
c7e45822-a5fd-446a-8971-66eb0a6c1825	hq@test.com	$2a$10$ImoWOUgyO0IyMrLyPmtWieKUQwe4B5n8/GpOcp6wyGZy3zmNiATC6	HQ User	2026-07-23 01:57:31.599675	2026-07-23 01:57:31.599675	OPERATOR	aa950bb6-cb01-41ca-b134-56bb42b730bc	\N
2eafb813-ea2f-481f-9e58-9f6b479b3aa2	owner_test@test.com	$2a$10$38A.SwVw0wN.u031.IcE4Og3dAqYCRUAu4E1PQmUd8GJBQCio.yby	Owner Test	2026-07-23 18:08:29.987782	2026-07-23 18:12:05.427637	ADMIN	aa950bb6-cb01-41ca-b134-56bb42b730bc	OWNER
7ddb4b41-a4c8-495f-a7f3-6d8877ad5cfa	admin@test.com	$2a$10$38A.SwVw0wN.u031.IcE4Og3dAqYCRUAu4E1PQmUd8GJBQCio.yby	Admin	2026-05-07 13:41:46.115045	2026-07-25 19:27:52.148607	ADMIN	a720623c-28d9-4d6b-b06b-059397718d18	\N
8118bfca-0404-44e0-984b-36653ec6557c	barcodetest@test.com	$2a$10$Dt2yr3zrysCHz47MU/HrEuq/dbtUuefAFp9LmUolaoCYhBQ.Uqio6	Barcode Tester	2026-07-27 14:39:53.163472	2026-07-27 14:39:53.163472	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
4c15fa8d-df65-49ff-8669-c0bfa2f351cf	pra_requester@test.com	$2a$10$t2/wtJG0dSLCCgVIUyzcROQ3TMbBZx/K1DmaIlD4u00JheJKpQx/O	Field PIC	2026-07-27 16:34:29.059787	2026-07-27 16:34:29.059787	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
f822e88d-6dfa-4bba-9c11-5fb5c1315b6f	pra_approver@test.com	$2a$10$lsfpN1KWkcoTnX6rEICD/OhngR3sl.yEwmrsi7AkBKFGRJywi0sCe	Finance PIC	2026-07-27 16:34:29.340112	2026-07-27 16:34:29.340112	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
d0a4f811-81ce-4342-9366-0245635b41ab	pra_receiver@test.com	$2a$10$Lpv4iKA40n8a6gfADYBjY..Ub1qDjeiCC4lVT99MMElQsPJWRQyfa	Warehouse Receiver	2026-07-27 16:34:29.522707	2026-07-27 16:34:29.522707	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
8a44e08a-1944-480f-bc4d-adf96b01ba9b	pra_admin@test.com	$2a$10$D1cVR7ICcIin6yx7oo6fnOURXAB3I7MitruVhQ7phzT2jmU3CjL/u	Admin	2026-07-27 16:34:29.702434	2026-07-27 16:34:29.702434	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
87526b10-4357-4d4b-a578-de4e1b317d75	pra_owner@test.com	$2a$10$/tF1gr0.TWWqLpGW80eEIuvqCdB0awGLpvEII.4hrNmXkzPt95f7G	Owner PIC	2026-07-27 16:36:30.381336	2026-07-27 16:36:30.381336	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	OWNER
3a6a229b-9797-499c-ba1a-9f48dad36695	pra_manager@test.com	$2a$10$weHe.95in9pZvhsvUx9EM./BLc61LuKVwY1SRPa8qtCInROagufIa	Manager PIC	2026-07-27 16:36:30.666046	2026-07-27 16:36:30.666046	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	MANAGER
b21f5996-8310-41f8-8d9e-e111e4cd5d43	conv_owner@test.com	$2a$10$.o6WqH1DeOSe3vni/gxZH.yAJTPT5gNYcyKG2T4B9ek0SexYXNw6u	Conv Owner	2026-07-28 15:45:10.636121	2026-07-28 15:45:10.636121	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	OWNER
17dd129d-2a27-49b5-9f54-37701a3112bc	conv_creator@test.com	$2a$10$YTOrKONI8..EUmT0QvJ.I.H6GGxR/uPI1rHCxvOCumLaEe3r3W9ia	Conv Creator	2026-07-28 15:45:11.121638	2026-07-28 15:45:11.121638	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
2b06de7a-dd37-492a-be55-717acf93cba1	conv_receiver@test.com	$2a$10$tOcUIOR.ljm36o9iQWJue.5DLPuzAGmaaK8OqCsjdIoTljukI3576	Conv Receiver	2026-07-28 15:45:11.536096	2026-07-28 15:45:11.536096	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
134b31e0-b277-4bfc-b810-5614f4773684	conv_owner2@test.com	$2a$10$ZMd9mEO7F7GcD74GU8KGren4wBm4fol84UZlnTnsYq2gHhVktfYSG	Conv Owner2	2026-07-28 15:46:35.679502	2026-07-28 15:46:35.679502	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	OWNER
e8c905e4-a07e-4183-ab47-7a18d0ef64ae	conv_creator2@test.com	$2a$10$EmAgkv.cafgpc3Ucy40QnOchdzvmdf.9fFBWfPTDhAioWU.JGbAJe	Conv Creator2	2026-07-28 15:46:36.19465	2026-07-28 15:46:36.19465	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
f214a3fd-8a02-4999-b8c1-f77ae229479f	conv_receiver2@test.com	$2a$10$a0km8FGEE9O4p6WufXKWkOIoyVDvvBWFNqf9ldHEDKHBbgp3qlRmq	Conv Receiver2	2026-07-28 15:46:36.885159	2026-07-28 15:46:36.885159	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
fef4d477-75fe-4e13-bd8f-0ec411290794	burnratetest1@test.com	$2a$10$aA4NRP8kfhtI12Fq3BSrK.ftg7PKcHoyEqE6udc2Jx/UwJXatkiKC	Burn Rate Test	2026-07-29 03:00:38.13481	2026-07-29 03:00:38.13481	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
1d05f4e5-bf04-4f9b-8596-38caf45812a7	burnratetest2@test.com	$2a$10$35qVNc8ha2q7cCXEHsdz4eOM.v8F8oOo/pSGQTWsZiN/dkT4nqqhC	Burn Rate Test 2	2026-07-29 03:04:55.107501	2026-07-29 03:04:55.107501	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
6f010108-3575-4bbf-814c-f8d0d10d0e8c	case2test@test.com	$2a$10$gBhhCPdBvQlL7nx1SOsqOuzE8TEhbbpxacqdM/9rEQDIaM1FJsnmq	Case2 Test	2026-07-29 03:09:00.570311	2026-07-29 03:09:00.570311	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
1aafd97c-5b3b-442b-9d35-d11ba8b19d7b	case5test@test.com	$2a$10$MYNFnCPUsCbRBpH.8aYmQel2FKROGp7AO4sKVw64NwpN6qbaHP632	Case5 Test	2026-07-29 03:13:40.525042	2026-07-29 03:13:40.525042	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
1af5b908-3a5b-4e98-a2e3-a382bf204c89	case9_owner@test.com	$2a$10$qQmPAr7xcCOwC5Syw6EfwerSNqeuRhyzEepapUWfx050PNl5lu4r2	Case9 Owner	2026-07-29 03:14:33.936667	2026-07-29 03:14:33.936667	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	OWNER
34191b8e-4292-478c-8aa9-92fd41968bd5	case9_creator@test.com	$2a$10$FdYyZo9Rl5aU5BYMvrcX1uy4Fmzitp36qcF0.JvendrUENIG7NHOu	Case9 Creator	2026-07-29 03:14:34.315012	2026-07-29 03:14:34.315012	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
f61772db-2411-4c27-8424-00eb883ad1c5	case9_receiver@test.com	$2a$10$x1tx4loTzGR.oWIBWtPwZeGHgMeiqCeGQTqczezf5Lyx/nzO7NH4m	Case9 Receiver	2026-07-29 03:14:34.702601	2026-07-29 03:14:34.702601	OPERATOR	a720623c-28d9-4d6b-b06b-059397718d18	\N
\.


--
-- Data for Name: work_orders; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.work_orders (id, completed_date, created_at, description, priority, status, target_date, title, updated_at, airsoft_unit_id, assigned_technician_id, tenant_id, assigned_at, completed_at, started_at, airsoft_unit_part_id, maintenance_schedule_id) FROM stdin;
4e2264f5-d26e-4973-bf1f-eecec83d7fe9	2026-06-05	2026-06-05 21:12:22.68589	Cylinder showing abnormal wear	HIGH	COMPLETED	2026-05-01	Test overdue	2026-06-05 21:14:59.293533	30273c0e-40b9-4cbe-af69-0943fa3e70ec	9be6bef2-2d9c-4cf6-870a-0fde06951139	a720623c-28d9-4d6b-b06b-059397718d18	\N	2026-06-24 11:55:47.929463	\N	\N	\N
168339f7-053e-4b7d-956e-9dc291566b31	\N	2026-06-26 14:49:08.658093	Routine inspection of gearbox and compression system	HIGH	COMPLETED	2026-06-25	Scheduled Maintenance - Monthly Inspection	2026-06-26 14:52:21.291437	30273c0e-40b9-4cbe-af69-0943fa3e70ec	\N	a720623c-28d9-4d6b-b06b-059397718d18	\N	2026-06-26 14:52:21.288123	\N	\N	579227cf-47bf-4fab-bd7b-7b3e956eb25c
1189beaa-9e4f-431f-907d-5dfe41e2d99c	\N	2026-07-02 14:32:50.780994	Spacer destroy	HIGH	IN_PROGRESS	2026-06-20	Replace cylinder head	2026-07-02 15:37:45.222598	30273c0e-40b9-4cbe-af69-0943fa3e70ec	9be6bef2-2d9c-4cf6-870a-0fde06951139	a720623c-28d9-4d6b-b06b-059397718d18	2026-07-02 14:33:47.502384	\N	2026-07-02 15:37:45.220071	\N	\N
ca2bdcfd-f14a-40d9-84d7-afbe545183dd	2026-06-18	2026-06-04 14:40:04.078719	Cylinder showing abnormal wear	HIGH	COMPLETED	2026-06-10	Inspect cylinder assembly	2026-06-18 15:53:27.75713	30273c0e-40b9-4cbe-af69-0943fa3e70ec	9be6bef2-2d9c-4cf6-870a-0fde06951139	a720623c-28d9-4d6b-b06b-059397718d18	\N	2026-06-18 15:53:27.748902	\N	\N	\N
6d0bd70f-4bda-444a-b579-1d9238c2de32	2026-06-18	2026-06-15 20:06:35.695172	Cylinder Head failed inspection	HIGH	COMPLETED	2026-06-20	Replace Cylinder Head	2026-06-18 15:53:27.75713	30273c0e-40b9-4cbe-af69-0943fa3e70ec	9be6bef2-2d9c-4cf6-870a-0fde06951139	a720623c-28d9-4d6b-b06b-059397718d18	2026-06-15 20:06:35.679194	2026-06-18 15:53:27.748902	\N	d92b4a2f-f53f-4967-b668-1be4bbaf1865	\N
62d05b28-c188-4ee1-bf71-bbd2b410fc15	\N	2026-06-04 11:24:54.474527	Gearbox shell marked FAILED during inspection	CRITICAL	ASSIGNED	2026-06-15	Replace failed gearbox shell	2026-06-19 13:53:54.348462	30273c0e-40b9-4cbe-af69-0943fa3e70ec	9be6bef2-2d9c-4cf6-870a-0fde06951139	a720623c-28d9-4d6b-b06b-059397718d18	2026-06-19 13:53:54.326537	\N	\N	\N	\N
f739903e-c97c-4178-9a3f-9993d6a4817d	\N	2026-06-20 02:28:15.282547	Trigger snap during usage	HIGH	IN_PROGRESS	2026-06-20	Replace Trigger	2026-06-23 17:29:32.053819	30273c0e-40b9-4cbe-af69-0943fa3e70ec	9be6bef2-2d9c-4cf6-870a-0fde06951139	a720623c-28d9-4d6b-b06b-059397718d18	2026-06-23 15:38:18.815877	\N	2026-06-23 17:22:00.142915	\N	\N
\.


--
-- Name: roles_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.roles_id_seq', 3, true);


--
-- Name: airsoft_models airsoft_models_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.airsoft_models
    ADD CONSTRAINT airsoft_models_pkey PRIMARY KEY (id);


--
-- Name: airsoft_unit_parts airsoft_unit_parts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.airsoft_unit_parts
    ADD CONSTRAINT airsoft_unit_parts_pkey PRIMARY KEY (id);


--
-- Name: airsoft_units airsoft_units_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.airsoft_units
    ADD CONSTRAINT airsoft_units_pkey PRIMARY KEY (id);


--
-- Name: airsoft_units airsoft_units_serial_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.airsoft_units
    ADD CONSTRAINT airsoft_units_serial_number_key UNIQUE (serial_number);


--
-- Name: barcode_allocations barcode_allocations_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.barcode_allocations
    ADD CONSTRAINT barcode_allocations_pkey PRIMARY KEY (id);


--
-- Name: barcode_counters barcode_counters_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.barcode_counters
    ADD CONSTRAINT barcode_counters_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: goods_receipt_items goods_receipt_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT goods_receipt_items_pkey PRIMARY KEY (id);


--
-- Name: goods_receipts goods_receipts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT goods_receipts_pkey PRIMARY KEY (id);


--
-- Name: maintenance_rule maintenance_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.maintenance_rule
    ADD CONSTRAINT maintenance_rule_pkey PRIMARY KEY (id);


--
-- Name: maintenance_rule maintenance_rule_tenant_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.maintenance_rule
    ADD CONSTRAINT maintenance_rule_tenant_id_key UNIQUE (tenant_id);


--
-- Name: maintenance_schedules maintenance_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.maintenance_schedules
    ADD CONSTRAINT maintenance_schedules_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: part_categories part_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_categories
    ADD CONSTRAINT part_categories_pkey PRIMARY KEY (id);


--
-- Name: part_condition_history part_condition_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_condition_history
    ADD CONSTRAINT part_condition_history_pkey PRIMARY KEY (id);


--
-- Name: part_instances part_instances_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT part_instances_pkey PRIMARY KEY (id);


--
-- Name: part_type part_type_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_type
    ADD CONSTRAINT part_type_pkey PRIMARY KEY (id);


--
-- Name: parts parts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parts
    ADD CONSTRAINT parts_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_items purchase_order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT purchase_order_items_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_status_history purchase_order_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_order_status_history
    ADD CONSTRAINT purchase_order_status_history_pkey PRIMARY KEY (id);


--
-- Name: purchase_orders purchase_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_pkey PRIMARY KEY (id);


--
-- Name: purchase_orders purchase_orders_po_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_po_number_key UNIQUE (po_number);


--
-- Name: purchase_request_authorization_items purchase_request_authorization_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_authorization_items
    ADD CONSTRAINT purchase_request_authorization_items_pkey PRIMARY KEY (id);


--
-- Name: purchase_request_authorization_status_history purchase_request_authorization_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_authorization_status_history
    ADD CONSTRAINT purchase_request_authorization_status_history_pkey PRIMARY KEY (id);


--
-- Name: purchase_request_authorizations purchase_request_authorizations_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_authorizations
    ADD CONSTRAINT purchase_request_authorizations_pkey PRIMARY KEY (id);


--
-- Name: purchase_request_items purchase_request_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_items
    ADD CONSTRAINT purchase_request_items_pkey PRIMARY KEY (id);


--
-- Name: purchase_request_status_history purchase_request_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_status_history
    ADD CONSTRAINT purchase_request_status_history_pkey PRIMARY KEY (id);


--
-- Name: purchase_requests purchase_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_pkey PRIMARY KEY (id);


--
-- Name: realisasi_items realisasi_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasi_items
    ADD CONSTRAINT realisasi_items_pkey PRIMARY KEY (id);


--
-- Name: realisasi_status_history realisasi_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasi_status_history
    ADD CONSTRAINT realisasi_status_history_pkey PRIMARY KEY (id);


--
-- Name: realisasis realisasis_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT realisasis_pkey PRIMARY KEY (id);


--
-- Name: roles roles_name_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_name_key UNIQUE (name);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: service_event_parts service_event_parts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_event_parts
    ADD CONSTRAINT service_event_parts_pkey PRIMARY KEY (id);


--
-- Name: service_events service_events_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_events
    ADD CONSTRAINT service_events_pkey PRIMARY KEY (id);


--
-- Name: stock_adjustment_status_history stock_adjustment_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_adjustment_status_history
    ADD CONSTRAINT stock_adjustment_status_history_pkey PRIMARY KEY (id);


--
-- Name: stock_adjustments stock_adjustments_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT stock_adjustments_pkey PRIMARY KEY (id);


--
-- Name: stock_movements stock_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_pkey PRIMARY KEY (id);


--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (id);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: part_instances uk7vaco1nuxw8mit69lrh43x3f0; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT uk7vaco1nuxw8mit69lrh43x3f0 UNIQUE (barcode);


--
-- Name: realisasis ukreeu382n59mscn02aqx7gumv9; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT ukreeu382n59mscn02aqx7gumv9 UNIQUE (external_order_ref);


--
-- Name: tenants uq_tenants_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT uq_tenants_code UNIQUE (code);


--
-- Name: user_profiles user_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_pkey PRIMARY KEY (user_id);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: work_orders work_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT work_orders_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: airsoft_units airsoft_units_model_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.airsoft_units
    ADD CONSTRAINT airsoft_units_model_id_fkey FOREIGN KEY (model_id) REFERENCES public.airsoft_models(id);


--
-- Name: airsoft_units airsoft_units_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.airsoft_units
    ADD CONSTRAINT airsoft_units_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: parts fk13acitugkoo5g9ss0ngj15t14; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parts
    ADD CONSTRAINT fk13acitugkoo5g9ss0ngj15t14 FOREIGN KEY (category_id) REFERENCES public.part_categories(id);


--
-- Name: realisasis fk13h7g35yes0peocbk4uevo0ix; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fk13h7g35yes0peocbk4uevo0ix FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);


--
-- Name: airsoft_unit_parts fk26981xf284bogtjrssllnapsv; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.airsoft_unit_parts
    ADD CONSTRAINT fk26981xf284bogtjrssllnapsv FOREIGN KEY (airsoft_unit_id) REFERENCES public.airsoft_units(id);


--
-- Name: goods_receipts fk39swwl7dbtcofspprw5qd5kk6; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT fk39swwl7dbtcofspprw5qd5kk6 FOREIGN KEY (realisasi_id) REFERENCES public.realisasis(id);


--
-- Name: stock_adjustment_status_history fk3klt5m29wubne4hru54wnd9yf; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_adjustment_status_history
    ADD CONSTRAINT fk3klt5m29wubne4hru54wnd9yf FOREIGN KEY (changed_by) REFERENCES public.users(id);


--
-- Name: purchase_order_status_history fk3n175i6n7wkefq1rlfgmqqcrc; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_order_status_history
    ADD CONSTRAINT fk3n175i6n7wkefq1rlfgmqqcrc FOREIGN KEY (purchase_order_id) REFERENCES public.purchase_orders(id);


--
-- Name: maintenance_schedules fk50pih82e61w6ms8l73o7tyfwr; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.maintenance_schedules
    ADD CONSTRAINT fk50pih82e61w6ms8l73o7tyfwr FOREIGN KEY (airsoft_unit_id) REFERENCES public.airsoft_units(id);


--
-- Name: tenants fk6kkrj1ji0mc4ylqg5upgpi8wy; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT fk6kkrj1ji0mc4ylqg5upgpi8wy FOREIGN KEY (parent_id) REFERENCES public.tenants(id);


--
-- Name: parts fk75haw46vt0ryrli40csd4etcm; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parts
    ADD CONSTRAINT fk75haw46vt0ryrli40csd4etcm FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: stock_adjustment_status_history fk7gf0dkaox07gn89fvo76qfg8g; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_adjustment_status_history
    ADD CONSTRAINT fk7gf0dkaox07gn89fvo76qfg8g FOREIGN KEY (stock_adjustment_id) REFERENCES public.stock_adjustments(id);


--
-- Name: airsoft_unit_parts fk7gx66p2hgu9017gbqso63k0lk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.airsoft_unit_parts
    ADD CONSTRAINT fk7gx66p2hgu9017gbqso63k0lk FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: stock_adjustments fk7oc03k8fxf5fnoyyyy9i93tex; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT fk7oc03k8fxf5fnoyyyy9i93tex FOREIGN KEY (requested_by) REFERENCES public.users(id);


--
-- Name: realisasis fk90tep8m7qbon8xcpfu3qxmpvx; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fk90tep8m7qbon8xcpfu3qxmpvx FOREIGN KEY (supersedes_id) REFERENCES public.realisasis(id);


--
-- Name: realisasi_status_history fk97vec3ke62q7q55ddcfk2gydo; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasi_status_history
    ADD CONSTRAINT fk97vec3ke62q7q55ddcfk2gydo FOREIGN KEY (changed_by) REFERENCES public.users(id);


--
-- Name: purchase_request_authorizations fk9b6mj4msv76dh045d58hibwi6; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_authorizations
    ADD CONSTRAINT fk9b6mj4msv76dh045d58hibwi6 FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_request_authorization_items fk9bdtdg9ekxt54m7357rlj15a7; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_authorization_items
    ADD CONSTRAINT fk9bdtdg9ekxt54m7357rlj15a7 FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: goods_receipt_items fk9untxautwqxlbcrkedk33lec6; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT fk9untxautwqxlbcrkedk33lec6 FOREIGN KEY (realisasi_item_id) REFERENCES public.realisasi_items(id);


--
-- Name: maintenance_rule fk_maintenance_rule_tenants; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.maintenance_rule
    ADD CONSTRAINT fk_maintenance_rule_tenants FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: part_type fk_part_type_category; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_type
    ADD CONSTRAINT fk_part_type_category FOREIGN KEY (category_id) REFERENCES public.part_categories(id);


--
-- Name: part_type fk_part_type_tenant; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_type
    ADD CONSTRAINT fk_part_type_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: users fk_users_tenant; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: work_orders fk_work_order_part; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT fk_work_order_part FOREIGN KEY (airsoft_unit_part_id) REFERENCES public.airsoft_unit_parts(id);


--
-- Name: purchase_requests fka2o5vo90i5q19ema982rh46vs; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT fka2o5vo90i5q19ema982rh46vs FOREIGN KEY (po_id) REFERENCES public.purchase_orders(id);


--
-- Name: realisasi_status_history fka46pcn8l8kxsdoybttem9csoy; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasi_status_history
    ADD CONSTRAINT fka46pcn8l8kxsdoybttem9csoy FOREIGN KEY (realisasi_id) REFERENCES public.realisasis(id);


--
-- Name: purchase_request_authorization_status_history fkar6extq5tpb347teftdydpjc2; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_authorization_status_history
    ADD CONSTRAINT fkar6extq5tpb347teftdydpjc2 FOREIGN KEY (purchase_request_authorization_id) REFERENCES public.purchase_request_authorizations(id);


--
-- Name: realisasi_items fkb49toxl6bxuwwixwu2v4tij9s; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasi_items
    ADD CONSTRAINT fkb49toxl6bxuwwixwu2v4tij9s FOREIGN KEY (pra_item_id) REFERENCES public.purchase_request_authorization_items(id);


--
-- Name: purchase_order_status_history fkcfnwnc5qu6jnpd19v1txdems9; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_order_status_history
    ADD CONSTRAINT fkcfnwnc5qu6jnpd19v1txdems9 FOREIGN KEY (changed_by) REFERENCES public.users(id);


--
-- Name: parts fkcn1p10lsqimdl17dgmubk1m0w; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parts
    ADD CONSTRAINT fkcn1p10lsqimdl17dgmubk1m0w FOREIGN KEY (part_type_id) REFERENCES public.part_type(id);


--
-- Name: work_orders fkd9scw824lbjgyoeqro0kub419; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT fkd9scw824lbjgyoeqro0kub419 FOREIGN KEY (maintenance_schedule_id) REFERENCES public.maintenance_schedules(id);


--
-- Name: stock_adjustments fkdi22c1aybptbuh4yp02nro7e5; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT fkdi22c1aybptbuh4yp02nro7e5 FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_request_status_history fke0lhcbetncj98ul5kv9w506yl; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_status_history
    ADD CONSTRAINT fke0lhcbetncj98ul5kv9w506yl FOREIGN KEY (changed_by) REFERENCES public.users(id);


--
-- Name: stock_adjustments fkeoxthwcbjvh6w4uohtjnt5bb0; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT fkeoxthwcbjvh6w4uohtjnt5bb0 FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: part_instances fkfsb0q829p1x6k451fqcchy6k4; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT fkfsb0q829p1x6k451fqcchy6k4 FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: work_orders fkg1ne1vjc9mn32q0n4bippl31h; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT fkg1ne1vjc9mn32q0n4bippl31h FOREIGN KEY (airsoft_unit_id) REFERENCES public.airsoft_units(id);


--
-- Name: part_condition_history fkgcxfikqp60rq9phyuqnnij5mk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_condition_history
    ADD CONSTRAINT fkgcxfikqp60rq9phyuqnnij5mk FOREIGN KEY (airsoft_unit_part_id) REFERENCES public.airsoft_unit_parts(id);


--
-- Name: part_instances fkgn0do9i9ys7thu7p28h6skbf8; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT fkgn0do9i9ys7thu7p28h6skbf8 FOREIGN KEY (taken_by) REFERENCES public.users(id);


--
-- Name: realisasis fkgsl5bis106cxc284xb8o72a2f; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fkgsl5bis106cxc284xb8o72a2f FOREIGN KEY (pra_id) REFERENCES public.purchase_request_authorizations(id);


--
-- Name: work_orders fkh0d5ub1jwbp08sn44daqvhlky; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT fkh0d5ub1jwbp08sn44daqvhlky FOREIGN KEY (assigned_technician_id) REFERENCES public.users(id);


--
-- Name: suppliers fkh74geoqfe4ber25w1u7b1w70n; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT fkh74geoqfe4ber25w1u7b1w70n FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: barcode_allocations fkhjlj3o9w8i7ny5oeoevfi58ip; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.barcode_allocations
    ADD CONSTRAINT fkhjlj3o9w8i7ny5oeoevfi58ip FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_request_status_history fki9r4p6sm35b89criodrm0ehuw; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_status_history
    ADD CONSTRAINT fki9r4p6sm35b89criodrm0ehuw FOREIGN KEY (purchase_request_id) REFERENCES public.purchase_requests(id);


--
-- Name: purchase_request_authorization_items fkixinp0te1c1hr9m7ymvh7hfrc; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_authorization_items
    ADD CONSTRAINT fkixinp0te1c1hr9m7ymvh7hfrc FOREIGN KEY (pra_id) REFERENCES public.purchase_request_authorizations(id);


--
-- Name: barcode_allocations fkjpj0et4af4khjequcm7hppqpg; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.barcode_allocations
    ADD CONSTRAINT fkjpj0et4af4khjequcm7hppqpg FOREIGN KEY (issued_to) REFERENCES public.users(id);


--
-- Name: realisasis fkmjggvsdgeto5rrnj55clxe8o6; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fkmjggvsdgeto5rrnj55clxe8o6 FOREIGN KEY (approved_by) REFERENCES public.users(id);


--
-- Name: part_categories fkmsnbynj1f3nml6k5dxw19892r; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_categories
    ADD CONSTRAINT fkmsnbynj1f3nml6k5dxw19892r FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: realisasis fkmuvafrqnmfkg3kkh626kylofu; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fkmuvafrqnmfkg3kkh626kylofu FOREIGN KEY (reimbursed_to) REFERENCES public.users(id);


--
-- Name: work_orders fko0w91m27jrono6u6lfrbnhgm4; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.work_orders
    ADD CONSTRAINT fko0w91m27jrono6u6lfrbnhgm4 FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_request_authorizations fko6vcr55nanfvvs51141mthare; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_authorizations
    ADD CONSTRAINT fko6vcr55nanfvvs51141mthare FOREIGN KEY (approved_by) REFERENCES public.users(id);


--
-- Name: realisasi_items fkppkdnx0934f6d824s3gvgpswa; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasi_items
    ADD CONSTRAINT fkppkdnx0934f6d824s3gvgpswa FOREIGN KEY (realisasi_id) REFERENCES public.realisasis(id);


--
-- Name: realisasi_items fkpxydu09g3f90kyi0r17ou4nwe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasi_items
    ADD CONSTRAINT fkpxydu09g3f90kyi0r17ou4nwe FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: part_instances fkq5ptqyjx50eyfav7xju869j97; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT fkq5ptqyjx50eyfav7xju869j97 FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: purchase_request_authorization_status_history fkqdfgovp1qjcuumkglcgavrq2m; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_authorization_status_history
    ADD CONSTRAINT fkqdfgovp1qjcuumkglcgavrq2m FOREIGN KEY (changed_by) REFERENCES public.users(id);


--
-- Name: purchase_requests fkqdvlxlcyllarlkpynmqkc128k; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT fkqdvlxlcyllarlkpynmqkc128k FOREIGN KEY (pra_id) REFERENCES public.purchase_request_authorizations(id);


--
-- Name: notifications fkqjegl56prrnbxmjq7q8k5mav; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fkqjegl56prrnbxmjq7q8k5mav FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: part_instances fkrox47tibsqlfyymf40fqr3e6v; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.part_instances
    ADD CONSTRAINT fkrox47tibsqlfyymf40fqr3e6v FOREIGN KEY (installed_unit_part_id) REFERENCES public.airsoft_unit_parts(id);


--
-- Name: realisasis fks0wolh6gyvs648o9c0uixie1f; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fks0wolh6gyvs648o9c0uixie1f FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: airsoft_unit_parts fksb56o3tgxtp901gninci2wh4n; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.airsoft_unit_parts
    ADD CONSTRAINT fksb56o3tgxtp901gninci2wh4n FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: realisasis fksbe7igucp4tx4ybgmlsfos3vj; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.realisasis
    ADD CONSTRAINT fksbe7igucp4tx4ybgmlsfos3vj FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: service_events fksy234hpbjpgwi6rfhmmi2utde; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_events
    ADD CONSTRAINT fksy234hpbjpgwi6rfhmmi2utde FOREIGN KEY (work_order_id) REFERENCES public.work_orders(id);


--
-- Name: notifications fkt8ievafor22iuvg5sd4p7lhbk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fkt8ievafor22iuvg5sd4p7lhbk FOREIGN KEY (recipient_user_id) REFERENCES public.users(id);


--
-- Name: maintenance_schedules fktb243ksmtva1b5qfpca8lbplk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.maintenance_schedules
    ADD CONSTRAINT fktb243ksmtva1b5qfpca8lbplk FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: goods_receipt_items goods_receipt_items_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT goods_receipt_items_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: goods_receipt_items goods_receipt_items_receipt_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goods_receipt_items
    ADD CONSTRAINT goods_receipt_items_receipt_id_fkey FOREIGN KEY (receipt_id) REFERENCES public.goods_receipts(id) ON DELETE CASCADE;


--
-- Name: goods_receipts goods_receipts_po_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT goods_receipts_po_id_fkey FOREIGN KEY (po_id) REFERENCES public.purchase_orders(id);


--
-- Name: goods_receipts goods_receipts_received_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT goods_receipts_received_by_fkey FOREIGN KEY (received_by) REFERENCES public.users(id);


--
-- Name: goods_receipts goods_receipts_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goods_receipts
    ADD CONSTRAINT goods_receipts_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_order_items purchase_order_items_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT purchase_order_items_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: purchase_order_items purchase_order_items_po_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT purchase_order_items_po_id_fkey FOREIGN KEY (po_id) REFERENCES public.purchase_orders(id) ON DELETE CASCADE;


--
-- Name: purchase_orders purchase_orders_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);


--
-- Name: purchase_orders purchase_orders_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: purchase_request_items purchase_request_items_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_items
    ADD CONSTRAINT purchase_request_items_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: purchase_request_items purchase_request_items_pr_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_request_items
    ADD CONSTRAINT purchase_request_items_pr_id_fkey FOREIGN KEY (pr_id) REFERENCES public.purchase_requests(id) ON DELETE CASCADE;


--
-- Name: purchase_requests purchase_requests_requested_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_requested_by_fkey FOREIGN KEY (requested_by) REFERENCES public.users(id);


--
-- Name: purchase_requests purchase_requests_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: service_event_parts service_event_parts_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_event_parts
    ADD CONSTRAINT service_event_parts_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: service_event_parts service_event_parts_service_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_event_parts
    ADD CONSTRAINT service_event_parts_service_event_id_fkey FOREIGN KEY (service_event_id) REFERENCES public.service_events(id) ON DELETE CASCADE;


--
-- Name: service_events service_events_airsoft_unit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_events
    ADD CONSTRAINT service_events_airsoft_unit_id_fkey FOREIGN KEY (airsoft_unit_id) REFERENCES public.airsoft_units(id);


--
-- Name: service_events service_events_technician_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_events
    ADD CONSTRAINT service_events_technician_id_fkey FOREIGN KEY (technician_id) REFERENCES public.users(id);


--
-- Name: service_events service_events_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_events
    ADD CONSTRAINT service_events_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: stock_movements stock_movements_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.parts(id);


--
-- Name: stock_movements stock_movements_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: user_profiles user_profiles_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: user_profiles user_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_roles user_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict pyqr5jtZS3M3KW6SmtH13MBycGr6C4r3aWDMwxXdtUyolxPQxgVhzhp6RR3mRK6

