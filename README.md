# simple-tomcat 학습 가이드

최종 목표는 `simple-spring`에서 주문 한 건을 저장한 뒤 `GET /orders/count`를 보내면, 직접 만든 서버를 거쳐 상태 200과 본문 `1`을 받는 것이다.

## 요청 한 건의 여정

브라우저 → TCP 소켓 → HTTP 커넥터 → 서블릿 컨테이너 → simple-spring 디스패처 → 주문 컨트롤러. 응답은 역순으로 돌아가고 커넥터가 바이트를 소켓에 쓴다. 커넥터는 HTTP를 해석하고, 컨테이너는 서블릿을 선택해 호출하고, 디스패처는 컨트롤러 메서드를 선택한다. [Tomcat 아키텍처](https://tomcat.apache.org/tomcat-10.1-doc/architecture/overview.html) · [Spring MVC DispatcherServlet](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html)

## 실행 환경

JDK 21과 Maven Wrapper 3.9.9를 사용한다. 테스트는 JUnit Jupiter 5.11.4로 실행한다. JDK가 설정돼 있으면 별도 Maven 설치 없이 빌드할 수 있다.

```powershell
.\mvnw.cmd -B clean verify
```

현재 1단계 소켓 연결까지 완료됐다. 테스트 6개와 JAR 패키징, 같은 서버에 대한 실제 HTTP 요청 두 번을 검증했다. Windows 에이전트 환경에서 필요한 세션 PATH 보정과 IntelliJ 설정은 [진행 문서](doc/progress.md)에 기록했다.

## 1단계 서버 실행

빌드 후 다음 명령으로 서버를 실행한다. 기본 포트는 8080이며 마지막 인자로 다른 포트를 지정할 수 있다.

```powershell
java -cp target/classes simpletomcat.util.net.Endpoint
```

다른 터미널에서 같은 서버에 두 번 요청한다.

```powershell
curl.exe --noproxy '*' -v http://localhost:8080/hello
curl.exe --noproxy '*' -v http://localhost:8080/hello
```

두 요청 모두 `200 OK`, `Content-Length: 22`, `Connection: close`와 본문 `Hello, simple-tomcat!`을 받는다. 본문 끝에는 줄바꿈 한 바이트가 있다. 서버 터미널에서 `Ctrl+C`로 종료한다.

`Endpoint.serve()`는 `ServerSocket.accept()`로 연결을 받아 `readHeaders()`에서 헤더 끝(`\r\n\r\n`)까지 읽는다. `writeResponse()`로 고정 응답을 쓴 뒤 해당 연결을 닫고 다음 연결을 받는다. `close()`는 리스닝 소켓과 처리 중인 소켓을 닫아 대기 중에도 종료할 수 있게 한다.

1단계는 본문 없는 GET과 연결당 요청 한 건을 순차 처리한다. 경로를 구분하지 않고 고정 응답을 보내며 HTTP 파싱·잘못된 요청 처리는 2단계, 작업 풀·읽기 타임아웃은 8단계에서 추가한다.

## 구현 순서

소켓 연결 → Coyote HTTP 처리 → 서블릿과 Wrapper → Catalina 컨테이너 → Mapper → 전체 연결과 생명주기 → 필터 체인 → 동시성과 종료 → simple-spring 연결 → 학습용 Jasper 순서로 구현한다.

확정한 패키지 배치, 단계별 완료 기준과 실행 기록은 [doc/progress.md](doc/progress.md)를 기준으로 갱신한다.

## 기존 프로젝트를 활용하는 법

이웃 저장소의 `minitomcat.MiniTomcat`에는 이미 단일 스레드 GET 처리, 정확한 경로 매핑, 서블릿 생명주기가 있다. 소켓·HTTP·서블릿 생명주기 구현 후 기존 코드와 동작을 비교한다. 9단계에서 독립 라이브러리로 연결할 때 기존의 `addServlet`, `start`, `close` 사용법을 기준으로 삼는다. 기본 서블릿 매핑은 두 프로젝트를 연결하기 위해 추가할 기능이다.

첫 버전은 학습용 서블릿 인터페이스를 사용한다. 전체 Jakarta Servlet 호환, 다중 웹 애플리케이션 배포, keep-alive, NIO는 기본 연동을 확인한 뒤 각각 별도 실험으로 다룬다.
