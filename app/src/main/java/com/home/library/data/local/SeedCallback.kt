package com.home.library.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.home.library.security.PasswordHasher

/**
 * DB 최초 생성 시 1회 실행되는 시드.
 * - 관리자 계정 1건 (login_id=admin, password=admin1234 → BCrypt 해시, pwd_change_required=1)
 * - APP_CONFIG 기본값 7건
 *
 * onCreate는 DB 생성 스레드(백그라운드)에서 호출되므로 blocking 해싱을 사용한다.
 * DAO 재진입 데드락을 피하기 위해 execSQL로 직접 삽입한다.
 */
class SeedCallback : androidx.room.RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedAdmin(db)
        seedConfig(db)
    }

    private fun seedAdmin(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        val hash = PasswordHasher.hashBlocking("admin1234")
        db.execSQL(
            """
            INSERT INTO users
                (login_id, password_hash, name, phone, role, status,
                 fail_count, locked_until, pwd_change_required, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                "admin",       // login_id
                hash,          // password_hash
                "관리자",       // name
                null,          // phone
                "ROLE_ADMIN",  // role (enum name)
                "ACTIVE",      // status (enum name)
                0,             // fail_count
                null,          // locked_until
                1,             // pwd_change_required (true)
                now,           // created_at
            ),
        )
    }

    private fun seedConfig(db: SupportSQLiteDatabase) {
        val defaults = listOf(
            Triple(ConfigKeys.LOAN_PERIOD_DAYS, "14", "대출 기간(일)"),
            Triple(ConfigKeys.LOAN_MAX_COUNT, "5", "1인 최대 대출 권수"),
            Triple(ConfigKeys.LOAN_EXTEND_DAYS, "7", "연장 기간(일)"),
            Triple(ConfigKeys.LOAN_EXTEND_MAX, "1", "최대 연장 횟수"),
            Triple(ConfigKeys.SESSION_TIMEOUT_MINUTES, "5", "세션 자동 로그아웃(분)"),
            Triple(ConfigKeys.LOGIN_FAIL_LIMIT, "5", "로그인 실패 잠금 임계값"),
            Triple(ConfigKeys.LOGIN_LOCK_MINUTES, "5", "로그인 실패 잠금 시간(분)"),
        )
        defaults.forEach { (key, value, desc) ->
            db.execSQL(
                "INSERT INTO app_config (config_key, config_value, description) VALUES (?, ?, ?)",
                arrayOf(key, value, desc),
            )
        }
    }
}
