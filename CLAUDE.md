# CLAUDE.md — 가정용 도서 대출 관리 앱

이 파일은 Claude Code가 매 세션 시작 시 읽는 프로젝트 지침서다.
아래 규칙과 설계 원칙을 **반드시 준수**하며 개발한다.

-----

## 0. 에이전트 작업 규칙 (최우선)

1. **결정사항은 반드시 사용자에게 먼저 확인한다.**
- 다음에 해당하면 코드를 작성/변경하기 전에 멈추고 사용자에게 질문한다:
  - 새 라이브러리/의존성 추가
  - DB 스키마 변경 (Entity/컬럼/테이블 추가·수정·삭제)
  - 요건정의서에 없는 기능이나 동작을 새로 정의해야 할 때
  - 화면 흐름(Navigation), 아키텍처 구조를 바꿀 때
  - 여러 구현 방식이 가능해 트레이드오프가 있는 경우
- “확인 없이 알아서 진행”은 금지. 애매하면 질문한다.
1. **한 번에 한 단계씩** 진행한다. 단계를 건너뛰지 않는다 (아래 6장 단계 계획 참조).
1. 각 단계 종료 시 **DoD(완료 조건)** 를 코드로 충족했는지 스스로 점검하고, 사용자에게 실기기 검증을 요청한다.
1. 매 단계 완료 후 **git 커밋**한다 (아래 5장 커밋 규칙).
1. 빌드가 깨진 상태로 단계를 끝내지 않는다. 항상 `./gradlew assembleDebug` 성공 상태로 마감한다.

-----

## 1. 프로젝트 개요

- **목적**: 가정 내 보유 도서를 등록·관리하고, 가족 구성원이 대출·반납하며 이력을 조회하는 안드로이드 앱.
- **구조**: 앱 단독 구동. 서버 없음. 모든 데이터는 단말 로컬 DB(Room/SQLite)에 저장.
- **외부 통신**: ISBN 도서정보 조회 API 호출에만 한정.
- **기본 패키지**: `com.home.library`

## 2. 기술 스택

|구분                             |기술                                      |
|-------------------------------|----------------------------------------|
|언어                             |Kotlin 2.0.21 (K2)                      |
|UI                             |Jetpack Compose                         |
|아키텍처                           |MVVM + Repository, 단방향 데이터 흐름(StateFlow)|
|로컬 DB                          |Room (SQLite)                           |
|DI                             |Hilt                                    |
|네비게이션                          |Navigation Compose                      |
|카메라                            |CameraX                                 |
|바코드 인식                         |ML Kit Barcode Scanning (번들 버전, 오프라인 동작)|
|네트워크                           |Retrofit + OkHttp + Moshi               |
|외부 API                         |카카오 책 검색 API                            |
|비밀번호                           |BCrypt (cost 12)                        |
|minSdk / targetSdk / compileSdk|26 / 36 / 36                            |
|JVM Target                     |17                                      |

## 3. 설계 원칙 (반드시 지킬 것)

1. **enum은 name(String)으로 저장한다.** ordinal(Int) 저장 금지. enum 상수 순서가 바뀌어도 데이터가 깨지지 않게 하기 위함. (`@TypeConverters` 사용)
1. **물리 삭제 금지, 논리 삭제만 한다.** 도서는 `status = DISCARDED`, 사용자는 `status = INACTIVE`. 이력 참조 무결성 보장.
1. **`fallbackToDestructiveMigration()` 금지.** 개발 중에도 사용하지 않는다. 스키마 변경 시 반드시 Migration 작성 + version 증가 + `app/schemas/*.json` 커밋.
1. **대출/반납은 단일 트랜잭션.** `LOANS` 등록/수정과 `BOOKS.available_qty` 갱신은 `@Transaction`으로 원자적 처리.
1. **가용수량은 조건부 SQL로 방어한다.** `decreaseAvailable`은 `WHERE available_qty > 0`, `increaseAvailable`은 `WHERE available_qty < total_qty`. 반환값 0이면 실패로 판정하고 롤백.
1. **`LoanHistoryDao`는 append-only.** insert/select만 정의. update/delete 메서드를 만들지 않는다.
1. **비밀번호는 BCrypt cost 12로 해시.** 평문 저장·로그 금지. 해싱은 `Dispatchers.Default`에서.
1. **API 키는 하드코딩 금지.** `local.properties` → `BuildConfig`로 주입. `local.properties`는 `.gitignore`에 포함.
1. **세션 정보는 메모리에만.** SharedPreferences 등에 평문 저장 금지.
1. **문자열 하드코딩 금지.** `strings.xml`로 분리. 파일 인코딩 UTF-8.
1. **ISBN은 DB에 항상 13자리로 저장.** 하이픈/공백 제거 후, 유효한 ISBN-10(10자리, 마지막 자리 `X`=10)은 ISBN-13(978 접두)으로 자동 변환하여 저장한다. 변환·검증은 도메인 계층(`BookFormValidator.normalizeIsbn`/`validateIsbn`)에 두어 수기 입력·스캔·API 경로가 공유한다. 스캔 매칭·중복 판정 일관성 유지.

