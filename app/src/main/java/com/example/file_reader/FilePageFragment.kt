package com.example.file_reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.SeekBar
import androidx.fragment.app.Fragment
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
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

class FilePageFragment : Fragment() {

    private lateinit var binding: FragmentFilePageBinding
    private var currentTextSize = 16f
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var pageCount = 0
    private var totalTextPages = 0
    private var currentTextPage = 0
    var fileUriString = ""
    var fileName = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFilePageBinding.inflate(inflater, container, false)
        val view = binding.root

        fileUriString = arguments?.getString("fileUri").toString()
        fileName = arguments?.getString("fileName").toString()
        val fileUri = Uri.parse(fileUriString)

        CoroutineScope(Dispatchers.Main).launch {
            when (requireContext().contentResolver.getType(fileUri)) {
                "application/pdf" -> {
                    binding.apply {
                        scrollView.visibility = View.GONE
                        imageViewPdf.visibility = View.VISIBLE
                        seekBar.visibility = View.VISIBLE
                        zoomInButton.visibility = View.GONE
                        zoomOutButton.visibility = View.GONE
                    }
                    displayPdf(fileUri)
                }
                else -> {
                    val documentText = withContext(Dispatchers.IO) {
                        readTextFromUri(fileUri)
                    }
                    binding.apply {
                        imageViewPdf.visibility = View.GONE
                        scrollView.visibility = View.VISIBLE
                        textViewText.text = documentText
                    }
                    setupInitialView()
                    setupScrollView()
                }
            }
        }
        binding.buttonBack.setOnClickListener {
            findNavController().navigate(R.id.mainPageFragment)
        }
        setupZoomButtons()
        setupSeekBar()

        return view
    }

    private fun setupZoomButtons() {
        binding.zoomInButton.setOnClickListener {
            if (currentTextSize < 30f) {
                currentTextSize += 2f
                binding.textViewText.textSize = currentTextSize
                updatePageInfo()
            }
        }

        binding.zoomOutButton.setOnClickListener {
            if (currentTextSize > 10f) {
                currentTextSize -= 2f
                binding.textViewText.textSize = currentTextSize
                updatePageInfo()
            }
        }
    }

    private fun updatePageInfo() {
        binding.scrollView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.scrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val textHeight = binding.textViewText.height
                val scrollViewHeight = binding.scrollView.height

                val totalScrollableHeight = textHeight - scrollViewHeight
                if (totalScrollableHeight > 0) {
                    totalTextPages = (textHeight.toFloat() / scrollViewHeight).toInt()
                    currentTextPage = ((binding.scrollView.scrollY.toFloat() / totalScrollableHeight) * totalTextPages).toInt()
                    binding.pageInfo.text = "${fileName}:\nPage ${currentTextPage + 1} of ${totalTextPages + 1}"

                    binding.seekBar.progress = currentTextPage
                    binding.seekBar.max = totalTextPages
                } else {
                    binding.pageInfo.text = "Page 1 of 1"
                    binding.seekBar.progress = 0
                    binding.seekBar.max = 0
                }
            }
        })
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    showPage(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupScrollView() {
        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val textHeight = binding.textViewText.height
            val scrollViewHeight = binding.scrollView.height

            val totalScrollableHeight = textHeight - scrollViewHeight
            if (totalScrollableHeight > 0) {
                totalTextPages = (textHeight.toFloat() / scrollViewHeight).toInt()
                currentTextPage = ((scrollY.toFloat() / totalScrollableHeight) * totalTextPages).toInt()
                binding.pageInfo.text = "${fileName}:\nPage ${currentTextPage + 1} of ${totalTextPages + 1}"

                binding.seekBar.progress = currentTextPage
                binding.seekBar.max = totalTextPages
            }
        }
    }

    private fun setupInitialView() {
        binding.scrollView.viewTreeObserver.addOnGlobalLayoutListener {
            val textHeight = binding.textViewText.height
            val scrollViewHeight = binding.scrollView.height

            val totalScrollableHeight = textHeight - scrollViewHeight
            if (totalScrollableHeight > 0) {
                totalTextPages = (textHeight.toFloat() / scrollViewHeight).toInt()
                currentTextPage = ((binding.scrollView.scrollY.toFloat() / totalScrollableHeight) * totalTextPages).toInt()
                binding.pageInfo.text = "${fileName}:\nPage ${currentTextPage + 1} of ${totalTextPages + 1}"

                binding.seekBar.progress = currentTextPage
                binding.seekBar.max = totalTextPages
            } else {
                binding.pageInfo.text = "Page 1 of 1"
                binding.seekBar.progress = 0
                binding.seekBar.max = 0
            }
        }
    }

    private fun readTextFromUri(uri: Uri): String {
        val contentResolver = requireContext().contentResolver
        val inputStream = contentResolver.openInputStream(uri)

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
            currentPage = renderer.openPage(index).apply {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                binding.imageViewPdf.setImageBitmap(bitmap)

                binding.pageInfo.text = "${fileName}: Page ${index + 1} of $pageCount"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentPage?.close()
        pdfRenderer?.close()
    }
}
