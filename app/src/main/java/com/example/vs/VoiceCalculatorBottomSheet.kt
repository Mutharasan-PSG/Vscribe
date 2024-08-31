package com.example.vs

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import net.objecthunter.exp4j.ExpressionBuilder
import net.objecthunter.exp4j.function.Function
import java.util.Locale

class VoiceCalculatorBottomSheet : BottomSheetDialogFragment(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var inputTextView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var historyListView: ListView
    private lateinit var startButton: ImageButton
    private var lastResult: Double = 0.0
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private val historyList = mutableListOf<String>()
    private lateinit var historyAdapter: VoiceCalculatorAdapter
    private lateinit var historyEmptyTextView: TextView
    private lateinit var history: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_voice_calculator, container, false)

        history=view.findViewById(R.id.history)
        inputTextView = view.findViewById(R.id.inputTextView)
        resultTextView = view.findViewById(R.id.resultTextView)
        historyListView = view.findViewById(R.id.historyListView)
        startButton = view.findViewById(R.id.btn_speech)
        historyEmptyTextView = view.findViewById(R.id.historyEmptyTextView)
        historyAdapter = VoiceCalculatorAdapter(requireContext(), historyList)
        historyListView.adapter = historyAdapter

        initializeSpeechRecognizer()
        textToSpeech = TextToSpeech(requireContext(), this)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        startButton.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            speechRecognizer.startListening(intent)
            Toast.makeText(requireContext(), "Listening to your inputs...", Toast.LENGTH_SHORT).show()
        }

        fetchHistory()

        return view
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                startButton.setImageResource(R.drawable.voice_frequency)
            }

            override fun onBeginningOfSpeech() {
                startButton.setImageResource(R.drawable.voice_frequency)
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                startButton.setImageResource(R.drawable.mic)
            }

            override fun onError(error: Int) {
                Toast.makeText(requireContext(), "Error recognizing speech", Toast.LENGTH_SHORT).show()
                startButton.setImageResource(R.drawable.mic)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val input = matches[0]
                    inputTextView.text = input
                    if (!handleNavigationCommands(input)) {
                        handleInput(input)

                    }
                }
                startButton.setImageResource(R.drawable.mic)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }


    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            val behavior = BottomSheetBehavior.from(bottomSheet)
            bottomSheet.layoutParams.height = (resources.displayMetrics.heightPixels * 0.8).toInt()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
    private fun convertLargeNumbers(input: String): String {
        return input
            .replace(Regex("(\\d+(\\.\\d+)?)\\s*lakh(s)?")) { matchResult ->
                val number = matchResult.groupValues[1].toDouble()
                (number * 100000).toString()
            }
            .replace(Regex("(\\d+(\\.\\d+)?)\\s*crore(s)?")) { matchResult ->
                val number = matchResult.groupValues[1].toDouble()
                (number * 10000000).toString()
            }
            .replace(Regex("(\\d+(\\.\\d+)?)\\s*million(s)?")) { matchResult ->
                val number = matchResult.groupValues[1].toDouble()
                (number * 1000000).toString()
            }
            .replace(Regex("(\\d+(\\.\\d+)?)\\s*billion(s)?")) { matchResult ->
                val number = matchResult.groupValues[1].toDouble()
                (number * 1000000000).toString()
            }
            .replace(Regex("(\\d+(\\.\\d+)?)\\s*trillion(s)?")) { matchResult ->
                val number = matchResult.groupValues[1].toDouble()
                (number * 1000000000000).toString()
            }
    }
    private fun handleInput(input: String) {
        try {
            var expressionString = input.lowercase(Locale.getDefault())
            expressionString = convertLargeNumbers(expressionString)
            expressionString = expressionString
                .replace("plus", "+")
                .replace("add", "+")
                .replace("minus", "-")
                .replace("subtract", "-")
                .replace("times", "*")
                .replace("multiplied by", "*")
                .replace("multiply", "*")
                .replace("divided by", "/")
                .replace("divide by", "/")
                .replace("divide", "/")
                .replace("by", "/")
                .replace("into", "*")
                .replace("power", "^")
                .replace("raised to the power of", "^")
                .replace("square", "^2")
                .replace("cube", "^3")
                .replace("square root", "sqrt")
                .replace("square root of", "sqrt")
                .replace("root", "sqrt")
                .replace("sine", "sin")
                .replace("cosine", "cos")
                .replace("tangent", "tan")
                .replace("secant", "sec")
                .replace("cosecant", "csc")
                .replace("cotangent", "cot")
                .replace("arcsine", "asin")
                .replace("arccosine", "acos")
                .replace("arctangent", "atan")
                .replace("arcsecant", "asec")
                .replace("arccosecant", "acsc")
                .replace("arccotangent", "acot")
                .replace("hyperbolic sine", "sinh")
                .replace("hyperbolic cosine", "cosh")
                .replace("hyperbolic tangent", "tanh")
                .replace("hyperbolic secant", "sech")
                .replace("hyperbolic cosecant", "csch")
                .replace("hyperbolic cotangent", "coth")
                .replace("inverse hyperbolic sine", "asinh")
                .replace("inverse hyperbolic cosine", "acosh")
                .replace("inverse hyperbolic tangent", "atanh")
                .replace("inverse hyperbolic secant", "asech")
                .replace("inverse hyperbolic cosecant", "acsch")
                .replace("inverse hyperbolic cotangent", "acoth")
                .replace("exponential", "exp")
                .replace("logarithm", "log")
                .replace("natural logarithm", "ln")
                .replace("log base 2", "log2")
                .replace("factorial", "factorial")
                .replace("result", lastResult.toString())
                .replace("answer", lastResult.toString())

            expressionString = expressionString.replace("what is|calculate|compute|find|the|and".toRegex(), "").trim()

            val factorialFunction = object : Function("factorial", 1) {
                override fun apply(vararg args: Double): Double {
                    val n = args[0].toInt()
                    return (1..n).fold(1) { acc, i -> acc * i }.toDouble()
                }
            }

            val expression = ExpressionBuilder(expressionString)
                .function(factorialFunction)
                .build()

            val result = expression.evaluate()

            lastResult = result

            val resultString = if (result % 1 == 0.0) {
                result.toLong().toString()
            } else {
                result.toString()
            }

            // Update the resultTextView to show only the result
            resultTextView.text = resultString

            // Use text-to-speech to speak the full message
            val ttsMessage = getString(R.string.speech_result, input, resultString)
            speakOut(ttsMessage)

            historyList.add("Input: $input | Result: $resultString")
            historyAdapter.notifyDataSetChanged()

            saveHistory(input, resultString)

        } catch (e: Exception) {
            val errorMessage = getString(R.string.invalid_input_format)
            resultTextView.text = errorMessage
            speakOut(errorMessage)
        }
    }



    private fun handleNavigationCommands(input: String): Boolean {
        return when {
            input.contains("home page", ignoreCase = true) -> {
                startActivity(Intent(requireContext(), HomeActivity::class.java))
                true
            }
            input.contains("speech to text", ignoreCase = true) -> {
                startActivity(Intent(requireContext(), SpeechToTextActivity::class.java))
                true
            }
            input.contains("voice calculator", ignoreCase = true) -> {
                // Prevent multiple instances of the same bottom sheet
                if (parentFragmentManager.findFragmentByTag("VoiceCalculatorBottomSheet") == null) {
                    val bottomSheet = VoiceCalculatorBottomSheet()
                    bottomSheet.show(parentFragmentManager, bottomSheet.tag)
                }
                true
            }
            input.contains("voice to do list", ignoreCase = true) -> {
                startActivity(Intent(requireContext(), VoiceToDoListActivity::class.java))
                true
            }
            input.contains("Downloads", ignoreCase = true) -> {
                startActivity(Intent(requireContext(), DownloadActivity::class.java))
                true
            }
            input.contains("Profile", ignoreCase = true) -> {
                startActivity(Intent(requireContext(), ProfileActivity::class.java))
                true
            }
            else -> false
        }
    }

    private fun speakOut(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(requireContext(), "Language not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveHistory(input: String, result: String) {
        val currentUser = auth.currentUser
        currentUser?.let {
            val userHistoryRef = database.child("users").child(it.uid).child("Calculator_history")
            val historyItem = mapOf("input" to input, "result" to result)
            userHistoryRef.push().setValue(historyItem)
        }
    }

    private fun fetchHistory() {
        val currentUser = auth.currentUser
        currentUser?.let {
            val userHistoryRef = database.child("users").child(it.uid).child("Calculator_history")
            userHistoryRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    historyList.clear()
                    if (snapshot.exists()) {
                        for (historySnapshot in snapshot.children) {
                            val input = historySnapshot.child("input").getValue(String::class.java)
                            val result = historySnapshot.child("result").getValue(String::class.java)
                            if (input != null && result != null) {
                                historyList.add("Input: $input | Result: $result")
                            }
                        }
                    }
                    historyAdapter.notifyDataSetChanged()
                    if (historyList.isEmpty()) {
                        historyEmptyTextView.visibility = View.VISIBLE
                        history.visibility = View.GONE
                        historyListView.visibility = View.GONE
                    } else {
                        historyEmptyTextView.visibility = View.GONE
                        history.visibility = View.VISIBLE
                        historyListView.visibility = View.VISIBLE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to load history", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.shutdown()
    }
}