## 4. 데이터 모델 (확정)

- **USERS**: user_id(PK), login_id(unique, 수정불가), password_hash, name, phone, role(ROLE_ADMIN/ROLE_USER), status(ACTIVE/INACTIVE/LOCKED), fail_count, locked_until, pwd_change_required, created_at
- **BOOKS**: book_id(PK), isbn(nullable), title, author, publisher, pub_date, cover_url, category, location, total_qty, available_qty, status(AVAILABLE/LOST/DISCARDED), created_at, updated_at
- **LOANS**: loan_id(PK), book_id(FK), user_id(FK), loan_date, due_date, return_date(nullable), status(LOANED/RETURNED/OVERDUE), extend_count
- **LOAN_HISTORY** (append-only): history_id(PK), loan_id(FK), action(LOAN/RETURN/EXTEND/FORCE_RETURN), action_at, actor_id(FK), memo
- **APP_CONFIG**: config_key(PK), config_value, description
  - 기본값: loan.period.days=14, loan.max.count=5, loan.extend.days=7, loan.extend.max=1, session.timeout.minutes=5, login.fail.limit=5, login.lock.minutes=5

### 시드 데이터 (최초 DB 생성 시 1회)

- 관리자: login_id=`admin`, password=`admin1234`(BCrypt 해시 저장), role=ROLE_ADMIN, pwd_change_required=1
- APP_CONFIG 7건 기본값

## 5. Git / 형상 관리

- 원격: GitHub 저장소로 관리.
- `local.properties`, `.idea/`, `build/`, `*.apk`, `*.keystore`는 `.gitignore`에 포함.
- **각 단계 완료 시 커밋**. 커밋 메시지 규칙:
  - `feat(db): Room 스키마 및 시드 데이터 구성`
  - `feat(auth): 회원가입/로그인 및 5분 자동 로그아웃`
  - `feat(book): 도서 CRUD 및 검색`
  - `feat(scan): 바코드 스캔(스캐너+카메라) 및 ISBN API 연동`
  - `feat(loan): 대출/반납 트랜잭션 및 연체 처리`
  - `feat(admin): 이력 조회 및 관리자 기능`

## 6. 단계별 개발 계획

각 단계는 독립적으로 빌드·실행 가능한 상태로 종료한다. **현재 1단계 완료 상태에서 2단계부터 시작.**

### 1단계 — 프로젝트 골격 + Room 스키마 + 시드 (완료 기준)

- Entity 5, DAO 5, AppDatabase + SeedCallback, DatabaseModule(Hilt), PasswordHasher, ConfigKeys, Enums, Converters
- DoD: 5개 테이블 생성, admin 시드 1건(BCrypt 해시), app_config 7건, `app/schemas/1.json` 생성

### 2단계 — 인증 (가입/로그인/5분 자동 로그아웃)

- 커버 요건: AUTH-001~004, SCR-02, SCR-03 (AUTH-006은 7단계로 이관)
- AuthRepository(signUp/login/logout), SessionManager(메모리 보관), SessionTimeoutHandler(10초 폴링), MainActivity.dispatchTouchEvent/dispatchKeyEvent로 활동시각 갱신
- 백그라운드 진입 중에도 타이머 경과, 복귀 시 즉시 만료 판정
- 로그인 5회 실패 시 5분 잠금, admin 최초 로그인 시 비밀번호 변경 강제
- 세션 타임아웃 값은 `AppConfigDao.getValue("session.timeout.minutes")`로 읽는다
- DoD: 가입→로그인→5분 무조작 자동 로그아웃 / 터치 시 리셋 / 백그라운드 6분 후 복귀 시 즉시 로그아웃 / 5회 실패 잠금 / admin 비번 변경 강제

### 3단계 — 도서 CRUD + 검색

- 커버 요건: BOOK-001, BOOK-004~008, SCR-04, SCR-05, SCR-11
- BookRepository(등록/수정/논리삭제/검색), 중복 ISBN 시 수량 증가(확인 다이얼로그 필수), 삭제 전 대출중 검증
- 검색(BOOK-07): 제목·저자·출판사·ISBN 부분일치 + 분류 필터 + 대출가능 필터, 입력 디바운스 300ms. `BookDao.search`를 Flow 반환으로 확장.
- **접근 모델**: 앱 메인화면 = 도서 목록(start destination). 목록/검색/상세는 비로그인 포함 누구나 접근. 로그인 요구는 5단계 대출/반납부터. 상단바에서 로그인/로그아웃. 세션 만료 시 로그인 화면이 아니라 도서 목록으로 복귀.
- 권한 가드: 등록/수정/삭제(FAB 포함)는 관리자만. UI 숨김 + ViewModel 거부 이중 방어. 표지는 placeholder만(Coil은 4단계).
- DoD: 30건 등록 후 부분검색 / 동일 ISBN 재등록 시 확인 후 수량 증가 / 일반 사용자·비로그인 등록·수정 차단 / 삭제 시 DISCARDED 논리삭제

### 4단계 — 바코드 스캔(이중 지원) + ISBN API 연동

