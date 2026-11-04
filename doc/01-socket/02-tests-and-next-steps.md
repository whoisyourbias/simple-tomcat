> 이 문서는 1단계 당시 구조·실행 기록을 보존한다. 2단계에서 HTTP 처리는 Processor로, main은 Http11Protocol로 이동했다. 현재 실행 방법은 [2단계 문서](../02-http/01-coyote-and-request-flow.md)를 따른다.

# 01-02. 소켓 테스트, 실행 증거와 다음 작업

## 자동 테스트

[EndpointTest](../../src/test/java/simpletomcat/util/net/EndpointTest.java)는 실제 TCP 소켓을 사용한다. 각 테스트가 포트 0으로 Endpoint를 만들고 별도 테스트 스레드에서 `serve()`를 실행한다. 테스트 종료 시 서버를 닫고 Future 완료를 기다린 뒤 실행기를 정리한다.

| 테스트 | 검증 내용 |
|---|---|
| `respondsToTwoConnectionsOnTheSameServer` | 같은 Endpoint의 연속 두 연결에 200·헤더·본문 응답, 각 연결 종료 |
| `waitsForTheEndOfHeadersAcrossSocketWrites` | 마지막 LF 전에는 응답하지 않고, 나뉜 헤더 끝이 완성되면 응답 |
| `closesIncompleteRequestsWithoutStoppingTheServer` | 헤더 완료 전 EOF에는 빈 응답으로 닫고, 다음 요청 정상 처리 |
| `closeUnblocksAcceptAndReleasesThePort` | accept 대기 중 종료, 반복 종료, 같은 포트 재바인딩 |
| `closeUnblocksAnIncompleteRequest` | 헤더 읽기 중 종료해도 serve의 Future가 완료 |

클라이언트의 읽기 타임아웃은 테스트가 무한 대기하지 않게 하는 장치다. 서버의 읽기 타임아웃을 구현한 것은 아니다. `readAllBytes()`가 반환하는 것으로 응답 후 EOF도 확인한다.

프로젝트 루트에서 실행한다.

```powershell
.\mvnw.cmd -B "-Dtest=simpletomcat.util.net.EndpointTest" test
.\mvnw.cmd -B clean verify
```

## 수동 실행

빌드 후 서버 터미널에서 실행한다.

```powershell
java -cp target/classes simpletomcat.util.net.Endpoint
# 다른 포트가 필요하면 마지막 인자를 추가한다.
# java -cp target/classes simpletomcat.util.net.Endpoint 8081
```

다른 터미널에서 같은 서버에 두 번 요청한다.

```powershell
curl.exe --noproxy '*' --max-time 5 -v http://localhost:8080/hello
curl.exe --noproxy '*' --max-time 5 -v http://localhost:8080/hello
```

두 요청 모두 `HTTP/1.1 200 OK`, `Content-Length: 22`, `Connection: close`와 `Hello, simple-tomcat!` 본문을 기대한다. 마지막 LF도 본문에 포함된다. 서버 터미널의 `Ctrl+C`는 종료 훅을 통해 Endpoint를 닫는다.

## 기존 검증 기록

실행일은 **2026-11-03**이다. 2026-11-04 문서 재구성에서는 서버와 Java 테스트를 재실행하지 않았다.

- EndpointTest를 먼저 작성하고 미구현 Endpoint 때문에 testCompile이 실패하는 것을 확인한 뒤 구현했다.
- `.\mvnw.cmd -B clean verify`: BUILD SUCCESS, BuildEnvironmentTest 1개와 EndpointTest 5개로 전체 6개 통과. 실패·오류·건너뜀 0, JAR 생성.
- 서버 한 프로세스에 위 curl 명령을 두 번 실행했다. 두 요청 모두 200·22바이트 본문·연결 종료, curl 종료 코드 0을 확인했다. 확인 후 서버 프로세스를 종료했다.
- 푸시 전 `.\mvnw.cmd -B verify`도 전체 6개 통과했다. 구현 커밋은 `8ff6b6c`다.

당시 기록은 [초기 구현·검증 이력](../history/01-initial-implementation.md)에 보존한다. 현재 테스트 보고서의 파일 존재 여부를 새 실행 증거로 해석하지 않는다.

## 현재 한계와 다음 작업

헤더 종료만 읽으므로 HTTP 메서드·경로·헤더 값을 사용할 수 없고, 잘못된 요청에 400을 보내지도 않는다. 본문 처리·keep-alive·동시성·읽기 타임아웃은 없다.

2단계에서 Request·Response·Adapter·ProtocolHandler 계약을 만들고 HTTP 파싱·응답 작성을 Http11Processor로 분리한다. 테스트용 Adapter의 왕복, 잘못된 요청의 400, 한글 본문의 UTF-8 바이트 길이를 검증한다. 작업 풀과 타임아웃은 8단계로 남긴다.

[학습 문서 목차](../README.md) · [전체 진행 상황](../00-overview/01-progress.md)
