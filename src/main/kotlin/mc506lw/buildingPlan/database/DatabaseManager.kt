package mc506lw.buildingPlan.database

import mc506lw.buildingPlan.model.PlayerData
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.ConcurrentHashMap

class DatabaseManager(private val dataFolder: File) {

    private val dbFile = File(dataFolder, "data")
    private var connection: Connection? = null
    private val writeExecutor: ExecutorService = Executors.newSingleThreadExecutor()
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
                    original_price  TEXT NOT NULL DEFAULT ''
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
        }
    }

    fun getLock(uuid: String): ReentrantLock {
        return playerLocks.computeIfAbsent(uuid) { ReentrantLock() }
    }

    fun removeLock(uuid: String) {
        playerLocks.remove(uuid)
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
                            last5hRefresh = rs.getLong("last_5h_refresh"),
                            lastDailyReset = rs.getString("last_daily_reset"),
                            lastWeeklyReset = rs.getString("last_weekly_reset"),
                            bonusPoints = rs.getInt("bonus_points"),
                            originalPrice = rs.getString("original_price")
                        )
                    } else {
                        val data = PlayerData(uuid = uuid)
                        insertPlayerData(data)
                        data
                    }
                }
            }
        }, writeExecutor)
    }

    private fun insertPlayerData(data: PlayerData) {
        val conn = connection ?: throw IllegalStateException("Database not initialized")
        conn.prepareStatement("""
            INSERT INTO player_data (uuid, tier, subscribe_time, expire_time, balance_5h, balance_daily, balance_weekly, pool, last_5h_refresh, last_daily_reset, last_weekly_reset, bonus_points, original_price)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()).use { ps ->
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
            ps.executeUpdate()
        }
    }

    private fun fillSaveStatement(ps: PreparedStatement, data: PlayerData) {
        ps.setString(1, data.tier)
        ps.setLong(2, data.subscribeTime)
        ps.setLong(3, data.expireTime)
        ps.setInt(4, data.balance5h)
        ps.setInt(5, data.balanceDaily)
        ps.setInt(6, data.balanceWeekly)
        ps.setInt(7, data.pool)
        ps.setLong(8, data.last5hRefresh)
        ps.setString(9, data.lastDailyReset)
        ps.setString(10, data.lastWeeklyReset)
        ps.setInt(11, data.bonusPoints)
        ps.setString(12, data.originalPrice)
        ps.setString(13, data.uuid)
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
                            last5hRefresh = rs.getLong("last_5h_refresh"),
                            lastDailyReset = rs.getString("last_daily_reset"),
                            lastWeeklyReset = rs.getString("last_weekly_reset"),
                            bonusPoints = rs.getInt("bonus_points"),
                            originalPrice = rs.getString("original_price")
                        ))
                    }
                    results
                }
            }
        }, writeExecutor)
    }

    fun savePlayerData(data: PlayerData): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            val conn = connection ?: throw IllegalStateException("Database not initialized")
            conn.prepareStatement("""
                UPDATE player_data SET
                    tier = ?, subscribe_time = ?, expire_time = ?,
                    balance_5h = ?, balance_daily = ?, balance_weekly = ?,
                    pool = ?, last_5h_refresh = ?, last_daily_reset = ?,
                    last_weekly_reset = ?, bonus_points = ?, original_price = ?
                WHERE uuid = ?
            """.trimIndent()).use { ps ->
                fillSaveStatement(ps, data)
                ps.executeUpdate()
            }
        }, writeExecutor)
    }

    fun savePlayerDataSync(data: PlayerData) {
        CompletableFuture.runAsync({
            val conn = connection ?: throw IllegalStateException("Database not initialized")
            conn.prepareStatement("""
                UPDATE player_data SET
                    tier = ?, subscribe_time = ?, expire_time = ?,
                    balance_5h = ?, balance_daily = ?, balance_weekly = ?,
                    pool = ?, last_5h_refresh = ?, last_daily_reset = ?,
                    last_weekly_reset = ?, bonus_points = ?, original_price = ?
                WHERE uuid = ?
            """.trimIndent()).use { ps ->
                fillSaveStatement(ps, data)
                ps.executeUpdate()
            }
        }, writeExecutor).join()
    }

    fun close() {
        writeExecutor.shutdown()
        connection?.close()
        connection = null
    }
}
