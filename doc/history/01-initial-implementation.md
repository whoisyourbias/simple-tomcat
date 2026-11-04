# 초기 구현·검증 기록

2026-11-03의 설계·0단계·1단계 작업 기록이다. 2026-11-04 문서 재구성 시 기존 `doc/progress.md`의 날짜별 기록을 보존했다. 각 항목의 상태와 다음 작업은 해당 시점 기준이다. 현재 상태는 [진행 상황](../00-overview/01-progress.md)을 참고한다.

## 작업 기록

### 2026-11-03

- 수행한 작업 — 목적·아키텍처 정리, 시각화와 독립 리뷰, 학습 진행 문서 작성.
- 확인한 결과 — 브라우저 애니메이션 검증 결과가 있다. Java 서버 구현과 실제 HTTP 검증은 시작 전이다.
- 다음 작업 — 1단계 HTTP 왕복 구현.

### 2026-11-03 · 0단계 완료

- 수행한 작업 — 이전 패키지 배치를 교체하고 JDK 21·Maven Wrapper 3.9.9·JUnit Jupiter 5.11.4를 설정했다.
- 검증 명령 — Windows PowerShell 세션의 PATH 보정 후 .\mvnw.cmd -B clean verify.
- 관찰 결과 — BUILD SUCCESS. BuildEnvironmentTest 1개 실행, 실패 0·오류 0·건너뜀 0. target/simple-tomcat-0.1.0-SNAPSHOT.jar 생성.
- 검증 범위 — JDK 컴파일러 사용 가능 여부와 Java 컴파일·테스트 실행·JAR 패키징이다. 서버 소켓과 서블릿 동작은 1단계 이후 검증한다.
- 다음 작업 — util.net.Endpoint의 연결 수락·고정 응답·종료.

### 2026-11-03 · 1단계 완료

- 구현한 것 — origin/main을 fast-forward 방식으로 동기화했다(이미 최신, 6907b98). codex/step-1-socket-endpoint 브랜치에서 util.net.Endpoint와 실제 TCP 소켓 테스트 5개를 추가했다. 외부 라이브러리 추가 없이 블로킹 소켓으로 순차 처리한다.
- 학습한 개념 — 생성자에서 ServerSocket을 바인딩하고 getPort()로 실제 포트를 확인한다. serve() → accept() → readHeaders() → writeResponse()가 요청 한 건의 흐름이다. CRLF 두 번까지 헤더를 읽고, UTF-8 본문의 바이트 수로 Content-Length를 정한다. 응답을 flush한 뒤 try-with-resources로 연결을 닫는다.
- 종료 — close()가 리스닝 소켓과 현재 연결을 닫는다. accept()나 헤더 읽기로 대기하던 serve()가 반환한다. main()은 기본 포트 8080(인자로 변경 가능)과 종료 훅을 제공한다. 종료한 Endpoint를 다시 쓰려면 새 인스턴스를 만든다.
- 테스트 우선 검증 — EndpointTest를 먼저 작성하고 Endpoint가 없어 testCompile이 실패하는 것을 확인한 뒤 구현했다. 테스트는 연속 두 연결, 나뉘어 도착한 헤더 끝, 헤더 완료 전 EOF 후 다음 요청, accept 대기 중 반복 종료·포트 재바인딩, 헤더 읽기 중 종료를 확인한다.
- 빌드 명령 — Windows PowerShell 세션 PATH 보정 후 `.\mvnw.cmd -B clean verify`.
- 빌드 결과 — BUILD SUCCESS. BuildEnvironmentTest 1개와 EndpointTest 5개, 합계 6개 통과(실패 0·오류 0·건너뜀 0). target/simple-tomcat-0.1.0-SNAPSHOT.jar 생성.
- 수동 실행 명령 — `java -cp target/classes simpletomcat.util.net.Endpoint`로 한 번 시작하고 다른 터미널에서 `curl.exe --noproxy '*' --max-time 5 -v http://localhost:8080/hello`를 두 번 실행했다.
- 관찰 결과 — 같은 프로세스의 두 요청 모두 HTTP/1.1 200 OK, Content-Type: text/plain; charset=UTF-8, Content-Length: 22, Connection: close와 본문 `Hello, simple-tomcat!`(마지막 LF 포함)을 받았다. 각 curl 종료 코드는 0이고 연결 종료를 확인했다. 확인 후 서버 프로세스를 종료했다.
- 현재 범위 — 헤더 종료 구분만 읽고 메서드·경로를 해석하지 않아 모든 완성된 헤더에 같은 응답을 보낸다. 본문·keep-alive·동시성·읽기 타임아웃은 지원하지 않는다. 느린 연결은 다음 연결 처리를 지연시킨다. HTTP 해석은 2단계, 작업 풀과 타임아웃은 8단계에서 추가한다.
- 남은 질문 또는 실패한 실험 — 1단계 완료 기준에 미해결 항목은 없다.
- 다음 작업 — 2단계 Coyote HTTP 처리와 테스트용 Adapter 왕복.

## 2026-11-03 · 원격 저장

- 푸시 전 `.\mvnw.cmd -B verify`를 실행해 전체 테스트 6개 통과, 실패·오류·건너뜀 0과 BUILD SUCCESS를 확인했다.
- 1단계 구현과 문서를 `8ff6b6c` (`feat: implement stage 1 blocking socket endpoint`)로 커밋했다.
- `codex/step-1-socket-endpoint`를 origin의 같은 브랜치에 푸시했다. 당시 작업 폴더는 깨끗했다.

## 2026-11-04 · 문서 재구성

- simple-jorm의 문서 구성을 참고해 목차, overview, 단계별 학습, reference, history로 분리했다.
- 1단계의 메서드 흐름·테스트·제약을 코드에 연결하고, 현재 존재하는 소스와 향후 패키지 계획을 구분했다.
- 기존 `doc/progress.md`는 새 문서 위치 안내로 남겼다. 초기 구현 계획에도 현재 문서 링크를 추가했다.
- 검증 결과 — Markdown 13개 문서의 상대 링크 93개, 코드 블록 닫힘과 줄 끝 공백을 확인했다. 기존 날짜별 기록의 원문 보존과 테스트 5개의 문서 연결도 확인했다.
- 검증 범위 — Java 코드·테스트·빌드 설정·구조도에 변경이 없음을 확인했다. 문서 변경만 수행했으므로 서버·Java 테스트는 재실행하지 않았다.

[학습 문서 목차](../README.md) · [1단계 테스트와 다음 작업](../01-socket/02-tests-and-next-steps.md)
