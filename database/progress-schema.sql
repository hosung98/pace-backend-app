-- DBeaver에서 database/schema.sql 및 database/community-schema.sql을 적용한 뒤 한 번 실행합니다.
-- 애플리케이션과 테스트 운영 DB에 자동 적용되지 않습니다.
BEGIN;

CREATE TABLE IF NOT EXISTS user_badges (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    badge_id VARCHAR(40) NOT NULL,
    earned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, badge_id)
);
CREATE INDEX IF NOT EXISTS idx_user_badges_user ON user_badges(user_id);
COMMENT ON TABLE user_badges IS '사용자별 최초 배지 획득 시각. 획득 후 러닝 삭제·기간 변경에도 유지';

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS representative_badge_id VARCHAR(40);
COMMENT ON COLUMN user_settings.representative_badge_id IS '프로필에 표시할 사용자가 획득한 대표 배지';

CREATE TABLE IF NOT EXISTS run_reflections (
    run_id BIGINT PRIMARY KEY REFERENCES runs(id) ON DELETE CASCADE,
    effort VARCHAR(16) NOT NULL CHECK (effort IN ('easy', 'moderate', 'hard')),
    note VARCHAR(500) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE run_reflections IS '사용자별 러닝 강도와 메모';

CREATE TABLE IF NOT EXISTS crew_monthly_goals (
    crew_id BIGINT NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
    month_start DATE NOT NULL CHECK (EXTRACT(DAY FROM month_start) = 1),
    target_km INTEGER NOT NULL CHECK (target_km BETWEEN 1 AND 10000),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (crew_id, month_start)
);
COMMENT ON TABLE crew_monthly_goals IS '크루 월간 거리 목표. 월 경계는 Asia/Seoul';

ALTER TABLE crew_monthly_goals
    ADD COLUMN IF NOT EXISTS configured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS reward_eligible BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS attained_at TIMESTAMPTZ;
COMMENT ON COLUMN crew_monthly_goals.reward_eligible IS '설정 당시 합계가 목표 미만인 목표 버전만 보상 가능';

COMMIT;
