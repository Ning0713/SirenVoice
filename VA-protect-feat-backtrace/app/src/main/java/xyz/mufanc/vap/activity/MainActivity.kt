package xyz.mufanc.vap.activity

import android.os.Bundle
import android.os.SystemProperties
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.topjohnwu.superuser.Shell
import xyz.mufanc.vap.BuildConfig
import xyz.mufanc.vap.R
import xyz.mufanc.vap.util.Log

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Shell.getShell { shell ->
            Log.d(TAG, "main shell: $shell")
            if (shell == null) {
                Log.w(TAG, "Failed to get root shell!")
            } else {
                Log.i(TAG, "Root shell obtained successfully")
            }
        }

        mSwitch = findViewById(R.id.switch_btn)
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
    }

    private fun refreshUi() {
        mDefenceEnabled = SystemProperties.getBoolean("debug.vap.defence.enable", false)
        mSwitch.isChecked = mDefenceEnabled
        Log.d(TAG, "Defence enabled: $mDefenceEnabled")
    }
}
