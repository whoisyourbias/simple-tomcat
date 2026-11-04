# 구현 단계와 실제 Tomcat 역할의 대응

기준일: 2026-11-04. 같은 역할을 학습한다는 뜻이며 실제 Tomcat의 전체 기능이나 Jakarta Servlet 표준 호환성을 구현했다는 뜻은 아니다.

## 단계별 대응표

| 학습 단계 | 직접 구현할 코드 | 대응 역할 | 현재 상태 |
|---|---|---|---|
| 0 실행 환경 | Maven 설정·BuildEnvironmentTest | Java 컴파일·테스트·라이브러리 패키징 | 완료 |
| 1 소켓 연결 | util.net.Endpoint | 연결 수락·소켓 입출력 | 블로킹 순차 처리·고정 응답 완료 |
| 2 HTTP 처리 | Coyote Request·Response·Http11Processor·ProtocolHandler·Adapter | 프로토콜 처리와 컨테이너 호출 경계 | 예정 |
| 3 서블릿과 Wrapper | Servlet 계약·StandardWrapper | 서블릿 init/service/destroy 호출 | 예정 |
| 4 컨테이너 계층 | Engine·Host·Context·Wrapper·Pipeline·Valve | 컨테이너 계층과 요청 호출 경로 | 예정 |
| 5 Mapper | Mapper·MappingData | 대상 서블릿 선택·매핑 결과 저장 | 예정 |
| 6 전체 연결 | Connector·CoyoteAdapter·StandardServer·StandardService·SimpleTomcat | Coyote와 Catalina 연결·시작·종료 | 예정 |
| 7 필터 체인 | Filter·FilterChain·ApplicationFilterChain | 서블릿 전후 처리·중간 응답 종료 | 예정 |
| 8 동시성과 종료 | 제한된 작업 풀·읽기 타임아웃 | 여러 연결 처리·요청 정리 후 종료 | 예정 |
| 9 simple-spring | 기본 매핑의 디스패처 서블릿 | 서버의 서블릿 호출에서 애플리케이션 컨트롤러 호출로 연결 | 예정 |
| 10 Jasper | JspServlet·compiler·runtime | JSP를 서블릿으로 변환·컴파일·실행 | 예정 |

## 현재 Endpoint와 이후 분리

현재 Endpoint 한 클래스에 연결 수락·헤더 끝 읽기·고정 응답이 함께 있다. 2단계에서 HTTP 해석과 응답 작성을 Processor로 옮기고 ProtocolHandler로 조립한다. Http11Protocol은 이 학습 프로젝트에서 블로킹 구현을 조립하기 위해 사용할 이름이다. 실제 Tomcat의 Http11NioProtocol·NioEndpoint처럼 NIO를 구현한 상태는 아니다.

## Mapper와 디스패처의 차이

Tomcat 역할의 Mapper는 요청을 처리할 서블릿 Wrapper를 선택한다. simple-spring 디스패처는 선택된 서블릿 안에서 업무 컨트롤러 메서드를 선택한다. 매핑 대상 선택과 컨테이너 호출을 구분해 구현한다.

## 서블릿과 Jasper의 경계

학습용 servlet 패키지는 simple-spring과 공유할 자체 계약이다. Jakarta Servlet의 모든 API나 배포 규칙을 구현하지 않는다. Jasper는 JSP를 서블릿 소스로 변환·컴파일하고 실행하는 단계이며, EL 지원 구문은 구현을 시작할 때 정한다.

## 관련 공식 자료와 저장소 문서

- [Tomcat 아키텍처](https://tomcat.apache.org/tomcat-10.1-doc/architecture/overview.html)
- [Tomcat 요청 처리 흐름](https://tomcat.apache.org/tomcat-10.1-doc/architecture/requestProcess.html)
- [Jasper 안내](https://tomcat.apache.org/tomcat-10.1-doc/jasper-howto.html)
- [Spring MVC DispatcherServlet](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html)
- [전체 목표와 경계](../00-overview/02-architecture.md) · [예정 패키지 배치](01-package-structure.md)

[학습 문서 목차](../README.md) · [현재 진행 상황](../00-overview/01-progress.md)
