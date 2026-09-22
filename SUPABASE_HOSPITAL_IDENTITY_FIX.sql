-- P.U.L.S.E hospital identity migration
-- Safe to run in Supabase SQL Editor. All statements are idempotent.

ALTER TABLE public.hospitals
    ADD COLUMN IF NOT EXISTS government_hospital_key VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS ux_hospitals_government_hospital_key
    ON public.hospitals(government_hospital_key)
    WHERE government_hospital_key IS NOT NULL;

ALTER TABLE public.hospital_registrations
    ADD COLUMN IF NOT EXISTS government_hospital_key VARCHAR(64);

-- Optional backfill for exact official-name matches is performed automatically
-- by P.U.L.S.E at startup from data/government-hospitals.csv.
