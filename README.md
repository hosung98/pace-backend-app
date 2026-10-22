# PACE 백엔드

Java 21 · Spring Boot 3.5 · Gradle · PostgreSQL · JPA · MyBatis 기반 API입니다.

## 구현 범위

- Google ID 토큰 검증 후 가입·로그인
- JWT 접근 토큰(30분), 갱신 토큰(30일) 회전과 재사용 감지
- 내 정보·사용자 설정 부분 수정 및 회원 탈퇴
- 러닝 저장·목록·상세·제목 수정·소프트 삭제·24시간 이내 복구
- 시간대 기준 대시보드·주간·월간 통계
- Swagger, 상태 확인, 비루트 사용자 Docker 이미지

Apple·Kakao 인증, 크루·대결, 이미지 업로드와 실제 알림 발송은 후속 범위입니다.
프론트의 저장소와 로그인 화면은 아직 이 API에 연결하지 않았습니다.

## 구조

```text
src/main/java/com/pace/
  config/                   보안·OpenAPI·트랜잭션 설정
  common/
    aop/                    API 처리 시간 기록 (요청·응답 본문 제외)
    exception/              비즈니스 예외·공통 오류 처리
    filter/                 요청 ID 발급과 MDC 설정
    util/                   JSON·해시·시간대 처리
  auth/                     인증 도메인
  user/                     사용자·설정 도메인
  run/                      러닝 도메인
  stats/                    집계 도메인
```

도메인 안에서 `controller`, `dto`, `vo`, `entity`, `repository`, `mapper`,
`service`, `service/impl`을 필요한 역할에 따라 분리합니다.
VO는 경로 좌표·경계·커서 같은 불변 값, DTO는 API 요청·응답,
Mapper의 Row는 조회 결과를 표현합니다.

- Controller: 입력 검증과 HTTP 요청·응답
- Service 인터페이스 / ServiceImpl: 기능 계약과 비즈니스 로직·트랜잭션
- JPA Repository: 엔티티 저장·수정·삭제
- MyBatis Mapper / XML: 커서 목록·통계 SQL 및 가입 동시성 잠금
- 같은 DataSource와 JPA 트랜잭션 관리자를 사용합니다. JPA 변경 직후 MyBatis로
  조회할 때는 먼저 flush해야 합니다.

## DB 준비 (DBeaver 수동 실행)

1. PostgreSQL에 `pace_dev` 또는 `pace_prod` DB와 접속 계정을 준비합니다.
2. DBeaver에서 **대상 DB를 확인하고** [database/schema.sql](database/schema.sql)을 실행합니다.
3. 애플리케이션 계정에 테이블·시퀀스 사용 권한을 부여합니다.

서버는 테이블을 생성하거나 변경하지 않습니다. `ddl-auto: validate`로 기존
스키마를 검증하고 SQL 자동 초기화도 끕니다. SQL은 런타임 classpath 밖에 있습니다.
운영에서는 DDL 실행 계정과 애플리케이션 계정의 권한을 분리하는 것을 권장합니다.

## 환경별 접속 설정

| 프로필 | DB | 사용자 | Swagger |
|---|---|---|---|
| `local` | 환경 변수 또는 기본 localhost의 `pace_dev` | `pace_dev_user` | 기본 활성화 |
| `dev` | 환경 변수 또는 기본 localhost의 `pace_dev` | `pace_dev_user` | 기본 활성화 |
| `prod` | `DB_HOST` 호스트의 `pace_prod` | `DB_USERNAME` 필수 | 기본 비활성화 |

JDBC에는 `http://`를 사용하지 않습니다. 원격 DB를 쓰면 `DB_URL`을 `jdbc:postgresql://...` 형태로 지정하세요.

운영 호스트·사용자·비밀번호는 개발 값으로 추정하지 않고 환경 변수로 지정합니다.
`DB_URL`을 지정하면 프로필의 기본 URL을 대체합니다. DB에서 TLS를 요구하면
인증서와 함께 `sslmode=verify-full` 등을 URL에 구성합니다.