- 커버 요건: BOOK-002, BOOK-003, CMN-001, CMN-002, SCR-06
- **[중요] 바코드 입력은 두 소스를 모두 지원한다:**
  - **(A) USB-C 하드웨어 스캐너**: OS가 HID(키보드)로 인식. 스캔 시 ISBN 숫자가 타이핑되고 보통 Enter로 끝남. 포커스를 가진 숨은 입력 핸들러가 이 입력을 받아 ISBN-13으로 처리.
  - **(B) 폰 내장 카메라**: CameraX + ML Kit으로 EAN-13 촬영 인식.
  - 두 소스는 **동일한 결과(ISBN-13 문자열)** 를 공통 콜백으로 넘긴다. 입력 소스만 다르고 이후 처리(로컬 조회→API→폼 자동채움)는 완전히 공유한다.
  - **스캐너가 연결되지 않은 환경에서도 카메라로 동작**해야 한다. 스캐너 연결 여부를 감지하거나, 사용자가 입력 방식을 선택할 수 있게 한다.
- 카카오 API: `GET /v3/search/book?target=isbn&query={isbn}&size=1`, 헤더 `Authorization: KakaoAK {key}`, 타임아웃 연결3s/읽기5s, 재시도 1회
- 동일 ISBN 중복 응답 대비 `documents[0]`만 사용. 결과 0건/401/429 → 수동 입력 폼 전환
- 표지 이미지 로딩(Coil) + INTERNET 권한 추가(3단계에서 이관). API로 채워진 cover_url을 목록/상세에서 표시.
- DoD: 하드웨어 스캐너로 ISBN 입력→자동채움 / 카메라로 촬영 인식→자동채움 / 스캐너 미연결 시 카메라 동작 / 오프라인 시 수동 폼 전환 / 보유 도서 스캔 시 수량 증가

### 5단계 — 대출/반납/연체

- 커버 요건: LOAN-001~003, LOAN-006, SCR-07, SCR-08
- LoanPolicy(AppConfig 기반), LoanValidator(최대권수/연체보유/가용수량 3대 검증)
- loan()/returnBook() 모두 @Transaction, decreaseAvailable 반환 0이면 롤백
- OverdueUpdater: 앱 시작 시 markOverdue(now) 1회
- 대출/반납 트랜잭션 중에는 자동 로그아웃 유예
- 대출/반납 화면에서도 4단계 바코드 공통 컴포넌트(스캐너+카메라) 재사용
- DoD: 대출 시 원자적 반영 / 가용0 거부 / 6권째 거부 / due_date 과거 조작 후 재시작 시 OVERDUE 전환 및 신규대출 차단

### 6단계 — 이력 조회 + 관리자 기능

- 커버 요건: HIST-001~004, USER-001~004, SCR-09, SCR-10, SCR-12, SCR-13
- LoanWithBook Relation, MyLoanScreen(현황+이력 탭), AdminHomeScreen, AdminLoanStatusScreen
- UserRepository: 수정 시 loginId 제외, 비밀번호는 입력 시에만 재해시, 대출중이면 비활성화 차단
- DoD: 4종 행위 append-only 기록 / 비번 공백 시 기존 해시 유지 / 대출중 사용자 비활성화 차단 / 일반 사용자 관리자 화면 진입 불가

### 7단계 — 선택 요건 및 마감

- LOAN-004(바코드 반납), LOAN-005(연장), LOAN-007(강제반납), CMN-003(정책설정), CMN-004(백업/복원), HIST-005(통계), AUTH-006(만료 30초 전 안내), BOOK-009(분실처리)
- ProGuard 규칙(Room/Hilt/Moshi/ML Kit), release 빌드 검증, 다크모드, 문자열 하드코딩 제거

## 7. 빌드 / 실행 명령

```bash
# 빌드
./gradlew clean :app:assembleDebug

# 연결된 기기/에뮬레이터 확인
adb devices

# 설치 후 로그 확인
adb logcat | grep -i "com.home.library"
```

## 8. 검증 시 주의

- Room은 lazy 초기화. DAO를 최소 1회 호출해야 SeedCallback.onCreate가 실행된다.
- 시드 재검증이 필요하면 앱 데이터 삭제 후 재실행(onCreate는 DB 최초 생성 시 1회만).
- 화면 표시·바코드 물리 스캔·카메라 권한 승인 등 **육안/물리 확인은 사용자(사람)가 수행**한다. 에이전트는 코드·빌드·로그 분석까지 담당.

-----

## 9. 실사용 피드백 개선 백로그

6단계 완료 후 실사용에서 도출한 개선 요건 11건. **7단계와는 별개 트랙.** 한 번에 진행하면 검증이 어려우므로 4개 묶음(A~D)으로 분할한다. 각 묶음은 **독립적으로 빌드·검증 가능한 단위**로 종료한다.

> 상태: **미착수(기록만).** 착수 시 묶음 단위로 계획을 먼저 제시하고 확인받는다.

### 묶음 A — UI·문구 중심 (스키마 불변, 우선 진행)

