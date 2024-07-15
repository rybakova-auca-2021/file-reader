package com.example.file_reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.SeekBar
import androidx.navigation.fragment.findNavController
import com.example.file_reader.databinding.FragmentFilePageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.*
import java.nio.charset.Charset

class FilePageFragment : Fragment() {

    private var _binding: FragmentFilePageBinding? = null
    private val binding get() = _binding!!

    private var currentTextSize = 16f
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var pageCount = 0
    private var totalTextPages = 0
    private var currentTextPage = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilePageBinding.inflate(inflater, container, false)
        val view = binding.root

        val fileUriString = arguments?.getString("fileUri")
        val fileUri = Uri.parse(fileUriString)

        // Load the document text in the background
        CoroutineScope(Dispatchers.Main).launch {
            when (requireContext().contentResolver.getType(fileUri)) {
                "application/pdf" -> {
                    binding.scrollView.visibility = View.GONE
                    binding.imageViewPdf.visibility = View.VISIBLE
                    binding.seekBar.visibility = View.VISIBLE
                    displayPdf(fileUri)
                }
                else -> {
                    val documentText = withContext(Dispatchers.IO) {
                        readTextFromUri(fileUri)
                    }
                    binding.imageViewPdf.visibility = View.GONE
                    binding.scrollView.visibility = View.VISIBLE
                    binding.textViewText.text = documentText
                    displayText(documentText)
                }
            }
        }

        binding.buttonBack.setOnClickListener {
            findNavController().navigate(R.id.action_filePageFragment_to_mainPageFragment)
        }

        binding.zoomInButton.setOnClickListener {
            if (currentTextSize < 30f) {
                currentTextSize += 2f
                binding.textViewText.textSize = currentTextSize
            }
        }

        binding.zoomOutButton.setOnClickListener {
            if (currentTextSize > 10f) {
                currentTextSize -= 2f
                binding.textViewText.textSize = currentTextSize
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    showPage(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Do nothing
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Do nothing
            }
        })

        return view
    }

    private fun readTextFromUri(uri: Uri): String {
        val contentResolver = requireContext().contentResolver
        val inputStream = contentResolver.openInputStream(uri)

        // Check the MIME type and handle accordingly
        val mimeType = contentResolver.getType(uri)
        return when (mimeType) {
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                readDocxFromUri(inputStream!!)
            }
            "application/vnd.ms-excel" -> {
                readXlsFromUri(inputStream!!)
            }
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> {
                readXlsxFromUri(inputStream!!)
            }
            "application/pdf" -> {
                "PDF file detected"
            }
            else -> {
                readPlainTextFromUri(inputStream!!)
            }
        }
    }

    private fun readPlainTextFromUri(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream, Charset.forName("UTF-8")))
        val stringBuilder = StringBuilder()
        var line: String?

        try {
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append('\n')
            }
        } finally {
            reader.close()
        }

        return stringBuilder.toString()
    }

    private fun readDocxFromUri(inputStream: InputStream): String {
        val document = XWPFDocument(inputStream)
        val stringBuilder = StringBuilder()

        for (paragraph in document.paragraphs) {
            stringBuilder.append(paragraph.text).append('\n')
        }

        document.close()
        return stringBuilder.toString()
    }

    private fun readXlsFromUri(inputStream: InputStream): String {
        val workbook = HSSFWorkbook(inputStream)
        return readWorkbook(workbook)
    }

    private fun readXlsxFromUri(inputStream: InputStream): String {
        val workbook = XSSFWorkbook(inputStream)
        return readWorkbook(workbook)
    }

    private fun readWorkbook(workbook: Workbook): String {
        val stringBuilder = StringBuilder()
        val sheet = workbook.getSheetAt(0)

        for (row in sheet) {
            for (cell in row) {
                stringBuilder.append(cell.toString()).append('\t')
            }
            stringBuilder.append('\n')
        }

        workbook.close()
        return stringBuilder.toString()
    }

    private fun displayPdf(uri: Uri) {
        val contentResolver = requireContext().contentResolver
        val fileDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: return
        pdfRenderer = PdfRenderer(fileDescriptor)

        pageCount = pdfRenderer?.pageCount ?: 0
        binding.seekBar.max = pageCount - 1

        showPage(0)
    }

    private fun showPage(index: Int) {
        pdfRenderer?.let { renderer ->
            currentPage?.close()
            currentPage = renderer.openPage(index).apply {val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                binding.imageViewPdf.setImageBitmap(bitmap)

                binding.pageInfo.text = "Page ${index + 1} of $pageCount"
            }
        }
    }

    private fun displayText(text: String) {
        val lines = text.split("\n")
        totalTextPages = (lines.size + 999) / 1000
        currentTextPage = 0

        binding.seekBar.max = totalTextPages - 1
        showTextPage(currentTextPage)
    }

    private fun showTextPage(index: Int) {
        val lines = binding.textViewText.text.split("\n")
        val start = index * 1000
        val end = minOf((index + 1) * 1000, lines.size)

        val pageText = lines.subList(start, end).joinToString("\n")
        binding.textViewText.text = pageText
        binding.pageInfo.text = "Page ${index + 1} of $totalTextPages"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        currentPage?.close()
        pdfRenderer?.close()
    }
}
