package com.ifthen.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Rule::class, ExecLog::class, Folder::class, Chain::class],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun execLogDao(): ExecLogDao
    abstract fun folderDao(): FolderDao
    abstract fun chainDao(): ChainDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN folderId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS folders (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, emoji TEXT NOT NULL, color INTEGER NOT NULL, " +
                        "parentId INTEGER, createdAt INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN nextRuleId INTEGER")
                db.execSQL("ALTER TABLE folders ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rules ADD COLUMN kind TEXT NOT NULL DEFAULT '${RuleKind.REPEATING.name}'"
                )
                db.execSQL("ALTER TABLE rules ADD COLUMN dueDate INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN miniThen TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exec_logs ADD COLUMN mini INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exec_logs ADD COLUMN backfill INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v7->8：链从 nextRuleId 派生指针升成真实体（见 docs/adr/0001）
        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS chains (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, folderId INTEGER, " +
                        "status TEXT NOT NULL DEFAULT 'ACTIVE', color INTEGER, " +
                        "note TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL)"
                )

                val ifText = HashMap<Long, String>()
                val folderOf = HashMap<Long, Long?>()
                val createdOf = HashMap<Long, Long>()
                val nextOf = HashMap<Long, Long>()
                val eligible = HashSet<Long>()
                db.query(
                    "SELECT id, ifText, folderId, kind, status, nextRuleId, createdAt FROM rules"
                ).use { c ->
                    val iId = c.getColumnIndexOrThrow("id")
                    val iIf = c.getColumnIndexOrThrow("ifText")
                    val iFol = c.getColumnIndexOrThrow("folderId")
                    val iKind = c.getColumnIndexOrThrow("kind")
                    val iStatus = c.getColumnIndexOrThrow("status")
                    val iNext = c.getColumnIndexOrThrow("nextRuleId")
                    val iCreated = c.getColumnIndexOrThrow("createdAt")
                    while (c.moveToNext()) {
                        val id = c.getLong(iId)
                        ifText[id] = c.getString(iIf)
                        folderOf[id] = if (c.isNull(iFol)) null else c.getLong(iFol)
                        createdOf[id] = c.getLong(iCreated)
                        if (!c.isNull(iNext)) nextOf[id] = c.getLong(iNext)
                        val active = c.getString(iStatus) == RuleStatus.ACTIVE.name
                        val notOneOff = c.getString(iKind) != RuleKind.ONE_OFF.name
                        if (active && notOneOff) eligible.add(id)
                    }
                }

                // 复刻 sanitizedNext：丢弃悬空/自环、每个目标最多一个前驱
                val next = HashMap<Long, Long>()
                val hasPred = HashSet<Long>()
                for ((from, to) in nextOf) {
                    if (from !in eligible || to !in eligible) continue
                    if (to == from || to in hasPred) continue
                    next[from] = to; hasPred.add(to)
                }
                // 破环
                val seen = HashSet<Long>()
                for (start in next.keys.toList()) {
                    if (start in seen) continue
                    var cur: Long? = start; var prev: Long? = null
                    while (cur != null) {
                        if (!seen.add(cur)) { prev?.let { next.remove(it) }; break }
                        prev = cur; cur = next[cur]
                    }
                }

                val preds = next.values.toSet()
                val membership = HashMap<Long, Pair<Long, Int>>()
                var chainSeq = 0L
                for (head in next.keys.filter { it !in preds }) {
                    val path = ArrayList<Long>()
                    val guard = HashSet<Long>()
                    var cur: Long? = head
                    while (cur != null && guard.add(cur)) { path.add(cur); cur = next[cur] }
                    // 按文件夹边界拆段
                    val segs = ArrayList<Pair<Long?, MutableList<Long>>>()
                    var segFolder = folderOf[head]
                    var seg = ArrayList<Long>()
                    for (id in path) {
                        val f = folderOf[id]
                        if (seg.isEmpty()) { segFolder = f; seg.add(id) }
                        else if (f == segFolder) seg.add(id)
                        else { segs.add(segFolder to seg); seg = ArrayList(); segFolder = f; seg.add(id) }
                    }
                    if (seg.isNotEmpty()) segs.add(segFolder to seg)
                    for ((sf, ids) in segs) {
                        if (ids.size < 2) continue
                        chainSeq += 1
                        val h = ids.first()
                        db.execSQL(
                            "INSERT INTO chains (id, name, folderId, status, color, note, createdAt) " +
                                "VALUES (?, ?, ?, 'ACTIVE', NULL, '', ?)",
                            arrayOf<Any?>(chainSeq, ifText[h] ?: "未命名链", sf, createdOf[h] ?: 0L)
                        )
                        ids.forEachIndexed { i, rid -> membership[rid] = chainSeq to i }
                    }
                }

                // 重建 rules：去 nextRuleId，加 chainId/stepIndex
                db.execSQL(
                    "CREATE TABLE rules_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "ifText TEXT NOT NULL, thenText TEXT NOT NULL, " +
                        "miniThen TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT '', " +
                        "triggerConfig TEXT, folderId INTEGER, sortOrder INTEGER NOT NULL DEFAULT 0, " +
                        "chainId INTEGER, stepIndex INTEGER, " +
                        "kind TEXT NOT NULL DEFAULT 'REPEATING', dueDate INTEGER, " +
                        "status TEXT NOT NULL DEFAULT 'ACTIVE', createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO rules_new (id, ifText, thenText, miniThen, tags, triggerConfig, " +
                        "folderId, sortOrder, chainId, stepIndex, kind, dueDate, status, createdAt) " +
                        "SELECT id, ifText, thenText, miniThen, tags, triggerConfig, folderId, sortOrder, " +
                        "NULL, NULL, kind, dueDate, status, createdAt FROM rules"
                )
                for ((rid, m) in membership) {
                    db.execSQL(
                        "UPDATE rules_new SET chainId = ?, stepIndex = ? WHERE id = ?",
                        arrayOf<Any?>(m.first, m.second, rid)
                    )
                }
                db.execSQL("DROP TABLE rules")
                db.execSQL("ALTER TABLE rules_new RENAME TO rules")
            }
        }

        // v8->9：预案定时提醒（当天分钟数 + 星期位掩码）
        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN reminderMin INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE rules ADD COLUMN reminderDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "ifthen.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
                )
                    .build().also { instance = it }
            }
    }
}
