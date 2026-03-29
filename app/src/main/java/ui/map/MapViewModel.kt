package com.huertocero.huertocero.ui.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.huertocero.huertocero.Product
import com.huertocero.huertocero.ProductRepository

class MapViewModel : ViewModel() {

    private val repository = ProductRepository()

    private val _products = MutableLiveData<List<Product>>()
    val products: LiveData<List<Product>> = _products

    fun loadProducts() {

        repository.getProducts { list: List<Product> ->
            _products.value = list
        }
    }

    fun addProduct(product: Product) {
        repository.addProduct(product)
        loadProducts()
    }
}