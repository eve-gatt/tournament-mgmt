create table reftool_inputs
(
    id       serial
        constraint reftool_inputs_pk primary key,
    red      varchar,
    blue     varchar,
    added_on timestamp with time zone default now() not null,
    added_by varchar                                not null
);

