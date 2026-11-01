-- Standard SQL that runs on PostgreSQL and on H2 in PostgreSQL mode.
create table tasks (
    id          uuid                     not null primary key,
    title       varchar(120)             not null,
    description varchar(2000),
    status      varchar(20)              not null,
    due_date    date,
    created_by  varchar(120)             not null,
    created_at  timestamp with time zone not null,
    updated_at  timestamp with time zone not null,
    version     bigint                   not null,
    constraint chk_tasks_status check (status in ('TODO', 'IN_PROGRESS', 'DONE'))
);

create index idx_tasks_status on tasks (status);
create index idx_tasks_created_at on tasks (created_at);
