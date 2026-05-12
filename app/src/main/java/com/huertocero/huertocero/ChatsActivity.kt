package com.huertocero.huertocero

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ChatsActivity : HuertoActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val chats = mutableListOf<ChatThread>()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            finish()
            return
        }

        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val listChats = findViewById<ListView>(R.id.listChats)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        adapter = object : BaseAdapter() {
            override fun getCount(): Int = chats.size
            override fun getItem(position: Int): ChatThread = chats[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_chat_thread, parent, false)
                val chat = chats[position]

                view.findViewById<TextView>(R.id.tvProductName).text = chat.productName
                view.findViewById<TextView>(R.id.tvLastMessage).text =
                    chat.lastMessage.ifEmpty { getString(R.string.conversation_started) }

                return view
            }
        }
        listChats.adapter = adapter

        listChats.setOnItemClickListener { _, _, position, _ ->
            val chat = chats[position]
            startActivity(
                Intent(this, ChatActivity::class.java)
                    .putExtra("conversationId", chat.id)
                    .putExtra("productName", chat.productName)
            )
        }

        db.collection("conversations")
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                chats.clear()
                for (doc in snapshot) {
                    chats.add(
                        ChatThread(
                            id = doc.id,
                            productName = doc.getString("productName") ?: getString(R.string.product),
                            lastMessage = doc.getString("lastMessage") ?: "",
                            updatedAt = doc.getTimestamp("updatedAt")
                        )
                    )
                }
                chats.sortByDescending { it.updatedAt?.seconds ?: 0L }

                tvEmpty.visibility = if (chats.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }
    }

    private data class ChatThread(
        val id: String,
        val productName: String,
        val lastMessage: String,
        val updatedAt: Timestamp?
    )
}
