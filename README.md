# PACE 백엔드

Java 21 · Spring Boot 3.5 · Gradle · PostgreSQL · JPA · MyBatis 기반 API입니다.

## 구현 범위

- Google·Apple ID 토큰 검증 후 가입·로그인
- JWT 접근 토큰(30분), 갱신 토큰(30일) 회전과 재사용 감지
- 내 정보·사용자 설정 부분 수정 및 회원 탈퇴
- 러닝 저장·목록·상세·제목 수정·소프트 삭제·24시간 이내 복구
- 시간대 기준 대시보드·주간·월간 통계
- 크루 생성·검색·주간/월간 순위·실제 러닝 통계·초대 코드·가입 승인과 크루장 권한 양도
- 공개·초대 전용 대결 생성·검색·참가·취소와 모집 마감 시점의 인원 확정
- Swagger, 상태 확인, 비루트 사용자 Docker 이미지

Kakao 인증, 이미지 업로드와 실제 알림 발송은 후속 범위입니다.
대결 참가 결과는 GPS 기록 검증과 연동하지 않으며, 참가자 결과는 `null`입니다.

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
    filter/                 JWT 인증·보안 예외 필터 (보안 체인에서만 등록)
    provider/               Google·Apple 검증·Apple 연결 해제·JWT 인증 Provider
    security/               인증 실패·권한 거부 공통 응답
  user/                     사용자·설정 도메인
  run/                      러닝 도메인
  stats/                    집계 도메인
  crew/                     크루·가입 신청 도메인
  race/                     대결·참가 도메인
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

### 조회 DTO와 요청 호환성

기존 도메인별 계층 구조를 유지하며 HTTP 바인딩 객체는 각 도메인의 `dto`에 둡니다.
컨트롤러 내부의 크루장 양도 요청도 `LeaderTransferRequest` DTO로 분리합니다.
엔티티를 HTTP 요청 객체로 사용하거나 컨트롤러에서 Repository를 직접 호출하지 않습니다.

조회 조건은 불변 record와 `@ModelAttribute`로 받습니다. JSON 본문으로 변경하지 않으며
URL·쿼리 파라미터 이름·응답 필드와 기존 기본값을 유지합니다.
OpenAPI에는 `@ParameterObject`로 개별 쿼리 파라미터를 표시합니다.

| 조회 | DTO | 생략 또는 빈 값의 기본값 |
|---|---|---|
| 러닝 목록 | `RunListQuery` | `limit=20` |
| 러닝 상세 | `RunDetailQuery` | `route=simplified` |
| 대시보드 | `DashboardQuery` | `timeZone=Asia/Seoul` |
| 러닝 통계 | `RunningStatsQuery` | `period=week`, `timeZone=Asia/Seoul` |
| 크루 검색 | `CrewSearchQuery` | 선택 조건은 그대로 전달 |
| 크루 순위·상세 | `CrewPeriodQuery` | `period=week` |
| 대결 목록 | `RaceListQuery` | `scope=open`, `hideFull=false` |

날짜·숫자·불리언 변환 실패는 400으로 처리합니다. 기간·거리·페이지 크기 등의
비즈니스 유효성 검증은 기존 Service에 남겨 HTTP 이외의 호출에도 동일하게 적용합니다.
경로 ID·인증 주체·멱등성 헤더는 의미가 다른 입력이므로 조회 DTO에 섞지 않습니다.

### 인증 및 전역 예외 처리

보안 체인의 핵심 순서는 다음과 같습니다. 요청 ID는 체인 밖의 `RequestIdFilter`가 먼저 발급합니다.

1. `SecurityExceptionFilter`: 하위 필터에서 전파된 인증·인가 예외만 처리합니다.
2. `JwtAuthenticationFilter`: Spring Security의 표준 토큰 추출과 SecurityContext 처리를 재사용합니다.
3. `ProviderManager` → `JwtTokenProvider`: `JwtDecoder`의 HS256 서명·발급자 `pace-api`·대상
   `pace-app`·만료 및 필수 클레임 검증 후 활성 사용자인지 확인합니다.
4. `AuthenticatedUserMdcFilter`: 인증 사용자 ID를 로깅 컨텍스트에 넣고 요청 종료 시 정리합니다.
5. Spring Security 인가 필터: 공개 경로를 제외한 모든 요청에 인증을 요구합니다.

사용자 정의 필터는 서블릿 필터 Bean으로 중복 등록하지 않고 `SecurityFilterChain`에만 추가합니다.
세션은 만들지 않으며 쿠키 인증이 아닌 Authorization 헤더 기반 API이므로 CSRF를 비활성화합니다.
일반 서버 예외를 인증 실패로 위장하지 않으며 응답이 이미 전송됐다면 오류 본문을 덧붙이지 않습니다.

