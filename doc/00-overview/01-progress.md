# 00-01. 현재 진행 상황

기준일: 2026-11-04

**현재: 0단계 빌드 환경과 1단계 소켓 연결 완료. 다음: 2단계 Coyote HTTP 처리.**

## 단계별 현황과 완료 기준

| 단계 | 구현 범위 | 상태 | 완료 기준 |
|---|---|---|---|
| 0. 실행 환경 | JDK·Maven Wrapper·JUnit·기본 패키지 | 완료 | clean verify, JDK 컴파일러 테스트, JAR 생성 |
| 1. 소켓 연결 | Endpoint의 연결 수락·고정 응답·종료 | 완료 | 같은 서버의 두 요청 응답, accept·헤더 읽기 중 종료, 포트 해제 |
| 2. Coyote HTTP 처리 | Request·Response·Http11Processor·ProtocolHandler·Adapter | 예정 | 테스트용 Adapter까지 HTTP 왕복, 잘못된 요청 400, 한글 본문 바이트 길이 |
| 3. 서블릿과 Wrapper | Servlet 계약·StandardWrapper·서블릿 생명주기 | 예정 | 소켓 없이 두 번 호출 후 종료, init/service/destroy 횟수 1/2/1 |
| 4. Catalina 컨테이너 | Engine → Host → Context → Wrapper·Pipeline·Valve | 예정 | 선택 대상을 지정해 컨테이너 계층의 서블릿 호출 순서 확인 |
| 5. Mapper | Mapper·MappingData·대상 선택 | 예정 | 정확한 경로·최장 경로 접두어·확장자·기본 매핑 순서, 선택과 호출 분리 |
| 6. 전체 연결과 생명주기 | Connector·CoyoteAdapter·서블릿 요청/응답·StandardServer·StandardService·SimpleTomcat | 예정 | 실제 /hello 서블릿, 404·500 후 다음 요청 성공, 시작 실패 자원 정리 |
| 7. 필터 체인 | Filter·FilterChain·ApplicationFilterChain·필터 등록 | 예정 | A 전 → B 전 → 서블릿 → B 후 → A 후, 중간 응답 시 서블릿 호출 0회 |
| 8. 동시성과 종료 | 제한된 작업 풀·읽기 타임아웃·처리 중 연결 정리 | 예정 | 느린 연결 중 다른 요청 성공, 요청 정리 뒤 서블릿 해제 |
| 9. simple-spring 연결 | 기본 / 매핑에 직접 만든 디스패처 등록 | 예정 | 주문 한 건 후 GET /orders/count에 200과 본문 1 |
| 10. 학습용 Jasper | JSP 변환·컴파일·로딩·실행·재사용 | 예정 | /hello.jsp HTML 반환, 컴파일 결과 재사용, JSP 수정 시 갱신 |

## 현재 동작과 검증

- Endpoint가 연결을 순차 수락하고 `\r\n\r\n`까지 읽은 뒤 고정 응답을 쓴다.
- 응답은 `200 OK`, `Content-Length: 22`, `Connection: close`, 본문 `Hello, simple-tomcat!\n`이다.
- 응답 후 연결을 닫고 다음 연결을 받는다. `close()`는 리스닝 소켓과 처리 중 소켓을 닫는다.
- 마지막 기능 검증은 2026-11-03이다. `clean verify`로 테스트 6개 통과·JAR 생성, 같은 서버의 curl 요청 두 번에 200을 확인했다.
- 2026-11-04 작업은 문서 재구성이다. 현재 단계와 지원 기능은 그대로이며, 기존 테스트 수치를 새 실행 결과로 간주하지 않는다.

[구현 상세·제약](../reference/02-implementation-status.md)과 [1단계 테스트·실행 증거](../01-socket/02-tests-and-next-steps.md)를 참고한다.

## 다음 작업

1. `coyote.Request`·`Response`, `Adapter`·`ProtocolHandler` 계약을 정의한다.
2. Endpoint의 헤더 읽기·고정 응답을 Http11Processor의 HTTP 파싱·응답 작성으로 분리한다.
3. Http11Protocol에서 Endpoint와 Processor를 연결한다.
4. 테스트용 Adapter까지 HTTP 왕복을 검증한다.
5. 잘못된 요청의 400 응답과 한글 본문의 바이트 길이를 확인한다.

## 상세 문서

- [전체 목표·프로젝트 경계](02-architecture.md) · [빌드·테스트 환경](03-build-environment.md)
- [Endpoint 요청 흐름](../01-socket/01-endpoint-and-request-flow.md)
- [계획한 패키지 배치](../reference/01-package-structure.md) · [학습 단계와 실제 기술 대응](../reference/03-learning-stage-mapping.md)
- [초기 작업·검증 기록](../history/01-initial-implementation.md)

[학습 문서 목차](../README.md)
