package com.techmodware.tester

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku
import java.security.MessageDigest
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val passHash = "35044bdb19c15a8e293ed035b8e9beaba366832a62eebfa86b1e7d48a2725418"
    private val reqCode = 100

    private lateinit var gateStatus: TextView
    private lateinit var gateMsg: TextView
    private lateinit var passInput: EditText
    private lateinit var output: TextView

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { if (::gateStatus.isInitialized) refreshGateStatus() }
    }

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permListener)
        showGate()
    }

    override fun onResume() {
        super.onResume()
        if (::gateStatus.isInitialized && gateStatus.parent != null) refreshGateStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun root(): LinearLayout {
        val pad = (16 * dp).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 22f; setTypeface(null, Typeface.BOLD)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { onClick() }
    }

    private fun sha256(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun shizukuReady() =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    private fun showGate() {
        val r = root()
        r.addView(title("Techmodware Tester"))

        gateStatus = TextView(this).apply { setPadding(0, (12 * dp).toInt(), 0, (8 * dp).toInt()) }
        r.addView(gateStatus)
        r.addView(button("1. Authorize Shizuku") { requestShizuku() })

        r.addView(TextView(this).apply {
            text = "2. Password"; setPadding(0, (16 * dp).toInt(), 0, 0)
        })
        passInput = EditText(this).apply {
            hint = "Masukkan password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        r.addView(passInput)

        r.addView(button("Masuk") { tryEnter() })
        gateMsg = TextView(this).apply { setPadding(0, (8 * dp).toInt(), 0, 0) }
        r.addView(gateMsg)

        setContentView(r)
        refreshGateStatus()
    }

    private fun refreshGateStatus() {
        gateStatus.text = when {
            !Shizuku.pingBinder() -> "Shizuku: belum berjalan. Buka aplikasi Shizuku lalu Start."
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                "Shizuku: aktif, tapi belum diizinkan."
            else -> "Shizuku: terautentikasi ✔"
        }
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) { refreshGateStatus(); return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(reqCode)
        }
    }

    private fun tryEnter() {
        if (!shizukuReady()) { gateMsg.text = "Authorize Shizuku dulu sebelum masuk."; return }
        val input = passInput.text.toString().trim().uppercase()
        if (sha256(input) != passHash) { gateMsg.text = "Password salah."; return }
        showMenu()
    }

    private fun showMenu() {
        val r = root()
        r.addView(title("Techmodware Tester"))

        r.addView(button("INJECT") { run("all") })

        r.addView(TextView(this).apply {
            text = "Pengubah Resolusi"; textSize = 16f
            setTypeface(null, Typeface.BOLD); setPadding(0, (16 * dp).toInt(), 0, 0)
        })

        val res = listOf("560p" to "560", "720p" to "720", "1080p" to "1080",
            "1.5K" to "1200", "2K" to "1440", "4K" to "2160")
        res.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (label, v) ->
                row.addView(button(label) { run("res", v) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            r.addView(row)
        }
        r.addView(button("Reset Setelan Awal (resolusi)") { run("resreset") })

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 12f; setTextIsSelectable(true)
        }
        r.addView(ScrollView(this).apply { addView(output) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(r)
    }

    private fun run(vararg args: String) {
        if (!shizukuReady()) { output.text = "Shizuku tidak aktif / izin hilang. Restart aplikasi."; return }
        output.text = "Menjalankan..."
        thread {
            val result = try { ShellRunner.runScript(this, *args) }
            catch (e: Throwable) { "Error: ${e.message}" }
            runOnUiThread { output.text = result }
        }
    }
}
