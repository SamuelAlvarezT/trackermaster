package com.trackermaster.core.domain.unlock

data class UnlockCycle(val id: String, val screenOffElapsedMs: Long, val bootCount: Int)
data class UnlockDecision(val eligible: Boolean, val destination: String)

object UnlockLaunchRules {
    val destinations = listOf("home","habits","tasks","expense","focus","journal")
    val intervalsMinutes = listOf(30,60,120,180)
    fun decide(cycle: UnlockCycle?, nowElapsedMs: Long, bootCount: Int, intervalMinutes: Int,
        rotation: Int): UnlockDecision {
        require(intervalMinutes in intervalsMinutes)
        val eligible = cycle != null && cycle.bootCount == bootCount &&
            nowElapsedMs >= cycle.screenOffElapsedMs && nowElapsedMs-cycle.screenOffElapsedMs >= intervalMinutes*60_000L
        return UnlockDecision(eligible,destinations[Math.floorMod(rotation,destinations.size)])
    }
    fun nextRotation(current: Int) = (Math.floorMod(current,destinations.size)+1)%destinations.size
}
