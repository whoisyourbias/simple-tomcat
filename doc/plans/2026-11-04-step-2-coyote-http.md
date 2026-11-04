# 2단계 Coyote HTTP 처리 Implementation Plan


**Goal:** 실제 TCP 요청을 Coyote Request로 해석하고 테스트용 Adapter가 만든 Response를 HTTP로 반환한다. 잘못된 요청의 400 응답과 한글 본문의 바이트 길이를 검증한다.

**Architecture:** Tomcat의 Endpoint → ConnectionHandler → Processor → Adapter 책임 분리를 따른다. Endpoint는 연결 수락·종료와 연결별 I/O 오류 격리, Http11Protocol은 Handler·Processor 조립, Http11Processor는 요청 행·헤더 파싱 → 버전·요청 준비 → Adapter.service → 응답 준비·전송을 담당한다. NIO 이벤트·캐시·입출력 필터는 아래 차이표에 명시한 범위로 축소한다.

**Tech Stack:** JDK 21, Maven Wrapper 3.9.9, JUnit Jupiter 5.11.4, Surefire 3.5.2. 운영 코드의 외부 의존성은 추가하지 않는다.

**Spec:** [현재 진행 상황의 2단계와 다음 작업](../00-overview/01-progress.md), [전체 목표와 경계](../00-overview/02-architecture.md), [패키지 배치](../reference/01-package-structure.md), [1단계 테스트와 다음 작업](../01-socket/02-tests-and-next-steps.md).

작성·수정일: 2026-11-04. 이 문서는 **구현 전 계획**이다. 루트 AGENTS.md에 따라 Tomcat 공식 소스를 대조해 수정했다. 이번 작업은 계획 수정까지이며 구현·Java 테스트 실행·커밋은 수행하지 않는다. 아래의 검증은 향후 구현자가 실행할 절차다.

## Global Constraints

- 단일 JAR 라이브러리이며 기본 패키지는 `simpletomcat`이다.
- 초기 범위는 호스트 하나·웹 앱 하나, 본문 없는 GET, 연결당 요청 한 건이다.
- GET은 정상 왕복의 학습 기준이다. Coyote에 GET 전용 분기를 두지 않으며, 본문 없는 다른 유효 메서드도 Adapter에 전달한다. HEAD 응답의 본문 억제는 Processor가 담당한다.
- Http11Protocol은 블로킹 소켓을 조립하는 학습용 이름이다.
- 테스트는 main과 같은 패키지를 사용하고 HTTP 통합 검증은 `src/test/java/simpletomcat/integration/`에 추가한다.
- 작업 풀·서버 읽기 타임아웃은 8단계에 둔다. 헤더 크기 제한은 이번 단계에 추가하지 않으며 현재 제약으로 기록한다.
- Servlet·Catalina·Mapper·필터·simple-spring·Jasper·keep-alive·NIO는 이번 단계의 구현 대상에 포함하지 않는다.
- Endpoint의 accept 대기·처리 중 소켓 종료, 반복 close, 연결별 IOException 이후 다음 연결 처리 동작을 보존한다.

## Tomcat 구현 근거

**기준: Apache Tomcat 10.1.34, 릴리스 태그 `10.1.34`.** 기존 문서의 10.1 계열과 연결하면서 조사 결과를 재현하기 위해 이 태그로 고정했다. 최신 버전이나 배포 권장 버전이라는 뜻은 아니다. 아래 파일은 이 태그의 공식 저장소 소스를 실제 읽고 확인했다. 상태 코드 정책을 RFC나 클래스 이름에서 추정하지 않는다.

