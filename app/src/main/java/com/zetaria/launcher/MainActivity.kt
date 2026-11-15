package com.zetaria.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

class MainActivity : Activity() {

    private class Opt(val label: String, val action: () -> Unit)

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
    private lateinit var hintRecents: TextView
    private lateinit var dock: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var drawerHandle: FrameLayout
    private lateinit var handleBar: View
    private lateinit var drawerTitle: TextView
    private lateinit var search: EditText
    private lateinit var console: TextView
    private lateinit var appList: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var recents: LinearLayout
    private lateinit var recentsTitle: TextView
    private lateinit var recentsList: RecyclerView
    private lateinit var recentsMessage: TextView
    private lateinit var recentsGrant: TextView
    private lateinit var recentsClear: TextView
    private lateinit var recentAdapter: RecentAdapter
    private lateinit var homeGestures: GestureDetector
    private lateinit var handleGestures: GestureDetector

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("zetaria", MODE_PRIVATE) }

    private var allApps: List<AppInfo> = emptyList()
    private var shownApps: List<AppInfo> = emptyList()
    private var drawerOpen = false
    private var recentsOpen = false
    private var animEnabled = true
    private var clock12 = true
    private var fxOverImage = true
    private var scrimIndex = 2
    private var accent: Int = 0xFF00FFC8.toInt()

    private val time12Fmt = SimpleDateFormat("h:mm", Locale.US)
    private val time24Fmt = SimpleDateFormat("HH:mm", Locale.US)
    private val dowFmt = SimpleDateFormat("EEE", Locale.getDefault())
    private val monFmt = SimpleDateFormat("MMM", Locale.getDefault())

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
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
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
        hintRecents = findViewById(R.id.hintRecents)
        dock = findViewById(R.id.dock)
        drawer = findViewById(R.id.drawer)
        drawerHandle = findViewById(R.id.drawerHandle)
        handleBar = findViewById(R.id.handleBar)
        drawerTitle = findViewById(R.id.drawerTitle)
        search = findViewById(R.id.search)
        console = findViewById(R.id.console)
        appList = findViewById(R.id.appList)
        recents = findViewById(R.id.recents)
        recentsTitle = findViewById(R.id.recentsTitle)
        recentsList = findViewById(R.id.recentsList)
        recentsMessage = findViewById(R.id.recentsMessage)
        recentsGrant = findViewById(R.id.recentsGrant)
        recentsClear = findViewById(R.id.recentsClear)

        val controller = WindowCompat.getInsetsController(window, root)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        // Preferencias guardadas
        accent = themes[prefs.getString("theme", "zeta")] ?: themes.getValue("zeta")
        animEnabled = prefs.getBoolean("anim", true)
        clock12 = prefs.getBoolean("clock12", true)
        fxOverImage = prefs.getBoolean("fx", true)
        scrimIndex = prefs.getInt("scrim", 2).coerceIn(0, SCRIM_ALPHAS.size - 1)
        bg.imageEffects = fxOverImage
        bg.scrimAlpha = SCRIM_ALPHAS[scrimIndex]

        // Cajon de apps
        adapter = AppAdapter(
            onClick = { openApp(it) },
            onLongClick = { showAppMenu(it) }
        )
        appList.layoutManager = GridLayoutManager(this, 4)
        appList.adapter = adapter
        appList.setHasFixedSize(true)
        appList.clipToPadding = false

        // Panel de recientes
        recentAdapter = RecentAdapter(
            onClick = { openApp(it) },
            onLongClick = { showAppMenu(it) }
        )
        recentsList.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        recentsList.adapter = recentAdapter
        LinearSnapHelper().attachToRecyclerView(recentsList)
        recents.setOnClickListener { closeRecents() }
        recentsGrant.setOnClickListener { openUsageAccess() }
        recentsClear.setOnClickListener { clearRecents() }

        // Insets (barras del sistema y teclado)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            homeLayer.setPadding(dp(20), bars.top, dp(20), bars.bottom)
            drawer.setPadding(dp(16), bars.top, dp(16), maxOf(bars.bottom, ime.bottom))
            recents.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        // Los anillos giran alrededor del reloj
        clockBlock.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            bg.ringCenterY = (top + bottom) / 2f
        }

        // Gestos del inicio: arriba = nucleo, abajo = notificaciones,
        // izquierda = recientes, pulsacion larga = menu
        homeGestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dy = e2.y - (e1?.y ?: e2.y)
                val dx = e2.x - (e1?.x ?: e2.x)
                if (abs(dy) > abs(dx) && abs(dy) > dp(60)) {
                    if (dy < 0) openDrawer() else expandNotifications()
                    return true
                }
                if (abs(dx) > abs(dy) && dx < -dp(80)) {
                    openRecents()
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
        hintRecents.setOnClickListener { openRecents() }

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
            recents.translationX = root.width.toFloat()
            recents.visibility = View.INVISIBLE
            root.requestApplyInsets()
        }

        loadSavedWallpaper()
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
        updateAnimationState()
        updateBattery()
        handler.removeCallbacks(clockTick)
        handler.post(clockTick)
        if (recentsOpen) loadRecents() // por si vuelve de conceder el acceso de uso
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
        // Boton Home: cierra paneles y vuelve al inicio
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (drawerOpen) closeDrawer()
            if (recentsOpen) closeRecents()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un launcher nunca se cierra: solo cierra los paneles abiertos.
        when {
            recentsOpen -> closeRecents()
            drawerOpen -> closeDrawer()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_WALLPAPER && resultCode == RESULT_OK) {
            data?.data?.let { importWallpaper(it) }
        }
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
        hint.setTextColor(Ui.alpha(accent, 150))
        hintRecents.setTextColor(Ui.alpha(accent, 150))
        clock.setTextColor(white)
        clock.setShadowLayer(Ui.dp(this, 18f), 0f, 0f, Ui.alpha(accent, 200))
        drawerTitle.setTextColor(accent)
        handleBar.background = Ui.pill(this, Ui.alpha(accent, 170))
        search.background = Ui.panel(this, accent, 170)
        search.setTextColor(white)
        search.setHintTextColor(Ui.alpha(accent, 110))
        console.setTextColor(accent)
        console.background = Ui.panel(this, accent, 120)
        recentsTitle.setTextColor(accent)
        recentsMessage.setTextColor(Ui.alpha(accent, 200))
        recentsGrant.setTextColor(accent)
        recentsGrant.background = Ui.panel(this, accent, 150)
        recentsClear.setTextColor(accent)
        recentsClear.background = Ui.panel(this, accent, 150)
        adapter.accent = accent
        recentAdapter.accent = accent
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
        updateAnimationState()
    }

    private fun updateAnimationState() {
        bg.imageEffects = fxOverImage
        bg.scrimAlpha = SCRIM_ALPHAS[scrimIndex]
        if (animEnabled && bg.needsAnimation) bg.start() else bg.stop()
        bg.invalidate()
    }

    private fun setClock12(on: Boolean) {
        clock12 = on
        prefs.edit().putBoolean("clock12", on).apply()
        updateClock()
    }

    // ------------------------------------------------------------------------------------
    // Reloj, calendario Holoceno y estado
    // ------------------------------------------------------------------------------------

    private fun updateClock() {
        val now = GregorianCalendar()

        // Hora 12 h con AM/PM (o 24 h)
        val timeStr = (if (clock12) time12Fmt else time24Fmt).format(now.time)
        val sb = SpannableStringBuilder(timeStr)
        if (clock12) {
            val start = sb.length
            sb.append(if (now.get(Calendar.AM_PM) == Calendar.AM) " AM" else " PM")
            sb.setSpan(RelativeSizeSpan(0.3f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        clock.text = sb

        val sec = now.get(Calendar.SECOND)
        seconds.text = String.format(Locale.US, "SEC %02d", sec)

        // Calendario Holoceno (Era Humana): anio gregoriano + 10000
        val dow = dowFmt.format(now.time).replace(".", "").uppercase(Locale.getDefault())
        val mon = monFmt.format(now.time).replace(".", "").uppercase(Locale.getDefault())
        dateText.text = String.format(
            Locale.US, "%s %02d %s %d EC",
            dow, now.get(Calendar.DAY_OF_MONTH), mon, now.get(Calendar.YEAR) + HOLOCENE_OFFSET
        )

        if (sec % 6 == 0) statusText.text = statusLines[(sec / 6) % statusLines.size]
        if (sec % 20 == 0) updateBattery()
    }

    private fun dateInfo(): String {
        val now = GregorianCalendar()
        val full = SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault()).format(now.time)
        val greg = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        return "$full de ${now.get(Calendar.YEAR) + HOLOCENE_OFFSET} EC\n" +
            "(Era Holocena) | dia ${now.get(Calendar.DAY_OF_YEAR)} del anio\n" +
            "gregoriano: $greg"
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
        closeRecents(animated = false)
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
        showMenu(
            app.label,
            listOf(
                Opt(if (pinned) "Quitar del dock" else "Fijar en el dock") { togglePin(app) },
                Opt("Informacion de la app") {
                    safeStart(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", app.packageName, null)
                        )
                    )
                },
                Opt("Desinstalar") {
                    safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                }
            )
        )
    }

    // ------------------------------------------------------------------------------------
    // Cajon de apps
    // ------------------------------------------------------------------------------------

    private fun updateHomeDim() {
        homeLayer.animate()
            .alpha(if (drawerOpen || recentsOpen) 0.15f else 1f)
            .setDuration(220)
            .start()
    }

    private fun openDrawer() {
        if (drawerOpen) return
        if (recentsOpen) closeRecents(animated = false)
        drawerOpen = true
        root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        drawer.visibility = View.VISIBLE
        drawer.animate().cancel()
        drawer.animate()
            .translationY(0f)
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator())
            .start()
        updateHomeDim()
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
        updateHomeDim()
        appList.scrollToPosition(0)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }

    // ------------------------------------------------------------------------------------
    // Panel de apps recientes
    // ------------------------------------------------------------------------------------

    private fun openRecents() {
        if (recentsOpen) return
        if (drawerOpen) closeDrawer(animated = false)
        recentsOpen = true
        root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        recents.visibility = View.VISIBLE
        recents.animate().cancel()
        recents.animate()
            .translationX(0f)
            .setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .start()
        updateHomeDim()
        loadRecents()
    }

    private fun closeRecents(animated: Boolean = true) {
        if (!recentsOpen && recents.visibility != View.VISIBLE) return
        recentsOpen = false
        val w = root.width.toFloat()
        recents.animate().cancel()
        if (animated) {
            recents.animate()
                .translationX(w)
                .setDuration(200)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { if (!recentsOpen) recents.visibility = View.INVISIBLE }
                .start()
        } else {
            recents.translationX = w
            recents.visibility = View.INVISIBLE
        }
        updateHomeDim()
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccess() {
        try {
            val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            if (Build.VERSION.SDK_INT >= 29) i.data = Uri.parse("package:$packageName")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (_: Exception) {
            safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun showRecentsMessage(text: String, needsGrant: Boolean) {
        recentsList.visibility = View.GONE
        recentsMessage.text = text
        recentsMessage.visibility = View.VISIBLE
        recentsGrant.visibility = if (needsGrant) View.VISIBLE else View.GONE
        recentsClear.visibility = View.GONE
    }

    private fun loadRecents() {
        if (!hasUsageAccess()) {
            recentAdapter.submit(emptyList())
            showRecentsMessage(
                "Android no permite que un launcher reemplace el boton cuadrado,\n" +
                    "asi que Zetaria usa su propio panel.\n\n" +
                    "Para ver tus apps recientes concede el\nACCESO DE USO a Zetaria Launcher.",
                needsGrant = true
            )
            return
        }
        val apps = allApps
        val since = prefs.getLong("recents_cleared", 0L)
        executor.execute {
            val items = try {
                queryRecents(apps, since)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (isDestroyed || !recentsOpen) return@runOnUiThread
                recentAdapter.submit(items)
                if (items.isEmpty()) {
                    showRecentsMessage("Sin actividad reciente.\nAbre alguna app y vuelve aqui.", needsGrant = false)
                } else {
                    recentsMessage.visibility = View.GONE
                    recentsGrant.visibility = View.GONE
                    recentsList.visibility = View.VISIBLE
                    recentsClear.visibility = View.VISIBLE
                    recentsList.scrollToPosition(0)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun queryRecents(apps: List<AppInfo>, since: Long): List<RecentItem> {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = max(end - 3L * 24L * 3600L * 1000L, since)
        val events = usm.queryEvents(begin, end)
        val ev = UsageEvents.Event()
        val last = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last[pkg] = ev.timeStamp
        }
        val byPkg = apps.associateBy { it.packageName }
        return last.entries
            .sortedByDescending { it.value }
            .mapNotNull { e -> byPkg[e.key]?.let { RecentItem(it, e.value) } }
            .take(15)
    }

    /** Vacia la lista y libera memoria cerrando los procesos en segundo plano de esas apps. */
    private fun clearRecents() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (item in recentAdapter.items) {
            try {
                am.killBackgroundProcesses(item.app.packageName)
            } catch (_: Exception) {
            }
        }
        prefs.edit().putLong("recents_cleared", System.currentTimeMillis()).apply()
        recentAdapter.submit(emptyList())
        showRecentsMessage("Recientes limpiados.\nMemoria liberada.", needsGrant = false)
    }

    // ------------------------------------------------------------------------------------
    // Fondos de pantalla personalizados
    // ------------------------------------------------------------------------------------

    private fun pickWallpaper() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "Elegir fondo"), REQ_WALLPAPER)
        } catch (_: Exception) {
            toast("No hay galeria disponible")
        }
    }

    private fun loadSavedWallpaper() {
        val f = File(filesDir, WALLPAPER_FILE)
        if (!f.exists()) return
        executor.execute {
            val bmp = try {
                BitmapFactory.decodeFile(f.absolutePath)
            } catch (_: Throwable) {
                null
            }
            if (bmp != null) {
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    bg.wallpaper = bmp
                    updateAnimationState()
                }
            }
        }
    }

    private fun importWallpaper(uri: Uri) {
        val dm = resources.displayMetrics
        val tw = dm.widthPixels
        val th = dm.heightPixels
        toast("Procesando imagen...")
        executor.execute {
            val bmp = try {
                decodeWallpaper(uri, tw, th)
            } catch (_: Throwable) {
                null
            }
            if (bmp == null) {
                runOnUiThread { toast("No se pudo cargar la imagen") }
                return@execute
            }
            try {
                FileOutputStream(File(filesDir, WALLPAPER_FILE)).use {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
                }
            } catch (_: Exception) {
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                bg.wallpaper = bmp
                updateAnimationState()
                toast("Fondo aplicado")
            }
        }
    }

    /** Decodifica con muestreo, corrige la rotacion EXIF y recorta al centro al tamano de pantalla. */
    private fun decodeWallpaper(uri: Uri, tw: Int, th: Int): Bitmap? {
        val resolver = contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val degrees = try {
            resolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }

        val rotated = degrees == 90 || degrees == 270
        val bw = if (rotated) bounds.outHeight else bounds.outWidth
        val bh = if (rotated) bounds.outWidth else bounds.outHeight
        var sample = 1
        while (bw / (sample * 2) >= tw && bh / (sample * 2) >= th) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val rw = if (rotated) src.height else src.width
        val rh = if (rotated) src.width else src.height
        val scale = max(tw.toFloat() / rw, th.toFloat() / rh)
        val m = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postRotate(degrees.toFloat())
            postScale(scale, scale)
            postTranslate(tw / 2f, th / 2f)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }

    private fun removeWallpaper() {
        File(filesDir, WALLPAPER_FILE).delete()
        bg.wallpaper = null
        updateAnimationState()
        toast("Fondo alienigena restaurado")
    }

    private fun showWallpaperMenu() {
        val opts = mutableListOf(Opt("Elegir imagen de la galeria") { pickWallpaper() })
        if (bg.wallpaper != null) {
            opts.add(Opt(if (fxOverImage) "Efectos sobre la imagen: ON" else "Efectos sobre la imagen: OFF") {
                fxOverImage = !fxOverImage
                prefs.edit().putBoolean("fx", fxOverImage).apply()
                updateAnimationState()
            })
            opts.add(Opt("Oscurecer imagen: ${SCRIM_NAMES[scrimIndex]}") { showScrimPicker() })
            opts.add(Opt("Quitar imagen (fondo alienigena)") { removeWallpaper() })
        }
        showMenu("// FONDO DE PANTALLA", opts)
    }

    private fun showScrimPicker() {
        showMenu(
            "// OSCURECER IMAGEN",
            SCRIM_NAMES.mapIndexed { i, name ->
                Opt(name) {
                    scrimIndex = i
                    prefs.edit().putInt("scrim", i).apply()
                    updateAnimationState()
                }
            }
        )
    }

    // ------------------------------------------------------------------------------------
    // Menu del nucleo (pulsacion larga en el inicio)
    // ------------------------------------------------------------------------------------

    private fun showMenu(title: String, opts: List<Opt>) {
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(title)
            .setItems(opts.map { it.label }.toTypedArray()) { _, i -> opts[i].action() }
            .show()
    }

    private fun showCoreMenu() {
        showMenu(
            "// NUCLEO ZETARIA",
            listOf(
                Opt("Fondo de pantalla...") { showWallpaperMenu() },
                Opt("Energia (color del nucleo)") { showThemePicker() },
                Opt(if (clock12) "Reloj: 12 h (AM/PM)" else "Reloj: 24 h") { setClock12(!clock12) },
                Opt(if (animEnabled) "Animaciones: ON" else "Animaciones: OFF") { setAnimations(!animEnabled) },
                Opt("Reindexar aplicaciones") { loadApps() },
                Opt("Ajustes del sistema") { safeStart(Intent(Settings.ACTION_SETTINGS)) }
            )
        )
    }

    private fun showThemePicker() {
        showMenu("// ENERGIA DEL NUCLEO", themes.keys.map { name -> Opt(name) { applyTheme(name) } })
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
            "uname" -> "ZetariaOS 1.1.0 | Android ${Build.VERSION.RELEASE} | ${Build.MODEL}"
            "sysinfo", "neofetch" -> sysInfo()
            "date", "fecha" -> dateInfo()
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
            "recent", "recents", "recientes" -> {
                openRecents()
                null
            }
            "theme" -> {
                if (themes.containsKey(arg.lowercase())) {
                    applyTheme(arg.lowercase())
                    "energia -> ${arg.lowercase()}"
                } else {
                    "uso: theme <${themes.keys.joinToString("|")}>"
                }
            }
            "clock", "reloj" -> when (arg) {
                "12" -> {
                    setClock12(true)
                    "reloj: 12 h (AM/PM)"
                }
                "24" -> {
                    setClock12(false)
                    "reloj: 24 h"
                }
                else -> "uso: clock 12|24"
            }
            "wallpaper", "fondo" -> {
                if (arg.lowercase() == "clear") {
                    removeWallpaper()
                    null
                } else {
                    pickWallpaper()
                    null
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
        private const val REQ_WALLPAPER = 4101
        private const val WALLPAPER_FILE = "wallpaper.jpg"

        /** Era Holocena: anio gregoriano + 10000. */
        private const val HOLOCENE_OFFSET = 10000

        private val SCRIM_NAMES = listOf("Ninguno", "Suave", "Medio", "Fuerte")
        private val SCRIM_ALPHAS = intArrayOf(0, 70, 130, 190)

        private const val HELP = "comandos:\n" +
            "  help             esta ayuda\n" +
            "  sysinfo          estado del sistema\n" +
            "  date             fecha holocena\n" +
            "  ls               listar apps\n" +
            "  launch <app>     abrir una app\n" +
            "  recent           apps recientes\n" +
            "  theme <nombre>   zeta|violeta|magenta|ambar|hielo\n" +
            "  clock 12|24      formato de hora\n" +
            "  wallpaper [clear] fondo de pantalla\n" +
            "  anim on|off      animaciones\n" +
            "  wifi | bt | settings\n" +
            "  dock [reset]     ver / restablecer dock\n" +
            "  reload | clear | exit"
    }
}
