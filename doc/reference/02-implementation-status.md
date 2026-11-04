# 구현 상세와 현재 제약

기준일: 2026-11-04. [진행 상황](../00-overview/01-progress.md) · [요청 흐름](../02-http/01-coyote-and-request-flow.md) · [Tomcat 비교 D1~D12](../02-http/03-tomcat-comparison.md)

## 빌드와 검증

JDK 21·Maven Wrapper 3.9.9·JUnit Jupiter 5.11.4·Surefire 3.5.2를 사용한다. BuildEnvironmentTest는 JDK 컴파일러 존재를 확인한다. 2026-11-04 clean verify로 76개 테스트 통과·JAR 생성, 동일 서버 curl 두 요청 성공을 확인했다. [실행 증거](../02-http/02-tests-and-next-steps.md)에 명령·환경·결과가 있다. 과거 2026-11-03의 6개 테스트 기록은 초기 이력에 보존한다.

## 구현한 책임과 동작

- Endpoint(int, Handler)는 Handler 검증 후 포트를 바인딩하고 같은 호출 스레드에서 연결을 순차 처리한다. HTTP 데이터나 Coyote 타입에 의존하지 않는다.
- Http11Protocol은 Endpoint와 Processor를 조립한다. getAdapter/getPort/serve/close를 제공하고 main은 예제 Adapter를 실행한다.
- Http11Processor는 완성된 CRLF 헤더를 읽고 raw URI·query·메서드·헤더를 해석한다. 버전 준비·Host·길이 검증 후 Adapter를 호출하고 응답을 쓴다.
- HTTP/1.0·1.1과 본문 없는 유효 메서드를 처리한다. 1.0은 Host 없이 가능하며 1.1은 Host 한 개가 필요하다. 빈 Host는 허용하고 비어 있지 않은 authority는 JDK URI로 검증한다.
- 파싱·준비 실패 400, 지원 외 버전 505, 이번 단계의 요청 본문 미지원 501, 일반 Adapter Exception 500이다. 입력/출력 I/O 실패와 InterruptedIOException은 연결 종료로 전파한다.
- Response의 기본 상태는 200·Content-Type 미설정이다. 상태는 목록 제한 없이 저장하고 최종 전송은 200~599를 지원한다. 범위 밖 상태는 500으로 바꾼다. 예제·오류 응답은 Content-Type을 명시한다.
- 본문을 UTF-8 전체 버퍼로 보관하고 byte 길이로 Content-Length를 작성한다. HEAD는 표현 길이만 기록하고 본문 생략, 204·304는 본문·길이 생략, 205는 길이 0이다.
- 상태 행은 reason phrase를 생략한다. Content-Length·Connection·Transfer-Encoding은 Processor가 소유하며 사용자 헤더 값은 printable ASCII로 한정한다.
- 요청당 새 Request·Response를 사용한다. 응답 후 flush·연결 종료 후 다음 요청을 받는다. 불완전 헤더 EOF는 응답 없이 닫는다.

## 종료

close는 리스닝·처리 중 소켓을 같은 잠금으로 정리하고 반복 호출 가능하다. accept와 read 대기를 해제한다. 호출자는 serve 스레드 완료를 기다려야 한다. 닫은 인스턴스 재시작은 없으며 새 객체를 만든다. 현재 수동 실행 진입점은 Http11Protocol.main이고 Endpoint.main은 제거했다.

## 현재 제약과 후속 단계

| 항목 | 현재 제약·차이 ID | 후속 처리 |
|---|---|---|
| 네트워크·생명주기 | D1·D2: 블로킹 단일 스레드, SocketState·캐시·전체 lifecycle 없음 | lifecycle 6단계, 작업 풀 8단계, NIO 별도 실험 |
| 프로토콜 데이터·출력 | D3·D9: 새 불변 요청·전체 응답 버퍼, 출력 훅·commit·스트리밍·전체 헤더 API 없음 | 필요 시 별도 계획 |
| 입력 문법 | D4: CRLF·단일 SP·origin-form 한정, obs-fold·absolute-form·HTTP/0.9 미지원 | 의도적 제한, 확장 별도 계획 |
| Host | D5: 전용 Tomcat Host 파서 대신 JDK URI, serverName/serverPort 필드 없음 | 6단계 이전 재검토 |
| 요청 본문 | D6: CL 없음/0만 가능, 정상 본문도 501, TE+CL은 400 | 본문 지원 별도 계획 |
| 연결 재사용 | D7: 연결당 요청 한 건·항상 close | keep-alive 별도 실험 |
| 제한·타임아웃 | D8: 헤더 크기·개수 제한·서버 읽기 타임아웃 없음. 느린 연결이 다음 처리를 지연 | 타임아웃 8단계, 크기·개수 제한 시점 미정 |
| 오류 처리·Adapter | D10~D12: 고정 오류 본문, 테스트 Adapter만 있음, 오류에서 Adapter 경유 생략, async·upgrade·로그 없음 | 실제 Catalina 연결·오류 경유 6단계 |
| 서블릿·컨테이너·필터 | Servlet·StandardWrapper·Mapper·Pipeline·Filter 미구현 | 3~7단계 |
| simple-spring·Jasper | 디스패처·주문 API·JSP 미구현 | 9·10단계 |

각 차이의 실제 Tomcat 10.1.34 근거, 이유·영향·검증·후속 처리 전체는 비교 문서에 저장했다. 전체 Jakarta Servlet/Tomcat API 호환을 제공하지 않는다.
