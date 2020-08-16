create table tournament
(
    tournament_id  serial  not null
        constraint tournament_pk
            primary key,
    uuid           uuid    not null,
    name           varchar not null,
    practice_on_td boolean not null default false,
    play_on_td     boolean not null default false
);

create unique index tournament_name_uindex
    on tournament (name);

