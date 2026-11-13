/*******************************
 목   적 : 코어 프로젝트 이름 설정
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
 2026-11-13 : 파일 머리말 및 한글 설명 주석 추가
 2026-11-13 : FR-08·FR-13 및 앱 모듈 연결 지원
*******************************/
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
rootProject.name = "chageun-core"
include(":app")
