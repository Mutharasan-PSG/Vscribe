package com.example.vs

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.SearchView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadActivity : AppCompatActivity() {

    private lateinit var listViewFiles: ListView
    private lateinit var searchView: SearchView
    private lateinit var buttonRefresh: Button
    private lateinit var spinnerFilter: Spinner
    private lateinit var database: DatabaseReference
    private var fileList: MutableList<Map<String, String>> = mutableListOf()
    private var adapter: ArrayAdapter<String>? = null
    private var fileContentToSave: String = ""
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)

        listViewFiles = findViewById(R.id.list_view_files)
        searchView = findViewById(R.id.search_view)
        buttonRefresh = findViewById(R.id.button_refresh)
        spinnerFilter = findViewById(R.id.spinner_filter)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance().reference.child("Files")

        setupListView()
        setupSearchView()
        setupRefreshButton()
        setupFilterSpinner()
        setupSpeechRecognizer()

        loadFilesFromFirebase()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizer", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("SpeechRecognizer", "End of speech")
            }

            override fun onError(error: Int) {
                Log.e("SpeechRecognizer", "Error: $error")
            }

            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { resultList ->
                    val recognizedText = resultList[0]
                    handleSpeechResult(recognizedText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Optional: Add a button to start speech recognition
        val btnSpeech = findViewById<ImageButton>(R.id.btn_speech)
        btnSpeech.setOnClickListener {
            startSpeechRecognition()
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleSpeechResult(recognizedText: String) {
        when {
            recognizedText.contains("Home page", ignoreCase = true) -> {
                startActivity(Intent(this, HomeActivity::class.java))
            }

            recognizedText.contains("Speech To Text", ignoreCase = true) -> {
                startActivity(Intent(this, SpeechToTextActivity::class.java))
            }
            recognizedText.contains("Voice Calculator", ignoreCase = true) -> {
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
            }
            recognizedText.contains("Voice To Do List", ignoreCase = true) -> {
                startActivity(Intent(this, VoiceToDoListActivity::class.java))
            }
            recognizedText.contains("Profile", ignoreCase = true) -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }

        }
    }

    private fun loadFilesFromFirebase() {
        val sdf = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
        val currentMonth = sdf.format(Date())
        val currentUserId = SessionManager(this).getUserId() ?: "unknown"

        database.child(currentMonth).orderByChild("userId").equalTo(currentUserId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                fileList.clear()
                for (fileSnapshot in snapshot.children) {
                    val fileData = fileSnapshot.value as? Map<String, String>
                    fileData?.let { fileList.add(it) }
                }
                updateListView()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DownloadActivity", "Failed to load files", error.toException())
            }
        })
    }


    private fun setupListView() {
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fileList.map { it["fileName"] ?: "Unknown" })
        listViewFiles.adapter = adapter

        listViewFiles.setOnItemClickListener { _, _, position, _ ->
            val selectedFile = fileList[position]
            val fileContent = selectedFile["content"] ?: return@setOnItemClickListener
            val fileName = selectedFile["fileName"] ?: "Unknown.txt"
            fileContentToSave = fileContent
            showFileTypeSelectionDialog(fileName)
        }
    }

    private fun showFileTypeSelectionDialog(fileName: String) {
        val fileTypes = arrayOf("TXT", "PDF", "DOCX")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select File Type")
        builder.setItems(fileTypes) { dialog, which ->
            val selectedFileType = fileTypes[which]
            val newFileName = when (selectedFileType) {
                "PDF" -> fileName.replaceAfterLast('.', "pdf")
                "DOCX" -> fileName.replaceAfterLast('.', "docx")
                else -> fileName.replaceAfterLast('.', "txt")
            }
            openStorageToSaveFile(newFileName)
        }
        builder.show()
    }

    private fun openStorageToSaveFile(fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = when {
                fileName.endsWith(".pdf") -> "application/pdf"
                fileName.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                else -> "text/plain"
            }
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                when {
                    uri.toString().endsWith(".pdf") -> savePdfToFile(uri, fileContentToSave)
                    uri.toString().endsWith(".docx") -> saveDocxToFile(uri, fileContentToSave)
                    else -> saveTxtToFile(uri, fileContentToSave)
                }
            }
        }
    }

    private fun saveTxtToFile(uri: Uri, content: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                Toast.makeText(this, "File saved successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("DownloadActivity", "Failed to save file", e)
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savePdfToFile(uri: Uri, content: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = PdfWriter(outputStream)
                val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(writer)
                val document = Document(pdfDoc)
                document.add(Paragraph(content))
                document.close()
                Toast.makeText(this, "PDF saved successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("DownloadActivity", "Failed to save PDF", e)
            Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveDocxToFile(uri: Uri, content: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val document = XWPFDocument()
                val paragraph = document.createParagraph()
                val run = paragraph.createRun()
                run.setText(content)
                document.write(outputStream)
                Toast.makeText(this, "DOCX saved successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("DownloadActivity", "Failed to save DOCX", e)
            Toast.makeText(this, "Failed to save DOCX", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateListView() {
        adapter?.clear()
        adapter?.addAll(fileList.map { it["fileName"] ?: "Unknown" })
        adapter?.notifyDataSetChanged()
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterFiles(query)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterFiles(newText)
                return false
            }
        })
    }

    private fun setupRefreshButton() {
        buttonRefresh.setOnClickListener {
            loadFilesFromFirebase()
        }
    }

    private fun setupFilterSpinner() {
        val filters = listOf("No Filter", "Date", "Month", "Year", "File Type", "Ascending", "Descending", "Numbers First")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filters)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = adapter

        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val filterOption = filters[position]
                applyFilter(filterOption)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun filterFiles(query: String?) {
        val filteredFiles = fileList.filter {
            it["fileName"]?.contains(query ?: "", ignoreCase = true) == true
        }
        adapter?.clear()
        adapter?.addAll(filteredFiles.map { it["fileName"] ?: "Unknown" })
        adapter?.notifyDataSetChanged()
    }

    private fun applyFilter(filterOption: String) {
        val sortedFiles = when (filterOption) {
            "Date" -> fileList.sortedBy { it["fileDate"] }
            "Month" -> fileList.sortedBy { it["fileMonth"] }
            "Year" -> fileList.sortedBy { it["fileYear"] }
            "File Type" -> fileList.sortedBy { it["fileType"] }
            "Ascending" -> fileList.sortedBy { it["fileName"] }
            "Descending" -> fileList.sortedByDescending { it["fileName"] }
            "Numbers First" -> fileList.sortedBy { it["fileName"]?.toIntOrNull() ?: Int.MAX_VALUE }
            else -> fileList
        }
        adapter?.clear()
        adapter?.addAll(sortedFiles.map { it["fileName"] ?: "Unknown" })
        adapter?.notifyDataSetChanged()
    }

    companion object {
        const val CREATE_FILE_REQUEST_CODE = 1
    }
}