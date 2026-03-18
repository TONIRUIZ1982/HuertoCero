package com.huertocero.huertocero

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.huertocero.huertocero.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.btnLogin.setOnClickListener {
            loginUser()
        }

        binding.btnRegister.setOnClickListener {
            registerUser()
        }
    }

    // 🔐 LOGIN
    private fun loginUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {

                    Toast.makeText(this, "Login correcto", Toast.LENGTH_SHORT).show()

                    // 🔥 IR A HOME (NO MainActivity)
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()

                } else {
                    Toast.makeText(
                        this,
                        "Error login: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // 📝 REGISTRO
    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {

                    val user = auth.currentUser

                    if (user != null) {
                        saveUserToFirestore(user.uid, user.email!!)
                    }

                    Toast.makeText(this, "Usuario creado correctamente", Toast.LENGTH_SHORT).show()

                } else {
                    Toast.makeText(
                        this,
                        "Error registro: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // ☁️ FIRESTORE
    private fun saveUserToFirestore(uid: String, email: String) {

        val userMap = hashMapOf(
            "uid" to uid,
            "email" to email
        )

        db.collection("users")
            .document(uid)
            .set(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Usuario guardado en Firestore", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error Firestore", Toast.LENGTH_SHORT).show()
            }
    }
}