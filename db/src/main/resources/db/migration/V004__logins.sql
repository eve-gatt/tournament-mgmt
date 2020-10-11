create table logins
(
    character_id   integer                  not null
        constraint logins_pk
            primary key,
    character_name varchar                  not null,
    scopes         varchar                  not null,
    refresh_token  varchar,
    last_seen      timestamp with time zone not null
);

