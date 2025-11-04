package com.genaro.radiomp3.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.genaro.radiomp3.R
import com.genaro.radiomp3.data.HomePageButton

class HomePageButtonAdapter(
    val buttons: MutableList<HomePageButton>,
    val onCheckedChange: () -> Unit,
    val onDeleteClick: (HomePageButton) -> Unit
) : RecyclerView.Adapter<HomePageButtonAdapter.ViewHolder>(),
    HomePageButtonTouchHelper.Listener {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: CheckBox = itemView.findViewById(R.id.checkboxButton)
        val nameText: TextView = itemView.findViewById(R.id.textButtonName)
        val deleteBtn: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(button: HomePageButton) {
            nameText.text = "${button.emoji} ${button.name}"
            checkbox.isChecked = button.isEnabled
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                buttons[bindingAdapterPosition] = button.copy(isEnabled = isChecked)
                onCheckedChange()
            }

            // Delete button (solo per custom)
            if (button.type == HomePageButton.ButtonType.CUSTOM) {
                deleteBtn.visibility = View.VISIBLE
                deleteBtn.setOnClickListener { onDeleteClick(button) }
            } else {
                deleteBtn.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setup_button, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(buttons[position])
    }

    override fun getItemCount() = buttons.size

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        val item = buttons.removeAt(fromPosition)
        buttons.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onItemDismiss(position: Int) {
        // Non usiamo swipe-to-delete, lo faremo via pulsante
    }
}
