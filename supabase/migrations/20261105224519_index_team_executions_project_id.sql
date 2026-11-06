-- Index the nullable project foreign key so project-scoped lookups and FK maintenance do not scan team_executions.
CREATE INDEX IF NOT EXISTS team_executions_project_id_idx
    ON public.team_executions (project_id);
