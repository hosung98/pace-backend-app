-- 기존 DB에서 시/도 전체 지역 선택을 허용합니다. 데이터는 삭제하지 않습니다.
-- DBeaver에서 대상 DB와 기존 CHECK 이름을 확인한 뒤 서버 배포 전에 실행합니다.
BEGIN;
ALTER TABLE crews DROP CONSTRAINT IF EXISTS crews_check;
ALTER TABLE crews DROP CONSTRAINT IF EXISTS crews_region_check;
ALTER TABLE crews ADD CONSTRAINT crews_region_check CHECK (sido IS NOT NULL OR sigungu IS NULL);
COMMIT;
