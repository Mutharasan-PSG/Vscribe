package com.example.vs

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PolicyBottomSheetFragment : BottomSheetDialogFragment() {

    interface PolicyListener {
        fun onConfirm()
        fun onCancel()
    }

    private var listener: PolicyListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is PolicyListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement PolicyListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_policy_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnConfirm = view.findViewById<Button>(R.id.btn_confirm)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)

        btnConfirm.setOnClickListener {
            showCustomToast(requireContext(), "Please wait...", 1200) // Show custom toast for 3 seconds
            listener?.onConfirm()
            dismiss()
        }

        btnCancel.setOnClickListener {
            listener?.onCancel()
            dismiss()
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun showCustomToast(context: Context, message: String, duration: Int) {
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast.show()

        // Cancel the toast after the specified duration
        Handler(Looper.getMainLooper()).postDelayed({
            toast.cancel()
        }, duration.toLong())
    }
}
