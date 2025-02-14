package d.prototype.otadispenser.activities

data class TransactionData(
    val availableSize: Int,
    val size: Int,
    val amount: Double,
    val volume: Double,
    val concentration: Double,
    val attendantId: String,
    val customerId: String,
    val lastFlowCount: Int,
    val epoch: Long,
    val pid: Int,
    val flag: Int,
    val transactionId: String,
    val transactionType: String,
    val vehicleId: String
)