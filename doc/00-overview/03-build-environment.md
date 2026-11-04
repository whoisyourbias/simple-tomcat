# 00-03. 빌드·테스트 환경

0단계에서 Java 컴파일·테스트 실행·단일 JAR 패키징을 구성했다. 빌드 설정은 [pom.xml](../../pom.xml), JDK 컴파일러 확인은 [BuildEnvironmentTest](../../src/test/java/simpletomcat/BuildEnvironmentTest.java)에 있다.

## 환경

| 항목 | 설정 |
|---|---|
| Java | JDK 21, 확인한 설치는 Zulu 21.0.11 |
| 빌드 | Maven Wrapper 3.9.9, 별도 Maven 설치 불필요 |
| 테스트 | JUnit Jupiter 5.11.4, Surefire 3.5.2 |
| Maven 좌표 | `io.github.whoisyourbias:simple-tomcat:0.1.0-SNAPSHOT` |
| 산출물 | `target/simple-tomcat-0.1.0-SNAPSHOT.jar` |
| 테스트 보고서 | `target/surefire-reports/` |

IntelliJ에서는 `pom.xml`을 Maven 프로젝트로 열고 Project SDK와 Maven 실행 JDK를 JDK 21로 지정한다. 최초 실행 시 Wrapper 배포판과 의존성을 내려받을 네트워크가 필요하다.

## 실행 명령

모든 명령은 프로젝트 루트에서 실행한다.

```powershell
# 전체 테스트·JAR 패키징
.\mvnw.cmd -B clean verify

# 테스트만 실행
.\mvnw.cmd -B test

# Endpoint 테스트만 실행
.\mvnw.cmd -B "-Dtest=simpletomcat.util.net.EndpointTest" test
```

macOS·Linux에서는 `sh ./mvnw -B clean verify`를 사용한다. 기존 직접 실행 검증은 Windows에서 수행했다.

## Windows 세션 PATH 보정

`JAVA_HOME`은 JDK 설치 경로여야 한다. 기존 에이전트 환경에서는 JAVA_HOME이 있어도 PATH에서 java와 Windows PowerShell을 찾지 못했다. 같은 문제가 있으면 현재 세션에서 다음 보정을 적용한다.

```powershell
$env:PATH = "$env:JAVA_HOME/bin;$env:SystemRoot/System32;$env:SystemRoot/System32/WindowsPowerShell/v1.0;$env:PATH"
.\mvnw.cmd -B clean verify
```

이 보정은 시스템 PATH를 변경하지 않는다. 서버 실행 시에도 같은 세션을 사용하거나 다음처럼 JAVA_HOME의 실행 파일을 지정한다.

```powershell
& "$env:JAVA_HOME/bin/java.exe" -cp target/classes simpletomcat.util.net.Endpoint
```

## 기존 검증 결과

| 실행일 | 범위 | 명령 | 결과 |
|---|---|---|---|
| 2026-11-03 | 0단계 | `.\mvnw.cmd -B clean verify` | BuildEnvironmentTest 1개 통과, JAR 생성 |
| 2026-11-03 | 1단계 추가 후 | `.\mvnw.cmd -B clean verify` | 전체 6개 통과, 실패·오류·건너뜀 0, JAR 생성 |

과거 결과는 이번에 재실행한 결과가 아니다. 소켓 동작 검증은 [1단계 테스트](../01-socket/02-tests-and-next-steps.md), 당시 실행 기록은 [history](../history/01-initial-implementation.md)에 있다.

[학습 문서 목차](../README.md) · [현재 진행 상황](01-progress.md)
