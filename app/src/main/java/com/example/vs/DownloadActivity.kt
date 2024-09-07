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
import android.widget.ImageButton
import android.widget.ListView
import android.widget.SearchView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
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
    private lateinit var buttonRefresh: ImageButton
    private lateinit var spinnerFilter: Spinner
    private lateinit var firestore: FirebaseFirestore
    private var fileList: MutableList<Map<String, String>> = mutableListOf()
    private var adapter: ArrayAdapter<String>? = null
    private var fileContentToSave: String = ""
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var btnSpeech: ImageButton
    private lateinit var textNoFiles: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)

        listViewFiles = findViewById(R.id.list_view_files)
        searchView = findViewById(R.id.search_view)
        buttonRefresh = findViewById(R.id.button_refresh)
        spinnerFilter = findViewById(R.id.spinner_filter)
        btnSpeech = findViewById(R.id.btn_speech)
        textNoFiles = findViewById(R.id.text_no_files)
        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()

        setupListView()
        setupSearchView()
        setupRefreshButton()
        setupFilterSpinner()
        setupSpeechRecognizer()

        loadFilesFromFirestore()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizer", "Ready for speech")
                btnSpeech.setImageResource(R.drawable.voice_frequencyy)
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Beginning of speech")
                btnSpeech.setImageResource(R.drawable.voice_frequencyy)
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("SpeechRecognizer", "End of speech")
                btnSpeech.setImageResource(R.drawable.mic)
            }

            override fun onError(error: Int) {
                Log.e("SpeechRecognizer", "Error: $error")
                btnSpeech.setImageResource(R.drawable.mic)
            }

            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { resultList ->
                    val recognizedText = resultList[0]
                    handleSpeechResult(recognizedText)
                }
                btnSpeech.setImageResource(R.drawable.mic)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        btnSpeech.setOnClickListener {
            startSpeechRecognition()
            Toast.makeText(this, "Listening to your speech", Toast.LENGTH_SHORT).show()
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
        val pageMappings = mapOf(
            "Home page" to HomeActivity::class.java,
            "Speech To Text" to SpeechToTextActivity::class.java,
            "Voice Calculator" to VoiceCalculatorBottomSheet::class.java,
            "Voice To Do List" to VoiceToDoListActivity::class.java,
            "Profile" to ProfileActivity::class.java,
            "History of task page" to HistoryActivity::class.java,
            "Go to Home page" to HomeActivity::class.java,
            "Go to Speech To Text page" to SpeechToTextActivity::class.java,
            "Go to Voice Calculator page" to VoiceCalculatorBottomSheet::class.java,
            "Go to Downloads page" to DownloadActivity::class.java,
            "Go to History of task page" to HistoryActivity::class.java,
            "Go to Voice to do list page" to VoiceToDoListActivity::class.java,
            "Go to Profile page" to ProfileActivity::class.java
        )

        pageMappings.entries.find { recognizedText.contains(it.key, ignoreCase = true) }?.let { entry ->
            val clazz = entry.value
            if (clazz == VoiceCalculatorBottomSheet::class.java) {
                // Show bottom sheet if the action is to display the VoiceCalculatorBottomSheet
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
            } else {
                // Start activity for other cases
                startActivity(Intent(this, clazz))
            }
        }
    }

    private fun loadFilesFromFirestore() {
        val sdfMonth = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
        val currentMonth = sdfMonth.format(Date())
        val currentUserId = SessionManager(this).getUserId() ?: "unknown"

        val firestore = FirebaseFirestore.getInstance()

        // Query the UserFiles subcollection for the current month
        firestore.collection("Files")
            .document(currentMonth)
            .collection("UserFiles")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                fileList.clear()
                for (document in querySnapshot.documents) {
                    val fileData = document.data as? Map<String, String>
                    fileData?.let { fileList.add(it) }
                }
                fileList.sortByDescending { it["timestamp"] }
                updateListView()
            }
            .addOnFailureListener { exception ->
                Log.e("DownloadActivity", "Failed to load files", exception)
            }
    }


    private fun setupListView() {
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fileList.map { stripExtension(it["fileName"] ?: "Unknown") })
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
        val fileTypes = arrayOf("TXT", /*"PDF",*/ "DOCX")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select File Type")
        builder.setItems(fileTypes) { _, which ->
            val selectedFileType = fileTypes[which]
            val newFileName = when (selectedFileType) {
                // "PDF" -> fileName.replaceAfterLast('.', "pdf")
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
                val doc = XWPFDocument()
                doc.createParagraph().createRun().setText(content)
                doc.write(outputStream)
                doc.close()
                Toast.makeText(this, "DOCX saved successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("DownloadActivity", "Failed to save DOCX", e)
            Toast.makeText(this, "Failed to save DOCX", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val filteredList = fileList.filter { it["fileName"]?.contains(newText ?: "", ignoreCase = true) == true }
                adapter?.clear()
                adapter?.addAll(filteredList.map { stripExtension(it["fileName"] ?: "Unknown") })
                adapter?.notifyDataSetChanged()
                return true
            }
        })
    }

    private fun setupRefreshButton() {
        buttonRefresh.setOnClickListener {
            loadFilesFromFirestore()
            Toast.makeText(this, "Refreshing and loading the files...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateListView() {
        if (fileList.isEmpty()) {
            textNoFiles.visibility = View.VISIBLE
            listViewFiles.visibility = View.GONE
        } else {
            textNoFiles.visibility = View.GONE
            listViewFiles.visibility = View.VISIBLE
            adapter?.clear()
            adapter?.addAll(fileList.map { stripExtension(it["fileName"] ?: "Unknown") })
            adapter?.notifyDataSetChanged()
        }
    }

    private fun setupFilterSpinner() {
        val filterOptions = arrayOf("A-Z", "Z-A")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = spinnerAdapter

        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilter(filterOptions[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyFilter(filterOption: String) {
        val sortedFiles = when (filterOption) {
            "A-Z" -> fileList.sortedWith(compareBy { it["fileName"]?.let { name ->
                name.toLowerCase(Locale.getDefault()).toIntOrNull() ?: name.toLowerCase(Locale.getDefault())
            } ?: "" })
            "Z-A" -> fileList.sortedWith(compareByDescending { it["fileName"]?.let { name ->
                name.toLowerCase(Locale.getDefault()).toIntOrNull() ?: name.toLowerCase(Locale.getDefault())
            } ?: "" })
            "Numeric" -> fileList.sortedWith(compareBy { it["fileName"]?.toIntOrNull() ?: Int.MAX_VALUE })
            else -> fileList
        }
        adapter?.clear()
        adapter?.addAll(sortedFiles.map { stripExtension(it["fileName"] ?: "Unknown") })
        adapter?.notifyDataSetChanged()
    }

    private fun stripExtension(fileName: String): String {
        return fileName.substringBeforeLast('.', fileName)
    }

    companion object {
        private const val CREATE_FILE_REQUEST_CODE = 1
    }

    fun String.toDate(): Date? {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            format.parse(this)
        } catch (e: Exception) {
            Log.e("DownloadActivity", "Failed to parse date: $this", e)
            null
        }
    }
}
