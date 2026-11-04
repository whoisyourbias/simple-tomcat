# simple-tomcat

Tomcat의 구조를 따라 소켓 연결, HTTP 처리, 서블릿 컨테이너와 JSP 실행을 직접 구현하는 Java 학습 라이브러리다. AI가 단계별 실행 가능한 코드를 작성하고, 사용자는 코드를 읽고 실행·수정하며 요청 한 건의 흐름을 학습한다.

최종 목표는 별도 프로젝트 `simple-spring`에서 주문 한 건을 저장한 뒤 `GET /orders/count`에 상태 200과 본문 `1`을 받는 것이다. 현재는 1단계 소켓 서버까지 구현했다.

## 먼저 읽을 것

1. 사용자 지시와 적용되는 `AGENTS.md`를 확인한다. 이 README는 해당 지시를 대체하지 않는다.
2. [현재 진행 상황](doc/00-overview/01-progress.md)과 [구현 상세·제약](doc/reference/02-implementation-status.md)을 읽는다.
3. 수정할 구현과 대응 테스트를 함께 읽는다. 현재 대상은 [Endpoint](src/main/java/simpletomcat/util/net/Endpoint.java)와 [EndpointTest](src/test/java/simpletomcat/util/net/EndpointTest.java)다.
4. [학습 문서 목차](doc/README.md)에서 해당 단계의 설명을 확인한다. 문서와 코드가 다르면 코드·테스트를 대조하고 차이를 보고한다.

## 현재 지원 범위

기준: 2026-11-04. 1단계 소켓 연결 완료, 다음은 2단계 Coyote HTTP 처리다.

| 영역 | 구현한 동작 | 현재 제약 |
|---|---|---|
| 빌드 | JDK 21, Maven Wrapper, JUnit, 단일 JAR 패키징 | JDK는 로컬 설치 필요 |
| 소켓 연결 | 연결 수락, 헤더 끝 읽기, 고정 HTTP 응답, 연결 종료 후 다음 요청 수락 | 단일 스레드, 연결당 요청 한 건 |
| 서버 종료 | accept·헤더 읽기 중 종료, 리스닝·처리 중 소켓 정리 | 닫은 Endpoint는 새 인스턴스로 교체 |

본문 없는 GET을 학습 대상으로 삼으며, 현재 코드는 헤더 종료만 확인한다. 메서드·경로를 해석하지 않아 완성된 헤더에는 모두 같은 응답을 보낸다. HTTP 파싱·400 응답은 2단계, 작업 풀·읽기 타임아웃은 8단계에서 추가한다. 서블릿·컨테이너·simple-spring 연동·Jasper는 아직 구현하지 않았다.

## 환경과 검증 명령

JDK 21과 Maven Wrapper 3.9.9를 사용한다. 테스트는 JUnit Jupiter 5.11.4·Surefire 3.5.2로 실행한다. 별도 Maven 설치는 필요 없으며 최초 실행 시 배포판·의존성 다운로드를 위한 네트워크가 필요하다.

프로젝트 루트의 Windows PowerShell에서 실행한다.

```powershell
# 전체 테스트와 JAR 패키징
.\mvnw.cmd -B clean verify

# 소켓 서버 테스트만 실행
.\mvnw.cmd -B "-Dtest=simpletomcat.util.net.EndpointTest" test
```

macOS·Linux에서는 `sh ./mvnw -B clean verify`를 사용한다. JDK·PATH 설정과 IntelliJ 안내는 [빌드 환경](doc/00-overview/03-build-environment.md)에 정리했다.

2026-11-03 검증 기록은 테스트 6개 통과와 JAR 생성이다. 기록된 수치를 현재 실행 결과로 간주하지 말고, 변경 검증 시 실행한 명령과 결과를 따로 보고한다. 테스트 보고서는 `target/surefire-reports/`, JAR은 `target/simple-tomcat-0.1.0-SNAPSHOT.jar`에 생성된다.

## 사용 예제: 소켓 서버 실행

빌드 후 서버를 실행한다. 기본 포트는 8080이며 마지막 인자로 다른 포트를 지정할 수 있다.

```powershell
java -cp target/classes simpletomcat.util.net.Endpoint
```

다른 터미널에서 같은 서버에 두 번 요청한다.

```powershell
curl.exe --noproxy '*' --max-time 5 -v http://localhost:8080/hello
curl.exe --noproxy '*' --max-time 5 -v http://localhost:8080/hello
```

두 요청 모두 `200 OK`, `Content-Length: 22`, `Connection: close`와 본문 `Hello, simple-tomcat!`을 받는다. 본문 끝에는 LF 한 바이트가 있다. 서버 터미널에서 `Ctrl+C`로 종료한다. [요청 처리 흐름](doc/01-socket/01-endpoint-and-request-flow.md)과 [테스트·검증 기록](doc/01-socket/02-tests-and-next-steps.md)을 함께 읽는다.

## 코드 구조와 탐색 지점

현재 존재하는 소스 구조다. 이후 단계의 패키지는 [예정 패키지 배치](doc/reference/01-package-structure.md)에서 확인한다.

```text
src/main/java/simpletomcat/
├─ package-info.java
└─ util/net/Endpoint.java        # 연결 수락·고정 응답·종료
src/test/java/simpletomcat/
├─ BuildEnvironmentTest.java     # JDK 컴파일러 확인
└─ util/net/EndpointTest.java     # 실제 TCP 요청·종료 검증
doc/                            # 학습 순서·참고 자료·이전 기록
docs/superpowers/plans/          # 구현 작업 계획
tomcat-architecture.html         # 전체 목표 구조와 요청·응답 애니메이션
```

| 변경 대상 | 먼저 볼 구현·설정 | 대응 테스트 |
|---|---|---|
| 연결 수락·헤더 끝·응답·종료 | [Endpoint](src/main/java/simpletomcat/util/net/Endpoint.java) | [EndpointTest](src/test/java/simpletomcat/util/net/EndpointTest.java) |
| Java·테스트·패키징 | [pom.xml](pom.xml) | [BuildEnvironmentTest](src/test/java/simpletomcat/BuildEnvironmentTest.java) |

## 문서 관리

학습 문서는 [doc/README.md](doc/README.md)를 진입점으로 삼는다. 기능의 지원 범위가 바뀌면 현재 진행 상황, 해당 단계의 설명, 구현 상세·제약을 함께 갱신하고 실행 증거를 남긴다. 이전 실행 기록은 당시 날짜와 결과를 유지한다.

- [전체 목표와 프로젝트 경계](doc/00-overview/02-architecture.md)
- [구현 단계와 실제 Tomcat 역할의 대응](doc/reference/03-learning-stage-mapping.md)
- [구조도와 요청·응답 애니메이션](tomcat-architecture.html)
- [초기 구현·검증 기록](doc/history/01-initial-implementation.md)
