# 02-03. Tomcat 10.1.34와 구현 대응·차이

2026-11-04 구현 기준. 이 문서의 이번 구현 열은 현재 코드에 적용한 결과다. 전체 API 호환을 뜻하지 않는다. [요청 흐름](01-coyote-and-request-flow.md) · [검증 기록](02-tests-and-next-steps.md)

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

1단계에서는 Endpoint 한 클래스에 HTTP 처리가 섞여 있었다. 2단계에서 Request·Response·Adapter·ProtocolHandler·Processor를 S1~S11의 책임에 대응시켜 분리했다. 전체 계층·API 복제는 목표로 삼지 않는다.

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

S 번호는 위 태그 고정 소스 근거를 가리킨다. D1~D12는 현재 구현에서 확인한 차이이며 현재 제약은 구현 상세 문서에도 반영했다. Task 번호는 실행한 2단계 계획의 작업 번호다.

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
