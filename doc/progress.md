# simple-tomcat 학습 진행 기록

최종 갱신일은 2026-11-03이다. 현재 단계는 0단계 빌드·테스트 환경 구성 완료, 1단계 소켓 서버 구현 시작 전이다.

## 프로젝트 목적

Tomcat의 구조를 따라 작은 서블릿 컨테이너를 Java로 직접 구현한다. HTTP 요청이 소켓에 도착해 서블릿을 실행하고 응답으로 돌아오는 과정을 코드로 설명할 수 있는 것이 목표다. 완성한 서버는 별도 프로젝트인 simple-spring에서 내장 서버 라이브러리로 사용한다. simple-spring의 디스패처와 IoC·DI도 직접 구현한다.

AI가 단계별 실행 가능한 코드를 작성하고, 사용자는 코드를 읽고 실행·수정하면서 학습한다. 각 단계에서 구조도의 화살표가 어떤 메서드 호출에 해당하는지 확인한다. 완료 여부는 실행 결과를 근거로 갱신한다.

## 구현 영역과 프로젝트 경계

| 영역 | 직접 구현할 책임 |
| --- | --- |
| Coyote 역할 | Endpoint의 연결 수락·입출력, HTTP Processor의 요청 파싱·응답 작성, ProtocolHandler의 프로토콜 처리 연결 |
| Catalina 역할 | Server, Service, Connector, CoyoteAdapter, Mapper, Engine → Host → Context → Wrapper, Pipeline·Valve, 서블릿 생명주기와 필터 체인 |
| Jasper 역할 | 학습용 JSP를 서블릿 소스로 변환·컴파일하고 실행하는 과정 |
| simple-spring | 디스패처, HTTP 메서드·경로별 컨트롤러 매핑, IoC·DI, 애플리케이션 객체 호출 |

Catalina와 Coyote는 여러 구성 요소로 이루어진 하위 시스템이다. Service는 Connector들과 Engine 하나를 묶는다. Connector는 Coyote의 ProtocolHandler를 사용한다. Connector가 생성한 CoyoteAdapter는 Service의 Mapper에서 매핑 결과를 받은 뒤 Engine을 호출한다. Engine 안에 Host, Host 안에 Context, Context 안에 Wrapper가 있다.

Tomcat의 Mapper는 대상 서블릿을 선택하고, simple-spring의 디스패처는 대상 컨트롤러 메서드를 선택한다. 두 프로젝트의 호출 경계는 학습용 Servlet 인터페이스의 service(request, response)다. 인터페이스와 요청·응답 타입은 simple-tomcat이 제공하고 simple-spring 디스패처가 구현한다.

JSP 요청은 Mapper가 선택한 JspServlet용 Wrapper의 필터 체인을 거친다. 일반 주문 요청은 DispatcherServlet용 Wrapper를 거친다. JSP와 EL의 지원 구문은 Jasper 구현을 시작할 때 구체화한다.

Jasper EL, WebSocket, JULI, Catalina HA·Tribes는 구조도에 역할을 표시했다. 이들의 직접 구현 범위와 시점은 추후 결정한다. 초기 단계는 호스트 하나와 웹 앱 하나, 본문 없는 GET, 연결당 요청 한 건으로 시작하고 단계적으로 확장한다. JDK 21, Maven Wrapper 3.9.9, JUnit Jupiter 5.11.4를 사용한다.

## 확정한 폴더·패키지 구조

단일 JAR 라이브러리로 구성한다. 기본 패키지는 simpletomcat이다. 아래 구조가 현재 배치 기준이며 구현 단계에 맞춰 클래스와 패키지를 추가한다.

```text
simple-tomcat/
├─ pom.xml
├─ mvnw / mvnw.cmd
├─ .mvn/wrapper/maven-wrapper.properties
├─ README.md
├─ tomcat-architecture.html
├─ doc/progress.md
└─ src/
   ├─ main/
   │  ├─ java/simpletomcat/
   │  │  ├─ servlet/              # 학습용 Servlet·요청/응답·Filter 계약
   │  │  ├─ util/net/             # Endpoint·소켓 입출력
   │  │  ├─ coyote/
   │  │  │  ├─ ProtocolHandler.java
   │  │  │  ├─ Adapter.java
   │  │  │  ├─ Request.java
   │  │  │  ├─ Response.java
   │  │  │  └─ http11/            # Http11Protocol·Http11Processor
   │  │  ├─ catalina/
   │  │  │  ├─ Server·Service·Container·Engine·Host·Context·Wrapper 인터페이스
   │  │  │  ├─ Lifecycle·Pipeline·Valve 인터페이스
   │  │  │  ├─ core/              # Standard... 구현체·StandardPipeline
   │  │  │  │                     # 컨테이너별 기본 Valve·ApplicationFilterChain
   │  │  │  ├─ connector/         # Connector·CoyoteAdapter·서블릿 요청/응답 구현
   │  │  │  ├─ mapper/            # Mapper·MappingData
   │  │  │  ├─ valves/            # ValveBase·접근 로그 등 공통 Valve
   │  │  │  └─ startup/           # SimpleTomcat 조립·시작·종료
   │  │  └─ jasper/
   │  │     ├─ servlet/           # JspServlet
   │  │     ├─ compiler/          # JSP 변환·Java 컴파일
   │  │     └─ runtime/           # 생성된 JSP 서블릿 실행 지원
   │  └─ resources/
   └─ test/
      ├─ java/simpletomcat/       # main과 같은 패키지, HTTP 통합 검증은 integration/
      └─ resources/webapp/        # JSP 등 테스트 리소스
```

