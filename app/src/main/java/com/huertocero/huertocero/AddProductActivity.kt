package com.huertocero.huertocero

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.huertocero.huertocero.databinding.ActivityAddProductBinding

class AddProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        binding.btnSave.setOnClickListener {
            saveProduct()
        }
    }

    private fun saveProduct() {

        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()

        val user = auth.currentUser

        if (title.isEmpty() || description.isEmpty() || price.isEmpty()) {
            Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        val product = hashMapOf(
            "title" to title,
            "description" to description,
            "price" to price,
            "userId" to user?.uid
        )

        db.collection("products")
            .add(product)
            .addOnSuccessListener {
                Toast.makeText(this, "Producto guardado", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
            }
    }
}