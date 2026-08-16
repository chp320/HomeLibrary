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

> 상태: **묶음 A 완료(2026-07-18).** 묶음 B 착수 예정. 착수 시 묶음 단위로 계획을 먼저 제시하고 확인받는다.

### ✅ 묶음 A — UI·문구 중심 (완료, `feat` 커밋 `5121e0a` + 홈 버튼 `7a00486`)

스키마 불변(version 1 유지), 신규 의존성 없음. 실기기 검증(태블릿 Android 14) 6건 전부 DoD 통과.

- ✅ **A-1 도서 목록 건수 표시** — 미검색 "전체 N건", 검색·필터 중 "검색 결과 N건 / 전체 N건"(DISCARDED 제외). `BookDao.countActive()` Flow. **위치 변경**: TopAppBar title은 actions(관리자 6개)와 폭을 나눠 쓰다 잘려서, 건수를 **목록 바로 위로 분리**(title=화면 정체성, 건수=목록 상태). title은 "도서 목록"으로 단순화.
- ✅ **A-2 로그인 사용자 정보** — "홍길동님 · 대출 가능 N건 · 대출 중 N건". 비로그인 시 숨김. 대출 가능은 `LoanPolicy.maxCount()`(AppConfig) 기반(하드코딩 없음).
- ✅ **A-4 loginId 정규화** — trim은 이미 ViewModel 3곳에 있었으나 `loginId`만 Repository 정규화가 누락(name/phone은 정규화)돼 있던 비대칭 해소. `AuthValidator.normalizeLoginId`로 검증·중복검사·저장·조회를 한 값으로 통일. 유닛 테스트 12건(`AuthValidatorTest`). ※ 정규식 `^[a-z0-9]+$`가 공백을 거부해 공백 계정 저장 경로는 논리적으로 없음 → 기존 DB 점검 스킵.
- ✅ **A-5 대출 화면 3건** — 여력 표시, 반납 예정일 미리보기(AppConfig 기반), 도서 상세 3분기("대출 중"=본인 / "대출 불가"=가용0·타인 / "대출하기").
- ✅ **A-6 반납 확인 다이얼로그** — "『제목』을(를) 반납하시겠습니까?".
- ✅ **A-11 일괄 반납** — 체크박스 다중 선택 + 전체 선택/해제, 결과 요약 스낵바("N건 성공, N건 실패"). `returnBooks()`는 `returnBook()`(건당 트랜잭션)을 **루프 호출만**(트랜잭션으로 감싸지 않음 — 감싸면 1건 실패에 전부 롤백). 예외도 건별 격리.

**연체 반영(실사용 피드백 추가)**: `maxCount − active`만으로 계산하면 연체 보유 시 거짓말(검증 ②가 최대권수보다 먼저 걸려 연체 1건이면 실질 0). `LoanAllowance`에 규칙 단일화 → A-2·A-5 공유. "· 연체 N건"으로 이유 병기(error 색상).

**부수 완료 항목**:
- ✅ **검색창 지우기(X) 버튼** — `Icons.Filled.Clear`(신규 의존성 없음), 입력 있을 때만 노출.
- ✅ **서브 화면 홈 버튼** — 공용 `HomeButton`, 10개 서브 화면 우상단. `popBackStack(BOOK_LIST, inclusive=false)`로 검색어·스크롤 보존. 인증 3종 제외(강제 비밀번호 변경 우회 방지).

### 묶음 B — 목록 탐색성 (다음 차례)

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

### ✅ 착수 순서 확정 (2026-07-17)

**묶음 A → B → C → D → 7단계.**

근거:

1. **묶음 A 우선** — 실사용 불편에서 나온 요건이라 체감 효과가 큼. 7단계는 선택 요건이라 급하지 않음.
2. **`ReturnScreen` 충돌이 A 방향으로 풀림** — **A-6(반납 확인) → A-11(일괄 반납) → 7단계 LOAN-004(바코드 반납)** 순이면 매번 이전 작업 위에 쌓이는 구조. 반대 순서로 가면 리워크 발생.

