package com.genaro.radiomp3.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.genaro.radiomp3.R
import com.genaro.radiomp3.data.Prefs
import com.genaro.radiomp3.playback.PlayerRepo
import java.util.Stack

class FileBrowserActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private val navigationStack = Stack<DocumentFile>()
    private var currentDirectory: DocumentFile? = null
    private val audioExtensions = Regex(".*\\.(mp3|flac|wav|m4a|aac|ogg|opus)$", RegexOption.IGNORE_CASE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_browser)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val treeUri = Prefs.getTreeUri(this)
        if (treeUri == null) {
            Toast.makeText(this, "Please select folder in Settings first", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val rootDoc = DocumentFile.fromTreeUri(this, treeUri)
        if (rootDoc == null || !rootDoc.isDirectory) {
            Toast.makeText(this, "Invalid folder", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        adapter = FileAdapter { item ->
            onItemClicked(item)
        }
        recyclerView.adapter = adapter

        navigateToDirectory(rootDoc)
    }

    private fun navigateToDirectory(directory: DocumentFile) {
        currentDirectory = directory
        title = directory.name ?: "Audio Files"

        val items = mutableListOf<FileItem>()

        directory.listFiles()?.forEach { doc ->
            when {
                doc.isDirectory -> {
                    items.add(FileItem(doc, true))
                }
                doc.isFile && doc.name?.matches(audioExtensions) == true -> {
                    items.add(FileItem(doc, false))
                }
            }
        }

        // Sort: folders first, then files, both alphabetically
        val sorted = items.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))

        if (sorted.isEmpty()) {
            Toast.makeText(this, "No audio files or folders found", Toast.LENGTH_SHORT).show()
        }

        adapter.submitList(sorted)
    }

    private fun onItemClicked(item: FileItem) {
        if (item.isFolder) {
            navigationStack.push(currentDirectory)
            navigateToDirectory(item.document)
        } else {
            playFile(item.document)
        }
    }

    private fun playFile(document: DocumentFile) {
        val uri = document.uri.toString()
        val name = document.name ?: "Unknown"
        PlayerRepo.playUri(this, uri, name)
        finish()
    }

    override fun onBackPressed() {
        if (navigationStack.isNotEmpty()) {
            val previousDir = navigationStack.pop()
            navigateToDirectory(previousDir)
        } else {
            super.onBackPressed()
        }
    }

    // Data class for file/folder items
    private data class FileItem(
        val document: DocumentFile,
        val isFolder: Boolean
    ) {
        val name: String get() = document.name ?: "Unknown"
        val details: String get() = if (isFolder) "Folder" else getFileExtension()

        private fun getFileExtension(): String {
            val name = document.name ?: return ""
            val lastDot = name.lastIndexOf('.')
            return if (lastDot > 0) name.substring(lastDot + 1).uppercase() else ""
        }
    }

    // RecyclerView Adapter
    private class FileAdapter(
        private val onItemClick: (FileItem) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

        private var items = listOf<FileItem>()

        fun submitList(newItems: List<FileItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            return FileViewHolder(view as ViewGroup)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            holder.bind(items[position], onItemClick)
        }

        override fun getItemCount() = items.size

        class FileViewHolder(private val root: ViewGroup) : RecyclerView.ViewHolder(root) {
            private val imgIcon: ImageView = root.findViewById(R.id.imgIcon)
            private val txtName: TextView = root.findViewById(R.id.txtName)
            private val txtDetails: TextView = root.findViewById(R.id.txtDetails)

            fun bind(item: FileItem, onClick: (FileItem) -> Unit) {
                txtName.text = item.name
                txtDetails.text = item.details

                imgIcon.setImageResource(
                    if (item.isFolder) android.R.drawable.ic_menu_view
                    else android.R.drawable.ic_menu_more
                )

                root.setOnClickListener { onClick(item) }
            }
        }
    }
}
