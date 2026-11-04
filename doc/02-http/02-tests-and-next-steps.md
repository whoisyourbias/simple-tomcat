# 02-02. 테스트·실행 증거와 다음 작업

실행일: **2026-11-04 (Asia/Seoul)**. [요청 흐름](01-coyote-and-request-flow.md) · [Tomcat 비교](03-tomcat-comparison.md)

## 자동 검증

| 테스트 | 검증 내용 | 실행 수 |
|---|---|---|
| [RequestTest](../../src/test/java/simpletomcat/coyote/RequestTest.java) | raw URI·query, 헤더 조회, 깊은 복사 | 3 |
| [ResponseTest](../../src/test/java/simpletomcat/coyote/ResponseTest.java) | 기본값·UTF-8·상태·헤더 검증·방어 복사 | 10 |
| [EndpointTest](../../src/test/java/simpletomcat/util/net/EndpointTest.java) | 두 연결 Handler 위임, IOException 복구, accept/read 종료·포트 반환 | 4 |
| [Http11ProcessorTest](../../src/test/java/simpletomcat/coyote/http11/Http11ProcessorTest.java) | 행·헤더·Host·길이 파싱, 400/500/501/505, HTTP/1.0·POST·HEAD, 상태별 본문, UTF-8, EOF·전송 오류·상태 격리 | 48 |
| [Http11ProtocolTest](../../src/test/java/simpletomcat/integration/Http11ProtocolTest.java) | 실제 TCP 왕복·분할 전송·불완전 EOF·오류 복구·종료·UTF-8 | 10 |
| BuildEnvironmentTest | JDK 컴파일러 확인 | 1 |

각 단계에서 테스트를 먼저 작성하고 새 클래스/생성자 부재로 컴파일 실패(RED)를 확인한 뒤 구현해 통과시켰다. Request·Response 13개, Endpoint 4개, Processor 48개, Protocol 10개의 개별 실행이 성공했다.

```powershell
.\mvnw.cmd -B clean verify
```

결과: **BUILD SUCCESS, 76개 실행, 실패·오류·건너뜀 0**. `target/simple-tomcat-0.1.0-SNAPSHOT.jar` 생성. Endpoint의 의도적인 IOException 복구 테스트에서는 경고 로그가 한 번 출력된다. 실패한 테스트가 아니라 주입한 연결 실패를 격리하는 경로의 로그다.

실행 호스트에서는 PATH에 Windows PowerShell이 없어 Wrapper의 첫 실행이 실패했다. 프로세스 PATH에 `C:\Windows\System32\WindowsPowerShell\v1.0`을 추가하고 재실행했다. 프로젝트 빌드 설정은 바꾸지 않았다. 보고서는 `target/surefire-reports/`다.

## 수동 실행 증거

JAVA_HOME의 `bin/java.exe`로 Http11Protocol을 포트 **18082**에서 실행하고 같은 프로세스에 아래 curl 요청을 보냈다. curl은 `C:\Windows\System32\curl.exe`를 사용했다.

```powershell
java -cp target/classes simpletomcat.coyote.http11.Http11Protocol 18082
curl.exe --noproxy '*' --max-time 5 -v http://localhost:18082/hello
curl.exe --noproxy '*' --max-time 5 -v 'http://localhost:18082/hello?name=a'
```

두 요청 모두 curl 종료 코드 0, `HTTP/1.1 200 `, Content-Type `text/plain; charset=UTF-8`, Content-Length 22, Connection close, 본문 `Hello, simple-tomcat!\n`을 확인했다. 확인 후 Ctrl+C로 서버를 종료했다. 종료·포트 반환의 자동 검증은 EndpointTest와 Http11ProtocolTest가 담당한다.

## 다음 작업

3단계는 소켓 없이 Servlet·StandardWrapper의 init/service/destroy 호출 횟수 1/2/1을 검증한다. 실제 Catalina CoyoteAdapter와 오류 페이지·Mapper 연결은 6단계, 작업 풀·읽기 타임아웃은 8단계다. D1~D12의 남은 차이와 의도적인 제한은 비교 문서를 기준으로 유지한다. 과거 2026-11-03의 6개 테스트 기록은 당시 증거로 보존한다.
