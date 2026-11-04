# 1단계 소켓 연결 구현 계획

> 2026-11-03 작성·실행한 계획이다. 2026-11-04 문서 재구성 이후 현재 상태는 [진행 상황](../../../doc/00-overview/01-progress.md), 당시 실행 증거는 [초기 구현 기록](../../../doc/history/01-initial-implementation.md), 학습 설명은 [1단계 문서](../../../doc/01-socket/01-endpoint-and-request-flow.md)를 참고한다. 아래 파일 경로와 작업 목록은 당시 계획을 보존한다.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 같은 Endpoint에 두 번 요청하여 고정 HTTP 응답을 받고 서버를 종료한다.

**Architecture:** `simpletomcat.util.net.Endpoint`가 ServerSocket을 소유하고 호출 스레드에서 연결을 순서대로 처리한다. CRLF 두 번까지 헤더를 읽고 고정 응답을 쓴 뒤 연결을 닫는다. `main`은 수동 실행 진입점이며 종료 훅이 Endpoint를 닫는다.

**Tech Stack:** JDK 21, Maven Wrapper 3.9.9, JUnit Jupiter 5.11.4.

**Spec:** `doc/progress.md`의 1단계 및 다음 작업. 사용자의 “main최신화하고 1단계구현해” 요청으로 기존 범위의 실행이 승인됐다.

## Global Constraints

- 패키지는 `simpletomcat.util.net`, 단일 JAR 라이브러리로 유지한다.
- 초기 범위는 본문 없는 GET, 연결당 요청 한 건, 순차 처리다.
- 응답에는 올바른 Content-Length 및 Connection: close를 포함한다.
- HTTP 파싱과 상태 400은 2단계, 작업 풀과 읽기 타임아웃은 8단계에서 구현한다.

## Review Focus

- 한 연결만 처리하고 서버까지 종료하는 오류: 같은 Endpoint에서 연속 두 번 요청한다.
- TCP에서 헤더가 나뉘어 도착하는 경우: 마지막 CRLF 전에는 응답하지 않는다.
- 헤더 완료 전 EOF: 응답 없이 연결을 닫고 다음 요청을 받는다.
- accept 대기 중 종료: serve가 반환하고 포트를 다시 사용할 수 있다.
- 헤더 읽기 중 종료: 처리 중 소켓도 닫혀 serve가 반환한다.

### Task 1: Endpoint와 실행·종료 검증

**Files:**
- Create: `src/main/java/simpletomcat/util/net/Endpoint.java`
- Test: `src/test/java/simpletomcat/util/net/EndpointTest.java`
- Modify: `README.md`, `doc/progress.md`

**Interfaces:**
- Consumes: JDK `ServerSocket`, `Socket`, 입출력 스트림.
- Produces: `Endpoint(int port) throws IOException`, `int getPort()`, `void serve() throws IOException`, `void close() throws IOException`, `static void main(String[] args) throws IOException`.
- 포트 0은 테스트용 임시 포트이며, main 기본 포트는 8080이다.
- 고정 본문은 UTF-8 `Hello, simple-tomcat!\n` (22바이트)이다.

- [x] 위 Review Focus를 실제 TCP 소켓으로 검증하는 테스트를 작성한다.
- [x] `.\mvnw.cmd -B test`를 실행해 Endpoint 미구현으로 실패함을 확인한다.
- [x] Endpoint를 구현하고 동일 테스트를 통과시킨다.
- [x] `.\mvnw.cmd -B clean verify` 성공과 JAR 생성을 확인한다.
- [x] 같은 프로세스에 `curl.exe -v http://localhost:8080/hello`를 두 번 실행해 200, 본문, 연결 종료를 확인한다.
- [x] 실행 방법, 메서드 호출 흐름, 검증 결과와 2단계의 범위를 문서에 기록한다.

## 실행 기록

- main은 origin/main과 동일한 `6907b98`이었다. fast-forward pull 결과 Already up to date.
- 기본 테스트 1개가 통과했다. 현재 작업 폴더의 `codex/step-1-socket-endpoint` 브랜치에서 구현한다.
- 별도 작업 트리나 외부 변경 없이 현재 폴더에서 실행하고 최종 변경은 사용자가 검토할 수 있게 남긴다.
- Task 1: complete — clean verify 성공, 기존 1개와 Endpoint 5개로 합계 6개 통과. 같은 서버의 curl 두 요청 모두 200·22바이트 본문·연결 종료, 종료 코드 0 확인 후 서버 종료.
- 최종 독립 리뷰: 수정이 필요한 지적 없음. 클라이언트 연결 리셋의 별도 테스트는 선택적 보완으로 남긴다. HTTP 검증과 작업 풀·타임아웃은 각각 2·8단계 범위를 유지한다.
