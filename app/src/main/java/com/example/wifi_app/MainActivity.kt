package com.example.wifi_app

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// --- Data class to hold each AP's data ---
data class WiFiApData(
    val bssid: String,
    val ssid: String,
    val readings: List<Int>,
    val isExpanded: Boolean
) {
    val rssiRange: String
        get() = "${readings.minOrNull() ?: 0} .. ${readings.maxOrNull() ?: 0}"
    val key: String
        get() = "$bssid|$ssid"
}

// --- Enum for our three "locations" ---
enum class Location { One, Two, Three }

// --- ViewModel that manages scan data and progress ---
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val wifiManager =
        app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val scanChannel = Channel<Unit>(Channel.UNLIMITED)
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                scanChannel.trySend(Unit)
            }
        }
    }

    init {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        app.registerReceiver(scanReceiver, filter)
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(scanReceiver)
        super.onCleared()
    }

    private val _dataOne   = MutableStateFlow<List<WiFiApData>>(emptyList())
    private val _dataTwo   = MutableStateFlow<List<WiFiApData>>(emptyList())
    private val _dataThree = MutableStateFlow<List<WiFiApData>>(emptyList())
    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress

    fun getData(loc: Location): StateFlow<List<WiFiApData>> = when (loc) {
        Location.One   -> _dataOne
        Location.Two   -> _dataTwo
        Location.Three -> _dataThree
    }

    fun toggleExpand(loc: Location, apKey: String) {
        val flow = getData(loc)
        val updated = flow.value.map {
            if (it.key == apKey) it.copy(isExpanded = !it.isExpanded) else it
        }
        when (loc) {
            Location.One   -> _dataOne.value   = updated
            Location.Two   -> _dataTwo.value   = updated
            Location.Three -> _dataThree.value = updated
        }
    }

    fun scan(loc: Location) = viewModelScope.launch {
        val ctx = getApplication<Application>()

        if (ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("MainViewModel", "Location permission NOT granted — aborting scan")
            return@launch
        }

        // clear previous and reset progress
        when (loc) {
            Location.One   -> _dataOne.value   = emptyList()
            Location.Two   -> _dataTwo.value   = emptyList()
            Location.Three -> _dataThree.value = emptyList()
        }
        _scanProgress.value = 0

        val temp = mutableMapOf<String, MutableList<Int>>()
        repeat(100) { i ->
            try {
                wifiManager.startScan()
            } catch (se: SecurityException) {
                Log.e("MainViewModel", "startScan() failed at iteration $i", se)
                return@launch
            }
            scanChannel.receive()

            val results = try {
                wifiManager.scanResults
            } catch (se: SecurityException) {
                Log.e("MainViewModel", "scanResults() failed at iteration $i", se)
                emptyList()
            }
            for (res in results) {
                val key = "${res.BSSID}|${res.SSID}"
                val list = temp.getOrPut(key) { mutableListOf() }
                list.add(res.level)
            }
            _scanProgress.value = i + 1
        }

        val finalList = temp.map { (key, readings) ->
            val (bssid, ssid) = key.split("|", limit = 2)
            WiFiApData(bssid, ssid, readings, isExpanded = false)
        }
        _dataOne.value   = if (_lastLoc == Location.One) finalList else _dataOne.value
        _dataTwo.value   = if (_lastLoc == Location.Two) finalList else _dataTwo.value
        _dataThree.value = if (_lastLoc == Location.Three) finalList else _dataThree.value
        _scanProgress.value = 0
    }

    // track last scan location for proper assignment
    private var _lastLoc: Location = Location.One
    fun setLastLocation(loc: Location) {
        _lastLoc = loc
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val needed = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        val toAsk = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toAsk.isNotEmpty()) requestPermissions(toAsk.toTypedArray(), 0)

        setContent {
            val vm: MainViewModel = viewModel()
            WiFiScannerApp(vm)
        }
    }
}

@Composable
fun WiFiScannerApp(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Location 1", "Location 2", "Location 3", "Comparison")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = i == selectedTab, onClick = { selectedTab = i }) {
                    Text(title, modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                }
            }
        }
        when (selectedTab) {
            0 -> LocationScreen(Location.One, viewModel)
            1 -> LocationScreen(Location.Two, viewModel)
            2 -> LocationScreen(Location.Three, viewModel)
            3 -> ComparisonScreen(viewModel)
        }
    }
}

@Composable
fun LocationScreen(loc: Location, viewModel: MainViewModel) {
    // remember last scan target
    LaunchedEffect(loc) { viewModel.setLastLocation(loc) }
    val wifiList by viewModel.getData(loc).collectAsState()
    val progress by viewModel.scanProgress.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { viewModel.scan(loc) }, enabled = progress == 0) {
            Text(if (progress == 0) "Scan" else "Scanning... $progress%")
        }
        Spacer(Modifier.height(12.dp))
        if (progress in 1..99) {
            LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        LazyColumn {
            items(wifiList, key = { it.key }) { ap ->
                WiFiApItem(ap) { viewModel.toggleExpand(loc, ap.key) }
            }
        }
    }
}

@Composable
fun WiFiApItem(ap: WiFiApData, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${ap.bssid} (${ap.ssid}): ${ap.rssiRange}", modifier = Modifier.weight(1f))
            TextButton(onClick = onToggle) {
                Text(if (ap.isExpanded) "Collapse" else "Expand")
            }
        }
        if (ap.isExpanded) {
            ap.readings.chunked(10).forEach { rowList ->
                Row { rowList.forEach { r ->
                    Text(text = String.format("% d", r), modifier = Modifier.width(36.dp).padding(2.dp))
                }}
            }
        }
    }
}

@Composable
fun ComparisonScreen(viewModel: MainViewModel) {
    val list1 by viewModel.getData(Location.One).collectAsState()
    val list2 by viewModel.getData(Location.Two).collectAsState()
    val list3 by viewModel.getData(Location.Three).collectAsState()

    // union of all AP keys
    val allKeys = remember(list1, list2, list3) {
        (list1.map { it.key } + list2.map { it.key } + list3.map { it.key }).toSet().toList()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Comparative RSSI Ranges", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text("AP (BSSID/SSID)", modifier = Modifier.weight(2f))
            Text("Loc1", modifier = Modifier.weight(1f))
            Text("Loc2", modifier = Modifier.weight(1f))
            Text("Loc3", modifier = Modifier.weight(1f))
        }
        Divider()
        LazyColumn {
            items(allKeys) { key ->
                val ap1 = list1.find { it.key == key }
                val ap2 = list2.find { it.key == key }
                val ap3 = list3.find { it.key == key }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(ap1?.let { "${it.bssid}/${it.ssid}" }
                        ?: ap2?.let { "${it.bssid}/${it.ssid}" }
                        ?: ap3?.let { "${it.bssid}/${it.ssid}" } ?: key,
                        modifier = Modifier.weight(2f)
                    )
                    Text(ap1?.rssiRange ?: "-", modifier = Modifier.weight(1f))
                    Text(ap2?.rssiRange ?: "-", modifier = Modifier.weight(1f))
                    Text(ap3?.rssiRange ?: "-", modifier = Modifier.weight(1f))
                }
                Divider()
            }
        }
    }
}