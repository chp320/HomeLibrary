# HomeLibrary — 가정용 도서 대출 관리 앱

가정 내 보유 도서를 등록·관리하고, 가족 구성원이 대출·반납하며 이력을 조회하는 안드로이드 앱입니다.

- **서버 없는 로컬 전용**: 모든 데이터는 단말 로컬 DB(Room/SQLite)에 저장됩니다. 별도 서버나 계정 클라우드가 없습니다.
- **외부 통신은 ISBN 도서정보 조회 1건뿐**: 바코드/ISBN으로 책 정보를 자동 채우기 위한 카카오 책 검색 API 호출에만 네트워크를 씁니다.
- **기본 패키지**: `com.home.library`

## 주요 기능

- **도서 관리** — 등록·수정·검색(제목·저자·출판사·ISBN 부분일치 + 분류/대출가능 필터). 물리 삭제 대신 논리 삭제(`DISCARDED`). 동일 ISBN 재등록 시 수량 증가.
- **바코드 이중 지원** — 하나의 ISBN 처리 파이프라인을 두 입력 소스가 공유합니다.
  - **USB-C HID 하드웨어 스캐너**: OS가 키보드(HID)로 인식하는 스캐너 입력을 숨은 입력 핸들러가 받아 ISBN으로 처리.
  - **내장 카메라**: CameraX + ML Kit(오프라인 번들)로 EAN-13 인식.
  - 스캐너가 없어도 카메라로, 카메라 대신 수기 입력으로 동작합니다.
- **ISBN 도서정보 자동 채움** — 카카오 책 검색 API로 제목·저자·출판사·표지 등을 채웁니다. 오프라인/조회 실패 시 수동 입력 폼으로 자연스럽게 전환됩니다.
- **대출 / 반납 / 연체** — 대출·반납은 단일 트랜잭션으로 원자적 처리. 최대 권수·연체 보유·가용 수량·중복 대출을 검증하고, 앱 시작 시 기한 지난 대출을 연체로 전환합니다. 일괄 반납(다중 선택) 지원.
- **인증 · 세션** — 회원가입/로그인, 5분 무조작 자동 로그아웃(백그라운드 경과 포함), 로그인 5회 실패 시 5분 잠금, 관리자 최초 로그인 시 비밀번호 강제 변경.
- **이력 · 관리자** — 내 대출 현황/이력(도서명·기간 필터, 페이징), 도서별 대출 이력(append-only), 사용자 관리, 전체 대출 현황.

> 도서 목록·검색·상세는 비로그인 포함 누구나 볼 수 있습니다. 로그인은 대출/반납부터 필요하며, 도서 등록·수정·삭제와 관리자 기능은 관리자만 가능합니다.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어 | Kotlin 2.0.21 (K2) |
| 빌드 | AGP 8.11.2, Gradle 8.13, KSP 2.0.21-1.0.28 |
| SDK | compileSdk 36 / targetSdk 36 / minSdk 26 |
| JVM Target | 17 |
| UI | Jetpack Compose (Compose BOM 2024.09.00), Material 3 |
| 아키텍처 | MVVM + Repository, 단방향 데이터 흐름(StateFlow) |
| 로컬 DB | Room 2.6.1 (SQLite) |
| DI | Hilt 2.52 |
| 네비게이션 | Navigation Compose 2.7.7 |
| 카메라 | CameraX 1.3.4 |
| 바코드 | ML Kit Barcode Scanning 17.3.0 (번들 버전, 오프라인) |
| 네트워크 | Retrofit 2.11.0 + OkHttp 4.12.0 + Moshi 1.15.1 |
| 이미지 로딩 | Coil 2.7.0 |
| 비밀번호 | BCrypt (at.favre.lib, cost 12) |
| 외부 API | 카카오 책 검색 API |

## 아키텍처

MVVM + Repository 패턴에 단방향 데이터 흐름을 적용합니다.

```
UI (Compose) ──user action──▶ ViewModel ──▶ Repository ──▶ DAO/Room · Remote API
     ▲                                                          │
     └────────────── StateFlow (UiState) ◀──────────────────────┘
```

- 화면은 `ViewModel`이 노출하는 `StateFlow<UiState>` 하나를 구독하고, 사용자 액션은 ViewModel 함수 호출로 전달합니다.
- ViewModel은 문자열 리소스를 알지 못합니다. 검증 결과는 오류 코드로 반환하고 Composable이 `strings.xml`로 매핑합니다.
- Room `Flow`를 상위로 흘려 데이터 변경 시 화면이 자동 갱신됩니다.
- 세션은 메모리에만 보관합니다(영속 저장 없음).

## 데이터 모델

로컬 SQLite 5개 테이블로 구성됩니다.

