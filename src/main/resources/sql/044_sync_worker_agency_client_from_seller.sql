-- WORKER: выровнять is_agency_client с селлером-владельцем (как при создании в UserService.createUser).
UPDATE solution.users w
SET is_agency_client = s.is_agency_client
FROM solution.users s
WHERE w.role = 'WORKER'
  AND w.owner_id = s.id
  AND s.role = 'SELLER'
  AND w.is_agency_client IS DISTINCT FROM s.is_agency_client;
