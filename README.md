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

## 🤖 CI/CD (GitHub Actions)

- CI: `dev` 브랜치 push/PR 시 `./gradlew clean build` 수행
- CD: `main` push 시 자동 배포(Production 승인 후), 필요 시 `workflow_dispatch`로 수동 배포
- GitHub Secrets: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`

---

### 🐳 Docker Compose (prod)

```
docker compose -f ../../docker-compose.prod.yml up -d --build
```

---

## 🔐 외부 API 키 설정

외부 API 키는 환경변수로 주입하고, `application.yml`에서 참조합니다.
드라이브 추천은 **curated 스팟 + Kakao Local + TourAPI + Routing + KMA 날씨** 조합으로 동작합니다.

---

## 🔀 브랜치 전략 & 커밋 컨벤션

- 브랜치: `main`(배포), `dev`(통합), `feature/*`
- 흐름: `feature → dev → main(PR)`
- 커밋: `feat|fix|chore|docs|refactor|test: 메시지`

## 📄 라이선스

TBD
