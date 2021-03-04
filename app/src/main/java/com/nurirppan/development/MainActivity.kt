package com.nurirppan.development

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nurirppan.development.paywall.*
import com.nurirppan.development.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.btnOne.setOnClickListener {
            startActivity(Intent(this, SingleNonConsumableOneTimeProductAct::class.java))
        }

        binding.btnTwo.setOnClickListener {
            startActivity(Intent(this, MultipleNonConsumableOneTimeProductAct::class.java))
        }

        binding.btnThree.setOnClickListener {
            startActivity(Intent(this, SingleConsumableOneTimeProductAct::class.java))
        }

        binding.btnFour.setOnClickListener {
            startActivity(Intent(this, MultipleConsumableOneTimeProductAct::class.java))
        }

        binding.btnFive.setOnClickListener {
            startActivity(Intent(this, SingleInAppSubscriptionsAct::class.java))
        }

        binding.btnSix.setOnClickListener {
            startActivity(Intent(this, MultipleInAppSubscriptionsAct::class.java))
        }
    }
}
