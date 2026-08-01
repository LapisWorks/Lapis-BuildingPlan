package mc506lw.buildingPlan.model

data class PlayerData(
    val uuid: String,
    var tier: String = "NONE",
    var subscribeTime: Long = 0,
    var expireTime: Long = 0,
    var balance5h: Int = 0,
    var balanceDaily: Int = 0,
    var balanceWeekly: Int = 0,
    var pool: Int = 0,
    var last5hRefresh: Long = 0,
    var lastDailyReset: String = "",
    var lastWeeklyReset: String = "",
    var bonusPoints: Int = 0,
    var originalPrice: String = ""
)
