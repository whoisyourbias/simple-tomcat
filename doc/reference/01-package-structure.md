# 패키지 배치와 프로젝트 경계

기준일: 2026-11-04. 단일 JAR 라이브러리이며 기본 패키지는 `simpletomcat`이다.

## 현재 소스

```text
src/main/java/simpletomcat/
├─ package-info.java
├─ util/net/Endpoint.java
└─ coyote/Request.java · Response.java · Adapter.java · ProtocolHandler.java
   └─ http11/Http11Processor.java · Http11Protocol.java
src/test/java/simpletomcat/
├─ BuildEnvironmentTest.java
├─ util/net/EndpointTest.java
├─ coyote/RequestTest.java · ResponseTest.java
│  └─ http11/Http11ProcessorTest.java
└─ integration/Http11ProtocolTest.java
```

[Endpoint](../../src/main/java/simpletomcat/util/net/Endpoint.java)는 현재 연결 수락·Handler 위임·종료를 담당한다. HTTP 해석과 응답 작성은 Coyote Http11Processor가 담당한다. [EndpointTest](../../src/test/java/simpletomcat/util/net/EndpointTest.java)는 main과 같은 패키지에 둔다.

## 전체 목표 배치

아래는 확정한 배치 기준이며, 현재 존재하는 파일 목록이 아니다. 구현 단계에 맞춰 타입과 폴더를 추가한다.

```text
src/main/java/simpletomcat/
├─ servlet/              # 학습용 Servlet·요청/응답·Filter 계약
├─ util/net/             # Endpoint·소켓 입출력
├─ coyote/
│  ├─ ProtocolHandler.java
│  ├─ Adapter.java
│  ├─ Request.java
│  ├─ Response.java
│  └─ http11/            # Http11Protocol·Http11Processor
├─ catalina/
│  ├─ Server·Service·Container·Engine·Host·Context·Wrapper 인터페이스
│  ├─ Lifecycle·Pipeline·Valve 인터페이스
│  ├─ core/              # Standard... 구현체·StandardPipeline
│  │                     # 컨테이너별 기본 Valve·ApplicationFilterChain
│  ├─ connector/         # Connector·CoyoteAdapter·서블릿 요청/응답 구현
│  ├─ mapper/            # Mapper·MappingData
│  ├─ valves/            # ValveBase·접근 로그 등 공통 Valve
│  └─ startup/           # SimpleTomcat 조립·시작·종료
└─ jasper/
   ├─ servlet/           # JspServlet
   ├─ compiler/          # JSP 변환·Java 컴파일
   └─ runtime/           # 생성된 JSP 서블릿 실행 지원
```

## 배치 원칙

- Catalina 루트에는 계약, `core`에는 StandardEngine 등 표준 구현과 기본 Valve·필터 체인을 둔다. 공통 Valve는 `valves`에 둔다.
- 소켓 처리는 `util/net`, 내장 서버 진입점은 `catalina/startup/SimpleTomcat`으로 한다. Endpoint.main은 2단계에서 제거했고 현재 수동 실행 진입점은 coyote.http11.Http11Protocol.main이다.
- `servlet`은 simple-spring과 공유할 학습용 호출 계약이다. simple-spring의 디스패처와 컨트롤러 구현은 별도 저장소에 둔다.
- Coyote Request·Response는 프로토콜 데이터이며 Catalina의 connector Request·Response가 서블릿 계약을 구현해 연결한다. Coyote Adapter 계약은 Catalina CoyoteAdapter가 구현한다.
- Http11Protocol은 블로킹 소켓을 조립하는 학습용 이름이다. 이후 실제 Tomcat의 Http11NioProtocol·NioEndpoint와 대응하며 NIO로 확장할 수 있다.
- 테스트는 main과 같은 패키지를 사용하고 HTTP 통합 검증은 `src/test/java/simpletomcat/integration/`에 추가한다. JSP 리소스는 `src/test/resources/webapp/`에 둔다.
- 빌드 산출물과 Jasper가 생성할 소스·class 파일은 `target/` 아래에 둔다.

[현재 진행 상황](../00-overview/01-progress.md) · [전체 구조와 경계](../00-overview/02-architecture.md) · [학습 문서 목차](../README.md)
