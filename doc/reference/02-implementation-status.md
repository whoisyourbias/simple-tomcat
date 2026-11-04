# 구현 상세와 현재 제약

기준일: 2026-11-04. 단계별 요약과 다음 작업은 [현재 진행 상황](../00-overview/01-progress.md)을 참고한다.

## 빌드 환경

- Java 21, Maven Wrapper 3.9.9, JUnit Jupiter 5.11.4·Surefire 3.5.2를 사용한다.
- BuildEnvironmentTest는 `ToolProvider.getSystemJavaCompiler()`가 null이 아닌지 확인한다. 학습용 Jasper의 향후 Java 컴파일에 필요한 전체 JDK 확인이며 JSP 기능 자체의 검증은 아니다.
- 설정·실행·보고서 위치는 [빌드 환경](../00-overview/03-build-environment.md)에 있다.

## Endpoint 연결 처리

- 생성자에서 ServerSocket을 바인딩한다. 포트 0은 운영체제가 배정한 포트를 사용하며 `getPort()`로 확인한다.
- `serve()`는 한 호출 스레드에서 연결을 순차 처리한다. Endpoint가 스레드나 작업 풀을 생성하지 않는다.
- `readHeaders()`는 입력에서 CRLF 두 번을 찾는다. TCP 읽기가 나뉘어도 구분자를 이어서 인식한다. 요청 행·헤더 값·메서드·경로의 해석이나 검증은 없다.
- 완성된 헤더에는 경로와 관계없이 200과 고정 본문 `Hello, simple-tomcat!\n`을 보낸다. UTF-8 본문 바이트 배열 길이 22를 Content-Length에 쓰고 Connection: close를 포함한다.
- 응답 후 flush하고 연결을 닫아 다음 연결을 받는다. 헤더 끝 전 EOF는 응답 없이 닫는다.
- 연결별 IOException은 종료 중이 아니면 경고 로그를 남기고 다음 연결을 받는다. 리스닝 소켓의 예기치 않은 accept 오류는 호출자에게 전파한다.

코드: [Endpoint](../../src/main/java/simpletomcat/util/net/Endpoint.java). 학습: [요청 흐름](../01-socket/01-endpoint-and-request-flow.md).

## 종료와 생명주기

- `close()`는 리스닝 소켓과 현재 소켓을 닫으며 반복 호출할 수 있다.
- 현재 소켓 등록과 close가 같은 잠금을 사용해 종료 시점에 수락된 연결도 정리한다.
- accept 또는 헤더 읽기 대기가 해제되면 serve가 반환한다. 호출자가 스레드 완료를 기다려야 한다. 현재 테스트는 Future.get으로 확인한다.
- 닫은 인스턴스의 재시작은 지원하지 않는다. 새 Endpoint를 생성한다.
- main은 기본 포트 8080 또는 인자 포트를 사용하고 종료 훅을 등록한다.

## 미구현 기능

| 기능 | 현재 제약 | 예정 단계 |
|---|---|---|
| HTTP 파싱·오류 응답 | 잘못된 요청도 헤더 끝이 있으면 고정 200. 400 처리 없음 | 2 |
| 서블릿 계약·생명주기 | Servlet·StandardWrapper 없음 | 3 |
| Catalina 계층·Mapper | 컨테이너 호출·서블릿 경로 매핑 없음 | 4·5 |
| 전체 연결·내장 서버 | Connector·CoyoteAdapter·SimpleTomcat 없음, 404·500 처리 없음 | 6 |
| 필터 체인 | 필터 실행·중간 응답 종료 없음 | 7 |
| 동시성·읽기 제한 | 작업 풀·서버 읽기 타임아웃·헤더 크기 제한 없음. 느린 연결이 다음 처리를 지연 | 작업 풀·타임아웃은 8, 헤더 크기 제한 시점은 미정 |
| simple-spring 연결 | 디스패처·주문 API 연동 없음 | 9 |
| JSP 실행 | JSP 변환·컴파일·실행·재사용 없음 | 10 |

요청 본문·keep-alive·NIO·다중 웹 애플리케이션 배포의 세부 지원은 현재 구현에 포함되지 않는다. 전체 Jakarta Servlet 호환도 제공하지 않는다.

## 검증 증거

마지막 기능 검증은 2026-11-03이다. clean verify로 BuildEnvironmentTest 1개·EndpointTest 5개가 통과하고 JAR이 생성됐다. 같은 서버에 대한 curl 두 요청도 모두 200·22바이트 본문·연결 종료를 확인했다. 문서 정리 과정에서 새로 실행한 결과는 아니다.

EndpointTest는 연속 요청, 나뉜 헤더 끝, 불완전 요청 EOF 후 정상 요청, accept 대기 중 종료·포트 반환, 헤더 읽기 중 종료를 검증한다. 클라이언트 연결 리셋 복구는 별도 자동 테스트가 없다.

[테스트별 검증 내용과 실행 명령](../01-socket/02-tests-and-next-steps.md) · [초기 실행 기록](../history/01-initial-implementation.md) · [학습 문서 목차](../README.md)
