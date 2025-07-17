package org.moire.ultrasonic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.drakeet.multitype.ItemViewBinder
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Identifiable

/** Binder for simple divider rows with dynamic text */
class TextDividerBinder : ItemViewBinder<TextDividerBinder.TextDivider, TextDividerBinder.ViewHolder>() {
    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.list_item_divider, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, item: TextDivider) {
        holder.textView.text = item.text
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.text)
    }

    data class TextDivider(val text: String) : Identifiable {
        override val id: String get() = "text_divider_$text"
    }
}
