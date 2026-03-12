package xyz.mufanc.vap.activity

import android.os.Bundle
import android.os.SystemProperties
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.topjohnwu.superuser.Shell
import xyz.mufanc.vap.BuildConfig
import xyz.mufanc.vap.R
import xyz.mufanc.vap.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"

        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR or Shell.FLAG_MOUNT_MASTER)
            )
        }
    }

    private var mDefenceEnabled = false
    private lateinit var mSwitch: SwitchCompat
    private lateinit var mStatusText: TextView
    private lateinit var mTotalAttacks: TextView
    private lateinit var mBlockedAttacks: TextView
    private lateinit var mLastAttackTime: TextView
    private lateinit var mVersionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        Shell.getShell { shell ->
            Log.d(TAG, "main shell: $shell")
            if (shell == null) {
                Log.w(TAG, "Failed to get root shell!")
            } else {
                Log.i(TAG, "Root shell obtained successfully")
            }
        }

        // Initialize views
        mSwitch = findViewById(R.id.switch_btn)
        mStatusText = findViewById(R.id.status_text)
        mTotalAttacks = findViewById(R.id.total_attacks)
        mBlockedAttacks = findViewById(R.id.blocked_attacks)
        mLastAttackTime = findViewById(R.id.last_attack_time)
        mVersionText = findViewById(R.id.version_text)

        // Set version
        mVersionText.text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        mSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (mDefenceEnabled != isChecked) {
                Log.i(TAG, "Switch changed to: $isChecked")
                val result = if (isChecked) {
                    Shell.cmd("setprop debug.vap.defence.enable 1").exec()
                } else {
                    Shell.cmd("setprop debug.vap.defence.enable 0").exec()
                }
                
                Log.i(TAG, "Shell command result: code=${result.code}, out=${result.out}, err=${result.err}")
                
                if (!result.isSuccess) {
                    Log.w(TAG, "Failed to set property! Need root permission?")
                } else {
                    Log.i(TAG, "Property set successfully")
                }

                refreshUi()
            }
        }
        
        refreshUi()
        loadStatistics()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        loadStatistics()
    }

    private fun refreshUi() {
        mDefenceEnabled = SystemProperties.getBoolean("debug.vap.defence.enable", false)
        mSwitch.isChecked = mDefenceEnabled
        
        if (mDefenceEnabled) {
            mStatusText.text = "已启用"
            mStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            mStatusText.text = "已禁用"
            mStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
        
        Log.d(TAG, "Defence enabled: $mDefenceEnabled")
    }

    private fun loadStatistics() {
        val totalAttacks = xyz.mufanc.vap.util.AttackStatistics.getTotalAttacks()
        val blockedAttacks = xyz.mufanc.vap.util.AttackStatistics.getBlockedAttacks()
        val lastAttackTime = xyz.mufanc.vap.util.AttackStatistics.getLastAttackTime()

        mTotalAttacks.text = totalAttacks.toString()
        mBlockedAttacks.text = blockedAttacks.toString()

        if (lastAttackTime > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            mLastAttackTime.text = dateFormat.format(Date(lastAttackTime))
        } else {
            mLastAttackTime.text = "无"
        }

        Log.d(TAG, "Statistics loaded: total=$totalAttacks, blocked=$blockedAttacks")
    }
}
