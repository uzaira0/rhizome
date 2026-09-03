package com.geekbeast.hazelcast


/**
 * Hook for one-time upgrade work that must run before Hazelcast starts.
 */
interface PreHazelcastUpgradeService {
    fun runUpgrade()
}

class NoOpPreHazelcastUpgradeService : PreHazelcastUpgradeService {
    override fun runUpgrade() {
    }
}
