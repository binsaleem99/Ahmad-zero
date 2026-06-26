package com.zero.crm.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.zero.crm.data.Lead
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    // Compiles complete tabular records for Excel readability
    fun exportToExcelCSV(context: Context, fileName: String, clientList: List<Lead>) {
        val builder = StringBuilder()
        builder.append("Name,Phone Number,Status,Rating,Last Interaction Date\n")
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        for (client in clientList) {
            val dateStr = dateFormat.format(Date(client.lastContacted))
            // Escape commas for CSV safety
            val escapedName = client.name.replace(",", " ")
            val escapedStatus = client.status.replace(",", " ")
            builder.append("$escapedName,${client.phone},$escapedStatus,${client.rating},$dateStr\n")
        }
        
        executeShareIntent(context, fileName, builder.toString(), "text/csv")
    }

    // Leverages core rendering channels to compile PDF visually
    fun exportToPdf(context: Context, fileName: String, clientList: List<Lead>) {
        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint()
            
            var yPosition = 50f
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("ZERO CRM - Client Report", 50f, yPosition, paint)
            
            yPosition += 40f
            paint.textSize = 12f
            paint.isFakeBoldText = false
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            for (client in clientList) {
                if (yPosition > 800f) break // Simple pagination logic
                val dateStr = dateFormat.format(Date(client.lastContacted))
                canvas.drawText("${client.name} | ${client.phone} | Status: ${client.status} | Rating: ${client.rating}★ | Contacted: $dateStr", 50f, yPosition, paint)
                yPosition += 25f
            }
            
            pdfDocument.finishPage(page)
            
            val file = File(context.cacheDir, "${fileName}.pdf")
            file.outputStream().use { os ->
                pdfDocument.writeTo(os)
            }
            pdfDocument.close()
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Download PDF Report"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun executeShareIntent(context: Context, fileName: String, content: String, mimeType: String) {
        try {
            val file = File(context.cacheDir, "${fileName}.csv")
            file.writeText(content)
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Download CSV Report"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
