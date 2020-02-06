package se.dset.android.arsavings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ArSavingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_fragment_container)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ArSavingsFragment.newInstance())
                .commitNow()
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ArSavingsActivity::class.java)
        }
    }
}