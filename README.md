# RoutePick BE 🚦
커뮤니티 기반 **드라이브/루트 추천** 백엔드 서비스.

> Spring Boot 3.5, Java 21, MySQL 8, JPA Auditing, Swagger(springdoc), Docker Compose

---

## ⚙️ 기술 스택
- **Language/Runtime**: Java 21  
- **Framework**: Spring Boot (Web, Validation), Spring Data JPA (Hibernate)  
- **DB**: MySQL 8.x  
- **Build**: Gradle (Wrapper 포함)  
- **API 문서**: springdoc-openapi (Swagger UI)  
- **Infra(Local)**: Docker Compose (MySQL + Adminer)  
- **Log**: SLF4J + Logback  
- **기타**: Lombok, JPA Auditing(BaseEntity), 값 타입 컬렉션(ElementCollection)

> 이후 계획: QueryDSL 기반 검색/정렬, 글로벌 예외 표준화, CI/CD, Flyway 마이그레이션

---

## 🧱 아키텍처 & 패키지
**3-Layered**: `controller` → `service` → `repository`  
공용/설정: `common`, `config` / 도메인: `entity`, `dto`

```text
io.routepickapi
├─ controller/…      ─ API 입구
├─ service/…         ─ 트랜잭션/도메인 로직
├─ repository/…      ─ JPA 리포지토리
├─ entity/…          ─ 도메인 엔티티
├─ dto/…             ─ 요청/응답 DTO
├─ common/model      ─ BaseEntity 등
└─ config            ─ Swagger, Auditing
```

---

## 📚 API 문서
엔드포인트 목록/스펙은 **Swagger UI**에서 자동 생성본으로 관리합니다.  
도메인별 상세 문서는 필요 시 `docs/api/<domain>.md` 로 분리합니다. (예: `docs/api/posts.md`)

---

## 🔐 환경 변수
- `JWT_SECRET`: JWT 서명 키 (필수)
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NICKNAME`: 서버 시작 시 관리자 계정 시드 값 (미설정 시 생성 안 함)
- `KAKAO_REST_API_KEY`: Kakao Local Search REST API 키
- `KAKAO_OAUTH_CLIENT_ID`: Kakao OAuth Client ID (REST API 키)
- `KAKAO_OAUTH_CLIENT_SECRET`: Kakao OAuth Client Secret (사용 시)
- `KAKAO_OAUTH_REDIRECT_URI`: Kakao OAuth Redirect URI
- `KAKAO_OAUTH_LOGOUT_REDIRECT_URI`: Kakao OAuth Logout Redirect URI
- `LLM_API_URL`: LLM Chat Completions API URL
- `LLM_API_KEY`: LLM API 키
- `LLM_MODEL`: LLM 모델 이름 (기본값 `gpt-4o-mini`)
- `LLM_CURATION_DAILY_LIMIT`: 크루저 큐레이션 일일 요청 제한 (기본값 `5`)
- `KMA_SERVICE_KEY`: 기상청 단기예보 API 키
- `AWS_REGION`: AWS 리전 (기본값 `ap-northeast-2`)
- `AWS_S3_BUCKET`: 이미지 업로드용 S3 버킷 이름
- `SMTP_HOST`: SMTP 호스트 (기본값 `smtp.gmail.com`)
- `SMTP_PORT`: SMTP 포트 (기본값 `587`)
- `SMTP_USERNAME`: SMTP 로그인 이메일
- `SMTP_PASSWORD`: SMTP 앱 비밀번호
- `SMTP_FROM_EMAIL`: 발신 이메일 (기본값 `SMTP_USERNAME`)

### S3 공개 설정
- 업로드된 이미지는 버킷 정책을 통해 `s3:GetObject` 공개 접근을 허용해야 합니다.
- 버킷이 `ACL 비활성(Bucket owner enforced)` 상태여도 동작하도록 ACL을 사용하지 않습니다.

### Gmail SMTP 설정
- Gmail 계정에서 앱 비밀번호를 생성해 `SMTP_PASSWORD`로 사용하세요.
- SMTP는 `prod` 프로필에서 사용됩니다.

---


## 🔀 브랜치 전략 & 커밋 컨벤션
- 브랜치: `main`(배포), `dev`(통합), `feature/*`
- 흐름: `feature → dev → main(PR)`
- 커밋: `feat|fix|chore|docs|refactor|test: 메시지`


## 📄 라이선스
TBD