| ID | 공식 소스·확인한 메서드 | 확인한 사실 |
|---|---|---|
| S1 | [Http11NioProtocol](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/http11/Http11NioProtocol.java): 생성자, [AbstractProtocol](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/AbstractProtocol.java): 생성자·ConnectionHandler.process | 프로토콜이 Endpoint의 Handler를 연결하고 Handler가 Processor를 확보·호출한다. Http11NioProtocol은 NioEndpoint를 사용한다. |
| S2 | [AbstractEndpoint](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/tomcat/util/net/AbstractEndpoint.java): Handler.process, [NioEndpoint](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/tomcat/util/net/NioEndpoint.java): SocketProcessor.doRun | Handler는 SocketState를 반환하며 Endpoint 측에서 CLOSED 소켓을 닫는다. 소켓 생명주기와 HTTP 처리가 분리된다. |
| S3 | [AbstractHttp11Protocol](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/http11/AbstractHttp11Protocol.java): createProcessor·getMaxKeepAliveRequests·헤더 크기 설정 | 프로토콜과 Adapter로 Http11Processor를 생성한다. maxKeepAliveRequests=1로 요청 한 건 후 연결을 닫을 수 있다. 요청 헤더 크기·개수 제한도 제공한다. |
| S4 | [Http11InputBuffer](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/http11/Http11InputBuffer.java): parseRequestLine·parseHeaders·parseHeader | 메서드 token·요청 대상·헤더를 바이트로 파싱하고 URI와 query를 분리한다. LF·복수 공백·HTAB·접힌 헤더 등 허용 규칙도 존재한다. |
| S5 | [Http11Processor](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/http11/Http11Processor.java): service·prepareRequestProtocol·prepareRequest | HTTP/1.0·1.1을 처리하고 지원 외 버전은 505로 처리한다. Host 검증 등을 거쳐 Adapter를 호출하며, 유효 메서드를 GET만으로 제한하지 않는다. 일반 Adapter 예외는 500, InterruptedIOException은 즉시 연결 종료 경로다. |
| S6 | [Http11Processor](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/http11/Http11Processor.java): prepareInputFilters·addInputFilter | 본문용 identity·chunked 필터를 구성한다. 중복·비정상 Content-Length는 400, 지원하지 않는 transfer coding은 501이다. 유효한 chunked와 Content-Length가 함께 있으면 Content-Length를 제거하고 keep-alive를 끈다. |
| S7 | [Http11Processor](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/http11/Http11Processor.java): prepareResponse·endRequest, [Http11OutputBuffer](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/http11/Http11OutputBuffer.java): sendStatus·sendHeader | Content-Length·연결 헤더·본문 필터를 준비한다. HEAD와 본문 없는 상태 코드를 구분한다. 상태 행에는 reason phrase를 쓰지 않고 상태 코드 뒤 SP와 CRLF를 기록한다. |
| S8 | [AbstractProcessor](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/AbstractProcessor.java): 생성자·parseHost, [Host](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/tomcat/util/http/parser/Host.java): parse | Processor가 Request·Response를 보유하고 연결한다. Host의 도메인·IPv4·IPv6와 포트를 파싱하며, 빈 Host는 서버 이름을 빈 문자열로 처리하는 별도 분기가 있다. |
| S9 | [Request](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/Request.java): getContentLengthLong·recycle, [Response](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/Response.java): setStatus·setHeader·checkSpecialHeader·recycle, [MimeHeaders](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/tomcat/util/http/MimeHeaders.java): getUniqueValue | Coyote 데이터는 가변·재활용 대상이다. Response 초기 상태는 200·Content-Type 미설정이며 setStatus에 특정 상태 목록 제한은 없다. Content-Length·Content-Type 헤더는 전용 메타데이터와 연결된다. |
| S10 | [ProtocolHandler](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/ProtocolHandler.java): getAdapter·setAdapter·init/start/stop/destroy, [Adapter](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/Adapter.java): service·prepare·asyncDispatch·log | ProtocolHandler는 Adapter 설정과 생명주기 계약을 제공한다. Adapter.service(Request, Response)는 throws Exception이며 Catalina와의 호출 경계다. |
| S11 | [ErrorState](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/ErrorState.java): CLOSE_CLEAN·isIoAllowed, [Http11Processor](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/coyote/http11/Http11Processor.java): badRequest·service, [CoyoteAdapter](https://github.com/apache/tomcat/blob/10.1.34/java/org/apache/catalina/connector/CoyoteAdapter.java): service·postParseRequest | CLOSE_CLEAN에서도 응답 I/O가 허용되므로 400 등의 상태에서 Adapter의 오류 처리 경로로 진행할 수 있다. 따라서 Tomcat이 잘못된 요청에서 Adapter를 반드시 0회 호출한다고 설명하면 안 된다. |

현재 프로젝트는 Endpoint 한 클래스에 HTTP 처리가 섞여 있다. 로드맵 2단계가 요구한 Request·Response·Adapter·ProtocolHandler·Processor를 S1~S11의 책임에 대응시켜 분리한다. 전체 계층·API 복제는 목표로 삼지 않는다.

## 핵심 동작 대응

| 핵심 동작 | Tomcat 클래스·메서드와 동작 | 이 프로젝트의 구현 위치·호출 흐름 | 검증할 테스트 |
|---|---|---|---|
| 네트워크와 HTTP 경계 | S1·S2: Endpoint → Handler → Processor | Endpoint.Handler → Http11Protocol의 Handler → Http11Processor.process | Task 2 두 연결·Handler I/O 오류, Task 4 실제 왕복 |
| 요청 해석 | S4: 메서드·URI·query·헤더를 파싱 | Processor 내부 readRequest가 불변 Request 생성. raw URI·query 보존 | Task 3 parsesRequestAndInvokesAdapterOnce·acceptsZeroLengthAndPreservesEncodedTarget |
| 버전·Host·길이 검증 | S5·S6: 버전 준비 → 헤더 의미 검증 | prepareRequestProtocol → prepareRequest. 1.0·1.1, Host 유일성, 길이 검증 | Task 3 acceptsHttp10WithoutHost·rejectsMalformedRequestsWithoutCallingAdapter |
| 메서드와 상위 처리 경계 | S5·S10: 유효 메서드를 Adapter로 전달 | Processor의 GET 전용 거부 제거, Adapter.service(Request, Response) | Task 3 passesBodylessPostToAdapter |
| 요청 실패와 애플리케이션 실패 구분 | S5·S11: 파싱 400, 일반 service 예외 500, 전송 I/O 실패 종료. 오류 상태에서도 Adapter 경유 가능 | wire의 오류·종료 의미 유지. 실패 시 테스트 Adapter 호출 0회는 D12의 축소. 일반 Adapter Exception은 500, 전송 I/O 실패는 전파 | Task 3 adapterExceptionProduces500·propagatesTransportFailure, Task 4 오류 후 정상 요청 |
| 응답 framing·본문 의미 | S7: 길이·HEAD·본문 없는 상태 처리 | prepareResponse → writeResponse. UTF-8 byte 길이, HEAD 본문 억제, 204·205·304 구분 | Task 3 writesUtf8BodyUsingByteLength·suppressesBodyForHeadAndBodylessStatuses |
| 연결 종료와 요청 상태 격리 | S2·S3·S9: CLOSED·maxKeepAliveRequests=1·recycle | 항상 Connection: close. 새 Request·Response로 요청 상태 격리, 소켓은 Endpoint가 닫음 | Task 2 종료·포트 반환, Task 3 상태 누출·첫 요청만, Task 4 close 위임 |

## 차이와 생략

이 표는 이번 계획의 작업 기록이다. S 번호는 위 태그 고정 소스 근거를 가리킨다. 각 차이는 구현 후 `doc/02-http/03-tomcat-comparison.md`에 실제 결과와 함께 옮기고, 현재 제약은 `doc/reference/02-implementation-status.md`에도 반영한다.

| ID·항목 | 실제 Tomcat 동작·소스 근거 | 이번 구현의 동작 | 단순화·생략 이유 | 영향·제약 | 검증 방법 | 후속 처리·기록 문서 |
|---|---|---|---|---|---|---|
| D1 I/O·Handler | S1·S2: NIO·SocketWrapper·SocketEvent·SocketState·Processor 캐시 | 블로킹 Socket·void Handler, Protocol의 콜백이 Processor 호출 | 1단계 네트워크 기반 활용 | 순차 처리, 이벤트 재진입·Processor 캐시 없음. 책임·소켓 소유권은 유지 | 두 연결·Handler 실패·accept/read 종료 | 작업 풀·타임아웃은 8단계, NIO는 별도 실험. 비교 문서 D1 |
| D2 생명주기·조립 API | S10: Adapter setter·다단계 lifecycle | 생성자 Adapter 주입과 getAdapter, getPort/serve/close | 전체 연결 전의 최소 실행 계약 | start의 비동기 의미·pause/resume·재시작 없음. Endpoint(int)와 main의 변경도 이 프로젝트 API 선택 | Protocol Adapter 조회·왕복·반복 close | 6단계 조립·생명주기에서 재검토. D2 |
| D3 데이터·입출력 버퍼 | S4·S7·S8·S9: 재사용 데이터·독립 Input/OutputBuffer·출력 훅·필터 | 불변 Request·새 Response, Processor 내부 파싱·출력 메서드, UTF-8 전체 본문 버퍼링 | 작은 학습 단위 유지 | 바이트 해석·상위 호출·직렬화의 순서는 보존. 스트리밍·commit 전후 상태·캐시 생략 | 메모리 스트림 왕복·두 응답 상태 격리·전송 오류 | 스트리밍 필요 시 별도 계획, 전체 API 호환은 목표 밖. D3 |
| D4 파싱 허용 범위 | S4·S5: LF·복수 SP/HTAB·접힌 헤더·absolute-form·HTTP/0.9 등 처리 | CRLF·요청 행 단일 SP·origin-form만. obs-fold·다른 target 형태는 400, 0.9는 미지원 | 우선 학습 입력을 한정 | Tomcat이 허용하는 일부 입력 거부. 완성된 헤더 이후 해석하므로 오류 응답 시점도 다를 수 있음 | strict 문법 사례·불완전 EOF·분할 전송 | 의도한 제한으로 기록, 확장은 별도 계획. D4 |
| D5 Host authority | S8: 전용 Host/HttpParser 문법과 serverName/serverPort 설정 | 빈 Host는 허용. 비어 있지 않으면 JDK URI의 authority 검증으로 대체, raw Host 보존. 서버 이름·포트 필드는 아직 없음 | Mapper 이전 단계에서 표준 라이브러리 활용 | 경계 문법은 Tomcat Host parser와 완전히 같지 않음. 존재·유일성·일반적인 잘못된 Host의 400은 유지 | localhost·IPv4·대괄호 IPv6·숫자 포트 허용, 공백·userinfo·비숫자 포트 거부 | 6단계 CoyoteAdapter 연결 전 전용 파서·정규화 재검토. D5 |
| D6 요청 본문 | S6: identity·chunked 필터로 본문 지원 | CL 없음/0만 허용. 양수 CL 또는 TE 단독은 501. TE+CL은 400으로 더 엄격하게 거부 | 본문 없는 학습 범위 유지 | 정상 본문·chunked 요청도 미지원. 501을 Tomcat의 양수 CL 정책으로 설명하지 않음 | 0 길이 통과·본문 미지원·중복 길이·TE+CL 테스트 | 본문 지원 시 별도 계획. D6 |
| D7 keep-alive | S3·S5·S7: 요청 반복, 설정으로 한 건 제한 가능 | maxKeepAliveRequests=1에 해당하는 항상 close 정책 | 초기 범위 유지 | 처리 순서·종료 의미 유지, 연결 재사용 없음 | 첫 요청만·EOF·다음 연결 정상 처리 | keep-alive는 별도 실험. D7 |
| D8 헤더 제한·타임아웃 | S3·S5: 크기·개수 제한과 읽기 타임아웃 | 이번 단계에는 없음 | 기존 로드맵 범위 유지 | 느린 연결과 큰 헤더에 취약. close로 대기 해제는 유지 | 읽기 중 close. 제한·타임아웃 기능을 검증한 것으로 주장하지 않음 | 타임아웃 8단계, 크기·개수 제한 추가 시점은 별도 결정. D8 |
| D9 응답 데이터 API | S9: 일반 status, 전용 Content-Length·Content-Type 메타데이터 | status는 목록 제한 없이 저장. Content-Length·Connection·Transfer-Encoding은 Processor 소유로 setHeader 거부, Content-Type은 일반 헤더 | 전체 버퍼 길이로 framing을 확정 | 응답 헤더 setter 전체 호환 없음. 지원하는 최종 상태는 200~599, 그 밖의 값은 전송 전 500 | 404·500 상태 저장, 예약 헤더·응답 분할 거부, 204/205/304 | 헤더 API·commit 확장은 별도 계획. D9 |
| D10 오류 응답·헤더 출력 | S5·S7·S9: 오류 상태·출력 훅, 버퍼 직렬화 시 헤더 처리 | 고정 text/plain 오류 본문, 일반 Adapter 예외 시 새 500 Response, ASCII 헤더 값만 허용하며 setter에서 제어문자 거부 | Catalina 오류 페이지·출력 훅 이전의 최소 표현 | 400/500 의미와 전송 오류 중복 출력 금지는 유지. 실제 오류 페이지·Date 등 자동 헤더·비ASCII 값·중복 응답 헤더는 미지원 | 오류 본문·500 초기화·전송 중 실패·잘못된 응답 헤더 | 6단계 Catalina 오류 처리와 연결해 재검토. D10 |
| D11 Adapter 확장 계약 | S10: service 외 prepare·asyncDispatch·log 등 제공 | service(Request, Response) throws Exception만 제공 | 동기 왕복만 구현 | 상위 처리 호출 경계 유지, async·업그레이드·접근 로그 없음 | 테스트 Adapter 1회 호출·예외 처리 | Catalina 연결 6단계, 기타 확장은 별도 계획. D11 |
| D12 오류 상태의 Adapter 경유 | S5·S11: CLOSE_CLEAN이면 400 등의 상태에서도 Adapter를 호출해 오류 처리를 이어갈 수 있음 | 파싱·준비 실패는 Processor가 직접 응답하고 테스트 Adapter를 호출하지 않음 | 아직 Catalina 오류 처리용 Adapter가 없고 테스트 Adapter를 애플리케이션 대역으로 사용 | wire의 400·연결 종료는 보존하지만 내부 오류 호출 흐름은 다름. 호출 0회를 Tomcat과 동일한 동작으로 설명하지 않음 | 오류 요청의 400·테스트 Adapter 0회·다음 요청 성공 | 6단계 CoyoteAdapter 도입 시 오류 상태 전달·오류 페이지 경유 재설계. D12 |

## 설계 선택과 처리 정책

### 책임 분리

기존 문서의 분리 방향에 맞춰 **Endpoint에 소켓 처리 콜백을 주입**한다. Endpoint가 Coyote 타입을 직접 생성하는 방식은 네트워크 계층이 HTTP에 의존하게 되고, 기존 고정 응답을 별도 실행 경로로 계속 유지하는 방식은 같은 HTTP 책임을 두 곳에 남긴다. 콜백 방식은 Endpoint 종료 테스트를 HTTP 테스트와 독립적으로 유지할 수 있다.

Endpoint의 `readHeaders`, 고정 HEADERS/BODY, `writeResponse`와 `main`을 제거하고 실행 예제는 `Http11Protocol.main`으로 이동한다. `Endpoint(int)`는 `Endpoint(int, Handler)`로 교체한다. 기존 저장소의 호출자는 EndpointTest와 실행 문서이므로 함께 변경한다. 외부 바이너리·소스 호환성을 유지하는 작업은 이번 계획에 포함하지 않는다.

### 지원 입력과 오류 처리

아래는 완전한 HTTP 서버 규격 구현이 아닌 이번 학습 단계의 처리 정책이다.

| 입력·조건 | 제안 동작 |
|---|---|
| `GET /hello?name=a HTTP/1.1` + Host 한 개 | method=`GET`, requestURI=`/hello`, queryString=`name=a`, protocol=`HTTP/1.1`로 Adapter 1회 호출 |
| query가 없는 요청 | queryString은 null. 경로와 query는 percent decoding 없이 원문 보존 |
| 헤더 이름 대소문자·값 양끝 SP/HTAB | 이름은 Locale.ROOT 소문자로 정규화하고 값 양끝의 SP/HTAB 제거. 같은 이름의 값은 목록으로 보존 |
| 본문 없는 POST 등 GET 외 유효 메서드 | 그대로 Adapter 호출. 메서드별 애플리케이션 지원 여부는 Adapter 이후 책임 |
| HTTP/1.0 | 지원하고 Host 누락도 허용. 연결은 응답 후 닫음. 응답 상태 행은 S7과 같이 HTTP/1.1로 기록 |
| 요청 행 필드 누락·추가, 잘못된 method token, `/`로 시작하지 않는 target, target의 공백·제어문자·fragment | 완성된 헤더를 받은 후 400, Adapter 호출 0회. origin-form 한정은 D4 |
| 잘못된 버전 문법 | 엄격한 `HTTP/DIGIT.DIGIT` 형식이 아니면 400(D4). HTTP/1.0·1.1 외의 형식상 유효 버전은 505 |
| HTTP/1.1의 Host 누락 또는 버전과 무관한 Host 중복, 비정상 authority, 비 token 헤더 이름, 콜론 앞 공백·누락, obs-fold, 값의 금지 제어문자 | 400, Adapter 호출 0회. Host가 한 개이고 빈 값이면 S8처럼 허용. authority 대체 검증의 차이는 D5 |
| Content-Length 중복·음수·숫자 아님·long 범위 초과, Transfer-Encoding과 Content-Length 동시 존재 | 400, Adapter 호출 0회 |
| 양수 Content-Length 또는 Transfer-Encoding 단독 존재 | 이 프로젝트의 본문 미지원 정책(D6)으로 501, Adapter 호출 0회. 요청 본문을 읽으려고 대기하지 않음 |
| Content-Length 없음 또는 단일 값 0 | 본문 없는 요청으로 처리 |
| 헤더 끝 `CRLF CRLF` 전 EOF | 1단계 동작 유지: 응답 없이 닫음. 연결이 열려 있으면 완성 전 응답하지 않음 |
| 한 연결에 요청 두 건 | 첫 요청 한 건에만 응답 후 연결 종료 |
| 연결별 IOException | Endpoint가 소켓을 정리하고 다음 연결 수락. Processor는 소켓 소유권을 갖지 않음 |
| Adapter의 일반 Exception(IOException·RuntimeException 포함) | 버퍼링 중이므로 미전송 응답을 버리고 새 500 응답을 한 번 작성. 다음 연결 처리. Catalina 오류 페이지·서블릿 실패 통합은 6단계 |
| Adapter의 InterruptedIOException, 입력·출력 스트림 IOException | 전송 가능한 상태로 간주하지 않고 IOException 전파 후 연결 종료. 400/500으로 재전송하지 않음. Error는 일반 애플리케이션 예외로 포괄 처리하지 않음 |

파서는 헤더 끝까지 바이트로 읽으며 CRLF를 엄격하게 사용한다. 헤더가 완성되면 ISO-8859-1로 바이트를 보존해 해석한다. token은 영숫자와 `!#$%&'*+-.^_` 및 backtick, vertical bar, tilde다. 헤더 값은 HTAB을 제외한 C0 제어문자와 DEL을 거부한다. path에는 ASCII unreserved·sub-delims·`:`·`@`·`/`·`%`를 허용하고 query에는 추가로 `?`를 허용한다. percent escape의 디코딩·정상 여부 확인은 상위 URI 처리로 남긴다. 검증 순서는 행·헤더 문법(400) → 버전 준비(505) → Host·길이 의미 검증(400) → 이번 단계의 본문 제한(501) → Adapter 호출이다. 미완성 입력은 응답 없이 EOF 처리하는 D4의 타이밍을 따른다.

비어 있지 않은 Host의 D5 대체 검증은 `URI.create("http://" + host)`를 사용하고 host 파싱 결과가 null이 아니며, userinfo·query·fragment가 없고 path가 비어 있으며 raw authority가 원문과 같아야 한다. 실패는 400이다. 빈 Host는 이 검증을 건너뛴다. 별도 네트워크/DNS 조회는 하지 않는다.

규격 해설은 [RFC 9112](https://www.rfc-editor.org/rfc/rfc9112.html)와 [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html)을 보조 자료로 사용한다. 실제 Tomcat과의 일치 여부는 S1~S11을 기준으로 판단하고, 입력 형태 제한·본문 미지원 정책 등 D1~D12를 전체 RFC/Tomcat 호환으로 표현하지 않는다.

### 응답과 상태 격리

- Response는 요청마다 새로 생성한다(D3). 기본 상태는 S9처럼 200, Content-Type은 미설정, 본문은 빈 문자열이다. 예제 Adapter와 오류 응답에서 `text/plain; charset=UTF-8`을 명시한다.
- `setStatus(int)`는 S9처럼 값을 그대로 저장하며 특정 목록으로 제한하지 않는다. 최종 응답은 200~599만 지원한다(D9). 범위 밖이면 전송 전에 새 500 응답으로 바꾼다. 404·409 등 Adapter가 정한 상태도 유지한다.
- 본문은 UTF-8로 한 번 인코딩하고 그 byte 배열의 길이를 Content-Length로 기록한다. `안녕하세요\n`은 16바이트다.
- 상태 행은 S7의 `HTTP/1.1 <status> \r\n` 형식으로 reason phrase를 생략한다. 이후 설정된 헤더·framing 헤더·빈 CRLF 행·본문을 기록하고 flush한다. 헤더 순서는 완료 기준으로 고정하지 않는다.
- Content-Length·Connection·Transfer-Encoding은 Processor가 소유한다(D9). Response.setHeader에서 해당 이름을 거부하고 항상 `Connection: close`를 기록한다. 일반 응답의 Content-Length는 실제 바이트 길이다.
- HEAD는 GET과 같은 표현 길이를 Content-Length로 기록하되 본문을 보내지 않는다. 204·304는 본문과 Content-Length를 생략하고, 205는 본문 없이 Content-Length 0을 기록한다. HEAD와 본문 없는 상태가 겹치면 상태별 규칙을 먼저 적용한다. 이 밖의 200~599 최종 응답은 설정한 본문 길이를 사용한다.
- 추가 헤더는 ASCII 이름·값만 지원한다. token이 아닌 이름, 값의 CR/LF·제어문자를 거부해 잘못된 응답 행이 생기지 않게 한다. Content-Type은 대소문자와 관계없이 기존 값을 교체한다.
- 400·500·501·505 오류 본문은 각각 `Bad Request\n`, `Internal Server Error\n`, `Not Implemented\n`, `HTTP Version Not Supported\n`로 고정한다(D10). 파싱·준비 오류에서는 Adapter를 호출하지 않는다. 일반 Adapter 예외는 그 호출이 끝난 후 새 500 응답으로 처리한다. 전송 중 실패한 응답을 다시 작성하지 않는다.

## Review Focus

1. TCP 분할 수신·헤더 끝 전 EOF: 조기 Adapter 호출·응답 없이 완성을 기다리고, EOF 후 다음 연결을 처리한다. Task 3·4에서 검증한다.
2. 잘못된 요청·Host·본문 framing: 400 또는 명시한 미지원 응답을 반환하고 Adapter를 호출하지 않는다. HTTP/1.0의 Host 생략과 빈 Host를 잘못 거부하지 않는다. Task 3에서 검증한다.
3. 응답의 바이트·본문 의미: UTF-8 16바이트와 Content-Length가 일치하고 HEAD·204·205·304에서는 해당 규칙대로 본문을 억제한다. Task 3·4에서 검증한다.
4. 연속 연결·두 번째 응답: Request·Response 상태와 헤더·본문이 이전 요청에서 누출되지 않고, 오류 후에도 다음 요청을 받는다. Task 3·4에서 검증한다.
5. 연결 수락·읽기 중 종료: 주입 구조 변경 후에도 close가 대기를 해제하고 포트를 반환한다. Task 2·4에서 검증한다.

## 파일 구성

| 경로 | 작업·책임 |
|---|---|
| `src/main/java/simpletomcat/coyote/Request.java` | 생성: 불변 요청 데이터·헤더 조회 |
| `src/main/java/simpletomcat/coyote/Response.java` | 생성: 응답 상태·헤더·UTF-8 본문 데이터 |
| `src/main/java/simpletomcat/coyote/Adapter.java` | 생성: 프로토콜 처리와 상위 호출 사이의 계약 |
| `src/main/java/simpletomcat/coyote/ProtocolHandler.java` | 생성: Adapter 조회·블로킹 실행·포트 조회·종료 계약 |
| `src/main/java/simpletomcat/coyote/http11/Http11Processor.java` | 생성: 파싱·Adapter 호출·HTTP 응답 작성 |
| `src/main/java/simpletomcat/coyote/http11/Http11Protocol.java` | 생성: Endpoint·Processor 조립과 수동 실행 예제 |
| `src/main/java/simpletomcat/util/net/Endpoint.java` | 수정: Handler 주입, HTTP 코드와 main 제거 |
| `src/test/java/simpletomcat/coyote/RequestTest.java`, `ResponseTest.java` | 생성: 데이터 계약 검증 |
| `src/test/java/simpletomcat/coyote/http11/Http11ProcessorTest.java` | 생성: 메모리 스트림 기반 HTTP 검증 |
| `src/test/java/simpletomcat/util/net/EndpointTest.java` | 수정: 가짜 Handler로 기존 네트워크·종료 보장 검증 |
| `src/test/java/simpletomcat/integration/Http11ProtocolTest.java` | 생성: 실제 TCP → 테스트 Adapter → 응답 검증 |

## Task 1: Coyote 데이터와 호출 계약

**Files:** 위 파일 구성의 Request·Response·Adapter·ProtocolHandler와 RequestTest·ResponseTest.

**Interfaces:**

- Tomcat 근거: S9·S10. API 축소는 D2·D3·D9·D11이며, 실제 Coyote와 완전한 서명 호환을 뜻하지 않는다.
- `Request(String method, String requestURI, String queryString, String protocol, Map<String, List<String>> headers)`.
- Request 조회: `String getMethod()`, `String getRequestURI()`, `String getQueryString()`, `String getProtocol()`, `String getHeader(String name)`, `List<String> getHeaders(String name)`. 없는 헤더는 각각 null·빈 목록. 단일 조회는 첫 값. 입력 Map·List를 깊은 복사하고 정규화된 이름으로 보관한다.
- Response: `Response()`, `int getStatus()`, `void setStatus(int status)`, `void setHeader(String name, String value)`, `Map<String, String> getHeaders()`, `void setBody(String body)`, `byte[] getBody()`. 응답 헤더 키는 소문자, 반환 Map은 수정 불가, 본문 배열은 방어 복사한다. null body는 거부한다.
- `@FunctionalInterface Adapter`: `void service(Request request, Response response) throws Exception`.
- `ProtocolHandler extends AutoCloseable`: `Adapter getAdapter()`, `int getPort()`, `void serve() throws IOException`, `void close() throws IOException`. Adapter는 생성자로 주입한다(D2). serve는 호출 스레드를 사용하고 close는 반복 가능하다.

- [x] **실패 테스트 작성:** `requestPreservesRawPathAndQuery`, `headerLookupIgnoresCaseAndPreservesValues`, `requestDefensivelyCopiesHeaderLists`, `responseDefaultsAndUtf8Bytes`, `responsePreservesApplicationStatus`, `responseValidatesHeaders`. 주요 단언은 다음과 같다.

```java
assertEquals("GET", request.getMethod());
assertEquals("/hello%20world", request.getRequestURI());
assertEquals("name=a%20b", request.getQueryString());
assertEquals("localhost", request.getHeader("HOST"));
assertNull(request.getHeader("missing"));
assertEquals(List.of(), request.getHeaders("missing"));
assertEquals(200, response.getStatus());
assertNull(response.getHeaders().get("content-type"));
response.setStatus(404);
assertEquals(404, response.getStatus());
response.setStatus(500);
assertEquals(500, response.getStatus());
response.setBody("안녕하세요\n");
assertEquals(16, response.getBody().length);
assertThrows(IllegalArgumentException.class,
        () -> response.setHeader("X-Test", "ok\r\nInjected: yes"));
assertThrows(IllegalArgumentException.class,
        () -> response.setHeader("Content-Length", "999"));
```

- [x] **실패 확인:** `.\mvnw.cmd -B "-Dtest=RequestTest,ResponseTest" test` → 새 타입 부재에 따른 컴파일 실패를 확인한다.
- [x] **추가 계약 테스트:** `rejectsFramingHeaderOverrides`는 대소문자가 섞인 Content-Length·Connection·Transfer-Encoding 모두 IllegalArgumentException, `rejectsInvalidResponseHeaders`는 비 token 이름·비ASCII 값·CR/LF 값 거부를 확인한다. `getBody` 반환 배열 변경이 내부 데이터를 바꾸지 않는지도 확인한다.
- [x] **구현:** 위 계약과 응답 정책을 구현한다. 입력/반환 컬렉션·배열의 변경이 내부 상태를 바꾸지 않는 단언도 통과시킨다.
- [x] **차이 검토:** 특정 상태 코드 목록 제한과 기본 Content-Type을 넣지 않았는지 확인한다. 예약 헤더 거부·불변 복사·생성자 주입은 D2·D3·D9에 기록한 선택대로 유지한다.
- [x] **통과 확인:** 같은 명령 → BUILD SUCCESS, 실패·오류 0.
- [ ] **커밋 단위:** 이 Task 파일만 포함해 `feat: define Coyote request response and protocol contracts`.

## Task 2: Endpoint에서 HTTP 책임 분리

**Files:** `src/main/java/simpletomcat/util/net/Endpoint.java`, `src/test/java/simpletomcat/util/net/EndpointTest.java`.

**Interfaces:**

- Tomcat 근거: S1·S2. void Handler·단일 Socket은 D1의 축소이며 소켓 소유권은 Endpoint에 둔다.
- Endpoint 내부 `@FunctionalInterface public interface Handler { void handle(Socket socket) throws IOException; }`.
- 생성자 `Endpoint(int port, Handler handler) throws IOException`. handler null 검증은 ServerSocket 바인딩보다 먼저 한다.
- 기존 `getPort()`, `serve()`, `close()` 계약 유지. Endpoint는 수락 소켓을 등록한 뒤 Handler를 정확히 한 번 호출하고 try-with-resources에서 닫는다.
- Handler는 스트림을 사용할 수 있으나 소켓 종료·수락 반복을 소유하지 않는다. `util.net`에서 `coyote`를 import하지 않는다.

- [x] **실패 테스트 작성:** `respondsToTwoConnectionsOnTheSameServer`를 `invokesHandlerForTwoConnectionsOnTheSameServer`로 바꾸고 Handler가 고정 ASCII `handled\n`을 반환하도록 한다. 두 연결 모두 해당 데이터와 EOF를 확인한다.
- [x] **실패 테스트 작성:** `closeUnblocksAcceptAndReleasesThePort`, `closeUnblocksAnIncompleteRequest`를 주입 생성자로 변경한다. 후자는 한 바이트 입력 후 다음 read에서 대기하는 Handler와 CountDownLatch로 진입을 확인하고 close 후 Future 완료를 검증한다.
- [x] **실패 테스트 작성:** `continuesAfterHandlerIOException`에서 첫 Handler 호출이 IOException을 던지고 두 번째 호출이 `handled\n`을 반환하는지 확인한다. 헤더 분할·EOF의 기존 검증은 Task 3·4로 이동한다.
- [x] **실패 확인:** `.\mvnw.cmd -B "-Dtest=EndpointTest" test` → 새 생성자 부재로 실패.
- [x] **구현:** HTTP 상수·파싱·응답 메서드·main을 제거하고 Handler 위임을 넣는다. currentSocket 등록·close의 같은 잠금과 IOException 복구 흐름을 유지한다.
- [x] **통과 확인:** 같은 명령 → BUILD SUCCESS. accept 종료·읽기 종료·포트 재바인딩·반복 close 검증 모두 통과.
- [ ] **커밋 단위:** 위 두 파일로 `refactor: delegate endpoint connections to a handler`.

## Task 3: HTTP 파싱·Adapter 왕복·응답 작성

**Files:** `src/main/java/simpletomcat/coyote/http11/Http11Processor.java`, `src/test/java/simpletomcat/coyote/http11/Http11ProcessorTest.java`.

**Interfaces:**

- Task 1의 Request·Response·Adapter를 사용한다.
- Tomcat 근거: S4~S9. 준비·호출·출력 단계를 아래 내부 메서드로 구분한다. 독립 Input/OutputBuffer 대신 메서드로 두는 차이는 D3이다.
- `Http11Processor(Adapter adapter)`와 `void process(InputStream input, OutputStream output) throws IOException`를 제공한다. 생성 시 null adapter를 거부한다.
- 내부 계약: `private Request readRequest(InputStream input) throws IOException`, `private int prepareRequestProtocol(Request request)`, `private int prepareRequest(Request request)`, `private Response service(Request request) throws InterruptedIOException`, `private void writeResponse(Request request, Response response, OutputStream output) throws IOException`.
- 응답 정규화는 `private Response prepareResponse(Response response)`로 분리한다. 최종 상태 200~599는 그대로 반환하고 범위 밖이면 새 500 Response를 반환한다. writeResponse는 정규화된 Response에 HEAD·상태별 framing을 적용한다.
- `readRequest`는 헤더 끝 전 EOF에 null, 완성된 입력의 문법 오류에 내부 `private static final class BadRequestException extends IOException`을 던진다. process는 이 예외만 400으로 바꾸며 일반 IOException을 파싱 오류로 오인하지 않는다. prepare 메서드의 반환 0은 성공, 그 외는 해당 오류 상태다. service는 Adapter의 일반 Exception을 새 500 Response로 바꾸고 InterruptedIOException을 재전파한다.
- 파싱 중 Request가 없으면 writeResponse의 request 인자는 null일 수 있다. 이 경우 오류 본문을 일반 응답처럼 전송한다. 유효 Request에는 HEAD·본문 없는 상태 규칙을 적용한다.
- process는 연결당 한 번 실행한다. 요청·응답·파싱 임시 상태는 호출의 지역 변수로 두며 Processor 재사용 시 이전 요청이 남지 않게 한다.
- 헤더 끝을 바이트 단위로 인식한 후 내부 파싱 결과로 Request를 생성한다. 파싱 실패와 네트워크 IOException을 구분하며 네트워크 오류를 400으로 바꾸지 않는다.

- [x] **실패 테스트 작성:** 메모리 입력 `GET /hello?name=a HTTP/1.1\r\nHost: localhost\r\nX-Tag: one\r\nx-tag: two\r\n\r\n`을 사용한다. `parsesRequestAndInvokesAdapterOnce`에서 경로·query·버전·Host·태그 목록과 호출 횟수 1을 단언한다.
- [x] **실패 테스트 작성:** `writesUtf8BodyUsingByteLength`에서 Adapter가 Content-Type을 명시하고 `안녕하세요\n`을 설정한다. 응답을 CRLF CRLF 기준으로 바이트 분리하고 상태 행 `HTTP/1.1 200 \r\n`, `Content-Length: 16`, `Connection: close`, 실제 본문 배열을 단언한다. 메모리 스트림의 EOF는 네트워크 종료 증거로 쓰지 않는다. `writesEmptyBody`는 길이 0을 확인한다.
- [x] **실패 테스트 작성:** `rejectsMalformedRequestsWithoutCallingAdapter`를 매개변수화한다. 위 정책표의 400 사례 각각과 제어문자·빈 요청 행을 넣는다. strict LF 테스트는 중간 줄에 bare LF를 넣되 마지막은 CRLF CRLF로 끝내 문법 검증까지 도달하게 한다. 완성된 입력에는 상태 400, 본문 `Bad Request\n`, Adapter 호출 0을 단언한다.
- [x] **실패 테스트 작성:** `passesBodylessPostToAdapter`, `acceptsHttp10WithoutHost`, `acceptsEmptyHost`에서 200·Adapter 호출 1을 확인한다. 메서드·버전을 임의로 거부하지 않는 S5·S8 대응 테스트다. 빈 Host는 HTTP/1.1에서도 존재하는 헤더 한 개로 취급한다.
- [x] **실패 테스트 작성:** `validatesHostAuthority`에 localhost·127.0.0.1·localhost:8080·[::1]:8080의 성공과 `bad host`·`user@localhost`·`localhost:abc`·`localhost/path`의 400을 넣는다. 대체 URI 검증의 범위를 D5와 일치시킨다.
- [x] **실패 테스트 작성:** `rejectsUnsupportedFeatures`에는 Content-Length 1·Transfer-Encoding chunked의 501과 HTTP/1.2·HTTP/2.0의 505를 넣는다. Adapter 호출 0과 응답 후 종료 가능한 반환을 확인한다. `acceptsZeroLengthAndPreservesEncodedTarget`는 Content-Length 0과 인코딩된 경로 보존을 확인한다. `rejectsTransferEncodingWithContentLength`는 D6의 별도 400 정책을 확인한다.
- [x] **실패 테스트 작성:** `suppressesBodyForHeadAndBodylessStatuses`는 HEAD+200에 본문 `안녕하세요\n`을 설정하고 전송 본문 0·길이 헤더 16을 확인한다. GET/HEAD 각각의 204·304는 본문 0·Content-Length 없음, 205는 본문 0·Content-Length 0을 단언한다. `preservesCustomFinalStatus`는 404·409 유지, `invalidFinalStatusProduces500`은 101·999를 500으로 바꾸는 D9 정책을 확인한다.
- [x] **실패 테스트 작성:** `doesNotRespondToIncompleteHeaders`는 헤더 끝 전 EOF에서 출력 배열 길이 0·Adapter 호출 0, `processesOnlyFirstRequest`는 연속된 요청 두 건 입력에서 Adapter 호출 1을 단언한다.
- [x] **실패 테스트 작성:** `doesNotLeakResponseStateBetweenCalls`에서 같은 Processor의 첫 응답에만 사용자 헤더·본문을 설정한다. 두 번째 출력은 해당 헤더 없이 `Content-Length: 0`이어야 한다.
- [x] **실패 테스트 작성:** `adapterExceptionProduces500`은 IOException·RuntimeException·일반 checked Exception에 500·고정 본문을 확인한다. Adapter가 먼저 설정한 사용자 헤더·본문은 500에 남지 않아야 한다(D10). `interruptedAdapterDoesNotWriteResponse`는 InterruptedIOException 전파·출력 0을 확인한다.
- [x] **실패 테스트 작성:** `propagatesTransportFailure`는 입력 read 실패를 IOException으로 전파하고 출력 0, 출력 write 실패는 예외 전파·writeResponse 재시도 없음으로 확인한다. 입력/출력 오류를 400·500 응답으로 바꾸지 않는다.
- [x] **실패 확인:** `.\mvnw.cmd -B "-Dtest=Http11ProcessorTest" test` → Processor 부재로 실패.
- [x] **구현:** 위 내부 계약·정책표·응답 정책을 구현한다. 파싱/준비 실패는 Adapter 호출 전 오류 응답, 일반 Adapter 예외는 미전송 상태에서 500, 전송 I/O 실패는 종료로 구분한다. GET 전용 분기·HTTP/1.0 일괄 505·Adapter 일반 예외의 무응답 종료를 넣지 않는다.
- [x] **차이 검토:** S4~S9·S11과 핵심 대응 테스트를 대조하고 D3~D10·D12의 실제 선택이 계획과 다른 경우 차이표·테스트·학습 문서 갱신 작업을 함께 수정한다.
- [x] **통과 확인:** 같은 명령 → BUILD SUCCESS, 실패·오류 0.
- [ ] **커밋 단위:** 위 두 파일로 `feat: parse and write HTTP requests through Coyote adapter`.

## Task 4: Http11Protocol 조립과 실제 TCP 검증·학습 문서

**Files:** `src/main/java/simpletomcat/coyote/http11/Http11Protocol.java`, `src/test/java/simpletomcat/integration/Http11ProtocolTest.java`; 아래 학습 문서.

**Interfaces:**

- `Http11Protocol implements ProtocolHandler`.
- Tomcat 근거: S1·S3·S10. 실제 ConnectionHandler의 Processor 선택·캐시는 D1에 따라 단일 콜백으로 축소한다.
- `Http11Protocol(int port, Adapter adapter) throws IOException`: adapter를 검증하고 Processor를 먼저 구성한 뒤 Endpoint를 바인딩한다. Handler는 `processor.process(socket.getInputStream(), socket.getOutputStream())`에 위임한다.
- `Adapter getAdapter()`는 주입된 인스턴스를 반환한다. `int getPort()`, `void serve() throws IOException`, `void close() throws IOException`는 Endpoint에 위임한다. 별도 서버 스레드를 만들지 않으며 닫은 객체의 재시작은 지원하지 않는다.
- `static void main(String[] args) throws IOException`: 기본 포트 8080, 선택적 포트 인자, 종료 훅. 예제 Adapter는 Content-Type `text/plain; charset=UTF-8`과 기존 본문 `Hello, simple-tomcat!\n`을 설정한다. 예제에서 Processor에 GET 전용 거부를 넣지 않는다.

- [x] **실패 테스트 작성:** 테스트가 포트 0·단일 테스트 스레드·Future로 Protocol을 실행한다. `roundTripsTwoRequestsThroughAdapter`는 서로 다른 경로가 Adapter에 순서대로 도달하고 각각 한 응답·EOF를 받는지 확인한다.
- [x] **실패 테스트 작성:** `exposesConfiguredAdapter`에서 `assertSame(adapter, protocol.getAdapter())`를 확인한다. `acceptsHttp10AndBodylessPostOverTcp`는 Host 없는 HTTP/1.0 GET과 Host가 있는 HTTP/1.1 POST+Content-Length 0의 Adapter 호출·200·EOF를 확인한다.
- [x] **실패 테스트 작성:** `returnsBadRequestThenServesNextConnection`은 Host 없는 완성 HTTP/1.1 요청에 400·호출 0, 다음 정상 요청에 200·호출 1을 확인한다. `returnsUtf8BodyWithCorrectLength`는 실제 TCP에서도 한글 본문 16바이트를 확인한다.
- [x] **실패 테스트 작성:** 기존 EndpointTest의 분할 수신·불완전 EOF 사례를 `waitsForFinalHeaderByte`, `ignoresIncompleteEofThenServesNextConnection`으로 옮긴다. 마지막 LF 전에는 응답도 Adapter 호출도 없어야 한다.
- [x] **실패 테스트 작성:** `closeUnblocksAcceptAndReleasesPort`, `closeUnblocksHeaderRead`로 Protocol.close 위임을 확인한다. `continuesAfterAdapterFailure`는 첫 Adapter의 RuntimeException에 500·EOF, 다음 연결에 200을 확인한다. 오류 응답을 무응답 종료로 바꾸지 않는다.
- [x] **실패 확인:** `.\mvnw.cmd -B "-Dtest=Http11ProtocolTest" test` → Protocol 부재로 실패.
- [x] **구현:** 위 조립·실행·종료 계약을 구현한다. 기존 테스트의 2초 소켓/Future 제한, 테스트 전체 10초 제한과 finally 자원 정리를 따른다. 클라이언트 read timeout을 서버 기능으로 설명하지 않는다.
- [x] **통과 확인:** 같은 명령 → BUILD SUCCESS, 실패·오류 0.
- [x] **학습 문서 갱신:** `doc/02-http/01-coyote-and-request-flow.md`, `doc/02-http/02-tests-and-next-steps.md`, `doc/02-http/03-tomcat-comparison.md`를 작성한다. 앞의 두 문서에는 메서드 호출 흐름·테스트 링크·실행 기록을, 비교 문서에는 기준 태그·S1~S11 소스 링크·핵심 대응표·D1~D12의 실제 차이와 이유·영향·테스트·후속 처리를 저장한다. 변경된 API나 정책은 구현 결과에 맞춰 갱신한다.
- [x] **기존 문서 갱신:** `README.md`, `doc/README.md`, `doc/00-overview/01-progress.md`, `doc/reference/01-package-structure.md`, `doc/reference/02-implementation-status.md`, `doc/reference/03-learning-stage-mapping.md`에 구현 후의 상태·파일·실행 명령을 반영한다. 1단계 학습 문서 두 파일은 당시 구조임을 표시하고 2단계의 현재 진입점으로 연결한다. 과거 검증 날짜·수치는 보존한다.
- [x] **차이 기록 연결:** `doc/README.md`에서 비교 문서로 연결하고 `doc/reference/02-implementation-status.md`에 D1~D12 중 현재 제약에 해당하는 항목과 비교 문서 링크를 반영한다. 기본 Content-Type·상태 행 reason phrase·HTTP/1.0 지원·500 처리의 문서 설명이 과거 계획에 머물지 않게 한다.
- [x] **전체 검증:** `.\mvnw.cmd -B clean verify` → BUILD SUCCESS, 전체 테스트 실패·오류·건너뜀 0, `target/simple-tomcat-0.1.0-SNAPSHOT.jar` 생성. 실제 테스트 수·날짜를 새 기록으로 남긴다.
- [x] **수동 왕복:** 서버를 아래 명령으로 실행하고 같은 프로세스에 curl 두 번을 수행한다. 두 번 모두 상태 코드 200·Content-Length 22·기존 본문·연결 종료를 확인하고 서버를 종료한다. 상태 행은 S7처럼 reason phrase가 비어 있으므로 `200 OK` 문자열과의 완전 일치를 요구하지 않는다.

```powershell
java -cp target/classes simpletomcat.coyote.http11.Http11Protocol
curl.exe --noproxy '*' --max-time 5 -v http://localhost:8080/hello
curl.exe --noproxy '*' --max-time 5 -v 'http://localhost:8080/hello?name=a'
```

- [x] **변경 검토:** `git diff --check`와 변경 파일 목록을 확인한다. 패키지 의존 방향, 문서 명령과 main 이동, 종료 보장을 점검한다.
- [ ] **커밋 단위:** Protocol·TCP 테스트·문서로 `feat: assemble HTTP protocol and document stage 2`.

## 완료 기준과 다음 단계

2단계 완료는 **실제 TCP → Endpoint → Protocol의 Handler → Http11Processor → 테스트 Adapter → Response → TCP** 왕복, malformed 요청 400, 일반 Adapter 예외의 500, HTTP/1.0·메서드 전달·HEAD/상태별 본문 의미, UTF-8 길이 일치, 오류 후 다음 연결 성공, 기존 종료 보장과 전체 빌드 성공으로 판단한다. S1~S11의 근거·핵심 대응·D1~D12의 차이 기록이 학습 문서에 반영돼야 한다. 단순히 클래스 파일이 존재하는 것으로 완료 처리하지 않는다.

이후 3단계는 소켓 연결과 독립적으로 Servlet·StandardWrapper의 init/service/destroy 1/2/1을 구현한다. CoyoteAdapter와 실제 Catalina 연결은 6단계에 둔다.

## 계획 자체 검토

- 로드맵의 Request·Response·Adapter·ProtocolHandler·Processor·Protocol, HTTP 왕복·400·한글 길이를 Task 1~4에 배치했다.
- Task 간 생성자·메서드·소켓 소유권과 실행 방식이 일치하도록 정리했다.
- 기존 EndpointTest의 다섯 보장은 Endpoint 단독 테스트와 HTTP 통합 테스트로 나누어 보존한다.
- 기존의 GET 외 일괄 501, HTTP/1.0의 505, Adapter 일반 예외의 무응답 종료, 상태 코드 목록 제한을 제거하고 S5·S9의 핵심 동작에 맞췄다.
- S11을 추가 확인해 Tomcat의 오류 상태에서도 Adapter가 호출될 수 있음을 기록했다. 테스트 Adapter 호출 0회는 실제 Tomcat의 동일 동작으로 주장하지 않고 D12로 구분했다.
- API 축소·입력 제한·본문 미지원·새 응답 버퍼·오류 본문 등은 D1~D12에 근거·이유·영향·검증·후속 문서를 연결했다. 표는 설계 선택이며 이미 구현된 결과나 사용자의 세부 승인을 뜻하지 않는다.
- 이번 계획 작성에서는 Java 테스트나 서버를 실행하지 않았다. 위 체크박스와 커밋 메시지는 향후 실행 순서다.

## 2026-11-04 실행 기록

사용자의 “작성한 계획으로 구현해라” 지시에 따라 `codex/step-2-coyote-http` 브랜치에서 구현했다. 위의 “구현 전·계획 수정만” 문구는 계획 작성 당시 범위를 보존하며, 실행 결과는 이 절과 학습 문서의 새 기록을 따른다.

- Task 1 complete: RequestTest·ResponseTest를 작성해 타입 부재 컴파일 실패를 확인한 뒤 13개 통과.
- Task 2 complete: 새 Endpoint 생성자 부재 컴파일 실패를 확인한 뒤 4개 TCP 테스트 통과. 두 연결·Handler IOException 복구·accept/read close·포트 반환을 검증했다.
- Task 3 complete: Processor 부재 컴파일 실패를 확인한 뒤 48개 테스트 통과. parsing/preparation/service/write 단계를 구현하고 전송 실패와 애플리케이션 오류를 구분했다.
- Task 4 complete: Protocol 부재 컴파일 실패를 확인한 뒤 10개 실제 TCP 테스트 통과. 요청 흐름·테스트 기록·Tomcat 비교 문서와 현재 상태·과거 문서의 안내를 갱신했다.
- 전체 검증: `.\mvnw.cmd -B clean verify` → BUILD SUCCESS, 76개 실행, 실패·오류·건너뜀 0, JAR 생성. 독립 재검증에서도 76개 통과.
- 수동 검증: 포트 18082의 같은 서버에 `/hello`, `/hello?name=a` curl 두 요청. 둘 다 종료 코드 0·상태 200·길이 22·Connection close·고정 본문 확인. Ctrl+C로 종료했다.
- Ruling: 작업은 현재 폴더의 codex 브랜치에서 수행하고 커밋·push·merge는 하지 않는다. 기존 untracked AGENTS.md·계획을 포함해 사용자가 검토할 수 있는 작업 트리로 남긴다. 커밋 체크박스는 실행하지 않았으므로 미완료로 유지한다.
- Ruling: 계획의 `validatesHostAuthority`는 성공/실패 매개변수 테스트 `acceptsHostAuthority`·`rejectsHostAuthority`로 나눴고 TE+CL 사례는 malformed 입력 매개변수에 포함했다. 행위·입력·단언은 계획과 같다.
- Ruling: 기존 Endpoint의 헤더 분할·불완전 EOF 검증은 Protocol 통합 테스트로 이동했다. Endpoint는 HTTP를 모르며 단순 Handler·종료 보장만 검증한다.
- 환경: Maven Wrapper의 PowerShell 실행을 위해 프로세스 PATH에 Windows PowerShell 디렉터리를 추가했다. java/curl 수동 검증은 설치된 절대 실행 경로를 사용했다. 저장소 환경 설정은 변경하지 않았다.
- 차이 D1~D12의 실제 결과·소스 고정 링크·핵심 대응·후속 처리는 `doc/02-http/03-tomcat-comparison.md`에 저장했다.
