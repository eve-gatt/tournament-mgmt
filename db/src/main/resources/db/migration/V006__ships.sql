create table ships
(
    type_id       int
        constraint ships_pk
            primary key,
    group_name    varchar not null,
    name          varchar not null,
    ao_exact_type varchar,
    ao_class      varchar,
    ao_overlay    varchar,
    ao_points     int
);
