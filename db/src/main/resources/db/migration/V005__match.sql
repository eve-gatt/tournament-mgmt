create table match
(
    id                serial                   not null
        constraint match_pk
            primary key,
    reftool_inputs_id integer                  not null,
    created_at        timestamp with time zone not null default now(),
    created_by        varchar                  not null,
    tournament_uuid   uuid                     not null,
    blueTeam          uuid                     not null,
    redTeam           uuid                     not null,
    blueJson          varchar                  not null,
    redJson           varchar                  not null
);

