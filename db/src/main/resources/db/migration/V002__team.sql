create table team
(
    team_id serial not null constraint team_pk primary key,
    uuid uuid not null,
    tournament_uuid uuid not null,
    name varchar not null,
    captain varchar not null,
    created_by varchar not null,
    created_on timestamptz not null default now()
);

alter table team
	add constraint team_tournament_uuid_fk
		foreign key (tournament_uuid) references tournament (uuid)
			on update cascade on delete cascade;
