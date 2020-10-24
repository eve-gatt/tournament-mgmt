create table streamers
(
    name varchar not null
        constraint streamers_pk
            primary key,
    uuid uuid    not null
);