| 환경 변수 | 설명 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local`, `dev`, `prod` 중 실행 환경 |
| `DB_PASSWORD` | dev·prod 필수. local은 미지정 시 빈 값으로 접속 시도 |
| `DB_URL` | 전체 JDBC URL 재정의 |
| `DB_USERNAME` | DB 사용자. 운영 필수 |
| `DB_HOST`, `DB_PORT` | 운영 기본 URL의 호스트·포트 (포트 기본 5432) |
| `JWT_SECRET` | Base64로 인코딩한 최소 32바이트 난수. dev·prod 필수 |
| `GOOGLE_CLIENT_IDS` | 허용할 OAuth Client ID 목록, 쉼표로 구분. dev·prod 필수 |
| `SWAGGER_ENABLED` | 문서 공개 여부 |
| `CORS_ORIGINS` | 허용할 웹 출처 목록, 쉼표로 구분 |
| `PORT` | 서버 포트, 기본 8080 |

`local`에서 JWT 키를 생략하면 경고 후 임시 키를 생성합니다. 재시작하면 기존 토큰은
무효가 됩니다. Google 설정을 생략한 local 환경에서는 실제 로그인을 할 수 없습니다.
`.env`는 Spring Boot가 자동으로 읽지 않습니다. IDE 실행 환경이나 쉘, 컨테이너의
환경 변수 주입 기능을 사용하세요. 비밀번호와 JWT 키는 Git에 커밋하지 않습니다.

로컬에서는 `.env.local` 파일을 만들어 둘 수 있습니다. 이 파일은 Git에서 제외되며,
예시는 `.env.local.example`를 참고하세요.

## 로컬 실행

Tailscale 접속과 DBeaver SQL 실행을 먼저 완료합니다. macOS의 zsh에서는
비밀번호가 쉘 기록에 남지 않도록 다음과 같이 입력할 수 있습니다.

```bash
cp .env.local.example .env.local
set -a
source .env.local
set +a
read -s 'DB_PASSWORD?개발 DB 비밀번호(없으면 Enter): '; echo
export DB_PASSWORD
export SPRING_PROFILES_ACTIVE=local
# 실제 Google 로그인을 사용할 때 설정합니다.
export GOOGLE_CLIENT_IDS='발급받은-client-id.apps.googleusercontent.com'
./gradlew bootRun
```

Swagger: <http://localhost:8080/swagger-ui/index.html>

OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Google 로그인 API에 유효한 `idToken`을 보내고 반환된 `accessToken`을 Swagger의
**Authorize**에 입력하면 인증 API를 테스트할 수 있습니다.
Google 토큰의 서명·발급자·만료·대상 Client ID를 검증하며 이메일만으로 계정을 합치지 않습니다.

운영에서 Swagger를 활성화하면 문서 경로는 공개되므로 Ingress 등의 접근 제어를
함께 적용하세요. API 인증은 별도로 유지됩니다.

## 테스트와 빌드

```bash
./gradlew clean check bootJar --no-daemon
```

결과물: `build/libs/app.jar`

통합 테스트는 임시 PostgreSQL 프로세스를 실행하고 테스트 DB에만 수동 SQL을 적용합니다.
개발·운영 DB와 Google 계정에는 접속하지 않습니다. 테스트 실행 환경에는 임시
PostgreSQL 바이너리 실행 권한이 필요하며 Linux에서는 비루트 계정으로 실행하세요.

주요 검증: 토큰 회전·재사용 폐기, 입력 검증, 부분 수정, 사용자별 접근 제어,
기록 멱등성·커서 페이지네이션·삭제 복구, 경로 400점 제한, 시간대 경계·통계,
Swagger·상태 확인.

## Docker 및 기존 Jenkins → Argo 배포

기존 파이프라인을 사용하며 Jenkinsfile·Kubernetes·Argo 리소스는 포함하지 않습니다.

```bash
# Jenkins에서 테스트를 먼저 통과시킵니다.
./gradlew clean check --no-daemon

# 실제 레지스트리와 불변 태그로 바꿔 사용합니다.
docker build -t your-registry/pace-backend:commit-sha .
docker push your-registry/pace-backend:commit-sha
```

Dockerfile은 Java 21 멀티스테이지 빌드 후 UID/GID `10001`로 실행합니다.
이미지 빌드 자체는 테스트를 실행하지 않으므로 앞선 테스트 단계가 필수입니다.

배포 환경에서 DB 접속 정보·JWT 키·Google Client ID를 ConfigMap/Secret 등으로 주입합니다.
`prod`는 `DB_URL` 또는 `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`,
`GOOGLE_CLIENT_IDS`, `SPRING_PROFILES_ACTIVE=prod`가 필요합니다.

- 컨테이너 포트: `8080`
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness` (DB 연결 포함)
- 정상 종료: SIGTERM과 Spring의 graceful shutdown 사용
- DB SQL은 배포 전 별도 수동 실행
- DB/Tailscale·Google JWKS 서버로의 네트워크 접근 필요
- Ingress에서 HTTPS, 요청 크기 제한, 인증 API 호출 제한을 적용하세요.

## API 운영 규칙

- 기본 경로 `/api/v1`, JWT Bearer 인증
- 서버 시각은 UTC, 집계 기본 시간대는 `Asia/Seoul`
- 목록 정렬은 `startedAt DESC, id DESC`, limit 최대 50
- `POST /runs`는 `clientRunId`와 선택적 `Idempotency-Key`로 재전송 처리
- 로그아웃은 갱신 토큰 계열을 폐기합니다. 발급된 접근 토큰은 만료 전까지 유효합니다.
- 탈퇴는 즉시 인증 차단 후 기본 60초 간격 배치로 DB 데이터를 하드 삭제합니다.
- GPS 거리 차이가 10%를 넘으면 좌표 원문 없이 경고하고 현재는 클라이언트 거리를 사용합니다.
- 월 통계는 1~7일, 8~14일 단위 버킷입니다.
