package com.shd.codestudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isSettingUp by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        PRootManager.setupEnvironment(context) { msg ->
            logs.add(msg)
            scope.launch { listState.animateScrollToItem(logs.size - 1) }
        }
        isSettingUp = false

        PRootManager.runInPRoot(
            context = context,
            command = "echo 'Hello from Ubuntu!'; uname -a",
            onOutput = { msg -> logs.add(msg); scope.launch { listState.animateScrollToItem(logs.size - 1) } },
            onComplete = { logs.add("Test Complete: $it") }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F0F0F)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Terminal - PRoot Ubuntu", color = Color.White, modifier = Modifier.weight(1f))
                    if (!isSettingUp) {
                        TextButton(onClick = onDismiss) { Text("Close", color = NeonLime) }
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = if (log.contains("❌")) Color.Red else if (log.contains("✅")) NeonLime else Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                if (isSettingUp) {
                    CircularProgressIndicator(color = NeonLime, modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                }
            }
        }
    }
}