# PR 제목 규칙
# feat|fix|refactor|chore|docs|build|ci|test: 간단 설명
# 예) feat(post): add list API with paging

## Summary
변경 목적과 배경을 짧게 설명합니다.

## Changes
- 변경된 핵심 사항을 항목으로 정리합니다.
- Swagger 스펙 변화가 있으면 간단히 언급합니다.

## Test Plan
- [ ] 로컬 기동 및 스웨거 수동 테스트
- [ ] 핵심 API 성공/실패 케이스 확인
- [ ] DB 쿼리/로그 확인(필요 시)

## Screenshots / Logs (선택)
- 스크린샷, curl 결과, 주요 로그 등

## Checklist
- [ ] 비밀/자격증명 노출 없음
- [ ] 예외 포맷(ApiErrorResponse) 준수
- [ ] DTO @Valid 검증 추가/업데이트
- [ ] Swagger(@Operation/@Schema) 설명 반영
- [ ] README/문서 업데이트(필요 시)
- [ ] 관련 이슈 연결: Closes #<번호> (선택)
