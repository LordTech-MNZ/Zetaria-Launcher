package com.zetaria.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var bg: AlienBackgroundView
    private lateinit var homeLayer: LinearLayout
    private lateinit var clockBlock: LinearLayout
    private lateinit var clock: TextView
    private lateinit var seconds: TextView
    private lateinit var dateText: TextView
    private lateinit var statusText: TextView
    private lateinit var hudLeft: TextView
    private lateinit var hudRight: TextView
    private lateinit var hint: TextView
    private lateinit var dock: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var drawerHandle: FrameLayout
    private lateinit var handleBar: View
    private lateinit var drawerTitle: TextView
    private lateinit var search: EditText
    private lateinit var console: TextView
    private lateinit var appList: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var homeGestures: GestureDetector
    private lateinit var handleGestures: GestureDetector

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("zetaria", MODE_PRIVATE) }

    private var allApps: List<AppInfo> = emptyList()
    private var shownApps: List<AppInfo> = emptyList()
    private var drawerOpen = false
    private var animEnabled = true
    private var accent: Int = 0xFF00FFC8.toInt()

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEE dd MMM", Locale.getDefault())

    private val statusLines = arrayOf(
        "// SISTEMA NOMINAL",
        "// ESCANEANDO ESPECTRO",
        "// ENLACE ESTABLE",
        "// NUCLEO SINCRONIZADO"
    )

    private val themes = linkedMapOf(
        "zeta" to 0xFF00FFC8.toInt(),
        "violeta" to 0xFFB26BFF.toInt(),
        "magenta" to 0xFFFF2E9A.toInt(),
        "ambar" to 0xFFFFB02E.toInt(),
        "hielo" to 0xFF5CC8FF.toInt()
    )

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000L - (System.currentTimeMillis() % 1000L))
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadApps()
        }
    }

    // ------------------------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }

        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        bg = findViewById(R.id.bg)
        homeLayer = findViewById(R.id.homeLayer)
        clockBlock = findViewById(R.id.clockBlock)
        clock = findViewById(R.id.clock)
        seconds = findViewById(R.id.seconds)
        dateText = findViewById(R.id.dateText)
        statusText = findViewById(R.id.statusText)
        hudLeft = findViewById(R.id.hudLeft)
        hudRight = findViewById(R.id.hudRight)
        hint = findViewById(R.id.hint)
        dock = findViewById(R.id.dock)
        drawer = findViewById(R.id.drawer)
        drawerHandle = findViewById(R.id.drawerHandle)
        handleBar = findViewById(R.id.handleBar)
        drawerTitle = findViewById(R.id.drawerTitle)
        search = findViewById(R.id.search)
        console = findViewById(R.id.console)
        appList = findViewById(R.id.appList)

        val controller = WindowCompat.getInsetsController(window, root)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        accent = themes[prefs.getString("theme", "zeta")] ?: themes.getValue("zeta")
        animEnabled = prefs.getBoolean("anim", true)

        // Cajon de apps
        adapter = AppAdapter(
            onClick = { openApp(it) },
            onLongClick = { showAppMenu(it) }
        )
        appList.layoutManager = GridLayoutManager(this, 4)
        appList.adapter = adapter
        appList.setHasFixedSize(true)
        appList.clipToPadding = false

        // Insets (barras del sistema y teclado)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            homeLayer.setPadding(dp(20), bars.top, dp(20), bars.bottom)
            drawer.setPadding(dp(16), bars.top, dp(16), maxOf(bars.bottom, ime.bottom))
            insets
        }

        // Los anillos giran alrededor del reloj
        clockBlock.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            bg.ringCenterY = (top + bottom) / 2f
        }

        // Gestos del inicio: deslizar arriba = nucleo, abajo = notificaciones, pulsacion larga = menu
        homeGestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dy = e2.y - (e1?.y ?: e2.y)
                val dx = e2.x - (e1?.x ?: e2.x)
                if (abs(dy) > abs(dx) && abs(dy) > dp(60)) {
                    if (dy < 0) openDrawer() else expandNotifications()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showCoreMenu()
            }
        })
        homeLayer.setOnTouchListener { _, e -> homeGestures.onTouchEvent(e) }
        hint.setOnClickListener { openDrawer() }

        // Gestos del asa del cajon: deslizar abajo o tocar = cerrar
        handleGestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dy = e2.y - (e1?.y ?: e2.y)
                if (dy > dp(30)) {
                    closeDrawer()
                    return true
                }
                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                closeDrawer()
                return true
            }
        })
        drawerHandle.setOnTouchListener { _, e -> handleGestures.onTouchEvent(e) }

        // Buscador / terminal
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                if (!q.startsWith(">")) console.visibility = View.GONE
                filterApps(q)
            }
        })
        search.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                isEnter
            ) {
                val text = search.text.toString().trim()
                if (text.startsWith(">")) {
                    runCommand(text)
                    search.setText(">")
                    search.setSelection(search.text.length)
                } else {
                    shownApps.firstOrNull()?.let { openApp(it) }
                }
                true
            } else {
                false
            }
        }

        applyAccent()

        root.post {
            drawer.translationY = root.height.toFloat()
            drawer.visibility = View.INVISIBLE
            root.requestApplyInsets()
        }

        loadApps()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStop() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (animEnabled) bg.start()
        updateBattery()
        handler.removeCallbacks(clockTick)
        handler.post(clockTick)
    }

    override fun onPause() {
        bg.stop()
        handler.removeCallbacks(clockTick)
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Boton Home: cierra el cajon y vuelve al inicio
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (drawerOpen) closeDrawer()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un launcher nunca se cierra: solo cierra el cajon si esta abierto.
        if (drawerOpen) closeDrawer()
    }

    // ------------------------------------------------------------------------------------
    // Estilo
    // ------------------------------------------------------------------------------------

    private fun dp(v: Int): Int = Ui.dp(this, v.toFloat()).toInt()

    private fun applyAccent() {
        val white = 0xFFEAFFF8.toInt()
        bg.accent = accent
        hudLeft.setTextColor(Ui.alpha(accent, 190))
        hudRight.setTextColor(Ui.alpha(accent, 190))
        seconds.setTextColor(accent)
        dateText.setTextColor(Ui.alpha(accent, 220))
        statusText.setTextColor(Ui.alpha(accent, 150))
        hint.setTextColor(Ui.alpha(accent, 110))
        clock.setTextColor(white)
        clock.setShadowLayer(Ui.dp(this, 18f), 0f, 0f, Ui.alpha(accent, 200))
        drawerTitle.setTextColor(accent)
        handleBar.background = Ui.pill(this, Ui.alpha(accent, 170))
        search.background = Ui.panel(this, accent, 170)
        search.setTextColor(white)
        search.setHintTextColor(Ui.alpha(accent, 110))
        console.setTextColor(accent)
        console.background = Ui.panel(this, accent, 120)
        adapter.accent = accent
        renderDock()
    }

    private fun applyTheme(name: String) {
        val color = themes[name] ?: return
        accent = color
        prefs.edit().putString("theme", name).apply()
        applyAccent()
    }

    private fun setAnimations(on: Boolean) {
        animEnabled = on
        prefs.edit().putBoolean("anim", on).apply()
        if (on) bg.start() else bg.stop()
    }

    // ------------------------------------------------------------------------------------
    // Reloj y estado
    // ------------------------------------------------------------------------------------

    private fun updateClock() {
        val now = Calendar.getInstance()
        clock.text = timeFmt.format(now.time)
        val sec = now.get(Calendar.SECOND)
        seconds.text = String.format(Locale.US, "SEC %02d", sec)
        dateText.text = String.format(
            Locale.US, "ZT-%d.%03d :: %s",
            now.get(Calendar.YEAR),
            now.get(Calendar.DAY_OF_YEAR),
            dateFmt.format(now.time).uppercase(Locale.getDefault())
        )
        if (sec % 6 == 0) statusText.text = statusLines[(sec / 6) % statusLines.size]
        if (sec % 20 == 0) updateBattery()
    }

    private fun batteryInfo(): Pair<Int, Boolean> {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return pct to charging
    }

    private fun updateBattery() {
        val (pct, charging) = batteryInfo()
        hudRight.text = if (pct >= 0) "BAT $pct%" + (if (charging) " +" else "") else "BAT --"
    }

    // ------------------------------------------------------------------------------------
    // Apps y dock
    // ------------------------------------------------------------------------------------

    private fun loadApps() {
        executor.execute {
            val apps = try {
                AppRepository.load(this)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                allApps = apps
                filterApps(search.text.toString())
                renderDock()
            }
        }
    }

    private fun filterApps(q: String) {
        val query = q.trim()
        shownApps = if (query.isEmpty() || query.startsWith(">")) {
            allApps
        } else {
            allApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        adapter.submit(shownApps)
    }

    private fun openApp(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("No se pudo abrir ${app.label}")
        }
        closeDrawer(animated = false)
    }

    private fun dockPackages(): List<String> {
        val saved = prefs.getString("dock", null)
        return if (saved != null) saved.split(",").filter { it.isNotBlank() } else defaultDock()
    }

    @Suppress("DEPRECATION")
    private fun defaultDock(): List<String> {
        val pm = packageManager
        val installed = allApps.map { it.packageName }.toSet()
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        return intents
            .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .filter { it in installed }
            .distinct()
    }

    private fun renderDock() {
        dock.removeAllViews()
        dock.background = Ui.panel(this, accent, 150)
        val apps = dockPackages().mapNotNull { p -> allApps.firstOrNull { it.packageName == p } }
        val size = dp(54)
        for (app in apps) {
            val frame = FrameLayout(this)
            frame.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            frame.background = Ui.frame(this, accent)
            val iv = ImageView(this)
            iv.setImageDrawable(app.freshIcon(resources))
            frame.addView(iv, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
            frame.setOnClickListener { openApp(app) }
            frame.setOnLongClickListener {
                showAppMenu(app)
                true
            }
            dock.addView(frame)
        }
        dock.visibility = if (apps.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    private fun togglePin(app: AppInfo) {
        val list = dockPackages().toMutableList()
        if (list.contains(app.packageName)) {
            list.remove(app.packageName)
        } else {
            if (list.size >= 5) {
                toast("Dock lleno (max. 5)")
                return
            }
            list.add(app.packageName)
        }
        prefs.edit().putString("dock", list.joinToString(",")).apply()
        renderDock()
    }

    private fun showAppMenu(app: AppInfo) {
        val pinned = dockPackages().contains(app.packageName)
        val items = arrayOf(
            if (pinned) "Quitar del dock" else "Fijar en el dock",
            "Informacion de la app",
            "Desinstalar"
        )
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> togglePin(app)
                    1 -> safeStart(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", app.packageName, null)
                        )
                    )
                    2 -> safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                }
            }
            .show()
    }

    // ------------------------------------------------------------------------------------
    // Cajon de apps
    // ------------------------------------------------------------------------------------

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        drawer.visibility = View.VISIBLE
        drawer.animate().cancel()
        drawer.animate()
            .translationY(0f)
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator())
            .start()
        homeLayer.animate().alpha(0.15f).setDuration(260).start()
    }

    private fun closeDrawer(animated: Boolean = true) {
        if (!drawerOpen && drawer.visibility != View.VISIBLE) return
        drawerOpen = false
        hideKeyboard()
        search.setText("")
        val h = root.height.toFloat()
        drawer.animate().cancel()
        if (animated) {
            drawer.animate()
                .translationY(h)
                .setDuration(220)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { if (!drawerOpen) drawer.visibility = View.INVISIBLE }
                .start()
        } else {
            drawer.translationY = h
            drawer.visibility = View.INVISIBLE
        }
        homeLayer.animate().alpha(1f).setDuration(220).start()
        appList.scrollToPosition(0)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }

    // ------------------------------------------------------------------------------------
    // Menu del nucleo (pulsacion larga en el inicio)
    // ------------------------------------------------------------------------------------

    private fun showCoreMenu() {
        val items = arrayOf(
            "Energia (color del nucleo)",
            if (animEnabled) "Animaciones: ON" else "Animaciones: OFF",
            "Reindexar aplicaciones",
            "Ajustes del sistema"
        )
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("// NUCLEO ZETARIA")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showThemePicker()
                    1 -> setAnimations(!animEnabled)
                    2 -> loadApps()
                    3 -> safeStart(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .show()
    }

    private fun showThemePicker() {
        val names = themes.keys.toTypedArray()
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("// ENERGIA DEL NUCLEO")
            .setItems(names) { _, i -> applyTheme(names[i]) }
            .show()
    }

    // ------------------------------------------------------------------------------------
    // Terminal
    // ------------------------------------------------------------------------------------

    private fun printOut(text: String) {
        console.text = text
        console.visibility = View.VISIBLE
    }

    private fun runCommand(raw: String) {
        val input = raw.trim().removePrefix(">").trim()
        val parts = input.split(Regex("\\s+"), limit = 2)
        val cmd = parts[0].lowercase(Locale.ROOT)
        val arg = if (parts.size > 1) parts[1].trim() else ""

        val out: String? = when (cmd) {
            "", "help", "?" -> HELP
            "whoami" -> "zetariano@${Build.DEVICE.lowercase(Locale.ROOT)}"
            "uname" -> "ZetariaOS 1.0.0 | Android ${Build.VERSION.RELEASE} | ${Build.MODEL}"
            "sysinfo", "neofetch" -> sysInfo()
            "ls", "apps" -> {
                val names = allApps.take(15).joinToString("  ") { it.label.lowercase().replace(' ', '_') }
                val extra = if (allApps.size > 15) "\n... (+${allApps.size - 15} mas)" else ""
                "${allApps.size} aplicaciones indexadas\n$names$extra"
            }
            "launch", "run", "open" -> {
                val q = arg.lowercase()
                val app = allApps.firstOrNull { it.label.lowercase() == q }
                    ?: allApps.firstOrNull { it.label.lowercase().contains(q) }
                if (q.isEmpty()) {
                    "uso: launch <nombre de app>"
                } else if (app == null) {
                    "launch: '$arg' no encontrada"
                } else {
                    openApp(app)
                    null
                }
            }
            "theme" -> {
                if (themes.containsKey(arg.lowercase())) {
                    applyTheme(arg.lowercase())
                    "energia -> ${arg.lowercase()}"
                } else {
                    "uso: theme <${themes.keys.joinToString("|")}>"
                }
            }
            "anim" -> when (arg.lowercase()) {
                "on" -> {
                    setAnimations(true)
                    "animaciones: ON"
                }
                "off" -> {
                    setAnimations(false)
                    "animaciones: OFF"
                }
                else -> "uso: anim on|off"
            }
            "wifi" -> {
                safeStart(Intent(Settings.ACTION_WIFI_SETTINGS))
                null
            }
            "bt", "bluetooth" -> {
                safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                null
            }
            "settings" -> {
                safeStart(Intent(Settings.ACTION_SETTINGS))
                null
            }
            "dock" -> {
                if (arg.lowercase() == "reset") {
                    prefs.edit().remove("dock").apply()
                    renderDock()
                    "dock restablecido"
                } else {
                    val names = dockPackages().mapNotNull { p -> allApps.firstOrNull { it.packageName == p }?.label }
                    "dock: " + if (names.isEmpty()) "(vacio)" else names.joinToString(", ")
                }
            }
            "reload" -> {
                loadApps()
                "reindexando..."
            }
            "clear", "cls" -> {
                console.visibility = View.GONE
                null
            }
            "exit", "close" -> {
                closeDrawer()
                null
            }
            else -> "zsh: comando no encontrado: $cmd (prueba >help)"
        }
        if (out != null) printOut(out)
    }

    private fun sysInfo(): String {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalMb = mi.totalMem / 1048576L
        val freeMb = mi.availMem / 1048576L
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / 1e9
        val freeGb = stat.availableBytes / 1e9
        val (pct, charging) = batteryInfo()
        val up = SystemClock.elapsedRealtime() / 60000L
        val bat = if (pct >= 0) "$pct%" + (if (charging) " (cargando)" else "") else "--"
        return buildString {
            appendLine("zetariano@${Build.DEVICE.lowercase(Locale.ROOT)}")
            appendLine("----------------------------")
            appendLine("OS     : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("HW     : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI    : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
            appendLine("CPU    : ${Runtime.getRuntime().availableProcessors()} nucleos")
            appendLine("RAM    : $freeMb / $totalMb MB libres")
            appendLine(String.format(Locale.US, "DISCO  : %.1f / %.1f GB libres", freeGb, totalGb))
            appendLine("BAT    : $bat")
            append("UPTIME : ${up / 60}h ${up % 60}m")
        }
    }

    // ------------------------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------------------------

    private fun safeStart(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            toast("Accion no disponible en este equipo")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /** Despliega el panel de notificaciones (requiere EXPAND_STATUS_BAR; usa reflexion). */
    @SuppressLint("WrongConstant")
    private fun expandNotifications() {
        try {
            val sbs = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(sbs)
        } catch (_: Throwable) {
            // Algunos ROMs lo bloquean: se ignora.
        }
    }

    companion object {
        private const val DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert

        private const val HELP = "comandos:\n" +
            "  help             esta ayuda\n" +
            "  sysinfo          estado del sistema\n" +
            "  ls               listar apps\n" +
            "  launch <app>     abrir una app\n" +
            "  theme <nombre>   zeta|violeta|magenta|ambar|hielo\n" +
            "  anim on|off      animaciones del fondo\n" +
            "  wifi | bt | settings\n" +
            "  dock [reset]     ver / restablecer dock\n" +
            "  reload | clear | exit"
    }
}
