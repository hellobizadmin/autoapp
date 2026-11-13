# 에이전트 작업 지침

이 저장소는 Pleos Connect(Android Automotive OS)용 차량 경고 통역 앱 "차근차근"입니다.
모든 기능 요구사항은 `FEATURE_SPEC.md`에 있습니다. 작업 전에 반드시 끝까지 읽으세요.

## 반드시 지킬 규칙
1. `FEATURE_SPEC.md` 0장 작업 규칙을 따릅니다.
2. Pleos SDK의 클래스명, 메서드명, Gradle 의존성을 추측하지 않습니다. 사용자가 공식 문서를 제공하기 전까지는 `adapter/` 인터페이스와 `adapter/fake/` 구현만 사용하고, 필요한 곳에 `// TODO(verify-sdk):`를 남깁니다.
3. 안내 문구는 `app/src/main/assets/warning_dictionary_ko.json`에서만 읽습니다. 코드에 한국어 안내 문구를 직접 쓰지 않습니다.
4. 지시받은 단계(`FEATURE_SPEC.md` 13장)만 구현하고 멈춥니다. 범위 밖(9장) 기능은 만들지 않습니다.
5. `core/` 패키지는 Android/SDK 의존성 없는 순수 Kotlin으로 작성합니다.
6. 작업을 끝내기 전에 단위 테스트를 실행합니다: `./gradlew testDebugUnitTest`
7. 커밋 메시지에 관련 요구사항 ID(FR-xx)를 적습니다.
8. 다른 에이전트가 작업 중일 수 있으니, 작업 시작 전에 `git status`로 변경 사항을 확인하고 지시받지 않은 파일은 수정하지 않습니다.

## 작업 기록
작업을 마치면 `WORKLOG.md` 맨 아래에 한 줄을 추가합니다:
`- YYYY-MM-DD HH:MM [에이전트명] 단계/FR: 한 일, 남은 일`
