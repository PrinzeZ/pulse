-- P.U.L.S.E user scope repair
--
-- Hospital-scoped ADMIN/STAFF accounts must inherit their state and district
-- from the hospital they belong to. This repairs legacy rows where those
-- columns were left NULL. It intentionally does NOT modify global/dev ADMIN
-- accounts that have no hospital_id.
--
-- Safe to run repeatedly.

UPDATE public.users AS u
SET district_id = h.district_id,
    state_id = d.state_id
FROM public.hospitals AS h
JOIN public.districts AS d
  ON d.district_id = h.district_id
WHERE u.hospital_id = h.hospital_id
  AND u.role IN ('ADMIN', 'STAFF')
  AND (u.state_id IS NULL OR u.district_id IS NULL);

-- Verify the result. Expected: zero rows for hospital-scoped ADMIN/STAFF.
SELECT user_id, username, role, hospital_id, state_id, district_id
FROM public.users
WHERE role IN ('ADMIN', 'STAFF')
  AND hospital_id IS NOT NULL
  AND (state_id IS NULL OR district_id IS NULL)
ORDER BY user_id;