`SecurityErrorHandler`는 `AuthenticationEntryPoint`와 `AccessDeniedHandler`를 구현합니다.
인증 실패는 401 및 `WWW-Authenticate: Bearer`, 권한 부족은 403을 반환합니다.
MVC 밖의 필터 오류는 Advice가 잡을 수 없으므로 이 핸들러로 처리하고,
MVC 안의 인증·인가 예외는 `GlobalExceptionHandler`에서 같은 핸들러로 위임합니다.

공통 `ApiError`는 기존 `code`, `message`, `requestId`, `fieldErrors` 필드를 유지합니다.
`BusinessException`은 업무 오류의 상태와 코드를 보존하고, 바인딩 오류는 400,
DB 제약 충돌은 409, 예상하지 못한 예외는 상세 내용을 노출하지 않는 500으로 처리합니다.
필드 검증 실패의 `fieldErrors`에는 `field`와 `reason`만 포함하고 입력 원문은 반환하지 않습니다.

### 정리 대상과 민감 정보

현재 추적 파일에는 빌드 산출물·IDE 설정·개인 인증서가 없어 삭제할 파일은 없습니다.
개발 환경에 생성된 다음 항목은 필요 시 정리하고 Git에는 추가하지 않습니다.

- `build/`, `target/`, `.gradle/`, `out/`, `bin/`, `*.class`: 빌드 및 캐시
- `.idea/`, `.vscode/`, `*.iml`, `.classpath`, `.project`, `.settings/`: 개인 IDE 설정
- `*.log`, `*.tmp`, `*.swp`, `*.swo`, `*~`, `*.hprof`, `hs_err_pid*`: 로그·임시 파일·메모리 덤프
- `.env`, `.env.*`, `application-secrets.*`, `application-credentials.*`: 개인 비밀 설정
- `*.key`, `*.pem`, `*.p12`, `*.pfx`, `*.jks`, `*.keystore`: 개인 키·인증서

위 항목은 `.gitignore`로 제외하며 값 없는 `.env.example`, `.env.local.example`은 유지합니다.
Gradle Wrapper와 `database/` SQL, 비밀 값 없는 공용 프로필 YAML은 삭제하지 않습니다.
`application.yml`과 공용 프로필의 DB 비밀번호·JWT 키 등은 이미 환경 변수 참조로 분리되어 있습니다.
실제 값은 실행 환경 또는 Secret Manager로 주입하고, 개인 설정은 제외된 파일에만 보관합니다.
`.gitignore`는 이미 추적된 파일이나 과거 커밋의 비밀을 제거하지 않습니다.
노출 이력이 있다면 해당 자격 증명을 먼저 폐기·교체하고 별도로 저장소 이력을 정리해야 합니다.

## DB 준비 (DBeaver 수동 실행)

1. PostgreSQL에 `pace_dev` 또는 `pace_prod` DB와 접속 계정을 준비합니다.
2. 신규 DB는 DBeaver에서 [database/schema.sql](database/schema.sql)을 실행합니다.
3. 이어서 [database/community-schema.sql](database/community-schema.sql)을 한 번 실행합니다.
   기존 DB도 비우거나 `schema.sql`을 다시 실행하지 말고 커뮤니티 스키마만 추가합니다.
4. 애플리케이션 계정에 테이블·시퀀스 사용 권한을 부여합니다.

기존 Google 전용 DB는 **새 서버 배포 전에**
[database/apple-auth-schema.sql](database/apple-auth-schema.sql)을 DBeaver에서 실행합니다.
`user_auth_providers.provider`의 CHECK만 `google`, `apple` 허용으로 확장하며
사용자·러닝·토큰 행을 삭제하거나 테이블을 재생성하지 않습니다. 신규 DB는 수정된
`schema.sql`에 이미 반영되어 별도 실행이 필요 없습니다.
기존 제약 이름은 `user_auth_providers_provider_check`를 전제로 합니다. 실행 전
`SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'user_auth_providers'::regclass;`
로 확인하고, 다르면 실제 CHECK 이름에 맞춰 SQL을 수정하세요.

