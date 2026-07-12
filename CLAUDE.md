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

## 4. 데이터 모델 (확정)

- **USERS**: user_id(PK), login_id(unique, 수정불가), password_hash, name, phone, role(ROLE_ADMIN/ROLE_USER), status(ACTIVE/INACTIVE/LOCKED), fail_count, locked_until, pwd_change_required, created_at
- **BOOKS**: book_id(PK), isbn(nullable), title, author, publisher, pub_date, cover_url, category, location, total_qty, available_qty, status(AVAILABLE/LOST/DISCARDED), created_at, updated_at
- **LOANS**: loan_id(PK), book_id(FK), user_id(FK), loan_date, due_date, return_date(nullable), status(LOANED/RETURNED/OVERDUE), extend_count
- **LOAN_HISTORY** (append-only): history_id(PK), loan_id(FK), action(LOAN/RETURN/EXTEND/FORCE_RETURN), action_at, actor_id(FK), memo
- **APP_CONFIG**: config_key(PK), config_value, description
  - 기본값: loan.period.days=14, loan.max.count=5, loan.extend.days=7, loan.extend.max=1, session.timeout.minutes=5, login.fail.limit=5

### 시드 데이터 (최초 DB 생성 시 1회)

- 관리자: login_id=`admin`, password=`admin1234`(BCrypt 해시 저장), role=ROLE_ADMIN, pwd_change_required=1
- APP_CONFIG 6건 기본값

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
- DoD: 5개 테이블 생성, admin 시드 1건(BCrypt 해시), app_config 6건, `app/schemas/1.json` 생성

### 2단계 — 인증 (가입/로그인/5분 자동 로그아웃)

- 커버 요건: AUTH-001~004, AUTH-006, SCR-02, SCR-03
- AuthRepository(signUp/login/logout), SessionManager(메모리 보관), SessionTimeoutHandler(폴링), MainActivity.dispatchTouchEvent로 활동시각 갱신
- 백그라운드 진입 중에도 타이머 경과, 복귀 시 즉시 만료 판정
- 로그인 5회 실패 시 5분 잠금, admin 최초 로그인 시 비밀번호 변경 강제
- 세션 타임아웃 값은 `AppConfigDao.getValue("session.timeout.minutes")`로 읽는다
- DoD: 가입→로그인→5분 무조작 자동 로그아웃 / 터치 시 리셋 / 백그라운드 6분 후 복귀 시 즉시 로그아웃 / 5회 실패 잠금 / admin 비번 변경 강제

### 3단계 — 도서 CRUD + 검색

- 커버 요건: BOOK-001, BOOK-004~008, SCR-04, SCR-05, SCR-11
- BookRepository(등록/수정/논리삭제/검색), 중복 ISBN 시 수량 증가, 삭제 전 대출중 검증
- 관리자 권한 가드, 표지 이미지 로딩(Coil)
- DoD: 30건 등록 후 부분검색 / 동일 ISBN 재등록 시 수량 증가 / 일반 사용자 등록·수정 차단 / 삭제 시 DISCARDED 논리삭제

### 4단계 — 바코드 스캔(이중 지원) + ISBN API 연동

- 커버 요건: BOOK-002, BOOK-003, CMN-001, CMN-002, SCR-06
- **[중요] 바코드 입력은 두 소스를 모두 지원한다:**
  - **(A) USB-C 하드웨어 스캐너**: OS가 HID(키보드)로 인식. 스캔 시 ISBN 숫자가 타이핑되고 보통 Enter로 끝남. 포커스를 가진 숨은 입력 핸들러가 이 입력을 받아 ISBN-13으로 처리.
  - **(B) 폰 내장 카메라**: CameraX + ML Kit으로 EAN-13 촬영 인식.
  - 두 소스는 **동일한 결과(ISBN-13 문자열)** 를 공통 콜백으로 넘긴다. 입력 소스만 다르고 이후 처리(로컬 조회→API→폼 자동채움)는 완전히 공유한다.
  - **스캐너가 연결되지 않은 환경에서도 카메라로 동작**해야 한다. 스캐너 연결 여부를 감지하거나, 사용자가 입력 방식을 선택할 수 있게 한다.
- 카카오 API: `GET /v3/search/book?target=isbn&query={isbn}&size=1`, 헤더 `Authorization: KakaoAK {key}`, 타임아웃 연결3s/읽기5s, 재시도 1회
- 동일 ISBN 중복 응답 대비 `documents[0]`만 사용. 결과 0건/401/429 → 수동 입력 폼 전환
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