Catalina 루트에는 계약을, core에는 StandardEngine 등 표준 구현체와 기본 Valve, 필터 체인 구현을 둔다. 공통 Valve는 valves에 둔다. 소켓 처리는 util/net에 배치하고 내장 서버 진입점은 catalina/startup/SimpleTomcat으로 한다.

servlet은 simple-spring과 공유하는 학습용 호출 계약이다. Coyote의 Request·Response는 프로토콜 처리 데이터이며 Catalina의 connector/Request·Response가 서블릿 계약을 구현해 연결한다. Coyote는 Adapter 계약을 호출하고 Catalina의 CoyoteAdapter가 구현한다. simple-spring의 디스패처와 컨트롤러는 별도 저장소에서 이 계약을 사용한다.

Http11Protocol은 첫 단계의 블로킹 소켓 구현을 조립하는 학습용 이름이다. 실제 Tomcat의 Http11NioProtocol·NioEndpoint와 대응 관계를 설명하면서 이후 NIO를 확장할 수 있다. 직접 만든 servlet 패키지는 Jakarta Servlet 표준 전체와의 호환을 뜻하지 않는다.

현재 소스는 기본 패키지 설명과 BuildEnvironmentTest만 있다. 나머지는 구현할 때 추가한다. 빌드 산출물과 Jasper의 생성 소스·class 파일은 target 아래에 둔다.

## 빌드·테스트 실행

- JDK 21을 사용한다. 현재 확인한 설치는 Zulu 21.0.11이다.
- Maven Wrapper가 Maven 3.9.9를 내려받아 사용한다. 별도 Maven 설치는 필요 없다.
- JUnit Jupiter 5.11.4와 Surefire 3.5.2로 테스트한다.
- Maven 좌표는 io.github.whoisyourbias:simple-tomcat:0.1.0-SNAPSHOT이다.
- IntelliJ에서는 pom.xml을 Maven 프로젝트로 열고 SDK를 JDK 21로 지정한다.

Windows PowerShell에서 다음 명령을 실행한다.

```powershell
.\mvnw.cmd -B clean verify
```

JAVA_HOME은 JDK 설치 경로여야 한다. 현재 에이전트 환경은 JAVA_HOME은 있지만 PATH에서 java와 Windows powershell을 찾지 못했다. 해당 환경에서는 현재 PowerShell 세션에만 다음 보정을 적용한 뒤 실행했다.

```powershell
$env:PATH = "$env:JAVA_HOME/bin;$env:SystemRoot/System32;$env:SystemRoot/System32/WindowsPowerShell/v1.0;$env:PATH"
.\mvnw.cmd -B clean verify
```

macOS·Linux에서는 JDK 21 설정 후 sh ./mvnw -B clean verify로 실행한다. 이 환경의 직접 실행 검증은 Windows에서 수행했다.

## 현재 진행 상황

| 작업 | 상태 | 확인한 결과 |
| --- | --- | --- |
| 프로젝트 목적 및 simple-spring과의 경계 정리 | 완료 | HTTP 처리와 서블릿 실행, 컨트롤러 실행의 책임을 구분했다. |
| 폴더·패키지 구조 확정 | 완료 | 단일 라이브러리, simpletomcat 기본 패키지, servlet·util/net·coyote·catalina·jasper로 구분했다. Catalina 계약과 표준 구현을 분리했다. |
| 학습 가이드 작성 | 완료 | 루트 README에 학습 순서와 실험을 정리했다. |
| Tomcat 구조도 및 요청·응답 애니메이션 | 완료 | 한 그림에서 포함·호출·매핑 관계를 표시하고 일반 요청과 JSP 요청을 전환한다. |
| 아키텍처 독립 리뷰 및 수정 | 완료 | Mapper 조회와 왕복 경로 분리, Wrapper 선택선, 단계 위치, service() 설명, EL 예시를 수정했다. |
| 브라우저 동작 검증 | 완료 | Edge에서 일반·JSP 전체 재생, 단계 이동, 초기화, 종류 전환과 말풍선을 확인했다. |
| 0단계 빌드·테스트 환경 | 완료 | JDK 21·Maven Wrapper·JUnit 설정. clean verify 성공, 테스트 1개 통과 및 JAR 생성 확인. |
| 실제 HTTP 서버 및 simple-spring 연동 | 예정 | HTTP 통신과 연동 실행 결과는 구현 후 기록한다. |

