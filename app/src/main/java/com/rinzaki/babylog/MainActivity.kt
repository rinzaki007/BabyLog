package com.rinzaki.babylog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyLogDao
import com.rinzaki.babylog.data.BabyLogDatabase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var database: BabyLogDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = Room.databaseBuilder(
            applicationContext,
            BabyLogDatabase::class.java,
            "babylog.db"
        ).build()
        setContent { BabyLogApp(database.babyLogDao()) }
    }

    override fun onDestroy() {
        if (::database.isInitialized) database.close()
        super.onDestroy()
    }
}

@Composable
private fun BabyLogApp(dao: BabyLogDao) {
    val babies by dao.observeBabies().collectAsState(initial = emptyList())
    val baby = babies.firstOrNull()
    var editing by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("BabyLog", style = MaterialTheme.typography.headlineMedium)
                    Text("记录每一个小小的成长", style = MaterialTheme.typography.bodyLarge)
                }
                item {
                    if (baby == null || editing) {
                        BabyProfileEditor(
                            existing = baby,
                            onSave = { name, birthDate ->
                                val now = System.currentTimeMillis()
                                val birthTime = birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                dao.upsertBaby(
                                    BabyEntity(
                                        id = baby?.id ?: UUID.randomUUID().toString(),
                                        // Local-only placeholder. Replaced by a real family UUID during account linking.
                                        familyId = baby?.familyId ?: "00000000-0000-0000-0000-000000000000",
                                        displayName = name.trim(),
                                        birthTime = birthTime,
                                        createdAt = baby?.createdAt ?: now,
                                        updatedAt = now
                                    )
                                )
                                editing = false
                            }
                        )
                    } else {
                        BabyProfileCard(baby = baby, onEdit = { editing = true })
                    }
                }
                item {
                    Text("快捷记录", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAction("喂养", Modifier.weight(1f))
                        QuickAction("尿布", Modifier.weight(1f))
                        QuickAction("睡眠", Modifier.weight(1f))
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("今天的记录", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("喂养  —    小便  —    大便  —    睡眠  —")
                            Spacer(Modifier.height(6.dp))
                            Text("下一阶段将接入真实记录与时间轴。")
                        }
                    }
                }
                item {
                    Text("本地优先 · 家庭共享 · 安全同步", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun BabyProfileEditor(
    existing: BabyEntity?,
    onSave: suspend (String, LocalDate) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.displayName.orEmpty()) }
    var birthDate by remember(existing?.id) {
        mutableStateOf(
            existing?.let {
                java.time.Instant.ofEpochMilli(it.birthTime).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            }.orEmpty()
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (existing == null) "建立宝宝档案" else "编辑宝宝档案", style = MaterialTheme.typography.titleLarge)
            Text("先填写称呼和出生日期，资料只保存在这台设备。")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30); error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("宝宝昵称") },
                singleLine = true
            )
            OutlinedTextField(
                value = birthDate,
                onValueChange = { birthDate = it.take(10); error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("出生日期") },
                placeholder = { Text("例如：2026-11-14") },
                supportingText = { Text("格式：YYYY-MM-DD") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val parsed = runCatching { LocalDate.parse(birthDate.trim()) }.getOrNull()
                    when {
                        name.isBlank() -> error = "请填写宝宝昵称"
                        parsed == null -> error = "出生日期格式不正确，请使用 YYYY-MM-DD"
                        parsed.isAfter(LocalDate.now()) -> error = "出生日期不能晚于今天"
                        else -> {
                            saving = true
                            scope.launch {
                                try {
                                    onSave(name, parsed)
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "保存中…" else "保存档案")
            }
        }
    }
}

@Composable
private fun BabyProfileCard(baby: BabyEntity, onEdit: () -> Unit) {
    val birthDate = java.time.Instant.ofEpochMilli(baby.birthTime)
        .atZone(ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(birthDate, LocalDate.now()).coerceAtLeast(0) + 1

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("宝宝档案", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(baby.displayName, style = MaterialTheme.typography.headlineSmall)
            Text("出生日期：$birthDate")
            Text("今天是第 $days 天", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onEdit) { Text("编辑档案") }
        }
    }
}

@Composable
private fun QuickAction(label: String, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = { }, enabled = false, modifier = modifier) {
        Text(label)
    }
}
