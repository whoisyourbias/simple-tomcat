# 00-02. 전체 목표와 프로젝트 경계

Tomcat의 구조를 따라 작은 서블릿 컨테이너를 Java로 직접 구현한다. HTTP 요청이 소켓에 도착해 서블릿을 실행하고 응답으로 돌아오는 과정을 코드로 설명하는 것이 목표다. 최종적으로 `simple-spring`의 내장 서버 라이브러리로 사용한다.

아래 흐름과 구성은 전체 목표다. 현재는 Endpoint의 소켓 연결만 구현했다. 실제 지원 범위는 [구현 상세](../reference/02-implementation-status.md)를 기준으로 확인한다.

## 요청 한 건의 여정

```text
브라우저 → TCP 소켓 → HTTP 커넥터 → 서블릿 컨테이너
        → simple-spring 디스패처 → 주문 컨트롤러
```

응답은 역순으로 돌아가고 커넥터가 바이트를 소켓에 쓴다. HTTP 처리는 요청·응답을 해석하고, 컨테이너의 Mapper는 대상 서블릿을 선택하며, simple-spring 디스패처는 컨트롤러 메서드를 선택한다.

[구조도와 요청·응답 애니메이션](../../tomcat-architecture.html)에서 일반 요청과 JSP 요청을 전환하며 확인할 수 있다. 각 단계를 구현할 때 구조도의 화살표가 어떤 메서드 호출에 해당하는지 대조한다.

## 구현 영역

| 영역 | 직접 구현할 책임 |
|---|---|
| Coyote 역할 | Endpoint의 연결 수락·입출력, HTTP Processor의 요청 파싱·응답 작성, ProtocolHandler의 프로토콜 처리 연결 |
| Catalina 역할 | Server·Service·Connector·CoyoteAdapter·Mapper, Engine → Host → Context → Wrapper, Pipeline·Valve, 서블릿 생명주기·필터 체인 |
| Jasper 역할 | 학습용 JSP를 서블릿 소스로 변환·컴파일하고 실행 |
| simple-spring | 디스패처, HTTP 메서드·경로별 컨트롤러 매핑, IoC·DI, 애플리케이션 객체 호출 |

Catalina와 Coyote는 여러 구성 요소로 이루어진 하위 시스템이다. Service는 Connector들과 Engine 하나를 묶는다. Connector는 Coyote의 ProtocolHandler를 사용하고 CoyoteAdapter를 생성한다. Adapter는 Service의 Mapper에서 매핑 결과를 받은 뒤 Engine을 호출한다. Engine 안에 Host, Host 안에 Context, Context 안에 Wrapper를 둔다.

Coyote는 `Adapter` 계약을 호출하고 Catalina의 `CoyoteAdapter`가 이를 구현하도록 계획했다. Coyote Request·Response는 프로토콜 처리 데이터이며 Catalina의 connector Request·Response가 학습용 서블릿 계약을 구현해 연결한다.

## simple-spring과의 경계

공유 호출 계약은 simple-tomcat이 제공할 `Servlet.service(request, response)`다. simple-spring의 디스패처가 이 계약을 구현하고 컨트롤러를 호출한다. simple-spring의 IoC·DI와 주문 업무 코드는 별도 저장소에 둔다.

이웃 저장소의 `minitomcat.MiniTomcat`은 단일 스레드 GET 처리·정확한 경로 매핑·서블릿 생명주기를 비교할 참고 구현이다. 9단계 라이브러리 연결 시 기존 `addServlet`, `start`, `close` 사용법을 기준으로 삼는다. 두 프로젝트를 연결할 기본 서블릿 매핑은 추가할 기능이다.

## JSP와 확장 범위

JSP 요청은 Mapper가 선택한 JspServlet용 Wrapper의 필터 체인을, 일반 주문 요청은 DispatcherServlet용 Wrapper를 거치도록 계획했다. JSP와 EL의 지원 구문은 Jasper 구현을 시작할 때 정한다.

초기 범위는 호스트 하나·웹 앱 하나, 본문 없는 GET, 연결당 요청 한 건이다. 학습용 servlet 패키지가 Jakarta Servlet 전체 호환을 뜻하지 않는다. 다중 웹 애플리케이션 배포·keep-alive·NIO는 기본 연동 이후 별도 실험으로 다룬다. Jasper EL·WebSocket·JULI·Catalina HA·Tribes는 구조도에 역할을 표시했으며 직접 구현 범위와 시점은 미정이다.

## 관련 자료

- [Tomcat 아키텍처](https://tomcat.apache.org/tomcat-10.1-doc/architecture/overview.html)
- [Spring MVC DispatcherServlet](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html)
- [단계별 대응과 현재 상태](../reference/03-learning-stage-mapping.md)

[학습 문서 목차](../README.md) · [현재 진행 상황](01-progress.md)