## 구현 순서와 완료 기준

| 단계 | 상태 | 학습·구현할 것 | 완료 기준 |
| --- | --- | --- | --- |
| 0. 실행 환경 | 완료 | JDK·Maven Wrapper·JUnit 및 기본 패키지 | clean verify 성공, 테스트 1개 통과, JAR 생성. |
| 1. 소켓 연결 | 예정 | util.net.Endpoint에서 연결 수락·고정 HTTP 응답·종료 | 같은 서버에 두 번 요청해 모두 응답받는다. |
| 2. Coyote HTTP 처리 | 예정 | Request·Response·Http11Processor·ProtocolHandler·Adapter 계약 | 테스트용 Adapter까지 HTTP 왕복. 잘못된 요청 400, 한글 본문 바이트 길이 확인. |
| 3. 서블릿과 Wrapper | 예정 | Servlet 계약·StandardWrapper·서블릿 생명주기 | 소켓 없이 두 번 호출 후 종료. init/service/destroy 횟수 1/2/1. |
| 4. Catalina 컨테이너 | 예정 | Engine → Host → Context → Wrapper·Pipeline·Valve | 선택 대상을 지정해 컨테이너 계층을 통한 서블릿 호출 순서 확인. |
| 5. Mapper | 예정 | Mapper·MappingData·대상 선택 | 정확한 경로·최장 경로 접두어·확장자·기본 매핑 순서 확인. 대상 선택과 호출 분리. |
| 6. 전체 연결과 생명주기 | 예정 | Connector·CoyoteAdapter·요청/응답 구현·StandardServer·StandardService·SimpleTomcat | 실제 /hello 서블릿 왕복. 404·500 뒤 다음 요청 성공. 시작 실패 시 자원 정리. |
| 7. 필터 체인 | 예정 | Filter·FilterChain·ApplicationFilterChain·필터 등록 | A 전 → B 전 → 서블릿 → B 후 → A 후. 중간 응답 종료 시 서블릿 호출 0회. |
| 8. 동시성과 종료 | 예정 | 제한된 작업 풀·읽기 타임아웃·처리 중 연결 정리 | 느린 연결 중 다른 요청 성공. 요청 정리 뒤 서블릿 해제. |
| 9. simple-spring 연결 | 예정 | 기본 / 매핑에 직접 만든 디스패처 등록 | 주문 한 건 후 GET /orders/count에 200과 본문 1. |
| 10. 학습용 Jasper | 예정 | JSP 변환·컴파일·로딩·실행·재사용 | /hello.jsp HTML 반환, 컴파일 결과 재사용, JSP 수정 시 갱신. |

## 다음 작업

1. util.net.Endpoint 구현을 시작한다.
2. ServerSocket.accept()로 연결을 받아 HTTP 헤더의 끝까지 읽는다.
3. Connection: close와 올바른 Content-Length를 포함한 고정 응답을 쓴다.
4. 서버를 한 번 실행한 상태에서 curl.exe -v http://localhost:8080/hello를 두 번 실행한다.
5. 명령과 관찰 결과를 아래 기록에 남긴다.

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

### 기록 양식

새 작업 후 날짜별로 다음 항목을 추가한다. 상태는 예정·진행 중·완료로 관리하고, 완료에는 실행 증거를 남긴다.

- 구현한 것
- 학습한 개념과 해당 클래스·메서드
- 실행 명령과 관찰 결과
- 남은 질문 또는 실패한 실험
- 다음 작업

## 관련 자료

- [기존 학습 가이드](../README.md)
- [구조도와 요청·응답 애니메이션](../tomcat-architecture.html)
- [simple-spring의 참고용 MiniTomcat](../../simple-spring/src/main/java/minitomcat/MiniTomcat.java)

simple-spring의 기존 MiniTomcat은 동작 비교용 참고 구현이다. simple-tomcat에서 해당 단계를 완료했는지는 이 문서의 실행 기록으로 판단한다.