| 테이블 | 요약 |
|---|---|
| `USERS` | 사용자. login_id(unique·수정불가), password_hash, role(ADMIN/USER), status(ACTIVE/INACTIVE/LOCKED), 로그인 실패/잠금 상태 |
| `BOOKS` | 도서. isbn(13자리), 제목·저자·출판사·분류·위치, total/available_qty, status(AVAILABLE/LOST/DISCARDED) |
| `LOANS` | 대출. book_id·user_id(FK), loan/due/return_date, status(LOANED/RETURNED/OVERDUE), extend_count |
| `LOAN_HISTORY` | 대출 이력(append-only). action(LOAN/RETURN/EXTEND/FORCE_RETURN), actor_id, memo |
| `APP_CONFIG` | 정책 설정 키-값. 대출 기간·최대 권수·세션 타임아웃·로그인 잠금 정책 등 |

최초 DB 생성 시 관리자 계정(`admin`)과 정책 기본값이 시드로 1회 삽입됩니다.

## 설계 원칙

- **논리 삭제만** — 물리 삭제 금지. 도서는 `DISCARDED`, 사용자는 `INACTIVE`로 전환해 이력 참조 무결성을 지킵니다.
- **enum은 name(문자열)로 저장** — ordinal(Int) 저장 금지. 상수 순서가 바뀌어도 데이터가 깨지지 않습니다.
- **대출/반납은 단일 트랜잭션** — `LOANS` 갱신과 `BOOKS.available_qty` 변경을 원자적으로 처리하고, 가용 수량은 조건부 SQL(`WHERE available_qty > 0` 등)로 방어합니다.
- **이력은 append-only** — `LOAN_HISTORY`는 insert/select만. update/delete를 두지 않습니다.
- **ISBN은 항상 13자리로 저장** — 하이픈/공백 제거 후, 유효한 ISBN-10은 ISBN-13으로 자동 변환하여 저장합니다(스캔·수기·API 경로 공유).
- **비밀번호는 BCrypt(cost 12) 해시** — 평문 저장·로그 금지.
- **세션은 메모리 전용** — SharedPreferences 등에 평문 저장하지 않습니다.
- **마이그레이션 필수** — `fallbackToDestructiveMigration()` 금지. 스키마 변경 시 Migration 작성 + 버전 증가 + 스키마 JSON 커밋.

## 빌드 방법

```bash
# 디버그 빌드
./gradlew clean :app:assembleDebug

# 연결된 기기/에뮬레이터 확인
adb devices
```

### 카카오 API 키 설정 (필수)

ISBN 도서정보 조회 기능을 쓰려면 카카오 REST API 키가 필요합니다. 프로젝트 루트의 `local.properties`에 아래 항목을 추가하세요.

```properties
KAKAO_REST_API_KEY=여기에_본인_카카오_REST_API_키
```

- 키는 [카카오 개발자 콘솔](https://developers.kakao.com/)에서 애플리케이션을 만들어 발급받습니다(REST API 키).
- `local.properties`는 `.gitignore`에 포함되어 저장소에 커밋되지 않습니다. **키를 소스 코드나 저장소에 직접 넣지 마세요.**
- 키는 빌드 시 `BuildConfig`로 주입됩니다. **키를 바꾸면 재빌드가 필요합니다.**
- 키가 없어도 빌드는 통과하지만, 조회 시 인증 오류가 나며 수동 입력 폼으로 전환됩니다.

## 요건 목록

| 영역 | 요건 |
|---|---|
| 인증 (AUTH) | 회원가입, 로그인, 5분 자동 로그아웃, 로그인 실패 잠금, 만료 사전 안내 |
| 도서 (BOOK) | 등록, 바코드 스캔, ISBN API 조회, 수정, 논리 삭제, 검색, 대출 이력, 분실 처리 |
| 대출 (LOAN) | 대출, 반납, 연체 처리, 바코드 반납, 연장, 강제 반납 |
| 이력 (HIST) | 내 대출 현황/이력, 도서별 이력, 통계 |
| 사용자 (USER) | 사용자 등록/수정/비활성화, 관리자 관리 |
| 공통 (CMN) | 바코드 이중 입력, 정책 설정, 데이터 백업/복원 |

## 진행 상황

**완료**

- 1단계 — 프로젝트 골격 + Room 스키마 + 시드
- 2단계 — 인증(가입/로그인/5분 자동 로그아웃)
- 3단계 — 도서 CRUD + 검색
- 4단계 — 바코드 스캔(USB-C HID 스캐너 + 카메라) + ISBN API
- 5단계 — 대출/반납/연체
- 6단계 — 이력 조회 + 관리자 기능
- 실사용 피드백 개선 묶음 A — 도서 건수 표시, 로그인 사용자 정보, 아이디 정규화, 대출 상태 구분, 반납 확인, 일괄 반납(+ 검색창 지우기·서브 화면 홈 버튼)

**예정**

- 묶음 B — 목록 탐색성(스크롤바 + 초성 인덱스)
- 묶음 C — 도서 정보 품질(해외 원서 API 폴백, 분류 마스터화)
- 묶음 D — 계정 운영(비밀번호 초기화, 회원증 바코드)
- 7단계 — 선택 요건 및 마감(연장·강제반납·정책설정·백업/복원·통계·분실처리, ProGuard/릴리스, 다크모드)
