package mc506lw.buildingPlan.database

import mc506lw.buildingPlan.model.PlayerData
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.ConcurrentHashMap

class DatabaseManager(
    private val dataFolder: File,
    private val log: (String, Throwable?) -> Unit
) {

    constructor(dataFolder: File) : this(dataFolder, { msg, err ->
        val logger = java.util.logging.Logger.getLogger("BuildingPlan")
        if (err != null) logger.log(java.util.logging.Level.WARNING, msg, err) else logger.warning(msg)
    })

    private val dbFile = File(dataFolder, "data")
    private var connection: Connection? = null
    private val writeExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, DB_THREAD_NAME).apply { isDaemon = true }
    }
    private val playerLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun init() {
        dataFolder.mkdirs()
        // SpigotLibraryLoader 将 H2 放到服务器 classpath，但插件 PluginClassLoader 不一定能看到，
        // 显式加载驱动类确保 JDBC 能找到它。
        try {
            Class.forName("org.h2.Driver")
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("H2 driver not found in classpath", e)
        }
        connection = DriverManager.getConnection("jdbc:h2:file:${dbFile.absolutePath};MODE=MySQL;DATABASE_TO_LOWER=TRUE")
        connection!!.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_data (
                    uuid             TEXT PRIMARY KEY,
                    tier             TEXT NOT NULL DEFAULT 'NONE',
                    subscribe_time   BIGINT NOT NULL DEFAULT 0,
                    expire_time      BIGINT NOT NULL DEFAULT 0,
                    balance_5h       INTEGER NOT NULL DEFAULT 0,
                    balance_daily    INTEGER NOT NULL DEFAULT 0,
                    balance_weekly   INTEGER NOT NULL DEFAULT 0,
                    pool             INTEGER NOT NULL DEFAULT 0,
                    last_5h_refresh  INTEGER NOT NULL DEFAULT 0,
                    last_daily_reset TEXT NOT NULL DEFAULT '',
                    last_weekly_reset TEXT NOT NULL DEFAULT '',
                    bonus_points     INTEGER NOT NULL DEFAULT 0,
                    original_price  TEXT NOT NULL DEFAULT '',
                    weekly_used     INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            // 兼容已有数据库：将 subscribe_time、expire_time 从 INTEGER 迁移到 BIGINT
            try {
                stmt.execute("ALTER TABLE player_data ALTER COLUMN subscribe_time BIGINT")
                stmt.execute("ALTER TABLE player_data ALTER COLUMN expire_time BIGINT")
            } catch (_: Exception) {
                // 列已是 BIGINT 时 H2 可能抛出异常，忽略
            }
            // 兼容已有数据库：新增列（老数据库没有这些列）
            try {
                stmt.execute("ALTER TABLE player_data ADD COLUMN IF NOT EXISTS bonus_points INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            try {
                stmt.execute("ALTER TABLE player_data ADD COLUMN IF NOT EXISTS original_price TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {}
            try {
                stmt.execute("ALTER TABLE player_data ADD COLUMN IF NOT EXISTS weekly_used INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
        }
    }

    fun getLock(uuid: String): ReentrantLock {
        return playerLocks.computeIfAbsent(uuid) { ReentrantLock() }
    }

    fun removeLock(uuid: String) {
        playerLocks.remove(uuid)
    }

    fun clearLocks() {
        playerLocks.clear()
    }

    fun loadPlayerData(uuid: String): CompletableFuture<PlayerData> {
        return CompletableFuture.supplyAsync({
            val conn = connection ?: throw IllegalStateException("Database not initialized")
            conn.prepareStatement("SELECT * FROM player_data WHERE uuid = ?").use { ps ->
                ps.setString(1, uuid)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        PlayerData(
                            uuid = rs.getString("uuid"),
                            tier = rs.getString("tier"),
                            subscribeTime = rs.getLong("subscribe_time"),
                            expireTime = rs.getLong("expire_time"),
                            balance5h = rs.getInt("balance_5h"),
                            balanceDaily = rs.getInt("balance_daily"),
                            balanceWeekly = rs.getInt("balance_weekly"),
                            pool = rs.getInt("pool"),
                            weeklyUsed = rs.getInt("weekly_used"),
                            last5hRefresh = rs.getLong("last_5h_refresh"),
                            lastDailyReset = rs.getString("last_daily_reset"),
                            lastWeeklyReset = rs.getString("last_weekly_reset"),
                            bonusPoints = rs.getInt("bonus_points"),
                            originalPrice = rs.getString("original_price")
                        )
                    } else {
                        val data = PlayerData(uuid = uuid)
                        // MERGE 幂等：并发首次加载同一 uuid 不会主键冲突
                        insertPlayerData(data)
                        data
                    }
                }
            }
        }, writeExecutor).exceptionally { e ->
            log("加载玩家数据失败: $uuid", e)
            throw CompletionException(e)
        }
    }

    /**
     * 首次插入。使用 MERGE 保证幂等：并发加载同一 uuid 时不会因主键冲突而丢数据。
     */
    private fun insertPlayerData(data: PlayerData) {
        val conn = connection ?: throw IllegalStateException("Database not initialized")
        conn.prepareStatement(MERGE_SQL).use { ps ->
            fillSaveStatement(ps, data)
            ps.executeUpdate()
        }
    }

    /**
     * 绑定 MERGE 语句参数，索引与 [MERGE_SQL] 的列顺序严格一致：
     * 1 uuid / 2 tier / 3 subscribe_time / 4 expire_time / 5 balance_5h / 6 balance_daily /
     * 7 balance_weekly / 8 pool / 9 last_5h_refresh / 10 last_daily_reset / 11 last_weekly_reset /
     * 12 bonus_points / 13 original_price / 14 weekly_used
     */
    private fun fillSaveStatement(ps: PreparedStatement, data: PlayerData) {
        ps.setString(1, data.uuid)
        ps.setString(2, data.tier)
        ps.setLong(3, data.subscribeTime)
        ps.setLong(4, data.expireTime)
        ps.setInt(5, data.balance5h)
        ps.setInt(6, data.balanceDaily)
        ps.setInt(7, data.balanceWeekly)
        ps.setInt(8, data.pool)
        ps.setLong(9, data.last5hRefresh)
        ps.setString(10, data.lastDailyReset)
        ps.setString(11, data.lastWeeklyReset)
        ps.setInt(12, data.bonusPoints)
        ps.setString(13, data.originalPrice)
        ps.setInt(14, data.weeklyUsed)
    }

    fun loadAllPlayerData(): CompletableFuture<List<PlayerData>> {
        return CompletableFuture.supplyAsync({
            val conn = connection ?: throw IllegalStateException("Database not initialized")
            conn.prepareStatement("SELECT * FROM player_data WHERE tier != 'NONE'").use { ps ->
                ps.executeQuery().use { rs ->
                    val results = mutableListOf<PlayerData>()
                    while (rs.next()) {
                        results.add(PlayerData(
                            uuid = rs.getString("uuid"),
                            tier = rs.getString("tier"),
                            subscribeTime = rs.getLong("subscribe_time"),
                            expireTime = rs.getLong("expire_time"),
                            balance5h = rs.getInt("balance_5h"),
                            balanceDaily = rs.getInt("balance_daily"),
                            balanceWeekly = rs.getInt("balance_weekly"),
                            pool = rs.getInt("pool"),
                            weeklyUsed = rs.getInt("weekly_used"),
                            last5hRefresh = rs.getLong("last_5h_refresh"),
                            lastDailyReset = rs.getString("last_daily_reset"),
                            lastWeeklyReset = rs.getString("last_weekly_reset"),
                            bonusPoints = rs.getInt("bonus_points"),
                            originalPrice = rs.getString("original_price")
                        ))
                    }
                    results.toList()
                }
            }
        }, writeExecutor).exceptionally { e ->
            log("加载全部玩家数据失败", e)
            throw CompletionException(e)
        }
    }

    /**
     * 实际写入执行体。MERGE 同时覆盖"行不存在"和"行存在"两种情况，
     * 避免旧实现 UPDATE 在行缺失时静默丢数据。
     */
    private fun doSave(data: PlayerData) {
        val conn = connection ?: throw IllegalStateException("Database not initialized")
        conn.prepareStatement(MERGE_SQL).use { ps ->
            fillSaveStatement(ps, data)
            ps.executeUpdate()
        }
    }

    fun savePlayerData(data: PlayerData): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            doSave(data)
        }, writeExecutor).exceptionally { e ->
            log("保存玩家数据失败: ${data.uuid}", e)
            null
        }
    }

    fun savePlayerDataSync(data: PlayerData) {
        // 已在 DB 线程内（如从异步任务回调中同步保存）时直接执行，避免自己等自己死锁
        if (Thread.currentThread().name.startsWith(DB_THREAD_NAME)) {
            doSave(data)
            return
        }
        try {
            CompletableFuture.runAsync({ doSave(data) }, writeExecutor).join()
        } catch (e: Exception) {
            log("同步保存玩家数据失败: ${data.uuid}", e)
        }
    }

    fun close() {
        writeExecutor.shutdown()
        try {
            if (!writeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log("关闭数据库超时：仍有未完成的写入任务，可能丢失部分数据", null)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log("关闭数据库时被中断", e)
        }
        try {
            connection?.close()
        } catch (e: Exception) {
            log("关闭数据库连接失败", e)
        }
        connection = null
    }

    private companion object {
        const val DB_THREAD_NAME = "BuildingPlan-DB"

        val MERGE_SQL = """
            MERGE INTO player_data (uuid, tier, subscribe_time, expire_time, balance_5h, balance_daily, balance_weekly, pool, last_5h_refresh, last_daily_reset, last_weekly_reset, bonus_points, original_price, weekly_used) KEY(uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    }
}
