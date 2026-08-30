-- Prost baseline schema.
--
-- Generated from the Hibernate 6.1.7 entity model (Spring Boot 3.0.13) via
-- jakarta.persistence.schema-generation, then translated from H2 to PostgreSQL
-- types by hand.
--
-- ---------------------------------------------------------------------------
-- VERIFICATION STATUS
--
-- Verified against H2 in PostgreSQL compatibility mode
-- (MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE):
--   * Flyway applied it cleanly to an empty schema
--   * `ddl-auto: validate` passed, so it matches the entity model exactly
--   * login, catalogue seeding and checkout all worked on the result, which
--     exercises addresses_seq, beverage_seq, orders_seq and order_items_seq
--
-- NOT yet verified against a real PostgreSQL server — Docker was unavailable
-- when this was written. What remains untested is genuine PostgreSQL/H2
-- dialect divergence, not whether the schema matches the entities. Run this
-- before trusting it in any deployed environment:
--
--   docker compose down -v && docker compose up -d prost_postgres
--   APP_ENV=prod ./gradlew bootRun
--
-- Expect "Migrating schema \"public\" to version 1" followed by a clean start.
-- A SchemaManagementException means this file disagrees with the entities —
-- fix this file, never the entities.
-- ---------------------------------------------------------------------------
--
-- Note on sequences: Hibernate 6 resolves GenerationType.AUTO to one sequence
-- per entity with an allocation size of 50. Hibernate 5 used a single shared
-- `hibernate_sequence` incrementing by 1. Starting at 1 is correct only because
-- no database holds data; against existing rows each sequence must start above
-- that table's current max(id).

create sequence addresses_seq start with 1 increment by 50;
create sequence beverage_seq start with 1 increment by 50;
create sequence order_items_seq start with 1 increment by 50;
create sequence orders_seq start with 1 increment by 50;
create sequence roles_seq start with 1 increment by 50;

create table addresses (
    id          bigint       not null,
    number      varchar(255) not null,
    postal_code varchar(5)   not null,
    street      varchar(255) not null,
    primary key (id)
);

-- Single-table inheritance: Bottle and Crate share this table, discriminated by
-- dtype. Subtype-specific columns are nullable by necessity.
create table beverage (
    dtype           varchar(31) not null,
    id              bigint      not null,
    bottle_pic      varchar(255),
    in_stock        integer          check (in_stock >= 0),
    name            varchar(255),
    price           numeric(10, 2) check (price >= 0.01),
    supplier        varchar(255),
    volume          double precision check (volume >= 0),
    volume_percent  double precision check (volume_percent >= 0),
    crate_pic       varchar(255),
    crates_in_stock integer          check (crates_in_stock >= 0),
    no_of_bottles   integer          check (no_of_bottles >= 1),
    bottle_id       bigint,
    primary key (id)
);

create table orders (
    id         bigint       not null,
    created_on timestamp(6) not null,
    price      numeric(10, 2) check (price >= 0.01),
    user_id    varchar(255),
    primary key (id)
);

-- One row per order line, carrying a quantity, not one row per physical unit.
create table order_items (
    id          bigint  not null,
    position    varchar(255),
    price       numeric(10, 2) check (price >= 0.01),
    quantity    integer check (quantity >= 1),
    beverage_id bigint  not null,
    order_id    bigint,
    primary key (id)
);

create table roles (
    id   bigint not null,
    name varchar(255),
    primary key (id)
);

-- username is the natural primary key; every foreign key references it.
create table users (
    username            varchar(255) not null,
    birthday            date         not null,
    password            varchar(255) not null,
    billing_address_id  bigint,
    delivery_address_id bigint,
    primary key (username)
);

create table users_roles (
    user_id varchar(255) not null,
    role_id bigint       not null
);

alter table beverage
    add constraint FK76o4sj8k06g3veoc3vkfenxnw
    foreign key (bottle_id) references beverage;

alter table order_items
    add constraint FKmp0e2k5s1dt7374qmsyob6u6e
    foreign key (beverage_id) references beverage;

alter table order_items
    add constraint FKbioxgbv59vetrxe0ejfubep1w
    foreign key (order_id) references orders;

alter table orders
    add constraint FK32ql8ubntj5uh44ph9659tiih
    foreign key (user_id) references users;

alter table users
    add constraint FK59ttwko9dwcdjb2u2ech0h1qe
    foreign key (billing_address_id) references addresses;

alter table users
    add constraint FKclpd49b6e89u8ikjw9sqltbej
    foreign key (delivery_address_id) references addresses;

alter table users_roles
    add constraint FKj6m8fwv7oqv74fcehir1a9ffy
    foreign key (role_id) references roles;

alter table users_roles
    add constraint FK2o0jvgh89lemvvo17cbqvdxaa
    foreign key (user_id) references users;
