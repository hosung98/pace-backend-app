-- DBeaver에서 database/schema.sql을 적용한 뒤 대상 DB에 한 번 실행합니다.
-- 애플리케이션은 이 SQL을 실행하지 않으며 JPA는 스키마를 검증하기만 합니다.
BEGIN;

CREATE TABLE community_user_state (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    last_crew_left_at TIMESTAMPTZ
);

CREATE TABLE crews (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(26) NOT NULL UNIQUE,
    name VARCHAR(20) NOT NULL,
    intro VARCHAR(100) NOT NULL,
    sido VARCHAR(30),
    sigungu VARCHAR(40),
    leader_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invite_code VARCHAR(8) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT crews_region_check CHECK (sido IS NOT NULL OR sigungu IS NULL)
);
CREATE INDEX idx_crews_name ON crews (lower(name));

CREATE TABLE crew_members (
    crew_id BIGINT NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('leader', 'member')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (crew_id, user_id)
);
CREATE INDEX idx_crew_members_crew ON crew_members (crew_id, joined_at);

CREATE TABLE crew_join_requests (
    crew_id BIGINT NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (crew_id, user_id)
);
CREATE INDEX idx_crew_join_requests_crew ON crew_join_requests (crew_id, created_at);

CREATE TABLE races (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(26) NOT NULL UNIQUE,
    title VARCHAR(60) NOT NULL,
    host_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    distance_meters INTEGER NOT NULL CHECK (distance_meters IN (3000, 5000, 10000, 21097)),
    starts_at TIMESTAMPTZ NOT NULL,
    join_deadline_minutes INTEGER NOT NULL CHECK (join_deadline_minutes IN (10, 30, 60)),
    min_participants INTEGER NOT NULL CHECK (min_participants BETWEEN 2 AND 20),
    max_participants INTEGER NOT NULL CHECK (max_participants BETWEEN 2 AND 20),
    visibility VARCHAR(16) NOT NULL CHECK (visibility IN ('public', 'private')),
    cancelled BOOLEAN NOT NULL DEFAULT false,
    deadline_processed_at TIMESTAMPTZ,
    deadline_participant_count INTEGER,
    invite_code VARCHAR(8) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (min_participants <= max_participants),
    CHECK ((visibility = 'private') = (invite_code IS NOT NULL))
);
CREATE INDEX idx_races_open ON races (starts_at) WHERE cancelled = false AND visibility = 'public';
CREATE INDEX idx_races_host ON races (host_user_id, starts_at);

CREATE TABLE race_participants (
    race_id BIGINT NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (race_id, user_id)
);
CREATE INDEX idx_race_participants_user ON race_participants (user_id, race_id);

-- 사용자 탈퇴 등 FK cascade로 참가 행이 지워지는 경우에도 마감 시점 인원을 먼저 고정합니다.
CREATE FUNCTION snapshot_race_deadline_before_participant_delete() RETURNS trigger
LANGUAGE plpgsql AS '
DECLARE
    race_row races%ROWTYPE;
    participant_total INTEGER;
BEGIN
    SELECT * INTO race_row FROM races WHERE id = OLD.race_id FOR UPDATE;
    IF FOUND AND race_row.deadline_processed_at IS NULL
       AND now() >= race_row.starts_at - make_interval(mins => race_row.join_deadline_minutes) THEN
        SELECT count(*) INTO participant_total FROM race_participants WHERE race_id = OLD.race_id;
        UPDATE races
           SET deadline_processed_at = now(),
               deadline_participant_count = participant_total,
               cancelled = cancelled OR participant_total < min_participants
         WHERE id = OLD.race_id;
    END IF;
    RETURN OLD;
END;
';
CREATE TRIGGER trg_snapshot_race_deadline_before_participant_delete
BEFORE DELETE ON race_participants
FOR EACH ROW EXECUTE FUNCTION snapshot_race_deadline_before_participant_delete();

COMMENT ON TABLE community_user_state IS '크루 재가입 제한을 위한 사용자별 상태';
COMMENT ON TABLE crews IS '러닝 크루와 크루장·초대 코드';
COMMENT ON TABLE crew_members IS '사용자당 하나의 크루 멤버십';
COMMENT ON TABLE crew_join_requests IS '사용자당 하나의 대기 중인 가입 신청';
COMMENT ON TABLE races IS '러닝 대결 모집과 일정';
COMMENT ON TABLE race_participants IS '대결 참가자. 실제 결과 등록은 별도 기능';

COMMIT;
