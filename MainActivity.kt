package com.gamehacker.app

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    // Memory tab
    private lateinit var processListView: ListView
    private lateinit var searchEdit: EditText
    private lateinit var newValueEdit: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnEditValue: Button
    private lateinit var btnFreeze: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnAutoZero: Button
    private lateinit var resultsText: TextView
    private lateinit var selectedProcessText: TextView
    private lateinit var searchTypeSpinner: Spinner
    private lateinit var panelMemory: View
    private lateinit var panelApps: View

    // Apps tab
    private lateinit var appListView: ListView
    private lateinit var selectedAppText: TextView
    private lateinit var appLog: TextView
    private lateinit var btnRefreshApps: Button
    private lateinit var btnForceStop: Button
    private lateinit var btnClearData: Button
    private lateinit var btnDisable: Button
    private lateinit var btnEnable: Button
    private lateinit var btnKill: Button
    private lateinit var btnUnlock: Button
    private lateinit var btnRemoveAds: Button

    private var selectedPid = 0
    private var selectedName = ""
    private var foundAddresses = listOf<Long>()
    private val processMap = mutableMapOf<String, Int>()

    private var selectedPackage = ""
    private val appPackages = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        panelMemory = findViewById(R.id.panelMemory)
        panelApps = findViewById(R.id.panelApps)
        processListView = findViewById(R.id.processList)
        searchEdit = findViewById(R.id.searchValue)
        newValueEdit = findViewById(R.id.newValueEdit)
        btnSearch = findViewById(R.id.btnSearch)
        btnEditValue = findViewById(R.id.btnEditValue)
        btnFreeze = findViewById(R.id.btnFreeze)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnAutoZero = findViewById(R.id.btnAutoZero)
        resultsText = findViewById(R.id.resultsText)
        selectedProcessText = findViewById(R.id.selectedProcess)
        searchTypeSpinner = findViewById(R.id.searchTypeSpinner)

        appListView = findViewById(R.id.appList)
        selectedAppText = findViewById(R.id.selectedApp)
        appLog = findViewById(R.id.appLog)
        btnRefreshApps = findViewById(R.id.btnRefreshApps)
        btnForceStop = findViewById(R.id.btnForceStop)
        btnClearData = findViewById(R.id.btnClearData)
        btnDisable = findViewById(R.id.btnDisable)
        btnEnable = findViewById(R.id.btnEnable)
        btnKill = findViewById(R.id.btnKill)
        btnUnlock = findViewById(R.id.btnUnlock)
        btnRemoveAds = findViewById(R.id.btnRemoveAds)

        // Tabs
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.addTab(tabLayout.newTab().setText("Memory Hack"))
        tabLayout.addTab(tabLayout.newTab().setText("تعديل تطبيقات"))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    panelMemory.visibility = View.VISIBLE
                    panelApps.visibility = View.GONE
                } else {
                    panelMemory.visibility = View.GONE
                    panelApps.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Search types
        val types = arrayOf("Exact", "Fuzzy (أول مسح)", "Increased", "Decreased", "Changed", "Unchanged")
        searchTypeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

        // Root check
        if (!RootUtils.isRootAvailable()) {
            resultsText.text = "⚠ الجهاز غير Rooted\nتعديل الذاكرة والإجراءات الخطيرة مش هتشتغل."
            appLog.text = "⚠ Root مطلوب لمعظم الوظائف الخطيرة."
        } else {
            resultsText.text = "Root متاح ✓\nاختار عملية وابدأ البحث.\nأو استخدم زرار اكتشاف السعر التلقائي."
        }

        // Memory listeners
        btnRefresh.setOnClickListener { loadProcesses() }
        btnSearch.setOnClickListener { doSearch() }
        btnEditValue.setOnClickListener { doEditValue() }
        btnFreeze.setOnClickListener { doFreeze() }
        btnAutoZero.setOnClickListener { doAutoZero() }

        processListView.setOnItemClickListener { _, _, pos, _ ->
            val name = processListView.adapter.getItem(pos) as String
            selectedPid = processMap[name] ?: 0
            selectedName = name
            selectedProcessText.text = "مختار: $name  |  PID: $selectedPid"
            FreezeManager.currentPid = selectedPid
            MemoryScanner.resetScan()
            foundAddresses = emptyList()
            Toast.makeText(this, "تم اختيار $name", Toast.LENGTH_SHORT).show()
        }

        // Apps listeners
        btnRefreshApps.setOnClickListener { loadApps() }
        btnForceStop.setOnClickListener { runAppAction("Force Stop") { AppModder.forceStop(selectedPackage) } }
        btnClearData.setOnClickListener { runAppAction("Clear Data") { AppModder.clearData(selectedPackage) } }
        btnDisable.setOnClickListener { runAppAction("Disable") { AppModder.disableApp(selectedPackage) } }
        btnEnable.setOnClickListener { runAppAction("Enable") { AppModder.enableApp(selectedPackage) } }
        btnKill.setOnClickListener { runAppAction("Kill") { AppModder.killProcess(selectedPackage) } }
        btnUnlock.setOnClickListener { runAppAction("Unlock Premium") { AppModder.tryUnlockPremium(selectedPackage) } }
        btnRemoveAds.setOnClickListener { runAppAction("Remove Ads") { AppModder.tryRemoveAds(selectedPackage) } }

        appListView.setOnItemClickListener { _, _, pos, _ ->
            selectedPackage = appPackages[pos]
            val name = appListView.adapter.getItem(pos) as String
            selectedAppText.text = "مختار: $name\n$selectedPackage"
            Toast.makeText(this, "تم اختيار $name", Toast.LENGTH_SHORT).show()
        }

        loadProcesses()
        loadApps()
    }

    private fun loadProcesses() {
        lifecycleScope.launch {
            resultsText.text = "جاري تحميل العمليات..."
            val list = withContext(Dispatchers.IO) { getRunningProcesses() }
            processMap.clear()
            list.forEach { processMap[it.first] = it.second }
            val names = list.map { it.first }
            processListView.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, names)
            resultsText.text = "تم تحميل ${names.size} عملية."
        }
    }

    private fun getRunningProcesses(): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        try {
            File("/proc").listFiles()?.forEach { dir ->
                if (dir.isDirectory && dir.name.matches(Regex("\\d+"))) {
                    val pid = dir.name.toIntOrNull() ?: return@forEach
                    val cmdline = File("/proc/$pid/cmdline")
                    if (cmdline.exists()) {
                        val name = cmdline.readText().replace("\u0000", " ").trim()
                        if (name.isNotEmpty() && name.contains(".") && !name.startsWith("/")) {
                            result.add(name to pid)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result.distinctBy { it.first }.sortedBy { it.first }
    }

    private fun doSearch() {
        if (selectedPid == 0) {
            Toast.makeText(this, "اختار عملية أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val type = searchTypeSpinner.selectedItemPosition
        val valueStr = searchEdit.text.toString().trim()
        val value = valueStr.toLongOrNull()

        btnSearch.isEnabled = false
        resultsText.text = "جاري البحث..."

        lifecycleScope.launch {
            val addresses = withContext(Dispatchers.IO) {
                when (type) {
                    0 -> if (value == null) emptyList() else MemoryScanner.searchExact(selectedPid, value)
                    1 -> MemoryScanner.searchFuzzyFirst(selectedPid)
                    2 -> MemoryScanner.searchChanged(selectedPid, "increased")
                    3 -> MemoryScanner.searchChanged(selectedPid, "decreased")
                    4 -> MemoryScanner.searchChanged(selectedPid, "changed")
                    5 -> MemoryScanner.searchChanged(selectedPid, "unchanged")
                    else -> emptyList()
                }
            }
            foundAddresses = addresses
            btnSearch.isEnabled = true

            if (addresses.isEmpty()) {
                resultsText.text = "مفيش نتائج.\nلو بتستخدم Increased/Decreased اعمل Exact أو Fuzzy الأول."
            } else {
                val preview = addresses.take(40).joinToString("\n") { "0x${it.toString(16).uppercase()}" }
                resultsText.text = "نتائج: ${addresses.size} عنوان\n\n$preview" +
                        if (addresses.size > 40) "\n... (عرض أول 40)" else ""
            }
        }
    }

    private fun doEditValue() {
        if (foundAddresses.isEmpty()) {
            Toast.makeText(this, "مفيش عناوين. اعمل بحث أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val newVal = newValueEdit.text.toString().toLongOrNull()
        if (newVal == null) {
            Toast.makeText(this, "اكتب القيمة الجديدة", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            var success = 0
            withContext(Dispatchers.IO) {
                foundAddresses.forEach { addr ->
                    if (MemoryScanner.writeValue(selectedPid, addr, newVal)) success++
                }
            }
            Toast.makeText(this@MainActivity, "تم تعديل $success من ${foundAddresses.size}", Toast.LENGTH_LONG).show()
            resultsText.append("\n\n[تم كتابة القيمة $newVal على $success عنوان]")
        }
    }

    private fun doFreeze() {
        if (foundAddresses.isEmpty()) {
            Toast.makeText(this, "مفيش عناوين للتجميد", Toast.LENGTH_SHORT).show()
            return
        }
        val value = newValueEdit.text.toString().toLongOrNull()
            ?: searchEdit.text.toString().toLongOrNull()
            ?: 0L
        FreezeManager.clear()
        FreezeManager.currentPid = selectedPid
        FreezeManager.addAll(foundAddresses, value)
        Toast.makeText(this, "تجميد مفعّل على ${foundAddresses.size} عنوان بالقيمة $value", Toast.LENGTH_LONG).show()
        resultsText.append("\n\n[تجميد نشط: ${FreezeManager.count()} عنوان]")
    }

    /** اكتشاف أسعار شائعة وتحويلها لصفر + تجميد */
    private fun doAutoZero() {
        if (selectedPid == 0) {
            Toast.makeText(this, "اختار عملية اللعبة أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        btnAutoZero.isEnabled = false
        resultsText.text = "جاري البحث عن أسعار شائعة وتحويلها لصفر...\nده ممكن ياخد وقت."

        lifecycleScope.launch {
            val (count, addresses) = withContext(Dispatchers.IO) {
                MemoryScanner.autoZeroPrices(selectedPid)
            }
            foundAddresses = addresses
            btnAutoZero.isEnabled = true

            if (count == 0) {
                resultsText.text = "مفيش أسعار شائعة اتلاقت.\nجرب:\n1. افتح صفحة الشراء في اللعبة الأول\n2. استخدم البحث اليدوي (Exact) على السعر اللي ظاهر"
            } else {
                // تجميد على صفر
                FreezeManager.clear()
                FreezeManager.currentPid = selectedPid
                FreezeManager.addAll(addresses, 0L)

                val preview = addresses.take(30).joinToString("\n") { "0x${it.toString(16).uppercase()}" }
                resultsText.text = "تم تصفير $count عنوان سعر محتمل\n+ تم تفعيل التجميد على صفر\n\n$preview"
                Toast.makeText(this@MainActivity, "تم تصفير $count سعر محتمل", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadApps() {
        lifecycleScope.launch {
            appLog.text = "جاري تحميل التطبيقات..."
            val apps = withContext(Dispatchers.IO) { AppModder.getInstalledApps(this@MainActivity) }
            appPackages.clear()
            val names = apps.map {
                appPackages.add(it.packageName)
                val tag = if (it.isSystem) "[SYS] " else ""
                "$tag${it.name}"
            }
            appListView.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, names)
            appLog.text = "تم تحميل ${apps.size} تطبيق."
        }
    }

    private fun runAppAction(name: String, action: () -> String) {
        if (selectedPackage.isEmpty()) {
            Toast.makeText(this, "اختار تطبيق أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        if (!RootUtils.isRootAvailable()) {
            Toast.makeText(this, "Root مطلوب", Toast.LENGTH_SHORT).show()
            return
        }
        appLog.text = "جاري تنفيذ $name على $selectedPackage ..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { action() }
            appLog.text = "[$name] $selectedPackage\n\n$result"
            Toast.makeText(this@MainActivity, "تم تنفيذ $name", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FreezeManager.clear()
    }
}
