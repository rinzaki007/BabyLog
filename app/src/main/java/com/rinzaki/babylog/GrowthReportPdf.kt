package com.rinzaki.babylog

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PDF_PAGE_WIDTH = 595
private const val PDF_PAGE_HEIGHT = 842
private const val PDF_MARGIN = 44f

@Composable
internal fun GrowthReportCard(isExporting: Boolean, onExport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("成长报告", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "把宝宝从出生至今的成长测量、日常记录、里程碑和辅食探索整理成 PDF，保存在本地或分享给家人。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onExport, enabled = !isExporting, modifier = Modifier.fillMaxWidth()) {
                Text(if (isExporting) "正在生成报告…" else "生成并分享成长报告 PDF")
            }
            Text(
                "报告在本机生成，不会自动上传；仅整理已有记录，不作医学评估。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun createGrowthReportPdf(
    context: Context,
    baby: BabyEntity,
    events: List<BabyEventEntity>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): File {
    val report = buildGrowthReportData(baby, events, now, zone)
    val document = PdfDocument()
    val renderer = GrowthReportPdfRenderer(document, report, zone)
    try {
        renderer.render()
        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = report.babyName.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(24).ifBlank { "宝宝" }
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
        val file = File(directory, "BabyLog-成长报告-$safeName-$date.pdf")
        FileOutputStream(file).use { output -> document.writeTo(output) }
        return file
    } finally {
        document.close()
    }
}

internal fun shareGrowthReport(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "BabyLog 成长报告", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(sendIntent, "分享成长报告").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private class GrowthReportPdfRenderer(
    private val document: PdfDocument,
    private val report: GrowthReportData,
    private val zone: ZoneId
) {
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(58, 54, 64)
        textSize = 10.5f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(112, 105, 119)
        textSize = 9f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(91, 75, 132)
        textSize = 15f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(65, 54, 91)
        textSize = 25f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(58, 54, 64)
        textSize = 10.5f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(183, 121, 105)
        strokeWidth = 1f
    }
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = PDF_MARGIN

    fun render() {
        startPage()
        drawText("BabyLog 成长档案", titlePaint, 34f)
        drawText("一份记录成长点滴的家庭纪念", smallPaint, 18f)
        drawRule()
        drawText(report.babyName, headingPaint, 24f)
        drawText("出生日期：${report.birthDate}    当前年龄：${report.ageLabel}", bodyPaint)
        drawText("性别：${report.sex}    报告生成：${report.reportDate}", bodyPaint)
        drawSection("成长记录概览")
        drawText("记录总数：${report.eventCount} 条")
        drawText("喂养：${report.feedingCount} 次 · 已记录奶量：${report.recordedMilkMl} ml · 亲喂：${report.breastfeedingMinutes} 分钟")
        drawText("睡眠：${formatDuration(report.sleepMinutes)} · 日常护理：${report.diaperCount} 次")
        drawText("辅食：${report.solidFoodCount} 次 · 营养补充：${report.supplementCount} 次 · 疫苗：${report.vaccineCount} 次")
        drawSection("成长测量")
        if (report.growthMeasurements.isEmpty()) {
            drawText("暂未记录出生测量或后续成长数据。", smallPaint)
        } else {
            report.growthMeasurements.forEach { point ->
                val date = formatDate(point.occurredAt)
                val values = listOfNotNull(
                    point.weightKg?.let { "${"%.2f".format(Locale.CHINA, it)} kg" },
                    point.lengthCm?.let { "${"%.1f".format(Locale.CHINA, it)} cm 身长" },
                    point.headCircumferenceCm?.let { "${"%.1f".format(Locale.CHINA, it)} cm 头围" }
                ).joinToString(" · ")
                drawText("$date${if (point.isBirthMeasurement) "（出生）" else ""}：$values")
            }
        }
        drawSection("成长里程碑")
        if (report.milestones.isEmpty()) drawText("暂未记录成长里程碑。", smallPaint)
        else report.milestones.forEach { drawText("${formatDate(it.occurredAt)} · ${it.title}（${it.detail}）") }

        drawSection("辅食探索")
        if (report.foodExploration.isEmpty()) drawText("暂未记录辅食尝试。", smallPaint)
        else report.foodExploration.take(30).forEach { drawText(it) }

        drawSection("最近记录")
        if (report.recentRecords.isEmpty()) drawText("暂未添加日常记录。", smallPaint)
        else report.recentRecords.forEach {
            drawText("${formatDate(it.occurredAt)} · ${it.title}", boldPaint)
            drawText(it.detail, smallPaint, 14f)
        }

        drawRule()
        drawText("本报告由 BabyLog 根据当前设备中的已有记录自动整理。未填写的数据不会推测或补全。", smallPaint)
        drawText("本报告仅用于家庭成长记录与回顾，不构成医疗建议、发育评估或诊断。", smallPaint)
        finishPage()
    }

    private fun startPage() {
        pageNumber += 1
        val info = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, pageNumber).create()
        page = document.startPage(info)
        canvas = page?.canvas
        canvas?.drawColor(android.graphics.Color.WHITE)
        y = PDF_MARGIN
    }

    private fun finishPage() {
        canvas?.drawText("BabyLog · 家庭成长档案", PDF_MARGIN, PDF_PAGE_HEIGHT - 22f, smallPaint)
        canvas?.drawText(pageNumber.toString(), PDF_PAGE_WIDTH - PDF_MARGIN, PDF_PAGE_HEIGHT - 22f, smallPaint)
        page?.let(document::finishPage)
        page = null
        canvas = null
    }

    private fun ensureSpace(height: Float) {
        if (y + height > PDF_PAGE_HEIGHT - PDF_MARGIN - 24f) {
            finishPage()
            startPage()
        }
    }

    private fun drawSection(title: String) {
        ensureSpace(34f)
        y += 12f
        canvas?.drawText(title, PDF_MARGIN, y, headingPaint)
        y += 8f
        drawRule()
    }

    private fun drawRule() {
        ensureSpace(8f)
        canvas?.drawLine(PDF_MARGIN, y, PDF_PAGE_WIDTH - PDF_MARGIN, y, accentPaint)
        y += 12f
    }

    private fun drawText(text: String, paint: Paint = bodyPaint, spacing: Float = 17f) {
        val available = PDF_PAGE_WIDTH - PDF_MARGIN * 2
        val lines = wrapText(text, paint, available)
        lines.forEach { line ->
            ensureSpace(spacing)
            canvas?.drawText(line, PDF_MARGIN, y, paint)
            y += spacing
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        var current = StringBuilder()
        text.forEach { char ->
            val candidate = current.toString() + char
            if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                result += current.toString()
                current = StringBuilder().append(char)
            } else current.append(char)
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun formatDate(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))

    private fun formatDuration(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分钟" else "$minutes 分钟"
}
