-- DBeaver에서 대상 PostgreSQL 데이터베이스에 직접 실행합니다.
-- 서버는 이 파일을 실행하지 않으며, JPA는 스키마의 일치 여부만 검증합니다.
BEGIN;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(26) NOT NULL UNIQUE,
    nickname VARCHAR(30) NOT NULL,
    handle VARCHAR(30) UNIQUE,
    profile_image_url VARCHAR(500),
    bio VARCHAR(150),
    profile_visibility VARCHAR(16) NOT NULL DEFAULT 'public'
        CHECK (profile_visibility IN ('public', 'private')),
    status VARCHAR(16) NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'deleting')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE TABLE user_auth_providers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(16) NOT NULL CHECK (provider = 'google'),
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    family_id VARCHAR(26) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_family ON refresh_tokens(family_id);
CREATE TABLE user_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    weekly_goal_meters INTEGER NOT NULL DEFAULT 20000
        CHECK (weekly_goal_meters BETWEEN 5000 AND 100000 AND weekly_goal_meters % 5000 = 0),
    distance_unit VARCHAR(4) NOT NULL DEFAULT 'km' CHECK (distance_unit IN ('km', 'mi')),
    time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul',
    notify_run_reminder BOOLEAN NOT NULL DEFAULT true,
    notify_weekly_summary BOOLEAN NOT NULL DEFAULT true,
    run_reminder_time TIME DEFAULT '07:00',
    notify_race BOOLEAN NOT NULL DEFAULT true,
    notify_crew BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE runs (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(26) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_run_id VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    title VARCHAR(60),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds BETWEEN 0 AND 86400),
    distance_meters INTEGER NOT NULL CHECK (distance_meters BETWEEN 0 AND 300000),
    average_pace_seconds_per_km INTEGER,
    route_point_count INTEGER NOT NULL CHECK (route_point_count BETWEEN 0 AND 20000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    UNIQUE (user_id, client_run_id),
    CHECK (ended_at >= started_at),
    CHECK (duration_seconds <= EXTRACT(EPOCH FROM ended_at - started_at))
);
CREATE INDEX idx_runs_user_started ON runs(user_id, started_at DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE TABLE run_routes (
    run_id BIGINT PRIMARY KEY REFERENCES runs(id) ON DELETE CASCADE,
    points JSONB NOT NULL,
    simplified_points JSONB NOT NULL,
    min_lat DOUBLE PRECISION,
    max_lat DOUBLE PRECISION,
    min_lng DOUBLE PRECISION,
    max_lng DOUBLE PRECISION
);
CREATE TABLE run_idempotency_keys (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key VARCHAR(128) NOT NULL,
    run_id BIGINT NOT NULL REFERENCES runs(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, key)
);

COMMENT ON TABLE users IS '사용자 계정과 프로필';
COMMENT ON COLUMN users.public_id IS 'API에 노출하는 ULID 식별자';
COMMENT ON TABLE user_auth_providers IS '검증된 소셜 로그인 계정 연결';
COMMENT ON TABLE refresh_tokens IS '해시로 저장하는 회전형 갱신 토큰';
COMMENT ON COLUMN refresh_tokens.family_id IS '재사용 감지 시 함께 폐기하는 토큰 계열';
COMMENT ON TABLE user_settings IS '주간 목표와 사용자 설정';
COMMENT ON TABLE runs IS '러닝 요약 정보와 소프트 삭제 상태';
COMMENT ON COLUMN runs.request_hash IS '동일 기록의 재전송 내용 비교용 SHA-256';
COMMENT ON TABLE run_routes IS '원본 GPS 경로와 지도용 400점 이하 축약 경로';
COMMENT ON TABLE run_idempotency_keys IS '사용자별 요청 키와 러닝 기록의 연결';
COMMIT;
