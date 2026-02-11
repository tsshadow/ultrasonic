package org.moire.ultrasonic.fragment.tsshadow

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import org.moire.ultrasonic.R

class MultiSpinnerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    private var selectedItems = mutableSetOf<Int>()
    private var items: List<String> = emptyList()
    private val textView: TextView

    init {
        orientation = VERTICAL
        inflate(context, R.layout.tsshadow_simple_spinner_text, this)
        textView = findViewById(R.id.spinner_text)

        textView.setOnClickListener {
            showSelectionDialog()
        }
    }

    fun setItems(values: List<String>) {
        items = values
        selectedItems.clear()
        updateLabel()
    }

    private fun showSelectionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.tsshadow_multispinner, null)
        val listView = dialogView.findViewById<ListView>(R.id.multi_select_list)
        val okButton = dialogView.findViewById<Button>(R.id.btn_ok)
        val resetButton = dialogView.findViewById<Button>(R.id.btn_reset)

        val checked = BooleanArray(items.size) { selectedItems.contains(it) }
        val adapter =
            ArrayAdapter(context, android.R.layout.simple_list_item_multiple_choice, items)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        for (i in items.indices) listView.setItemChecked(i, checked[i])

        val alertDialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        okButton.setOnClickListener {
            selectedItems.clear()
            for (i in 0 until listView.count) {
                if (listView.isItemChecked(i)) selectedItems.add(i)
            }
            updateLabel()
            alertDialog.dismiss()
        }

        resetButton.setOnClickListener {
            for (i in 0 until listView.count) {
                listView.setItemChecked(i, false)
            }
            selectedItems.clear()
            updateLabel()
        }

        alertDialog.show()
    }

    private fun updateLabel() {
        textView.text = if (selectedItems.isEmpty()) {
            context.getString(R.string.select_items_placeholder)
        } else {
            selectedItems.joinToString(", ") { items[it] }
        }
    }

    fun setSelectedItems(selected: List<String>) {
        selectedItems.clear()
        selected.forEach { label ->
            val index = items.indexOf(label)
            if (index != -1) {
                selectedItems.add(index)
            }
        }
        updateLabel()
    }

    fun getSelectedItems(): List<String> = selectedItems.map { items[it] }
}