- **A-1 도서 목록 전체 등록 건수 표시** — "도서 목록 (전체 42건)". DISCARDED 제외.
- **A-2 도서 목록 로그인 사용자 정보** — "홍길동님 · 대출 가능 3건 · 대출 중 2건". 대출 가능 = `loan.max.count`(AppConfig) − 활성 대출 수. **하드코딩 금지.**
- **A-4 로그인 아이디 trim** — 앞뒤 공백 제거 후 인증. 가입·관리자 사용자 등록 경로도 동일 적용(공백 포함 아이디가 저장되면 영영 로그인 불가). 기존 데이터에 공백 계정이 있는지 점검.
- **A-5 대출 화면 3건**
  - 대출 확인 화면에 대출자의 대출 가능/대출 중 건수 표시.
  - 반납 예정일 명시 (예: "반납 예정일: 2026.07.31 (14일)").
  - 대출 완료 후 도서 상세 복귀 시, 본인 대출 중이면 "대출하기" 대신 "대출 중" 표시. `countActiveByUserAndBook` 활용. **"대출 중"(본인) vs "대출 불가"(가용 0, 타인 대출) 구분.**
- **A-6 반납 확인 다이얼로그** — "『제목』을 반납하시겠습니까?".
- **A-11 일괄 반납** — 체크박스 다중 선택 + 전체 선택/해제. ⚠️ **각 반납을 개별 트랜잭션으로 처리**(큰 트랜잭션으로 묶으면 1건 실패에 전부 롤백). 결과 요약 표시("2건 성공, 1건 실패").

### 묶음 B — 목록 탐색성

- **B-3 스크롤바(fast scroll) + 초성 인덱스 스크롤러**
  - ⚠️ Compose는 스크롤바·SectionIndexer 기본 제공 없음 → **전부 커스텀 구현.**
  - 한글 초성 추출: `(코드 - 0xAC00) / 588` 유니코드 계산.
  - ⚠️ SQLite `ORDER BY title`은 유니코드 순 → 숫자 → 영문 → 한글 순이 됨. **한글 우선 정렬을 원하면 별도 정렬 키 필요.**
  - 인덱스: ㄱ~ㅎ + A~Z + #(숫자·기타).

### 묶음 C — 도서 정보 품질

- **C-7 해외 원서 조회 실패** — 카카오 책 검색은 다음 책 서비스 기반이라 국내 유통 도서 위주. 해외 원서 미색인.
  - 해결: **API 폴백 체인** — 카카오 실패 → Google Books API 또는 Open Library (ISBN 조회 지원). Retrofit 기존 사용, 신규 의존성 불필요.
  - ⚠️ 착수 시 **각 API의 현재 이용 조건·키 필요 여부를 반드시 확인할 것.**
- **C-8 분류·위치 자유 입력 → 체계화**
  - 분류: **마스터 테이블 + 드롭다운 선택.** 관리자 화면에서 분류 관리(추가/수정/삭제). 값 통일로 필터 정상 작동.
  - 위치: **자유 입력 유지 + 기존 값 자동완성** (집마다 다르고 자주 바뀌어 마스터화는 부적합).
  - 🔴 **프로젝트 최초의 스키마 변경.** 3장 원칙 3에 따라 **Migration 작성 필수, `fallbackToDestructiveMigration` 절대 금지**, version 증가 + `schemas/*.json` 커밋. **기존 자유 입력 분류 데이터의 이관 방안도 함께 설계.**

### 묶음 D — 계정 운영

- **D-9 비밀번호 초기화** — 관리자가 임시 비번 지정 + `pwd_change_required=true` → 사용자 최초 로그인 시 강제 변경. **2단계에서 검증된 흐름 그대로 재사용.** 사용자 수정 화면에 버튼 추가. (자가 초기화는 로컬 앱이라 이메일·SMS 수단이 없어 불가.)
- **D-10 회원증 바코드 자동 로그인**
  - ⚠️ **보안 트레이드오프 인지**: 2단계의 BCrypt·5분 자동 로그아웃·5회 실패 잠금을 바코드가 우회함. 회원증을 사진 찍으면 도용 가능. 가족용이라 위협 수준은 낮으나 **아래 완화책 필수.**
  - `member_code` 컬럼 신규 (랜덤 토큰). **`user_id`는 추측 가능하므로 사용 금지** → 스키마 변경 + Migration 필요.
  - **Code128** 형식 사용. ⚠️ EAN-13은 13자리 숫자라 ISBN과 구분 불가 → **`M` 접두사로 명확히 구분** (예: `M7K2X9...`).
  - **권한 제한**: 회원증 스캔은 **대출/반납 전용 경량 세션**만 부여. 관리자 기능·비밀번호 변경은 비밀번호 필수 (admin 회원증 하나로 전부 뚫리는 것 방지).
  - 분실 시 재발급: `member_code` 재생성 → 기존 카드 자동 무효화.
  - 바코드 생성 라이브러리(ZXing 등) **신규 의존성 필요.** 화면 표시 → 인쇄.
  - ⚠️ **USB-C 스캐너 실물이 있어야 검증 가능** (현재 분실 상태).

