create type problem_type as enum
    ('pilot', 'team', 'tournament');

create table problems
(
    id                    serial
        constraint problems_pk primary key,
    type                  problem_type              not null,
    tournament_uuid       uuid                      not null,
    validation_identifier varchar                   not null,
    referenced_entity     uuid                      not null,
    message               varchar                   not null,
    created_by            varchar                   not null,
    created_on            timestamptz default now() not null
);

alter table team
    drop column msg;

alter table tournament
    drop column msg;
