# simple-tomcat 학습 문서

학습 순서대로 번호를 붙였다. 현재는 **1단계 소켓 연결 구현과 검증까지 완료**했다. 다음은 2단계 Coyote HTTP 처리다. 문서 정리 기준일은 2026-11-04이며, 기존 실행 증거의 날짜는 그대로 보존한다.

## 폴더 구조

```text
doc/
├─ README.md
├─ 00-overview/                    # 현재 상태·전체 방향·실행 환경
│  ├─ 01-progress.md
│  ├─ 02-architecture.md
│  └─ 03-build-environment.md
├─ 01-socket/                      # 연결 수락·고정 HTTP 응답·종료
│  ├─ 01-endpoint-and-request-flow.md
│  └─ 02-tests-and-next-steps.md
├─ reference/                      # 패키지 설계·현재 제약·단계 대응
│  ├─ 01-package-structure.md
│  ├─ 02-implementation-status.md
│  └─ 03-learning-stage-mapping.md
├─ history/                        # 날짜별 과거 작업·실행 증거
│  └─ 01-initial-implementation.md
└─ progress.md                     # 기존 경로의 이동 안내
```

## 읽는 순서

| 단계 | 문서 | 상태 |
|---|---|---|
| 00 | [현재 진행 상황](00-overview/01-progress.md), [전체 구조와 경계](00-overview/02-architecture.md) | 전체 안내 |
| 00 | [빌드·테스트 환경](00-overview/03-build-environment.md) | 0단계 완료 |
| 01 | [Endpoint와 요청 흐름](01-socket/01-endpoint-and-request-flow.md), [테스트와 다음 작업](01-socket/02-tests-and-next-steps.md) | 1단계 완료 |
| 참고 | [패키지 배치](reference/01-package-structure.md), [구현 상세·제약](reference/02-implementation-status.md), [실제 기술 대응](reference/03-learning-stage-mapping.md) | 설계·구현 범위 확인 |
| 기록 | [초기 구현 기록](history/01-initial-implementation.md) | 2026-11-03 실행 증거 보존 |

2단계 이후의 전체 순서와 완료 기준은 [진행 상황](00-overview/01-progress.md)에 둔다. 아직 구현하지 않은 단계의 빈 폴더는 만들지 않는다.

## 번호와 관리 규칙

- 학습 폴더는 `00`, `01`, `02`처럼 구현 단계 순서로 번호를 붙인다. 파일 앞 번호는 폴더 안에서 읽는 순서다. 0단계 빌드 환경은 `00-overview`에 둔다.
- `00-overview/01-progress.md`에는 현재 단계, 전체 로드맵과 다음 작업을 둔다.
- 단계별 문서에는 목적, 메서드 호출 흐름, 코드·테스트 링크, 실행 방법을 둔다.
- `reference`에는 패키지 배치, 현재 동작·제약, 실제 기술과의 대응을 둔다. 예정 타입과 구현된 타입을 구분한다.
- `history`에는 당시 작업과 검증 명령·관찰 결과를 보존한다. 새 검증은 날짜를 붙여 추가하고 과거 수치를 최신 결과로 바꾸지 않는다.
- 구현 작업 계획은 [docs/superpowers/plans](../docs/superpowers/plans/)에 보관한다. 학습 설명과 실행 이력은 이 목차에서 찾을 수 있게 한다.
- 실행 증거에는 명령, 실행 날짜, 성공·실패 결과와 검증 범위를 남긴다. 문서 정리만 수행했다면 테스트·서버·실험을 새로 실행한 것처럼 기록하지 않는다.

## 전체 구조 보기

[Tomcat 구조도와 요청·응답 애니메이션](../tomcat-architecture.html)은 전체 목표 구조다. 현재 구현한 부분은 Endpoint이며, 구조도의 나머지 구성 요소가 모두 구현됐다는 뜻은 아니다.

[저장소 README](../README.md) · [현재 구현 상세](reference/02-implementation-status.md)