### ⚠️ 순서·충돌 주의 (반드시 기록)

- **A-6(반납 확인)·A-11(일괄 반납)·7단계 LOAN-004(바코드 반납)가 모두 `ReturnScreen`을 건드림.** 순서를 잘못 잡으면 리워크 발생. **착수 전 순서 확정 필요.**
- **D-10(회원증)과 7단계 LOAN-004는 둘 다 스캐너 실물 필요.** 스캐너 확보 시 함께 검증하는 것이 효율적.
- **C-8, D-10은 스키마 변경.** 두 건을 한 번의 Migration으로 묶을지 별도로 갈지 **착수 시 판단.**
- **묶음 A → B → C → D 순 진행 예정. 7단계와의 선후 관계는 미정.**

-----

## 진행 상태

_최종 갱신: 2026-07-17_

### 🧪 실기기 테스트 환경

- 단말: 안드로이드 태블릿, Android 14.
- 연결: 무선 디버깅(adb over Wi-Fi).
- 검증 도구: Android Studio Database Inspector(시드/DB 상태 육안 확인).

### ✅ 1단계 — 프로젝트 골격 + Room 스키마 + 시드 (완료)

- Entity 5(USERS/BOOKS/LOANS/LOAN_HISTORY/APP_CONFIG), DAO 5, `AppDatabase`(v1), `SeedCallback`, `DatabaseModule`(Hilt), `PasswordHasher`(BCrypt cost 12), `ConfigKeys`, Enums, `Converters`(enum↔name) 구현.
- `BookDao.decreaseAvailable`(`WHERE available_qty > 0`) / `increaseAvailable`(`WHERE available_qty < total_qty`) 조건부 SQL 반영. `LoanHistoryDao`는 append-only.
- 빌드 검증: `./gradlew assembleDebug` **BUILD SUCCESSFUL**. `app/schemas/.../1.json` 생성(5개 테이블 확인).
- 빌드 환경 확정: Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / AGP 8.11.2 / Gradle 8.13 / compileSdk·targetSdk 36 / minSdk 26 / JVM 17. Room 2.6.1, Hilt 2.52, at.favre.lib:bcrypt 0.10.2.
- 형상관리: git init(`main`) → 커밋 2건 → GitHub push 완료. 원격 `https://github.com/chp320/HomeLibrary.git`.
  - `4f47618 feat(db): Room 스키마 및 시드 데이터 구성`
  - `9a5e926 chore: LF 줄바꿈 정규화(.gitattributes)`
- 시드 런타임 검증은 2단계에서 완료(아래 참조).

### ✅ 2단계 — 인증 (가입/로그인/5분 자동 로그아웃) (완료)

- 커버 요건: AUTH-001~004, SCR-02, SCR-03 (AUTH-006은 7단계로 이관).
- 세션: `SessionManager`(메모리 StateFlow 전용, 영속 저장 없음), `SessionTimeoutHandler`(10초 폴링 + 복귀 시 wall-clock 즉시 만료 판정). 타임아웃 값은 `session.timeout.minutes`를 AppConfig에서 조회.
- 도메인: `AuthRepository`(signUp/login/changePassword). 로그인 처리 순서 = status 판정 → 잠금 판정 → 해시 검증 → 성공 시 fail_count/lock 리셋 → pwd_change_required 라우팅. `login.fail.limit`/`login.lock.minutes`도 AppConfig에서 조회(하드코딩 없음).
- 검증: `AuthValidator`(표준 규칙 — 아이디 영소문자+숫자 4~20자, 비번 8~64자·영문+숫자 조합, 이름 20자 이하).
- UI: `AppNavHost`(세션 상태로 로그인/로그아웃 경계 구동) + `LoginScreen`·`SignUpScreen`·`ChangePasswordScreen`(강제 변경, BackHandler 차단)·`HomeScreen`(임시 착지) + 각 ViewModel(Hilt). 문자열 전부 `strings.xml` 분리.
- `MainActivity`: `dispatchTouchEvent`+`dispatchKeyEvent` 오버라이드로 활동시각 갱신(4단계 USB-C HID 스캐너 입력 대비), onStart/onResume/onStop 생명주기 연동.
- 새 의존성: `androidx.navigation:navigation-compose` 2.7.7 추가.
- APP_CONFIG 시드에 `login.lock.minutes=5` 추가(스키마 불변 → 마이그레이션/버전업 없음). 총 7건.
- 빌드 검증: `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**.
- 실기기 검증(태블릿 Android 14, 무선 디버깅): 가입/로그인, 5분 자동 로그아웃, 5회 실패 잠금, admin 최초 로그인 비밀번호 강제 변경 정상. Database Inspector로 시드(admin 1건·BCrypt 해시, app_config 7건) 확인. DoD 전부 통과.

### ✅ 3단계 — 도서 CRUD + 검색 (완료)

- 커버 요건: BOOK-001, BOOK-004~008, SCR-04, SCR-05, SCR-11.
- 데이터: `BookDao` 확장 — `search`(제목·저자·출판사·ISBN 부분일치 + 분류/대출가능 필터, **Flow** 반환), `getFlowById`, `getCategories`, `discard`, `addQuantity`. DAO 메서드 추가라 스키마 불변(마이그레이션 없음).
- 도메인: `BookRepository`(등록/중복수량/수정/논리삭제/검색) + `BookForm`, `BookFormValidator`.
- 검증(도메인 계층, 4단계 API와 공용): 제목 공백불가·200자 / 수량 1~9999 / **ISBN 하이픈제거 후 13자리+EAN-13 체크디지트** / **출판일 YYYY-MM-DD + LocalDate STRICT 실존검증** / 선택필드 100자. ISBN은 저장·중복조회 모두 `normalizeIsbn` 정규화(수기 하이픈 ↔ 스캔값 매칭).
- UI: `BookListScreen`(검색 **300ms 디바운스** + 분류/대출가능 필터칩 + 상단바 로그인·로그아웃 + 관리자 FAB), `BookDetailScreen`(관리자 수정/삭제, 대출중 삭제 차단), `BookEditScreen`(등록·수정 공용, ISBN 중복 시 확인 다이얼로그 후 수량만 증가). 각 ViewModel(Hilt).
- **접근 모델 변경**: 도서 목록 = start destination(비로그인 포함 누구나). 로그인/로그아웃은 상단바. 세션 만료 시 로그인 화면이 아니라 도서 목록으로 복귀. 로그인 요구는 5단계부터. `HomeScreen`/`HomeViewModel`(2단계 임시) 제거.
- 권한 가드: 등록/수정/삭제·FAB는 관리자만(UI 숨김 + ViewModel 거부 이중 방어).
- 신규 의존성 없음(Coil은 4단계로 이관, 표지는 미표시).
- 빌드 검증: `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**.
- 실기기 검증(태블릿 Android 14): 30건 등록·부분검색, 분류/대출가능 필터, 동일 ISBN 재등록 확인 다이얼로그 후 수량증가, ISBN 없는 책 개별 유지, 일반/비로그인 조회전용, 논리삭제(DISCARDED), 세션 만료 시 목록 복귀. 검증 강화(pub_date/ISBN/길이·수량 상한)까지 DoD 전부 통과.

