-- Добавление статуса DECLINED: приглашённый отклонил приглашение (в отличие от REVOKED — отозвал владелец).
ALTER TABLE solution.cabinet_access_invitations
    DROP CONSTRAINT IF EXISTS cabinet_access_invitations_status_check;

ALTER TABLE solution.cabinet_access_invitations
    ADD CONSTRAINT cabinet_access_invitations_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED', 'DECLINED'));

COMMENT ON COLUMN solution.cabinet_access_invitations.status IS
    'PENDING — ожидает; ACCEPTED — принято; REVOKED — отозвано владельцем; DECLINED — отклонено приглашённым; EXPIRED — истекло.';
