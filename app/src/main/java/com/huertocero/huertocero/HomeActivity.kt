package com.huertocero.huertocero

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.huertocero.huertocero.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // 👤 Mostrar usuario
        val user = auth.currentUser
        binding.tvWelcome.text = "Bienvenido: ${user?.email}"

        // ➕ Botón añadir producto
        binding.btnAddProduct.setOnClickListener {
            startActivity(Intent(this, AddProductActivity::class.java))
        }

        // 🗺️ Botón ver mapa
        binding.btnMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        // 🔐 Botón cerrar sesión
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}