### ✅ 4단계 — 바코드 스캔(이중 지원) + ISBN API (완료, 단 HID 스캐너 실물 테스트 보류)

- 커버 요건: BOOK-002, BOOK-003, CMN-001, CMN-002, SCR-06.
- 이중 입력 수렴: HID 스캐너·수동 키보드·카메라 3소스를 `ScanViewModel.dispatch()` 한 곳으로 수렴 → 정규화+13자리+체크디지트 통과분만 방출. ISBN 필드는 `singleLine`+`ImeAction.Done`+개행감지+조회버튼 3중 커버, 하이픈 허용(Number 미제한), 처리 후 비우기+재포커스(연속 스캔).
- 스캔 진입: 도서 목록 상단바 "스캔 등록"(관리자만). `MainActivity.dispatchKeyEvent`는 `super` 반환이라 스캐너 키 입력이 필드에 도달(소비 안 함).
- 네트워크: `KakaoBookApi`+DTO+`KakaoBookMapper`(datetime ISO8601→YYYY-MM-DD, ISBN10/13 공백구분 중 13자리 선택, author 배열 join)+`NetworkModule`(연결3s/읽기5s, `Authorization: KakaoAK` 인터셉터 주입)+`BookLookupRepository`(재시도1회, 0건/401/429/네트워크 분기).
- 파이프라인 단일화: `BookEditViewModel`이 `isbn` 인자로 로컬조회→API 실행. 로컬 존재 시 3단계 중복 다이얼로그 재사용(스캔은 +1 고정, 현재수량 표시), 미존재 시 API 자동채움, 실패 시 ISBN만 채운 수동 폼+안내. `addQuantity`에 9999 상한. 관리자 가드 재확인(인자 경로 우회 차단). API는 `viewModelScope`라 이탈 시 취소.
- 신규 의존성: CameraX 1.3.4, ML Kit barcode-scanning 17.3.0(번들·오프라인), Retrofit+converter-moshi 2.11.0, Moshi 1.15.1(KSP codegen), Coil 2.7.0(표지), okhttp logging-interceptor 4.12.0. 권한: `INTERNET`, `CAMERA`(선택 기능). `buildConfig=true`.
- API 키: `local.properties`의 `KAKAO_REST_API_KEY`(gitignore) → `BuildConfig`로 주입. 키 없으면 빈 문자열(빌드는 통과, 런타임 401). **키 변경 시 재빌드 필요.**
- 진단: OkHttp `HttpLoggingInterceptor`는 **debug 빌드 전용**(`if(BuildConfig.DEBUG)`) + `redactHeader("Authorization")`로 키 마스킹. catch 블록 예외는 로그화(무음 삼킴 제거).
- 빌드 검증: `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**.
- 실기기 검증(태블릿 Android 14): 수동 입력, 카메라 EAN-13 스캔, 오프라인 폴백, 보유도서 +1 수량증가 통과.
- **⚠️ 미완(보류): USB-C HID 하드웨어 스캐너 실물 테스트.** 스캐너 미확보로 물리 스캔 경로만 미검증. 배선(자동포커스 필드가 HID 키입력 수신)은 코드상 완료. 스캐너 확보 시 실물 검증 필요.

### ✅ 5단계 — 대출/반납/연체 (완료)

- 커버 요건: LOAN-001~003, LOAN-006, SCR-07, SCR-08. 자가대출 모델(대출자=세션 본인, actor_id=본인).
- 데이터: `LoanDao` 확장(`countActiveByUserAndBook`(신규)·`countOverdueByUser`·`markOverdue`·`getActiveLoansByUser` JOIN Flow) + `ActiveLoanView` 프로젝션.
- 도메인: `LoanPolicy`(AppConfig), `LoanValidator` **4대 검증**(중복대출→연체보유→최대권수→가용수량), `LoanRepository`(`db.withTransaction` 원자적).
  - `loan()`: 검증 → `decreaseAvailable`(0이면 롤백) → LOANS/LOAN_HISTORY.
  - `returnBook()`: `increaseAvailable` → LOANS(RETURNED)/HISTORY. **수량증가 0(데이터 불일치)이면 롤백 대신 로그(`Log.w`)만 남기고 반납 진행** — 사용자를 가두지 않기 위함.
- `OverdueUpdater`(멱등)를 `MainActivity.onStart`에서 호출(onCreate 아님 → 백그라운드 복귀 시에도 갱신).
- 자동 로그아웃 유예: `SessionManager.withCriticalSection`(try/finally) + `SessionTimeoutHandler`가 크리티컬 섹션 중 만료 스킵.
- UI: `IsbnScanInput`(4단계 스캔을 재사용 컴포넌트로 추출, ISBN 콜백까지만) → 등록/대출 공유, `ScanViewModel` 제거. `LoanScreen`(로컬조회만·API 금지, 미등록/가용0/중복/연체/최대권수 케이스별 안내), `ReturnScreen`(내 활성대출 목록·D-n/연체 색상구분·Flow 자동갱신, 6단계 재사용 구조).
- 접근: 비로그인도 "대출하기" 노출 → 로그인 유도 → **로그인 성공 시 LOGIN만 pop해 대출 화면으로 복귀**(AppNavHost 변경). LOAN/RETURN은 로그인 필요(만료 시 목록 복귀).
- 신규 의존성/스키마 변경 없음(마이그레이션 불필요).
- 빌드 검증: `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**.
- 실기기 검증(태블릿 Android 14): 대출 원자반영, 가용0 거부, 6권째 거부, 동일도서 중복 거부, due_date 조작 후 재시작 OVERDUE 전환·신규대출 차단, 반납, 로그인 후 대출 복귀. DoD 전부 통과.
- **⚠️ 4단계 USB-C HID 스캐너 실물 테스트는 여전히 보류 중**(스캐너 미확보). 배선은 코드상 완료.

