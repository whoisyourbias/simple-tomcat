# 00-01. 현재 진행 상황

기준일: 2026-11-04

**현재: 0~2단계 완료. 다음: 3단계 Servlet·StandardWrapper.**

## 단계별 현황과 완료 기준

| 단계 | 구현 범위 | 상태 | 완료 기준 |
|---|---|---|---|
| 0. 실행 환경 | JDK·Maven Wrapper·JUnit·기본 패키지 | 완료 | clean verify, JDK 컴파일러 테스트, JAR 생성 |
| 1. 소켓 연결 | Endpoint의 연결 수락·고정 응답·종료 | 완료 | 같은 서버의 두 요청 응답, accept·헤더 읽기 중 종료, 포트 해제 |
| 2. Coyote HTTP 처리 | Request·Response·Http11Processor·ProtocolHandler·Adapter | 완료 | 테스트용 Adapter까지 HTTP 왕복, 잘못된 요청 400, 한글 본문 바이트 길이 |
| 3. 서블릿과 Wrapper | Servlet 계약·StandardWrapper·서블릿 생명주기 | 예정 | 소켓 없이 두 번 호출 후 종료, init/service/destroy 횟수 1/2/1 |
| 4. Catalina 컨테이너 | Engine → Host → Context → Wrapper·Pipeline·Valve | 예정 | 선택 대상을 지정해 컨테이너 계층의 서블릿 호출 순서 확인 |
| 5. Mapper | Mapper·MappingData·대상 선택 | 예정 | 정확한 경로·최장 경로 접두어·확장자·기본 매핑 순서, 선택과 호출 분리 |
| 6. 전체 연결과 생명주기 | Connector·CoyoteAdapter·서블릿 요청/응답·StandardServer·StandardService·SimpleTomcat | 예정 | 실제 /hello 서블릿, 404·500 후 다음 요청 성공, 시작 실패 자원 정리 |
| 7. 필터 체인 | Filter·FilterChain·ApplicationFilterChain·필터 등록 | 예정 | A 전 → B 전 → 서블릿 → B 후 → A 후, 중간 응답 시 서블릿 호출 0회 |
| 8. 동시성과 종료 | 제한된 작업 풀·읽기 타임아웃·처리 중 연결 정리 | 예정 | 느린 연결 중 다른 요청 성공, 요청 정리 뒤 서블릿 해제 |
| 9. simple-spring 연결 | 기본 / 매핑에 직접 만든 디스패처 등록 | 예정 | 주문 한 건 후 GET /orders/count에 200과 본문 1 |
| 10. 학습용 Jasper | JSP 변환·컴파일·로딩·실행·재사용 | 예정 | /hello.jsp HTML 반환, 컴파일 결과 재사용, JSP 수정 시 갱신 |

## 현재 동작과 검증

- Endpoint → Handler → Http11Processor → 테스트 Adapter → HTTP 응답 흐름을 구현했다.
- HTTP/1.0·1.1, 본문 없는 유효 메서드, 400/500/501/505, UTF-8 바이트 길이와 HEAD·상태별 본문 처리, 연결당 한 요청·종료를 지원한다.
- 2026-11-04 clean verify: 76개 테스트 통과·JAR 생성. 같은 서버 포트 18082의 curl 두 요청에 200·22바이트·연결 종료를 확인했다.
- [2단계 요청 흐름](../02-http/01-coyote-and-request-flow.md), [테스트와 실행 증거](../02-http/02-tests-and-next-steps.md), [Tomcat 대응·차이 D1~D12](../02-http/03-tomcat-comparison.md)를 함께 읽는다.
- 2026-11-03의 1단계 검증은 6개 테스트·curl 두 요청 성공이며 당시 기록을 보존한다. 2026-11-04 문서 재구성 이후 별도로 2단계를 구현·검증했다.

## 다음 작업

3단계 Servlet 계약·StandardWrapper를 소켓 없이 구현한다. 같은 인스턴스에 두 번 service 후 종료하여 init/service/destroy 횟수 1/2/1을 검증한다. 실제 CoyoteAdapter·Catalina 연결은 6단계에 둔다.

## 상세 문서

- [전체 목표·프로젝트 경계](02-architecture.md) · [빌드·테스트 환경](03-build-environment.md)
- [Endpoint 요청 흐름](../01-socket/01-endpoint-and-request-flow.md)
- [계획한 패키지 배치](../reference/01-package-structure.md) · [학습 단계와 실제 기술 대응](../reference/03-learning-stage-mapping.md)
- [초기 작업·검증 기록](../history/01-initial-implementation.md)

[학습 문서 목차](../README.md)
