package com.huertocero.huertocero

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ChatActivity : HuertoActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            finish()
            return
        }

        val conversationId = intent.getStringExtra("conversationId") ?: return finish()
        val productName = intent.getStringExtra("productName") ?: "Chat"

        val tvTitle = findViewById<TextView>(R.id.tvChatTitle)
        val listMessages = findViewById<ListView>(R.id.listMessages)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)

        tvTitle.text = productName
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnReportChat).setOnClickListener {
            reportConversation(conversationId, productName, userId)
        }

        adapter = object : BaseAdapter() {
            override fun getCount(): Int = messages.size
            override fun getItem(position: Int): ChatMessage = messages[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_chat_message, parent, false)
                val row = view.findViewById<LinearLayout>(R.id.messageRow)
                val message = view.findViewById<TextView>(R.id.tvMessage)
                val item = messages[position]
                val isMine = item.senderId == userId

                row.gravity = if (isMine) Gravity.END else Gravity.START
                message.text = item.text
                message.setTextColor(getColor(if (isMine) android.R.color.white else R.color.ink))
                message.setBackgroundResource(if (isMine) R.drawable.chat_bubble_mine else R.drawable.chat_bubble_other)

                return view
            }
        }
        listMessages.adapter = adapter

        db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                messages.clear()
                for (doc in snapshot) {
                    messages.add(
                        ChatMessage(
                            text = doc.getString("text") ?: "",
                            senderId = doc.getString("senderId") ?: "",
                            createdAt = doc.getTimestamp("createdAt")
                        )
                    )
                }
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) {
                    listMessages.setSelection(messages.size - 1)
                }
            }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            btnSend.isEnabled = false
            db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .add(
                    mapOf(
                        "text" to text,
                        "senderId" to userId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                .addOnSuccessListener {
                    etMessage.setText("")
                    db.collection("conversations")
                        .document(conversationId)
                        .update(
                            mapOf(
                                "lastMessage" to text,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    btnSend.isEnabled = true
                }
                .addOnFailureListener {
                    btnSend.isEnabled = true
                    Toast.makeText(this, getString(R.string.chat_send_error), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun reportConversation(conversationId: String, productName: String, reporterId: String) {
        val reasons = arrayOf(
            "Spam",
            getString(R.string.harassment),
            getString(R.string.inappropriate_content),
            getString(R.string.fraud),
            getString(R.string.other_reason)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.report_conversation))
            .setItems(reasons) { _, which ->
                db.collection("reports").add(
                    mapOf(
                        "type" to "conversation",
                        "conversationId" to conversationId,
                        "productName" to productName,
                        "reporterId" to reporterId,
                        "reason" to reasons[which],
                        "status" to "new",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                Toast.makeText(this, getString(R.string.report_sent), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private data class ChatMessage(
        val text: String,
        val senderId: String,
        val createdAt: Timestamp?
    )
}