### 🔧 후속 수정 (5단계 이후)

- **ISBN-10 입력 지원**: 옛날 책(1970~2006, ISBN-10만 보유) 등록 불가 결함 수정. 유효 ISBN-10을 ISBN-13으로 자동 변환 저장(설계 원칙 11). `X`(=10) 체크디지트 처리 포함. 카카오 조회는 13자리 0건 && 978 접두 시 원본 ISBN-10로 1회 폴백. 변환 로직은 `BookFormValidatorTest` 유닛 테스트로 고정(`097522980X→9780975229804` 등).

### ✅ 6단계 — 이력 조회 + 관리자 기능 (완료)

- 커버 요건: HIST-001~004, USER-001~004, SCR-09/10/12/13. + BOOK-008(도서 상세 대출 이력, 3단계 미완분 완료).
- 데이터(스키마 불변): 프로젝션 `LoanHistoryRecord`·`AdminLoanView`·`BookLoanHistoryView`·`UserListItem`. `LoanDao`(내 이력 도서명·기간 필터+LIMIT/OFFSET 페이징, 전체 활성대출 JOIN), `LoanHistoryDao.getByBook`(append-only select), `UserDao.searchUsers`(검색+권한/상태 필터+대출중 권수 서브쿼리). ※ `action`은 SQLite 예약어라 별칭 없이 컬럼명 그대로 사용.
- 도메인: `UserRepository`(생성=임시비번+pwd_change_required, 수정=loginId 불변·비번 공백 시 기존 해시 유지, 비활성화=대출중 차단). 검증은 `AuthValidator` 재사용. 강제변경 재사용 차단은 `changePassword`가 현재 해시와 대조(일반화됨, 무수정).
- UI: `MyLoanScreen`(현황/이력 탭, 현황→반납하기 경로, 이력 필터·더보기 페이징), `AdminHomeScreen`, `UserListScreen`(검색·필터·대출중 권수), `UserEditScreen`, `AdminLoanStatusScreen`. `BookDetailScreen`에 대출 이력 추가. 상단바 오버플로 메뉴(⋮).
- 권한 가드: 관리자 화면 진입점 숨김 + ViewModel isAdmin 이중 방어.
- 빌드 검증: `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**. 실기기 DoD 검증 후 커밋.

### ✅ UX 개선 7가지 (완료, style 커밋 `c30a066`)

1. 앱 아이콘: 파란 배경 + 흰 책 벡터(적응형, 안전영역 준수).
2. 상단 메뉴 반응형: ≥600dp 텍스트 버튼 나열, 미만 오버플로(⋮). 항목 단일 정의(`BookListScreen.TopAction`).
3. 도서 표지: 공용 `BookCover`(AsyncImage + 📖 placeholder) — 상세(120×170)·목록 썸네일(48×64).
4. 뒤로가기: 공용 `BackButton`(`Icons.AutoMirrored.Filled.ArrowBack`, contentDescription 유지) 10개 화면. `material-icons-core`(BOM 관리) 추가.
5. 이력 기간 필터: `DateRangePicker`(시스템 시간대 경계, 종료일 inclusive) + 커스텀 headline(`yyyy.MM.dd`, titleLarge, maxLines=1) + title "조회 기간 선택".
6. 문구: "가용/총" → "잔여 N권 / 전체 N권"(공용 문자열 하나로 3화면 일괄).
7. (헤드라인은 5에 통합.)

### 📊 현재 상태 요약

- **1~6단계 완료** (각 단계 실기기 DoD 검증 통과).
- **UX 개선 7가지 완료.**
- **ISBN-10 → ISBN-13 자동 변환 지원** (DB엔 항상 13자리 저장, 설계 원칙 11).
- **실사용 피드백 개선 백로그 11건 등록**(9장, 묶음 A~D). **미착수 — 기록만.**
- **다음 차례: 7단계 (선택 요건 및 마감) 또는 9장 묶음 A. 선후 관계 미정 → 사용자 확인 필요.**

### 🚧 보류/미완 항목 (잊지 말 것)

- **USB-C HID 스캐너 실물 테스트 미완** — 스캐너 분실(4단계 (C) 항목). 카메라·수동·오프라인 폴백·보유도서(A/B/D/E)는 검증 완료. 스캐너 확보 시 확인: ① `MainActivity.dispatchKeyEvent`가 이벤트를 소비하지 않는지(포커스 필드 도달) ② 한국 도서 **부가기호 5자리**가 ISBN 뒤에 붙어오는지(붙으면 처리 필요).
- **갤럭시 S7 테스트 보류** — Android 8.0 = minSdk 26 경계 확인용이나, micro-USB라 USB-C 스캐너 직결 불가.
- **폰 좁은 화면(<600dp) ⋮ 메뉴 분기 미검증** — 태블릿은 항상 텍스트 분기라 실행되지 않음. **폰 에뮬레이터로 확인 필요.**
- **카카오 REST API 키 재발급 권장** — 대화 중 키가 노출된 이력 있음(보안).

### 🖥️ 개발 환경 메모

- 실기기: **Alldocube iPlay 60 mini Pro (Android 14)**, 무선 디버깅.
- 재연결: 태블릿 무선 디버깅 ON → 메인 화면에서 **IP:포트** 확인 → `adb connect IP:포트` (페어링은 완료됨, **포트는 매번 바뀜**).
- Database Inspector: **앱 실행 상태**에서 App Inspection → 프로세스 선택 → **Live updates** 권장.

### 📌 7단계 대비 메모

- 7단계는 항목이 많으니 **묶음을 나눠서** 진행.
- **CMN-004 백업/복원**: Room 기본 **WAL 모드**(`WRITE_AHEAD_LOGGING`)라 `.db`만 복사하면 `-wal`의 최근 변경이 누락됨 → **체크포인트 처리 필요**(예: `PRAGMA wal_checkpoint(TRUNCATE)`).
- **CMN-003 정책 설정**: 값 검증 필요 (예: 세션 타임아웃 0이면 로그인 즉시 로그아웃).
- **LOAN-007 강제반납**: `actor_id`에 처음으로 **본인이 아닌 관리자**가 기록되는 케이스(자가대출 모델의 예외).

### ⏭️ 다음: 7단계 — 선택 요건 및 마감

- LOAN-004(바코드 반납)·LOAN-005(연장)·LOAN-007(강제반납)·CMN-003(정책설정)·CMN-004(백업/복원)·HIST-005(통계)·AUTH-006(만료 30초 전 안내)·BOOK-009(분실처리).
- ProGuard 규칙(Room/Hilt/Moshi/ML Kit)·release 빌드 검증·다크모드·문자열 하드코딩 제거.
- 착수 시 계획(묶음 분할)을 먼저 제시하고 확인받는다.
- ⚠️ **9장 실사용 피드백 백로그(묶음 A~D)와 별개 트랙이나 충돌 지점 있음.** LOAN-004(바코드 반납)는 A-6·A-11과 `ReturnScreen`을 공유하고, 스캐너 실물 필요 항목은 D-10과 겹침. **7단계·9장 중 무엇을 먼저 할지 착수 전 확정.**
