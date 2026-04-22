package dev.pan4ur.abitour.server.service

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class QrPrintPdfService {
    fun buildPdf(templatePngBytes: ByteArray, qrCode: String, routeTitle: String): ByteArray {
        val canvasWidth = 864
        val canvasHeight = 1283

        val template = ImageIO.read(templatePngBytes.inputStream())
            ?: error("Failed to decode QR template image")
        val qrImage = generateQrImage(qrCode, 444, 428)

        val composed = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
        val g = composed.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

        g.drawImage(template, 0, 0, canvasWidth, canvasHeight, null)

        g.drawImage(qrImage, 212, 392, 444, 428, null)

        g.color = Color(0x45, 0x3A, 0x59)
        g.font = Font("Arial", Font.BOLD, 26)
        drawCenteredText(g, qrCode, 170, 959, 505, 37)

        g.font = Font("Arial", Font.BOLD, 23)
        g.drawString(routeTitle, 275, 1188 + g.fontMetrics.ascent)
        g.dispose()

        val pdf = PDDocument()
        try {
            val page = PDPage(PDRectangle(canvasWidth.toFloat(), canvasHeight.toFloat()))
            pdf.addPage(page)
            val pdImage = LosslessFactory.createFromImage(pdf, composed)
            PDPageContentStream(pdf, page).use { stream ->
                stream.drawImage(pdImage, 0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat())
            }
            val out = ByteArrayOutputStream()
            pdf.save(out)
            return out.toByteArray()
        } finally {
            pdf.close()
        }
    }

    private fun generateQrImage(payload: String, width: Int, height: Int): BufferedImage {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, width, height, hints)
        return matrixToImage(matrix)
    }

    private fun matrixToImage(matrix: BitMatrix): BufferedImage {
        val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)
        val on = Color.BLACK.rgb
        val off = Color.WHITE.rgb
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                image.setRGB(x, y, if (matrix.get(x, y)) on else off)
            }
        }
        return image
    }

    private fun drawCenteredText(
        g: java.awt.Graphics2D,
        text: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        val fm = g.fontMetrics
        var safe = text
        while (fm.stringWidth(safe) > width && safe.length > 3) {
            safe = safe.dropLast(1)
        }
        if (safe != text && safe.length > 3) {
            safe = "${safe.dropLast(3)}..."
        }
        val tx = x + (width - fm.stringWidth(safe)) / 2
        val ty = y + (height - fm.height) / 2 + fm.ascent
        g.drawString(safe, tx, ty)
    }
}
