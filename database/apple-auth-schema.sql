-- 기존 DB에서 배포 전에 DBeaver로 수동 실행합니다. users/runs 및 기존 행은 보존합니다.
-- schema.sql로 만든 기본 CHECK 이름을 사용합니다. 다른 이름이면 먼저 실제 제약을 확인하세요.
BEGIN;
ALTER TABLE user_auth_providers
    DROP CONSTRAINT user_auth_providers_provider_check;
ALTER TABLE user_auth_providers
    ADD CONSTRAINT user_auth_providers_provider_check CHECK (provider IN ('google', 'apple'));
COMMIT;
