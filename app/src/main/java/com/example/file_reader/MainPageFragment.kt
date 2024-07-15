package com.example.file_reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.file_reader.databinding.FragmentMainPageBinding

class MainPageFragment : Fragment() {

    private var _binding: FragmentMainPageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMainPageBinding.inflate(inflater, container, false)
        val view = binding.root

        binding.cardViewAllFiles.setOnClickListener {
            openFileExplorer("*/*")
        }
        binding.cardViewPdf.setOnClickListener {
            openFileExplorer("application/pdf")
        }
        binding.cardViewDoc.setOnClickListener {
            openFileExplorer("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        }
        binding.cardViewXlc.setOnClickListener {
            openFileExplorer("application/vnd.ms-excel")
        }
        binding.cardViewTxt.setOnClickListener {
            openFileExplorer("text/plain")
        }

        return view
    }

    private fun openFileExplorer(mimeType: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = mimeType
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, "/")

        startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                openFile(uri)
            }
        }
    }

    private fun openFile(uri: Uri) {
        val bundle = Bundle().apply {
            putString("fileUri", uri.toString())
        }
        findNavController().navigate(R.id.action_mainPageFragment_to_filePageFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_CODE_OPEN_DOCUMENT = 1
    }
}
