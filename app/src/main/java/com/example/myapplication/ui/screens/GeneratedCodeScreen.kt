package com.example.myapplication.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.myapplication.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratedCodeScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val code by viewModel.generatedCode.collectAsState()
    val buildExe by viewModel.buildExeScript.collectAsState()
    var email by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Сгенерированный код") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Отображение кода
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email для отправки") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (email.isNotBlank()) {
                        val agentFile = File(context.cacheDir, "agent.py").also { it.writeText(code) }
                        val buildFile = File(context.cacheDir, "build_exe.py").also { it.writeText(buildExe) }

                        val agentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", agentFile)
                        val buildUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", buildFile)

                        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                            putExtra(Intent.EXTRA_SUBJECT, "Ваш AI-агент")
                            putExtra(Intent.EXTRA_TEXT, "Сгенерированные файлы AI-агента")
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(agentUri, buildUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Отправить файлы"))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отправить на почту")
            }
        }
    }
}