`community-schema.sql`은 크루·신청·대결용 테이블과 FK/인덱스, 마감 인원 스냅샷 트리거를
추가합니다. `users`, `runs` 및 기존 행은 변경·삭제하지 않습니다. 커뮤니티 데이터가 아직
없으므로 새 테이블은 비어 있는 상태로 시작합니다. 이 파일은 대상 DB마다 한 번만 실행합니다.
서버는 테이블을 생성하거나 변경하지 않습니다. `ddl-auto: validate`로 전체 스키마를
검증하고 SQL 자동 초기화도 끕니다. SQL은 런타임 classpath 밖에 있습니다.
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
| `APPLE_CLIENT_IDS` | 검증할 Apple audience 목록, 쉼표로 구분. 기본 `com.hocompany.pace` |
| `APPLE_CLIENT_ID` | 탈퇴 코드 교환·폐기의 client_id. 기본 `com.hocompany.pace`, `APPLE_CLIENT_IDS`에도 포함해야 함 |
| `APPLE_CLIENT_SECRET` | Apple ES256 client secret JWT. Apple 탈퇴 기능에 필수, 기본 빈 값(탈퇴 시 503, 성공 처리하지 않음) |
| `SWAGGER_ENABLED` | 문서 공개 여부 |
| `CORS_ORIGINS` | 허용할 웹 출처 목록, 쉼표로 구분 |
| `PORT` | 서버 포트, 기본 8080 |

### Apple 로그인·탈퇴 계약 및 배포 순서

`POST /api/v1/auth/apple`은 인증 헤더 없이 다음 JSON을 받으며 Google과 동일한
`AuthTokenResponse`를 반환합니다.

```json
{"idToken":"Apple identityToken","nonce":"새로 생성한 raw UUID nonce","nickname":"애플 러너"}
```

- `idToken`은 필수, 최대 16,000자, `nonce`는 필수 원문 16~256자입니다.
  매 인증마다 암호학적으로 안전한 새 nonce를 생성하세요.
