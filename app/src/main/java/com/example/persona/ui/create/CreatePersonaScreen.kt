package com.example.persona.ui.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePersonaScreen(
    onBack: () -> Unit,
    viewModel: CreateViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Create Persona") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 头像预览
            AsyncImage(
                model = viewModel.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 名字输入
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text("Persona Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. AI 辅助按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Description", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = { viewModel.onAiAssistClick() },
                    enabled = !viewModel.isGenerating && viewModel.name.isNotBlank()
                ) {
                    if (viewModel.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Generate")
                    }
                }
            }

            // 4. 描述输入框 (支持 AI 回填)
            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("Click 'AI Generate' or type manually...") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. 保存按钮
            Button(
                onClick = { viewModel.onSaveClick(onSuccess = onBack) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !viewModel.isSaving && viewModel.name.isNotBlank()
            ) {
                if (viewModel.isSaving) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save Persona")
                }
            }
        }
    }
}