**D-10(회원증)과 7단계 LOAN-004는 스캐너 실물 필요** → 스캐너 확보 완료(4단계 검증)로 제약 해소, 순서에 영향 없음.

-----

## 10. 신규 요구사항 (2026-08-16 접수, 추적 대상)

사용자 요청으로 접수한 4건. **상태: 접수·검토 완료 / 착수 전 / 코드 미변경.**
각 항목의 **미결정 사항이 확정되기 전에는 구현에 착수하지 않는다**(0장 규칙 1).

> 요약: NEW-3(스키마 무변경) → NEW-1(스키마 변경) → NEW-2(스키마 변경, 최대 규모) 순 권고.
> NEW-4는 구현 트랙이 아니라 **설계 제약 트랙**으로 운영한다.

### NEW-1 — 회원가입 시 회원 바코드 자동 생성 + 바코드 자동 로그인

🔁 **기존 D-10(GitHub #5)과 동일 요건.** 신규 등록이 아니라 **D-10을 흡수·구체화**하고 우선순위를 올린 것으로 관리한다.

- **배경**: 대출·반납 때마다 아이디/비밀번호 입력이 번거로움. 회원증 바코드를 리더기로 읽어 즉시 로그인.
- **요건**
  - 회원가입 시 `member_code` 자동 생성·저장. **기존 사용자(admin 포함) 전원 백필 필요.**
  - 로그인 화면(및 대출/반납 화면)에서 회원 바코드 스캔 → 자동 로그인.
  - 분실 시 재발급(코드 재생성 → 기존 카드 자동 무효화).
- **기술 검토**
  - 🔴 **스키마 변경**: `USERS.member_code` 추가 + UNIQUE INDEX. Migration 필수(`fallbackToDestructiveMigration` 금지, version 증가, `schemas/*.json` 커밋).
  - **백필 방안**: Migration 내에서 기존 행을 순회하며 UPDATE(결정론적). 컬럼만 추가하고 런타임 lazy 생성은 UNIQUE 제약·NULL 처리가 지저분해지므로 비권장.
  - **코드 형식**: `M` 접두 + `SecureRandom` 랜덤 토큰(혼동 문자 `I/O/0/1` 제외). **`user_id` 사용 금지(추측 가능).** 바코드 심볼로지는 **Code128**(EAN-13은 ISBN과 구분 불가).
  - **스캔 라우팅**: 기존 `IsbnScanInput`은 ISBN 전용(13자리+체크디지트). 스캔값이 `M` 접두면 회원코드, 숫자 13자리면 ISBN으로 갈라주는 **공통 디스패처**가 필요.
  - **신규 의존성**: 바코드 이미지 생성용 ZXing(`com.google.zxing:core`) — ML Kit은 읽기 전용이라 생성 불가.
- ⚠️ **보안 트레이드오프(필수 인지)**: 바코드는 소지 기반 단일 요소. 사진 한 장으로 도용 가능하며, 2단계의 BCrypt·5회 실패 잠금을 **우회**한다. 아래 완화책은 선택이 아니라 요건이다.
  - 바코드 로그인은 **대출/반납 전용 경량 세션**만 부여. 관리자 기능·비밀번호 변경·회원정보 수정은 비밀번호 재인증 필수.
  - 🔴 **NEW-3(관리자 대리 대출)과 결합 시 위험이 급상승**한다 — admin 회원증 한 장이면 전 사용자 대출이 가능해짐. **admin 계정은 바코드 로그인 제외 또는 경량 세션에서 대리 대출 금지**를 반드시 결정할 것.
  - 5분 자동 로그아웃은 바코드 세션에도 동일 적용.
- **미결정**: ① admin 바코드 로그인 허용 여부 ② 경량 세션의 허용 범위 ③ 회원증 출력 방식(화면 표시만 / 이미지 저장·인쇄)

### NEW-2 — 예약 기능 (관리자가 특정 사용자에게 도서 배정)

- **배경**: 현재는 사용자가 책을 들고 와 스캔해 직접 대출. 관리자가 미리 도서를 특정 사용자 앞으로 잡아두고, 사용자가 나중에 실제 대출을 진행하게 하고 싶음.
- **요건**: 관리자가 도서 바코드 스캔 → 대상 사용자 선택 → 예약 등록. 사용자는 자기 예약 목록에서 대출로 전환.
- **성격 주의**: 일반 도서관의 "대출 중 도서 예약 대기(hold)"가 아니라 **재고 배정(assignment)** 에 가깝다. 용어는 "예약"으로 쓰되 동작은 배정으로 설계한다.
#### ✅ 확정 설계 (2026-08-16, 사용자 확정)

1. **재고 점유(A안 채택).** 예약은 도서 관점에서 **대출 중과 동일한 상태**로 취급한다. 예약 시 기존 `decreaseAvailable`(검증된 조건부 SQL)로 차감, 대출 전환 시 수량 변화 없이 RESERVATION→LOAN 전환, 취소·만료 시 `increaseAvailable`로 복원. 원칙 4·5를 그대로 재사용할 수 있는 유일한 안이다.
   - B안(점유하지 않고 표시만)은 남이 먼저 빌려가면 예약이 무의미해져 요건을 못 채운다 — 기각.
2. **자동 만료 72시간.** `reservation.expire.hours` = **72** (AppConfig). 만료되면 예약 해제 + 재고 복원. 이로써 "재고 영구 잠김" 문제는 해소된다.
   - **단위는 시간(hours).** days 단위면 24시간 미만 값(12시간 등)을 표현할 수 없다.
3. **예약도 사용자 할당량을 소모한다.** 예약 건은 `LoanAllowance`(A-2/A-5의 대출 여력)에 포함하고 최대 권수(`loan.max.count`)에 카운트한다.
   - 근거: 소모하지 않으면 5권 대출 + 5권 예약이 성립하고, 예약분을 대출로 전환하는 시점에 전부 거부되어 예약이 무의미해진다.
4. **연체 보유자는 예약 불가.** 예약 시점에 차단한다. 연체가 있으면 대출 전환이 어차피 막히므로 예약을 허용하면 재고만 잠긴다.
5. **본인이 이미 대출 중인 도서는 예약 불가.** 중복 대출 차단(검증 ①)과 같은 이유.

#### 기술 검토

- 🔴 **스키마 변경**: `RESERVATIONS` 테이블 신규(reservation_id, book_id, user_id, reserved_at, expires_at, status(RESERVED/CONVERTED/CANCELED/EXPIRED), actor_id). Migration 필수.
- 🔴 **AppConfig 신규 키 `reservation.expire.hours=72`** — `SeedCallback.onCreate`는 **DB 최초 생성 시에만** 실행되므로 시드 추가만으로는 **기존 DB에 값이 들어가지 않는다.** Migration에서 `INSERT`도 함께 수행할 것. (2단계에서 `login.lock.minutes`를 추가했을 때는 개발 중이라 넘어갔지만, 이제는 실사용 데이터가 있어 반드시 필요하다.)
- **만료 처리**: `OverdueUpdater`와 동일 패턴 — 앱 시작(`MainActivity.onStart`) 시 1회, 멱등. `status=EXPIRED` + `increaseAvailable`을 한 트랜잭션으로.
  - `increaseAvailable`은 `WHERE available_qty < total_qty` 조건부라 반환 0이 나올 수 있다 → 반납(`returnBook`)과 같은 방침으로 **로그만 남기고 진행**.
  - ⚠️ 앱을 열지 않으면 만료 처리가 돌지 않아 그동안 재고가 잠긴 채 남는다. 단말 1대 로컬 앱이라 실질 무해하나, **대출 시도 시점에 한 번 더 확인**하면 완전히 덮인다. NEW-4(다기기)로 가면 서버 배치로 옮겨야 할 항목.
- **대출 검증과의 접점**: 예약분을 대출로 전환할 때 `LoanValidator` ④가용수량은 이미 예약이 점유했으므로 우회해야 한다. ①②③은 예약 시점에 이미 본다(확정 3~5).
- **"잔여 N권" 문구는 의미가 유지된다** — `strings.xml:68` `book_available_count`("잔여 %1$d권 / 전체 %2$d권", 목록·상세·대출 3화면 공용)는 "지금 빌릴 수 있는 권수"라는 뜻이 점유 방식에서도 그대로다. 대신 **잔여 0의 *이유*가 두 갈래(대출중/예약중)로 갈리면서** 아래 3곳을 고쳐야 한다:
  1. 🔴 **`BookDetailScreen.kt:139` 3분기 → 4분기.** 현재 `availableQty <= 0`이면 무조건 "대출 불가" 비활성 버튼이다. **예약자 본인에게는 잔여 0이어도 "대출하기"가 활성**이어야 한다. 핵심 변경점.
  2. `strings.xml:148` "모든 권이 대출 중입니다." — 예약으로 잔여 0인 경우 사실과 다르다. 문구 분리 필요.
  3. "대출 가능" 필터(`available_qty > 0` 기준)에서 **본인이 예약한 도서가 빠진다.**
- **이력**: `LOAN_HISTORY`는 `loan_id` FK라 예약(대출 없음)을 담을 수 없다. append-only 원칙(원칙 6)을 지키려면 예약 이력은 RESERVATIONS의 status 전이로 남기고, 대출 전환 시점부터 LOAN_HISTORY에 기록.
- **UI**: "내 예약" 목록에는 `ReturnScreen`의 D-n 표시 방식을 재사용해 **만료까지 남은 시간**을 보여준다. 그러지 않으면 예약해 둔 것을 잊고 만료된다.
- ⚠️ **NEW-3과 UI 중복**: "관리자가 대상 사용자를 검색·선택"하는 컴포넌트가 두 요건에 공통. **NEW-3을 먼저 구현해 사용자 선택 컴포넌트를 공용화한 뒤 NEW-2를 얹는다**(A-6→A-11→LOAN-004와 같은 누적 구조).
- **미결정**: ① 일반 사용자도 본인 예약을 걸 수 있는지(현재 요건은 관리자가 거는 흐름만 명시 — **관리자 전용으로 가정**) ② 예약 취소 권한 범위(본인/관리자)

### NEW-3 — 관리자 대리 대출 (대출 대상자 선택)

- **배경**: admin으로 대출하면 admin 명의로만 대출됨. 관리자가 `test` 등 다른 계정 명의로 대출을 처리할 수 있어야 함.
- **기술 검토** — **4건 중 가장 작고, 스키마 변경이 없다.**
  - `LoanRepository.loan(userId, bookId)`는 **이미 userId를 파라미터로 받는다** → 저장소 계층 로직은 거의 무변경.
  - 🔴 **`loan()`이 LOAN_HISTORY의 `actorId`를 대출자와 동일하게 하드코딩**하고 있다(`LoanRepository.kt:114`). 대리 대출은 *대출자 ≠ 행위자*이므로 `loan(userId, bookId, actorId = userId)`로 시그니처를 확장한다(기본값 유지로 기존 호출부 무변경).
  - 🔴 **자가대출 모델의 첫 예외.** 5단계에서 "actor_id = 대출자"로 확정했고 7단계 LOAN-007(강제반납)에서 처음 깨질 것으로 예고했는데, **여기서 먼저 깨진다.** 1장·5단계 설계 메모를 함께 갱신할 것.
  - **검증은 반드시 선택된 대출자 기준**으로 수행(연체·최대권수·중복). `LoanValidator`는 userId를 받으므로 그대로 동작하나, `LoanViewModel`의 대출 여력 표시가 **세션 사용자 기준**이라 "대출 대상자 기준"으로 리팩터 필요(`LoanViewModel.kt`의 `userInfo`).
  - **사용자 검색**: 6단계 `UserDao.searchUsers` 재사용. INACTIVE 사용자는 대상에서 제외.
  - **권한 가드**: 대상자 ≠ 세션 사용자이면 관리자만 허용(UI 숨김 + ViewModel 거부 이중 방어).
- **미결정**: ① 대리 반납도 함께 지원할지(7단계 LOAN-007 강제반납과 중복 검토) ② 대리 대출 시 대상자의 연체/최대권수 제한을 관리자가 무시할 수 있는지(권고: 무시 불가)

### NEW-4 — 다기기 사용(서버화) 대비 — GitHub private 저장소 백엔드 검토

- **요청**: 여러 기기에 설치·로그인해 공유 사용. AWS 등 클라우드 대신 **GitHub private 저장소에 앱 데이터를 저장**하는 방식 검토. 당장 착수는 아니고 **대비**.
- **결론: 공유 DB(다기기 동시 읽기·쓰기) 용도로는 부적합. 백업/복원 채널로는 우수.**
- **부적합 근거**
  1. **트랜잭션 부재** — git에는 원자적 갱신이 없다. SQLite 파일을 통째로 올리면 바이너리 충돌은 머지 불가(한쪽 유실), JSON으로 쪼개도 `available_qty` 같은 카운터는 자동 머지가 불가능하다. **원칙 4(단일 트랜잭션)·원칙 5(조건부 SQL 방어)가 원천적으로 무너진다.**
  2. **권한 분리 불가** — repo 쓰기 토큰을 각 단말에 심으면 **모든 사용자가 전체 DB를 읽고 쓸 수 있다.** 앱 내 role 가드는 클라이언트 코드일 뿐이라 admin 전용 기능이 무의미해진다. PAT 영속 저장은 원칙 8·9와도 충돌.
  3. **개인정보가 히스토리에 영구 잔존** — 이름·전화번호·비밀번호 해시가 커밋 이력에 남아 history rewrite 없이는 삭제 불가. 논리 삭제(원칙 2)와 무관하게 문제.
  4. **실시간성 없음** — 폴링 기반이라 마지막 1권 동시 대출 같은 충돌을 사전 차단할 수 없다.
  - (레이트 리밋은 인증 시 5,000 req/h라 가정용 규모에서는 문제되지 않음.)
- **유효한 사용처**: ✅ 7단계 CMN-004 백업/복원 채널(무료·버전관리 내장·복원 용이) ✅ "쓰기 단말 1대 + 읽기 전용 단말들" 모델.
- **대안(착수 시 재조사 필요 — 이용 조건·무료 한도는 변동됨)**
  - **Supabase**(Postgres + Auth + RLS): 기존 SQL 스키마를 가장 그대로 옮길 수 있고 진짜 트랜잭션·행 수준 권한 제공. **1순위 권고.**
  - **Firebase Firestore**: 실시간 동기화·오프라인 캐시·보안 규칙. 오프라인 UX가 중요하면 유리.
  - **자체 서버(NAS·라즈베리파이 + Ktor)**: 집 안 사용 한정이면 가능. 외부 접속은 DDNS/포트포워딩 부담.
- **지금 해야 할 "대비"(코드 변경 아님, 설계 제약으로 운영)** — NEW-1~3 및 이후 모든 작업에서 아래를 어기지 않는다.
  1. **Repository 계층 경계 유지** — ViewModel이 DAO를 직접 호출하지 않는다. 원격 전환 시 Repository 구현만 교체하면 되도록. (현재 잘 지켜지고 있음.)
  2. 🔴 **PK 전략** — 현재 `autoGenerate` Long PK는 **다기기에서 ID 충돌**을 일으킨다. 나중에 바꾸려면 전 테이블 마이그레이션. 신규 테이블(NEW-2 RESERVATIONS)부터는 서버 친화적 식별자(UUID 문자열)를 병행할지 **착수 시 판단**.
  3. **동기화 메타데이터** — `updated_at`이 BOOKS에만 있다. USERS/LOANS에도 필요해질 수 있음(LWW 머지 기준).
  4. **시각 기준** — 현재 전부 단말 시계(`System.currentTimeMillis()`). 다기기에서는 시계 오차가 연체 판정을 흔든다. 서버 시각 기준이 필요.
  5. **파생 카운터 주의** — `available_qty`는 동기화에 가장 취약한 값. 원격 전환 시 서버 트랜잭션으로만 갱신.
- **미결정**: ① 다기기 사용의 실제 범위(동시 대출 처리 vs 조회 위주) ② GitHub 저장소를 백업 전용으로 한정하고 CMN-004에 흡수할지 ③ 서버 백엔드 채택 시점

### 신규 요구사항 반영에 따른 착수 순서 재검토 (미확정)

기존 확정 순서는 **묶음 A → B → C → D → 7단계**였다. 신규 4건은 실사용 불편에서 나온 요건이라 체감 효과가 큰 반면, 묶음 B(B-3 초성 인덱스)는 탐색 편의 개선이다.

- **권고**: **NEW-3 → NEW-1(=D-10 흡수) → NEW-2 → 묶음 B → C → 7단계**, NEW-4는 설계 제약으로 상시 병행.
- 🔴 **스키마 변경 3건이 몰린다** — C-8(분류 마스터), NEW-1(member_code), NEW-2(RESERVATIONS). **한 번의 Migration으로 묶을지 단계별로 나눌지 착수 전 반드시 결정**(226행 "순서·충돌 주의" 참조). 권고: 검증 단위를 작게 유지하기 위해 **분리**.
- **사용자 확정 전까지 위 순서는 확정이 아니다.**

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

### ✅ 4단계 — 바코드 스캔(이중 지원) + ISBN API (완료)

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
- **USB-C HID 하드웨어 스캐너 실물 검증 완료(2026-07-17).** 바코드 인식 → 도서 검색 → 등록 전 경로 정상. 우려했던 두 건 모두 **문제 없음**: ① `MainActivity.dispatchKeyEvent`의 이벤트 삼킴 없음(포커스 필드에 정상 도달) ② 한국 도서 **부가기호 5자리 미부착**(별도 처리 불필요). → **4단계 DoD 전부 통과, 보류 없음.**

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
- **실사용 피드백 개선 백로그 11건 등록**(9장, 묶음 A~D).
- **4단계 HID 스캐너 실물 검증 완료 + 카카오 API 키 재발급 완료** → 해당 보류 2건 해제(2026-07-17).
- **착수 순서 확정: 묶음 A → B → C → D → 7단계**(9장 참조).
- **✅ 묶음 A 완료(2026-07-18)** — 6건 + 부수(검색창 X 버튼·홈 버튼)까지 실기기 DoD 통과. 커밋 `5121e0a`·`7a00486`.
- **✅ README 작성(2026-07-18)** — 공개 문서용(커밋 `5115fab`, push 완료). 실제 버전·`local.properties` 카카오 키 안내 포함.
- **✅ GitHub Issues 18건 등록(2026-07-18)** — 백로그 B~D + 7단계 + 보류 + 개선. 라벨 6종(`backlog-b/c/d`·`stage-7`·`deferred`·`enhancement`). 아래 "이슈 매핑" 참조.
- **🆕 신규 요구사항 4건 접수(2026-08-16) — 10장 참조.** NEW-1(회원 바코드 로그인, 기존 D-10 흡수)·NEW-2(예약)·NEW-3(관리자 대리 대출)·NEW-4(다기기/서버화 대비). **검토 완료·착수 전·코드 미변경.** 각 미결정 사항 확정 후 착수.
- **다음 차례: 미정.** 기존 순서는 9장 묶음 B(B-3)였으나 신규 4건 접수로 **착수 순서 재검토 필요**(10장 마지막 절). 권고: NEW-3 → NEW-1 → NEW-2 → 묶음 B.
- **모든 변경 push 완료(origin/main = HEAD `674bbba`). 미커밋 없음(10장 추가분 제외).**

### 🚧 보류/미완 항목 (잊지 말 것)

- **갤럭시 S7 테스트 보류** — Android 8.0 = minSdk 26 경계 확인용. micro-USB라 USB-C 스캐너 직결은 불가(스캐너 경로는 태블릿에서 검증 완료라 무관).
- **폰 좁은 화면(<600dp) ⋮ 메뉴 분기 미검증** — 태블릿은 항상 텍스트 분기라 실행되지 않음. **폰 에뮬레이터로 확인 필요.**

_해제됨: USB-C HID 스캐너 실물 테스트(2026-07-17 검증 완료 → 4단계 참조) / 카카오 REST API 키 재발급(2026-07-17 재발급·적용·동작 확인 완료)._

### 🖥️ 개발 환경 메모

- 실기기: **Alldocube iPlay 60 mini Pro (Android 14)**, 무선 디버깅.
- 재연결: 태블릿 무선 디버깅 ON → 메인 화면에서 **IP:포트** 확인 → `adb connect IP:포트` (페어링은 완료됨, **포트는 매번 바뀜**).
- Database Inspector: **앱 실행 상태**에서 App Inspection → 프로세스 선택 → **Live updates** 권장.

### 📌 7단계 대비 메모

- 7단계는 항목이 많으니 **묶음을 나눠서** 진행.
- **CMN-004 백업/복원**: Room 기본 **WAL 모드**(`WRITE_AHEAD_LOGGING`)라 `.db`만 복사하면 `-wal`의 최근 변경이 누락됨 → **체크포인트 처리 필요**(예: `PRAGMA wal_checkpoint(TRUNCATE)`).
- **CMN-003 정책 설정**: 값 검증 필요 (예: 세션 타임아웃 0이면 로그인 즉시 로그아웃).
  - **관리 대상 = APP_CONFIG 전 키.** 화면에서 수정 가능해야 하며, 신규 키가 생기면 **자동으로 이 화면에 포함**되도록 만든다(키를 화면에 하드코딩하지 말 것).
  - NEW-2 도입 시 `reservation.expire.hours`(기본 72)가 대상에 추가된다 — **AppConfig 기반으로 설계했으므로 별도 작업 없이 편집 가능해야 정상**이다. 이것이 만료 기간을 상수가 아닌 AppConfig로 둔 이유다.
  - 키별 검증 범위(최솟값·최댓값·단위)를 함께 정의한다. 예: `reservation.expire.hours`는 1 이상(0이면 예약 즉시 만료).
- **LOAN-007 강제반납**: `actor_id`에 처음으로 **본인이 아닌 관리자**가 기록되는 케이스(자가대출 모델의 예외).

### 🐙 GitHub Issues 매핑 (2026-07-18 등록, 총 18건)

원격 저장소 `chp320/HomeLibrary`에 등록. 각 이슈에 배경·요구사항·완료 조건 + CLAUDE.md의 ⚠️/🔴 경고 포함.

- **#1** [backlog-b] B-3 스크롤바+초성 인덱스
- **#2** [backlog-c] C-7 API 폴백 체인 / **#3** [backlog-c] C-8 분류 마스터화(🔴 최초 스키마 변경)
- **#4** [backlog-d] D-9 비밀번호 초기화 / **#5** [backlog-d][new-req] **D-10 = NEW-1** 회원증 바코드(🔴 보안·스키마 변경) — 2026-08-16 NEW-1로 흡수(제목 변경 + 추가 요건 코멘트)
- **#6** LOAN-004 바코드 반납 / **#7** LOAN-005 연장 / **#8** LOAN-007 강제반납 / **#9** CMN-003 정책설정 / **#10** CMN-004 백업복원(🔴 WAL) / **#11** HIST-005 통계 / **#12** AUTH-006 만료안내 / **#13** BOOK-009 분실처리 / **#14** ProGuard·release / **#15** 다크모드·문자열 (전부 [stage-7])
- **#16** [deferred] 갤럭시 S7 / **#17** [deferred] 폰 <600dp ⋮ 메뉴
- **#18** [enhancement] 입력 폼 이탈 확인 다이얼로그(홈/뒤로가기 모두, dirty state 추적)

**신규 요구사항 (2026-08-16 등록, 라벨 `new-req`)** — 10장 참조. 총 21건 OPEN.

- **#19** [new-req] NEW-3 관리자 대리 대출(스키마 무변경, **가장 먼저 착수 권고**)
- **#20** [new-req] NEW-2 예약 기능(🔴 RESERVATIONS 신규 + AppConfig 신규 키, 설계 확정 완료)
- **#21** [new-req] NEW-4 다기기/서버화 대비(구현 트랙 아님 — **설계 제약 추적용**)
- NEW-1은 신규 이슈를 만들지 않고 **#5에 흡수**했다(중복 방지).

### ⏭️ 다음: 9장 묶음 B — 목록 탐색성 (B-3, GitHub #1)

**아직 착수 안 함. 코드 미변경.** 착수 전 사용자에게 계획을 제시하고 아래 미결정 2건을 확정받은 뒤 진행한다.

#### 사전 조사 결과(재조사 불필요)

- **🔴 현재 목록 정렬은 `BookDao.search`의 `ORDER BY updated_at DESC`(최근 수정순)다.** 초성 인덱스는 목록이 **제목순**이어야 의미가 있다 → B-3는 단순 UI 추가가 아니라 **기본 정렬 변경**을 포함한다.
- **정렬 위치 권고: SQL이 아니라 클라이언트(도메인 계층).** 도서 목록은 페이징이 없어 전체가 메모리에 온다. SQLite `ORDER BY title`은 유니코드 순(숫자→영문→한글)이라 한글이 뒤로 밀림 → 커스텀 콜레이션 대신 클라이언트 정렬로 초성 버킷과 정렬 키를 한 곳에서 관리(인덱스↔정렬 불일치 방지). `search`의 `ORDER BY`는 제거/무시.
- **초성 규칙**: 한글 음절 `(코드-0xAC00)/588` → 19초성. 인덱스 바는 **14개로 접어 표시**(ㄲ→ㄱ, ㄸ→ㄷ, ㅃ→ㅂ, ㅆ→ㅅ, ㅉ→ㅈ). 영문 `A~Z`(대소문자 통합). 숫자·기타 `#`. 정렬 순서 = **한글(ㄱ~ㅎ) → 영문(A~Z) → #**(한국어 앱).
- **예상 변경 파일**: 신규 `BookSortKey.kt`(초성·정렬·버킷), `InitialIndexBar.kt`(레일 컴포저블). 수정 `BookListViewModel`(정렬 적용·버킷 시작 인덱스), `BookListScreen`(레일 + `LazyListState` 연결), `BookDao.search`(ORDER BY 제거 검토). 테스트 `BookSortKeyTest`. **신규 의존성 없음, 스키마 불변.**

#### ⚠️ 미결정 2건(다음 세션에서 사용자 확인 필요)

1. **기본 정렬**: (A) 제목순(가나다순: 한글→영문→#)으로 **고정** — 권고. vs (B) 가나다순/최근수정순 **정렬 토글** 제공(작업량↑).
2. **fast scroll 범위**: (A) 초성 인덱스 레일만(우측 세로 ㄱ~ㅎ/A~Z/#, 탭·드래그 점프) — 권고, fast scroll 핵심 가치 충족. vs (B) 레일 + 별도 드래그 스크롤바 썸(견고 구현에 시간↑).

> 위 두 질문을 AskUserQuestion으로 물으려다 오늘 세션 종료. 다음 세션은 이 두 결정부터 받고 착수.

### 🔜 이후: 7단계 — 선택 요건 및 마감 (묶음 A~D 완료 후)

- LOAN-004(바코드 반납)·LOAN-005(연장)·LOAN-007(강제반납)·CMN-003(정책설정)·CMN-004(백업/복원)·HIST-005(통계)·AUTH-006(만료 30초 전 안내)·BOOK-009(분실처리).
- ProGuard 규칙(Room/Hilt/Moshi/ML Kit)·release 빌드 검증·다크모드·문자열 하드코딩 제거.
- 착수 시 계획(묶음 분할)을 먼저 제시하고 확인받는다.
- ⚠️ **LOAN-004는 A-6·A-11이 만든 `ReturnScreen` 위에 얹는다**(9장 착수 순서 근거 2 참조).