- Expo `signInAsync({nonce: SHA256(rawNonce)})`에는 **소문자 16진수 SHA-256**을,
  서버에는 `rawNonce`를 보냅니다. Expo 네이티브는 `request.nonce = options.nonce`로
  그대로 전달하므로 추가 자동 해싱을 가정하지 않습니다.
  [Expo 공식 문서](https://docs.expo.dev/versions/latest/sdk/apple-authentication/)
  및 [공식 네이티브 구현](https://github.com/expo/expo/blob/sdk-58/packages/expo-apple-authentication/ios/AppleAuthenticationRequest.swift)을 확인했습니다.
- Apple JWKS의 RS256 서명, 발급자 `https://appleid.apple.com`, 허용 audience,
  만료·필수 subject 및 nonce 해시를 검증합니다. 시각 검증은 Spring 기본 60초 오차를 허용합니다.
  서명 키는 원격 JWKS 캐시를 사용하며 연결 3초·응답 5초 제한을 둡니다.
- nickname은 선택, 원문 2~30자이며 strip 후 2자 이상이어야 합니다. 최초 가입에만
  저장하고 이후 로그인에서 덮어쓰지 않습니다. Apple 이름이 없는 최초 가입은 `러너`를 사용합니다.
- 이메일은 계정 연결 키로 사용하지 않습니다. 제공자와 subject가 같은 계정만 재사용합니다.
  Google과 Apple의 subject 또는 이메일이 같더라도 자동 병합하지 않습니다.
- nonce는 서버 발급 챌린지나 일회용 저장소가 아닙니다. 로그인 토큰·원문 nonce 쌍을
  탈취한 경우 만료 전 재전송을 막는 전역 replay 저장소는 제공하지 않으므로 HTTPS,
  인증 요청/응답 본문 비로깅, 클라이언트의 nonce 비재사용이 필수입니다.

**Apple 탈퇴는 프런트엔드 재인증 연동이 필수입니다.** 로그인 요청에 authorizationCode를
추가하거나 Apple refresh token을 DB에 저장하지 않습니다. 탈퇴 직전 Apple에 다시 로그인하고
새 `rawNonce`의 해시를 Apple 요청에 전달한 뒤 아래 요청을 보냅니다.

```http
DELETE /api/v1/users/me
Authorization: Bearer <PACE accessToken>
Content-Type: application/json

{"appleAuthorizationCode":"새 Apple authorizationCode","appleNonce":"이번 Apple 재인증의 rawNonce"}
```

서버는 일회용 코드(Apple 유효기간 5분)를 `/auth/token`에서 교환하고, 반환된 ID 토큰의
서명·nonce와 저장된 Apple subject를 대조한 뒤 `/auth/revoke`에 refresh token을 보냅니다.
Apple의 HTTP 200 폐기 응답을 확인한 후에만 PACE 계정을 `deleting`으로 변경하고
PACE 갱신 토큰을 폐기하여 202를 반환합니다. 그 뒤 기존 배치가 DB 데이터를 삭제합니다.
Google 등 Apple 연결이 없는 계정은 기존처럼 DELETE 본문 없이 탈퇴할 수 있습니다.

- Apple 계정에서 본문 없음: 409 `APPLE_REAUTH_REQUIRED`.
- 잘못된 본문: 400, 재인증 계정 불일치·ID 토큰/nonce 오류: 401.
- client secret 미설정: 503 `APPLE_REVOCATION_NOT_CONFIGURED`.
- 코드 교환·폐기 HTTP 오류/타임아웃/필수 토큰 누락: 502 `APPLE_REVOCATION_FAILED`.
  위 실패는 로컬 탈퇴를 진행하지 않습니다. 클라이언트는 성공 화면으로 처리하지 말고
  원인을 해결한 후 **새 Apple 코드·nonce**로 다시 시도해야 합니다.
- Apple 원격 폐기와 DB 커밋은 분산 트랜잭션이 아닙니다. 원격 폐기 후 로컬 커밋 실패나
  응답 유실 가능성까지 원자적 완료를 보장하지 않습니다. 202도 Apple 사용자의 전체 Apple ID
  삭제나 모든 기기의 즉시 반영, DB 배치 삭제 완료를 뜻하지 않습니다.

`APPLE_CLIENT_SECRET`은 배포 비밀 관리 시스템에서 생성·주기적으로 교체하는 Apple client
secret JWT입니다. 헤더 `alg=ES256`, `kid=<Apple Key ID>`, 클레임 `iss=<Team ID>`,
`sub=<APPLE_CLIENT_ID>`, `aud=https://appleid.apple.com`, 현재 `iat`, 최대 6개월 이내
`exp`로 Apple Sign in with Apple `.p8` 키를 사용해 서명합니다. 이 서버는 키 파일을
읽거나 client secret을 자동 갱신하지 않습니다. 만료 전 교체가 운영 필수 조건입니다.
코드·ID/refresh 토큰·client secret은 저장하거나 로그에 출력하지 않습니다.
인증·탈퇴 본문을 캡처하는 프록시/APM 및 JDK HTTP wire 로그도 사용하지 마세요.

배포 순서: DB CHECK 수동 확장 → Apple Developer의 앱 ID `com.hocompany.pace`에
Sign in with Apple 설정 및 client secret 준비 → 환경 변수 주입 → 백엔드 배포 →
실제 기기 로그인·재인증 탈퇴 확인입니다. 코드만 배포하고 secret 또는 프런트엔드 탈퇴
재인증을 생략하면 Apple 탈퇴가 **차단**됩니다. App Store 제출 전에 이를 완료해야 합니다.
서버에서 `appleid.apple.com` JWKS·token·revoke HTTPS 통신이 가능해야 합니다.
[Apple 코드 교환](https://developer.apple.com/documentation/signinwithapplerestapi/generate_and_validate_tokens),
[Apple 토큰 폐기](https://developer.apple.com/documentation/signinwithapplerestapi/revoke_tokens).

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
- DB/Tailscale·Google JWKS·Apple JWKS/token/revoke 서버로의 네트워크 접근 필요
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
- 크루는 50명 제한·사용자당 한 크루·탈퇴 후 24시간 재가입 제한을 적용합니다.
  주간은 서울 기준 월요일 시작, 월간은 매월 1일 시작이며 각 멤버의 가입 시각 이후
  실제 삭제되지 않은 러닝만 집계합니다. 비공개 프로필의 멤버 기록은 숨깁니다.
- 크루 생성 응답은 `{ "id": "ULID" }`, 현재 크루는 `GET /users/me/crew`의
  `{ "crewId": "ULID 또는 null" }`입니다. 크루 초대 코드는 8자이며 리더에게만 반환합니다.
  가입 신청 목록·승인·거절, 리더 양도는 리더만 수행할 수 있습니다.
- 대결 거리 `3000`, `5000`, `10000`, `21097`m와 마감 `10`, `30`, `60`분,
  참가 인원 2~20명을 지원합니다. 공개 모집은 `scope=open`, 내 대결은 `scope=mine`이며
  선택 필터는 `distanceMeters`, ISO-8601 `from`/`to`, `hideFull`입니다. 비공개 대결은
  초대 코드로만 가입할 수 있고 호스트/참가자에게만 상세를 노출합니다.
- 대결 모집 마감 시 서버는 인원을 한 번 고정합니다. 최소 인원 미달은 취소되고 충족된 경우
  확정 상태로 간주됩니다. 잠금과 DB 트리거로 계정 삭제 이후에도 마감 시점 인원을 보존합니다.
  참가 결과/GPS 점수 산정은 구현 범위가 아니며 참가자 `result`는 항상 `null`입니다.
