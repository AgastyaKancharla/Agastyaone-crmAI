package com.agastyaone.crmai.data.billing

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.agastyaone.crmai.data.patients.Patient
import com.agastyaone.crmai.data.tenant.Clinic
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PAGE_WIDTH = 595 // A4 at 72dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f

/**
 * Renders one invoice as a real, single-page PDF using android.graphics.pdf.PdfDocument -
 * part of the Android SDK, so this needs no third-party PDF library for a layout this
 * simple. Saved under cacheDir/invoices/ (see file_paths.xml's invoice_pdfs entry) so the
 * caller can hand the returned File to a FileProvider Uri for the Android share sheet,
 * same cache+FileProvider pattern as Phase 3b's ImageCaptureUtils.
 */
object InvoicePdfGenerator {

    fun generate(context: Context, clinic: Clinic, patient: Patient, invoice: Invoice): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val headingPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 11f }
        val smallPaint = Paint().apply { textSize = 9f; color = 0xFF555555.toInt() }
        val currency = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

        val colProcedure = MARGIN
        val colHsn = MARGIN + 230f
        val colQty = MARGIN + 320f
        val colUnit = MARGIN + 360f
        val colTotal = PAGE_WIDTH - MARGIN - 70f

        var y = MARGIN + 10f

        canvas.drawText(clinic.clinicName.ifBlank { "Clinic" }, MARGIN, y, titlePaint)
        y += 22f
        val addressLine = listOfNotNull(
            clinic.address.ifBlank { null },
            clinic.city.ifBlank { null },
            clinic.state.ifBlank { null },
        ).joinToString(", ")
        if (addressLine.isNotBlank()) {
            canvas.drawText(addressLine, MARGIN, y, bodyPaint)
            y += 16f
        }
        clinic.gstin?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText("GSTIN: $it", MARGIN, y, bodyPaint)
            y += 16f
        }

        y += 12f
        canvas.drawText("Invoice ${invoice.invoiceNumber}", MARGIN, y, headingPaint)
        canvas.drawText(invoiceDateText(invoice), colUnit, y, bodyPaint)
        y += 22f

        canvas.drawText("Bill to: ${patient.name}", MARGIN, y, bodyPaint)
        y += 16f
        patient.phone?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText("Phone: $it", MARGIN, y, bodyPaint)
            y += 16f
        }
        canvas.drawText("Billing state: ${invoice.billingState}", MARGIN, y, bodyPaint)
        y += 26f

        canvas.drawText("Procedure", colProcedure, y, headingPaint)
        canvas.drawText("HSN/SAC", colHsn, y, headingPaint)
        canvas.drawText("Qty", colQty, y, headingPaint)
        canvas.drawText("Amount", colTotal, y, headingPaint)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, bodyPaint)
        y += 16f

        for (item in invoice.parsedLineItems) {
            canvas.drawText(item.procedureName, colProcedure, y, bodyPaint)
            canvas.drawText(item.hsnSacCode, colHsn, y, bodyPaint)
            canvas.drawText(item.quantity.toString(), colQty, y, bodyPaint)
            canvas.drawText(currency.format(item.lineTotal), colTotal, y, bodyPaint)
            y += 18f
        }

        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, bodyPaint)
        y += 20f

        fun totalsLine(label: String, amount: Double, paint: Paint = bodyPaint) {
            canvas.drawText(label, colUnit, y, paint)
            canvas.drawText(currency.format(amount), colTotal, y, paint)
            y += 18f
        }
        totalsLine("Subtotal", invoice.subtotal)
        if (invoice.cgst > 0) totalsLine("CGST", invoice.cgst)
        if (invoice.sgst > 0) totalsLine("SGST", invoice.sgst)
        if (invoice.igst > 0) totalsLine("IGST", invoice.igst)
        totalsLine("Total", invoice.total, headingPaint)
        totalsLine("Amount paid", invoice.amountPaid)
        canvas.drawText(
            "Status: ${PaymentStatus.fromId(invoice.paymentStatus).label}",
            colProcedure,
            y,
            bodyPaint,
        )

        canvas.drawText(
            "HSN/SAC codes on this invoice are placeholder values, not verified for GST filing.",
            MARGIN,
            PAGE_HEIGHT - MARGIN - 24f,
            smallPaint,
        )
        canvas.drawText(
            "Confirm the correct code and applicable rate with your accountant before filing.",
            MARGIN,
            PAGE_HEIGHT - MARGIN - 12f,
            smallPaint,
        )

        document.finishPage(page)

        val directory = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(directory, "${invoice.invoiceNumber}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun invoiceDateText(invoice: Invoice): String {
        val timestamp = invoice.issuedAt ?: return ""
        return timestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
