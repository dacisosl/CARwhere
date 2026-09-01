# CLAUDE.md — 주차어디 프로젝트 지침 (v2)

안드로이드 주차 위치 기록 앱. 하차를 블루투스로 감지, 바텀시트에서 층수 원터치 저장, 상태바·위젯에 상시 표시.

## 반드시 먼저 읽을 문서
- PRD.md (v2) — 제품 결정사항, v2 변경 로그
- DESIGN.md (v2) — 계기판 무드 디자인, 컬러, 화면 스펙

## 기존 코드 (뼈대, 구조 유지)
- CarBluetoothReceiver.kt / ParkingDetectionService.kt / ParkingNotification.kt / FloorSelectedReceiver.kt
- README.md — 매니페스트, 기기별 함정

## 기술 스택
- Kotlin + Jetpack Compose, 최소 SDK 26
- 데이터: Room (주차 히스토리 필요해짐 — SharedPreferences에서 마이그레이션)
- 위젯: Glance
- 오버레이 바텀시트: SYSTEM_ALERT_WINDOW + WindowManager (권한 없으면 알림 폴백)
- 기압: SensorManager TYPE_PRESSURE, 주행 중에만 등록/해제

## 절대 규칙
1. 한 화면에 결정 하나 — 대시보드/팝업에 요소 추가 금지
2. 층 UI는 세로 스택 (그리드 금지)
3. 형광 #97C459는 화면당 1~2개 요소
4. 내 차 MAC 필터 제거/우회 금지
5. 기압 감지는 "추정 + 사람 탭 확정"까지만 — 자동 확정 구현 금지
6. 상시 위치 추적 금지 (GPS는 주차 확정 시 1회)
7. 배터리: 폴링 금지, 센서는 쓸 때만 등록하고 즉시 해제
8. 모든 화면 systemBarsPadding — 시스템 바 침범 금지

## 현재 우선순위 (v2)
1. 프로젝트 생성 + 기존 kt 통합 + 빌드 통과
2. 매니페스트/권한 (README + 오버레이 권한 추가)
3. 실기기 BT 감지 테스트 (삼성 절전 — 최대 리스크)
4. 바텀시트 팝업 (오버레이) + 알림 폴백
5. 대시보드 v2 (계기판 카드 + 최근 기록 + 인라인 설정 토글)
6. Room 히스토리 + 층/구역/메모
7. 상태바 아이콘 + Glance 홈 위젯 2종
8. 온보딩 4권한 버전
9. 기압 자동감지 베타 (마지막 — 다른 게 다 안정된 후)

## 코드 컨벤션
- 주석 한국어, "왜" 중심
- 상태 머신 변경 시 ParkingDetectionService 상단 흐름도 주석 동기화
- 매직 넘버 금지 (5초 필터, 10초 타임아웃, 기압 0.36hPa/층 등은 상수로)
