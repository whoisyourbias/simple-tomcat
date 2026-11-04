# 02-01. Coyote HTTP 처리와 요청 한 건의 흐름

2026-11-04 구현. [현재 진행 상황](../00-overview/01-progress.md) · [테스트·실행 기록](02-tests-and-next-steps.md) · [Tomcat과의 대응·차이](03-tomcat-comparison.md)

## 책임과 호출 순서

```text
Http11Protocol.serve
  → Endpoint.serve: accept, currentSocket 등록
    → 주입한 Endpoint.Handler
      → Http11Processor.process
        → readRequest: CRLF CRLF까지 바이트 읽기, 행·헤더 문법 해석
        → prepareRequestProtocol: HTTP/1.0·1.1 확인
        → prepareRequest: Host·Content-Length와 이번 단계 본문 제한 확인
        → Adapter.service(Request, Response)
        → prepareResponse: 최종 상태 범위 확인
        → writeResponse: 상태·헤더·길이·본문 기록, flush
    → Endpoint가 연결 종료, 다음 accept
```

[Endpoint](../../src/main/java/simpletomcat/util/net/Endpoint.java)는 HTTP 타입에 의존하지 않는다. 네트워크 자원 소유권과 현재 연결 등록·종료의 잠금을 유지한다. [Http11Protocol](../../src/main/java/simpletomcat/coyote/http11/Http11Protocol.java)은 Processor와 Endpoint를 조립한다. [Http11Processor](../../src/main/java/simpletomcat/coyote/http11/Http11Processor.java)는 HTTP 처리 순서를 담당한다.

[Request](../../src/main/java/simpletomcat/coyote/Request.java)는 불변 데이터다. 메서드·raw URI·raw query·버전과 대소문자를 구분하지 않는 헤더 조회를 제공한다. query가 없으면 null이며 percent decoding은 수행하지 않는다. [Response](../../src/main/java/simpletomcat/coyote/Response.java)는 상태·ASCII 헤더·UTF-8 전체 본문을 보관한다. 기본 상태는 200, Content-Type은 미설정이다. [Adapter](../../src/main/java/simpletomcat/coyote/Adapter.java)는 상위 처리 호출 경계이며 현재 main과 테스트의 대역 구현만 있다. Catalina CoyoteAdapter는 아직 없다.

## 응답과 오류

- 유효한 본문 없는 메서드는 GET 여부와 관계없이 Adapter에 전달한다. HTTP/1.0에서는 Host를 생략할 수 있다. HTTP/1.1에서는 Host가 한 개 있어야 하며 빈 값은 허용한다.
- 엄격한 요청 행·헤더 문법, Host 중복·잘못된 authority, 중복·잘못된 Content-Length와 TE+CL은 400이다. 지원 외 버전은 505, 양수 Content-Length나 Transfer-Encoding 단독은 이번 프로젝트의 본문 미지원 정책으로 501이다.
- 헤더 끝 전 EOF는 응답 없이 닫는다. 완성 전에는 Adapter 호출·응답을 하지 않는다. 완성된 잘못된 입력에는 Processor가 오류 응답을 쓰고 테스트 Adapter는 호출하지 않는다. 이 내부 오류 호출 경로는 실제 Tomcat과 다르다.
- Adapter의 일반 Exception은 미전송 응답을 버리고 새 500으로 바꾼다. InterruptedIOException이나 실제 입력·출력 I/O 오류는 연결 종료로 전파한다. 전송 중 실패한 응답을 다시 쓰지 않는다.
- 상태 행은 Tomcat 10.1.34처럼 `HTTP/1.1 200 \r\n`이며 reason phrase가 없다. 테스트는 `200 OK` 문자열과 완전 일치를 요구하지 않는다.
- 본문 `안녕하세요\n`은 UTF-8 16바이트다. Content-Length는 글자 수가 아닌 byte 배열의 길이다. HEAD는 길이만 보내고 본문을 생략한다. 204·304는 본문과 길이 헤더를 생략하고 205는 본문 없이 길이 0이다.
- 연결당 요청 한 건만 처리하고 `Connection: close`로 닫는다. 새 Request·Response를 만들어 이전 요청 상태가 누출되지 않게 한다.

## 실행

```powershell
.\mvnw.cmd -B clean verify
java -cp target/classes simpletomcat.coyote.http11.Http11Protocol
curl.exe --noproxy '*' --max-time 5 -v http://localhost:8080/hello
```

main의 예제 Adapter는 Content-Type을 명시하고 `Hello, simple-tomcat!\n`을 반환한다. 포트 인자를 생략하면 8080이다. Ctrl+C 종료 훅이 Protocol.close를 호출한다. 닫은 인스턴스의 재시작은 지원하지 않는다. 실제 Servlet·Mapper·동시 처리·서버 읽기 타임아웃·요청 본문·keep-alive·NIO는 이번 단계 밖이다.
