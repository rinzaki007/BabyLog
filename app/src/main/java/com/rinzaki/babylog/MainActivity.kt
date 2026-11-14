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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BabyLogApp() }
    }
}

@Composable
private fun BabyLogApp() {
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
                    Card(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(20.dp)) {
                            Text("宝宝档案尚未建立", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Text("完成宝宝出生信息设置后，这里会显示出生天数和今日记录。")
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { }, enabled = false) { Text("建立宝宝档案（即将开放）") }
                        }
                    }
                }
                item {
                    Text("快捷记录", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAction("喂养")
                        QuickAction("尿布")
                        QuickAction("睡眠")
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
                            Text("记录功能正在搭建，当前版本不会保存任何真实数据。")
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
private fun QuickAction(label: String) {
    OutlinedButton(onClick = { }, enabled = false, modifier = Modifier.weight(1f)) {
        Text(label)
    }
}
