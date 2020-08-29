alter table tournament
    add start_date timestamptz not null default now();
