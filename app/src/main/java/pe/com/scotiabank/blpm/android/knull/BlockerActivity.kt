package pe.com.scotiabank.blpm.android.knull

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class BlockerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Mandamos al Home inmediatamente
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(homeIntent)

        // 2. Cerramos esta actividad transparente
        finish()
    }
}