package xyz.mufanc.vap.util

import android.os.SystemProperties
import org.joor.Reflect

object AttackStatistics {
    private const val TAG = "AttackStatistics"
    private const val PROP_TOTAL_ATTACKS = "debug.vap.total_attacks"
    private const val PROP_BLOCKED_ATTACKS = "debug.vap.blocked_attacks"
    private const val PROP_LAST_ATTACK_TIME = "debug.vap.last_attack_time"

    fun recordAttack(blocked: Boolean) {
        try {
            // 读取当前值
            val totalAttacks = getTotalAttacks() + 1
            val blockedAttacks = if (blocked) getBlockedAttacks() + 1 else getBlockedAttacks()
            val lastAttackTime = System.currentTimeMillis()
            
            // 写入 SystemProperties
            Reflect.on(SystemProperties::class.java)
                .call("set", PROP_TOTAL_ATTACKS, totalAttacks.toString())
            
            Reflect.on(SystemProperties::class.java)
                .call("set", PROP_BLOCKED_ATTACKS, blockedAttacks.toString())
            
            Reflect.on(SystemProperties::class.java)
                .call("set", PROP_LAST_ATTACK_TIME, lastAttackTime.toString())
            
            Log.i(TAG, "Attack recorded: total=$totalAttacks, blocked=${if (blocked) "YES ($blockedAttacks)" else "NO"}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to record attack", e)
        }
    }

    fun getTotalAttacks(): Int {
        return try {
            val value = Reflect.on(SystemProperties::class.java)
                .call("get", PROP_TOTAL_ATTACKS, "0")
                .get<String>()
            value.toIntOrNull() ?: 0
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get total attacks", e)
            0
        }
    }

    fun getBlockedAttacks(): Int {
        return try {
            val value = Reflect.on(SystemProperties::class.java)
                .call("get", PROP_BLOCKED_ATTACKS, "0")
                .get<String>()
            value.toIntOrNull() ?: 0
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get blocked attacks", e)
            0
        }
    }

    fun getLastAttackTime(): Long {
        return try {
            val value = Reflect.on(SystemProperties::class.java)
                .call("get", PROP_LAST_ATTACK_TIME, "0")
                .get<String>()
            value.toLongOrNull() ?: 0
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get last attack time", e)
            0
        }
    }

    fun clearStatistics() {
        try {
            Reflect.on(SystemProperties::class.java)
                .call("set", PROP_TOTAL_ATTACKS, "0")
            Reflect.on(SystemProperties::class.java)
                .call("set", PROP_BLOCKED_ATTACKS, "0")
            Reflect.on(SystemProperties::class.java)
                .call("set", PROP_LAST_ATTACK_TIME, "0")
            Log.i(TAG, "Statistics cleared")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to clear statistics", e)
        }
    }
}
