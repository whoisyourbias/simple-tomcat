# simple-tomcat 학습 진행 기록

최종 갱신일은 2026-11-03이다. 현재 단계는 설계와 시각화 완료, Java 서버 구현 시작 전이다.

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

Jasper EL, WebSocket, JULI, Catalina HA·Tribes는 구조도에 역할을 표시했다. 이들의 직접 구현 범위와 시점은 추후 결정한다. 초기 단계는 호스트 하나와 웹 앱 하나, 본문 없는 GET, 연결당 요청 한 건으로 시작하고 단계적으로 확장한다. JDK·빌드 도구는 첫 구현 전에 환경을 확인해 정한다.

## 현재 진행 상황

| 작업 | 상태 | 확인한 결과 |
| --- | --- | --- |
| 프로젝트 목적 및 simple-spring과의 경계 정리 | 완료 | HTTP 처리와 서블릿 실행, 컨트롤러 실행의 책임을 구분했다. |
| 학습 가이드 작성 | 완료 | 루트 README에 학습 순서와 실험을 정리했다. |
| Tomcat 구조도 및 요청·응답 애니메이션 | 완료 | 한 그림에서 포함·호출·매핑 관계를 표시하고 일반 요청과 JSP 요청을 전환한다. |
| 아키텍처 독립 리뷰 및 수정 | 완료 | Mapper 조회와 왕복 경로 분리, Wrapper 선택선, 단계 위치, service() 설명, EL 예시를 수정했다. |
| 브라우저 동작 검증 | 완료 | Edge에서 일반·JSP 전체 재생, 단계 이동, 초기화, 종류 전환과 말풍선을 확인했다. |
| Java 소스 및 빌드 설정 | 예정 | 현재 simple-tomcat에 Java 소스와 빌드 설정이 없다. |
| 실제 HTTP 서버 및 simple-spring 연동 | 예정 | HTTP 통신과 연동 실행 결과는 구현 후 기록한다. |

## 구현 순서와 완료 기준

| 단계 | 상태 | 학습·구현할 것 | 완료 기준 |
| --- | --- | --- | --- |
| 1. HTTP 왕복 | 예정 | ServerSocket 연결 수락부터 시작하고 Endpoint·HTTP Processor·ProtocolHandler 역할을 분리한다. | 같은 서버에 /hello를 두 번 요청해 모두 200과 고정 본문을 받는다. 잘못된 요청은 400이며 UTF-8 본문의 Content-Length가 실제 바이트 수와 일치한다. |
| 2. 서블릿 실행 | 예정 | Connector·CoyoteAdapter·Mapper를 연결하고 서블릿 등록·생명주기를 구현한다. | 같은 서블릿에 두 번 요청한 뒤 종료하면 init/service/destroy 횟수가 1/2/1이다. 미등록 경로는 404, 서블릿 예외는 500이고 다음 요청은 성공한다. |
| 3. 컨테이너 계층 | 예정 | Server·Service와 Engine → Host → Context → Wrapper, Pipeline·Valve·FilterChain을 연결한다. | 호출 기록을 구조도와 대조할 수 있다. 필터가 응답을 끝내면 서블릿 호출 횟수가 0이다. Mapper 조회는 Engine 호출 전에 반환된다. |
| 4. simple-spring 연결 | 예정 | 서버를 라이브러리로 사용하고 / 기본 매핑에 직접 만든 디스패처를 등록한다. | 주문 한 건 저장 후 실제 HTTP GET /orders/count에 200과 본문 1을 받는다. 새 컨트롤러 경로를 Tomcat에 개별 등록하지 않아도 디스패처가 처리한다. |
| 5. 동시성과 종료 | 예정 | 연결 수락과 처리를 분리하고 제한된 작업 풀·읽기 타임아웃·종료 처리를 구현한다. | 헤더 전송을 멈춘 클라이언트가 있어도 다른 요청이 완료된다. 종료 시 새 연결 수락을 멈추고 요청 처리를 정리한 뒤 서블릿을 해제한다. |
| 6. 학습용 Jasper | 예정 | JSP 소스 변환·컴파일·실행과 결과 재사용을 구현한다. | /hello.jsp 첫 요청에서 서블릿 생성·컴파일을 확인하고 HTML을 받는다. 다음 요청에서 컴파일 결과를 재사용한다. |

## 다음 작업

1. JDK와 실행 환경을 확인하고 빌드·실행 방법을 정한다.
2. ServerSocket.accept()로 연결을 받아 HTTP 헤더의 끝까지 읽는다.
3. Connection: close와 올바른 Content-Length를 포함한 고정 응답을 쓴다.
4. 서버를 한 번 실행한 상태에서 curl.exe -v http://localhost:8080/hello를 두 번 실행한다.
5. 명령과 관찰 결과를 아래 기록에 남긴다.

## 작업 기록

### 2026-11-03

- 수행한 작업 — 목적·아키텍처 정리, 시각화와 독립 리뷰, 학습 진행 문서 작성.
- 확인한 결과 — 브라우저 애니메이션 검증 결과가 있다. Java 서버 구현과 실제 HTTP 검증은 시작 전이다.
- 다음 작업 — 1단계 HTTP 왕복 구현.

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
