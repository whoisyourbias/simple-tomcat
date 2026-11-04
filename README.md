# simple-tomcat

Tomcat의 구조를 따라 소켓 연결, HTTP 처리, 서블릿 컨테이너와 JSP 실행을 직접 구현하는 Java 학습 라이브러리다. 최종 목표는 별도 `simple-spring` 프로젝트에서 주문 한 건을 저장한 뒤 `GET /orders/count`에 200과 본문 `1`을 받는 것이다.

2026-11-04 기준 **2단계 Coyote HTTP 처리까지 완료**했다. 다음은 3단계 Servlet·StandardWrapper다.

## 먼저 읽을 것

1. [작업 지침](AGENTS.md)과 [현재 진행 상황](doc/00-overview/01-progress.md)을 읽는다.
2. [HTTP 요청 처리 흐름](doc/02-http/01-coyote-and-request-flow.md)과 수정할 코드·테스트를 함께 읽는다.
3. [Tomcat 구현 대응·차이](doc/02-http/03-tomcat-comparison.md), [구현 상세·제약](doc/reference/02-implementation-status.md)을 확인한다.
4. [학습 문서 목차](doc/README.md)에서 단계별 설명을 찾는다.

## 현재 지원 범위

Endpoint는 소켓 수락·종료를, Http11Protocol은 조립을, Http11Processor는 HTTP 파싱·요청 준비·Adapter 호출·응답 전송을 담당한다. Request·Response·Adapter·ProtocolHandler 계약을 제공한다. 실제 Catalina·서블릿은 아직 없다.

- HTTP/1.0·1.1, 본문 없는 요청, 연결당 요청 한 건·순차 처리.
- 유효 메서드를 Adapter에 전달하며 GET만으로 제한하지 않는다. main은 예제 Adapter로 고정 본문을 반환한다.
- 잘못된 요청 400, 일반 Adapter 예외 500, 프로젝트의 본문 미지원 501, 지원 외 버전 505.
- UTF-8 바이트 길이, HEAD·204·205·304의 본문 처리, 오류 후 다음 연결 처리.
- accept·요청 읽기 중 close, 반복 종료·포트 반환.

요청 본문·keep-alive·작업 풀·서버 읽기 타임아웃·헤더 크기 제한·NIO는 없다. Tomcat 10.1.34의 핵심 책임과 흐름을 기준으로 구현했으며, 단순화·생략 12개는 비교 문서에 기록했다.

## 환경과 실행

JDK 21, Maven Wrapper 3.9.9, JUnit Jupiter 5.11.4·Surefire 3.5.2를 사용한다. [빌드 환경](doc/00-overview/03-build-environment.md)을 참고한다.

```powershell
.\mvnw.cmd -B clean verify
java -cp target/classes simpletomcat.coyote.http11.Http11Protocol
curl.exe --noproxy '*' --max-time 5 -v http://localhost:8080/hello
curl.exe --noproxy '*' --max-time 5 -v 'http://localhost:8080/hello?name=a'
```

서버와 curl은 다른 터미널에서 실행한다. 기본 포트는 8080이며 main의 마지막 인자로 변경한다. Ctrl+C로 종료한다. 두 요청 모두 상태 200, Content-Length 22, Connection close와 `Hello, simple-tomcat!` 본문을 받는다. 본문 끝 LF 한 바이트를 포함한다. 상태 행은 Tomcat처럼 reason phrase를 생략하므로 `200 OK` 문자열과 완전 일치를 요구하지 않는다.

macOS·Linux에서는 `sh ./mvnw -B clean verify`를 사용한다. 2026-11-04 검증은 **76개 테스트 통과·JAR 생성·같은 서버에 curl 두 요청 성공**이다. [실행 증거](doc/02-http/02-tests-and-next-steps.md)를 참고한다. 과거 2026-11-03의 6개 테스트 기록은 [초기 기록](doc/history/01-initial-implementation.md)에 보존한다.

## 코드 구조

| 변경 대상 | 구현 | 검증 |
|---|---|---|
| 소켓 수락·Handler 위임·종료 | [Endpoint](src/main/java/simpletomcat/util/net/Endpoint.java) | [EndpointTest](src/test/java/simpletomcat/util/net/EndpointTest.java) |
| 요청·응답 데이터 | [Request](src/main/java/simpletomcat/coyote/Request.java), [Response](src/main/java/simpletomcat/coyote/Response.java) | RequestTest·ResponseTest |
| HTTP 파싱·Adapter 호출·응답 | [Http11Processor](src/main/java/simpletomcat/coyote/http11/Http11Processor.java) | [Http11ProcessorTest](src/test/java/simpletomcat/coyote/http11/Http11ProcessorTest.java) |
| 프로토콜 조립·실행 | [Http11Protocol](src/main/java/simpletomcat/coyote/http11/Http11Protocol.java) | [Http11ProtocolTest](src/test/java/simpletomcat/integration/Http11ProtocolTest.java) |

단일 JAR은 `target/simple-tomcat-0.1.0-SNAPSHOT.jar`, 테스트 보고서는 `target/surefire-reports/`에 생성된다. [패키지 배치](doc/reference/01-package-structure.md)와 [전체 목표 구조도](tomcat-architecture.html)는 이후 단계의 예정 타입도 구분해 설명한다. 프로젝트 문서는 `doc/`에 통합하고 작업 계획은 [doc/plans](doc/plans/)에 둔다. 기능 지원 범위 변경 시 진행 상황·학습 설명·구현 제약과 실제 실행 증거를 함께 갱신한다.
