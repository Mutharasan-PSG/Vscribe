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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_voice_calculator, container, false)

        inputTextView = view.findViewById(R.id.inputTextView)
        resultTextView = view.findViewById(R.id.resultTextView)
        historyListView = view.findViewById(R.id.historyListView)
        startButton = view.findViewById(R.id.btn_speech) // Changed from startButton to btn_speech

        historyAdapter = VoiceCalculatorAdapter(requireContext(), historyList)
        historyListView.adapter = historyAdapter

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        textToSpeech = TextToSpeech(requireContext(), this)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Toast.makeText(requireContext(), "Error recognizing speech", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    val input = matches[0]
                    inputTextView.text = input
                    if (!handleNavigationCommands(input)) {
                        handleInput(input)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        startButton.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            speechRecognizer.startListening(intent)
        }

        fetchHistory()

        return view
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            val behavior = BottomSheetBehavior.from(bottomSheet)
            bottomSheet.layoutParams.height = (resources.displayMetrics.heightPixels * 0.6).toInt()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun handleInput(input: String) {
        try {
            var expressionString = input.lowercase(Locale.getDefault())
            expressionString = expressionString.replace("plus", "+")
                .replace("add", "+")
                .replace("minus", "-")
                .replace("subtract", "-")
                .replace("times", "*")
                .replace("multiplied by", "*")
                .replace("multiply", "*")
                .replace("divided by", "/")
                .replace("by", "/")
                .replace("into", "*")
                .replace("raised to the power of", "^")
                .replace("square", "^2")
                .replace("cube", "^3")
                .replace("sqrt", "sqrt")
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
                .replace("absolute value", "abs")
                .replace("factorial", "factorial")
                .replace("result", lastResult.toString())

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
            resultTextView.text = resultString

            speakOut("You said: $input. Result is: $resultString")

            historyList.add("Input: $input | Result: $resultString")
            historyAdapter.notifyDataSetChanged()

            saveHistory(input, resultString)

        } catch (e: Exception) {
            resultTextView.text = "Invalid input format"
            speakOut("Invalid input format")
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
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(parentFragmentManager, bottomSheet.tag)
                true
            }
            input.contains("voice to do list", ignoreCase = true) -> {
                startActivity(Intent(requireContext(), VoiceToDoListActivity::class.java))
                true
            }
            input.contains("profile", ignoreCase = true) -> {
                startActivity(Intent(requireContext(), ProfileActivity::class.java))
                true
            }
            input.contains("downloads", ignoreCase = true) -> {
                startActivity(Intent(requireContext(), DownloadActivity::class.java))
                true
            }
            else -> false
        }
    }

    private fun saveHistory(input: String, result: String) {
        val userId = auth.currentUser?.uid ?: return
        val historyRef = database.child("users").child(userId).child("calculatorHistory")

        val historyItem = mapOf(
            "input" to input,
            "result" to result
        )

        historyRef.push().setValue(historyItem)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "History saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to save history", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchHistory() {
        val userId = auth.currentUser?.uid ?: return
        val historyRef = database.child("users").child(userId).child("calculatorHistory")

        historyRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                historyList.clear()
                for (snapshot in dataSnapshot.children) {
                    val input = snapshot.child("input").getValue(String::class.java) ?: ""
                    val result = snapshot.child("result").getValue(String::class.java) ?: ""
                    historyList.add("Input: $input | Result: $result")
                }
                historyAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load history", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.US
        }
    }

    private fun speakOut(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}
