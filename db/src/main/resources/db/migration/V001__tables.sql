create table tournament
(
    tournament_id  serial                                 not null,
    uuid           uuid                                   not null,
    name           varchar                                not null,
    start_date     timestamp with time zone default now() not null,
    practice_on_td boolean                  default false not null,
    play_on_td     boolean                  default false not null,
    teams_locked   boolean                  default false not null,
    msg            varchar,
    created_by     varchar                                not null,
    created_on     timestamp with time zone default now() not null,
    constraint tournament_pk
        primary key (tournament_id),
    constraint tournament_uuid_key
        unique (uuid)
);

create unique index tournament_name_uindex
    on tournament (name);

create table team
(
    team_id         serial                                 not null,
    uuid            uuid                                   not null,
    tournament_uuid uuid                                   not null,
    name            varchar                                not null,
    captain         varchar                                not null,
    locked          boolean                  default false not null,
    created_by      varchar                                not null,
    created_on      timestamp with time zone default now() not null,
    msg             varchar,
    constraint team_pk
        primary key (team_id),
    constraint team_tournament_uuid_fkq8
        foreign key (tournament_uuid) references tournament (uuid)
            on update cascade on delete cascade
);

create unique index team_uuid_uindex
    on team (uuid);

create unique index team_tournament_uuid_name_uindex
    on team (tournament_uuid, name);

create table team_member
(
    team_member_id serial                                 not null,
    uuid           uuid                                   not null,
    team_uuid      uuid                                   not null,
    name           varchar                                not null,
    added_by       varchar                                not null,
    added_on       timestamp with time zone default now() not null,
    constraint team_member_pk
        primary key (team_member_id),
    constraint team_member_uuid_key
        unique (uuid),
    constraint team_member_only_once
        unique (team_uuid, name),
    constraint team_member_team_uuid_fk
        foreign key (team_uuid) references team (uuid)
            on update cascade on delete cascade
);

create table tournament_role
(
    tournament_role_id serial    not null,
    tournament_uuid    uuid      not null,
    type               role_type not null,
    name               varchar   not null,
    constraint tournament_role_pk
        primary key (tournament_role_id),
    constraint tournament_role_tournament_uuid_fk
        foreign key (tournament_uuid) references tournament (uuid)
            on update cascade on delete cascade
);

create table thunderdome
(
    id              serial
        constraint thunderdome_pk
            primary key,
    tournament_uuid uuid    not null,
    allocated_to    varchar,
    username        varchar not null,
    password        varchar not null,
    constraint thunderdome_tournament_uuid_fk
        foreign key (tournament_uuid) references tournament (uuid)
            on update cascade on delete cascade
);
