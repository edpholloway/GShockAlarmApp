package ca.edholloway.dwh5600

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.avmedia.gshockapi.DeviceInfo
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.model.Alarm

class MainActivity : ComponentActivity() {
    private val api by lazy { GShockAPI(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AlarmTimerScreen(api) } }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimerScreen(api: GShockAPI) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices = remember { mutableStateListOf<DeviceInfo>() }
    val alarms = remember { mutableStateListOf<Alarm>() }
    var isScanning by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Connect your DW-H5600 to get started") }
    var hours by remember { mutableStateOf("00") }
    var minutes by remember { mutableStateOf("05") }
    var seconds by remember { mutableStateOf("00") }

    val neededPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    var permissionsReady by remember {
        mutableStateOf(neededPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> permissionsReady = result.values.all { it } }

    fun loadWatchSettings() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val loadedAlarms = withContext(Dispatchers.IO) { api.getAlarms() }
                val loadedTimer = withContext(Dispatchers.IO) { api.getTimer() }
                alarms.clear()
                alarms.addAll(loadedAlarms)
                hours = (loadedTimer / 3600).toString().padStart(2, '0')
                minutes = ((loadedTimer % 3600) / 60).toString().padStart(2, '0')
                seconds = (loadedTimer % 60).toString().padStart(2, '0')
                connected = true
                status = "Connected · settings loaded from watch"
            } catch (error: Exception) {
                status = "Could not read watch settings: ${error.message ?: "connection lost"}"
                connected = false
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsReady) permissionRequest.launch(neededPermissions)
        ProgressEvents.runEventActions(
            "DW5600AlarmTimer",
            arrayOf(
                EventAction("DeviceAppeared") {
                    val address = ProgressEvents.getPayload("DeviceAppeared") as? String
                    if (address != null) scope.launch {
                        status = "Connecting to watch…"
                        try {
                            withContext(Dispatchers.IO) { api.waitForConnection(address) }
                        } catch (error: Exception) {
                            status = "Connection failed. Start Bluetooth Link on the watch and try again."
                        }
                    }
                },
                EventAction("ConnectionSetupComplete") {
                    status = "Connected · loading watch settings…"
                },
                EventAction("WatchInitializationCompleted") {
                    loadWatchSettings()
                },
                EventAction("Disconnect") {
                    connected = false
                    status = "Watch disconnected. Reconnect to update settings."
                },
                EventAction("ConnectionFailed") {
                    connected = false
                    status = "Could not connect. Start Bluetooth Link on the watch and try again."
                }
            )
        )
    }

    fun startScan() {
        if (!permissionsReady) {
            permissionRequest.launch(neededPermissions)
            return
        }
        devices.clear()
        isScanning = true
        status = "Searching for your watch…"
        api.scan(context, { device ->
            device.name.contains("DW-H", ignoreCase = true) ||
                device.name.contains("CASIO", ignoreCase = true)
        }, { device ->
            if (devices.none { it.address == device.address }) devices.add(device)
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("G-SHOCK", fontWeight = FontWeight.Black)
                    Text("ALARM + TIMER", style = MaterialTheme.typography.labelSmall)
                }
            })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("DW-H5600", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                        if (busy) CircularProgressIndicator()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (isScanning) {
                                    api.stopScan(); isScanning = false
                                } else startScan()
                            }) { Text(if (isScanning) "Stop search" else "Find watch") }
                            if (connected) OutlinedButton(onClick = { loadWatchSettings() }) { Text("Refresh") }
                        }
                        if (!connected && devices.isEmpty()) {
                            Text("If it doesn’t appear, open Bluetooth Link on the watch, then tap Find watch.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (!connected && devices.isNotEmpty()) {
                item { Text("Choose your watch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                itemsIndexed(devices) { _, device ->
                    Card(onClick = {
                        api.stopScan()
                        isScanning = false
                        status = "Connecting to ${device.name}…"
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { api.waitForConnection(device.address) }
                                loadWatchSettings()
                            } catch (error: Exception) {
                                status = "Connection failed. Start Bluetooth Link on the watch and retry."
                            }
                        }
                    }) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(device.name, fontWeight = FontWeight.SemiBold)
                            Text("Tap to connect", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (connected) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("ALARMS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            if (alarms.isEmpty()) return@TextButton
                            busy = true
                            scope.launch {
                                try {
                                    val snapshot = ArrayList(alarms)
                                    withContext(Dispatchers.IO) { api.setAlarms(snapshot) }
                                    delay(900)
                                    val saved = withContext(Dispatchers.IO) { api.getAlarms() }
                                    alarms.clear(); alarms.addAll(saved)
                                    status = if (saved == snapshot) "Alarms saved on watch" else "Sent alarms; watch values differ. Check connection and refresh."
                                } catch (error: Exception) {
                                    status = "Could not save alarms: ${error.message ?: "connection lost"}"
                                } finally { busy = false }
                            }
                        }) { Text("Save alarms") }
                    }
                }
                if (alarms.isEmpty()) {
                    item { Text("No alarms were returned. Tap Refresh to try again.") }
                } else {
                    itemsIndexed(alarms) { index, alarm ->
                        AlarmRow(
                            index = index,
                            alarm = alarm,
                            enabled = !busy,
                            onToggle = { on -> alarms[index] = alarm.copy(enabled = on) },
                            onTime = { hour, minute -> alarms[index] = alarm.copy(hour = hour, minute = minute) }
                        )
                    }
                }
                item { Divider(Modifier.padding(vertical = 6.dp)) }
                item {
                    Text("COUNTDOWN TIMER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberPart("Hours", hours, { hours = it }, Modifier.weight(1f))
                        NumberPart("Minutes", minutes, { minutes = it }, Modifier.weight(1f))
                        NumberPart("Seconds", seconds, { seconds = it }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Quick set", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 25).forEach { preset ->
                            OutlinedButton(onClick = {
                                hours = "00"; minutes = preset.toString().padStart(2, '0'); seconds = "00"
                            }, enabled = !busy) { Text("${preset}m") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val h = hours.toIntOrNull()
                            val m = minutes.toIntOrNull()
                            val s = seconds.toIntOrNull()
                            if (h == null || m == null || s == null || h !in 0..24 || m !in 0..59 || s !in 0..59 || (h == 24 && (m != 0 || s != 0))) {
                                status = "Enter a valid duration up to 24:00:00"
                                return@Button
                            }
                            val duration = h * 3600 + m * 60 + s
                            if (duration == 0) {
                                status = "Timer duration must be at least one second"
                                return@Button
                            }
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { api.setTimer(duration) }
                                    delay(900)
                                    val saved = withContext(Dispatchers.IO) { api.getTimer() }
                                    status = if (saved == duration) "Timer saved on watch" else "Sent timer; watch value differs. Check connection and refresh."
                                } catch (error: Exception) {
                                    status = "Could not save timer: ${error.message ?: "connection lost"}"
                                } finally { busy = false }
                            }
                        },
                        enabled = !busy
                    ) { Text("Save timer to watch") }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun AlarmRow(
    index: Int,
    alarm: Alarm,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onTime: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Alarm ${index + 1}", style = MaterialTheme.typography.labelLarge)
                TextButton(
                    onClick = {
                        TimePickerDialog(context, { _, hour, minute -> onTime(hour, minute) }, alarm.hour, alarm.minute, true).show()
                    },
                    enabled = enabled
                ) {
                    Text("${alarm.hour.toString().padStart(2, '0')}:${alarm.minute.toString().padStart(2, '0')}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle, enabled = enabled)
        }
    }
}

@Composable
private fun NumberPart(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.length <= 2 && input.all(Char::isDigit)) onValue(input)
        },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        enabled = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

