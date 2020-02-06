package se.dset.android.arsavings

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.BaseArFragment
import kotlinx.android.synthetic.main.fragment_ar_savings.*
import kotlinx.android.synthetic.main.fragment_ar_savings.view.*
import java.text.DecimalFormat
import java.util.*

class ArSavingsFragment : ArFragment(), BaseArFragment.OnTapArPlaneListener {
    private lateinit var presenter: ArSavingsPresenter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        presenter = ArSavingsPresenter(context, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setOnTapArPlaneListener(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val layout = layoutInflater.inflate(R.layout.fragment_ar_savings, view as ViewGroup, true)

        layout.cashButton.setOnClickListener { presenter.onVisualizationTypeChanged(VisualizationType.CASH) }
        layout.carButton.setOnClickListener { presenter.onVisualizationTypeChanged(VisualizationType.CAR) }
        layout.homeButton.setOnClickListener { presenter.onVisualizationTypeChanged(VisualizationType.HOME) }

        val onMonthlySavingsChange = { progress: Int -> presenter.onMonthlySavingsChanged(progress * 100) }
        layout.monthlySavingsSlider.setOnSeekBarChangeListener(OnSeekBarChangeListenerAdapter(onMonthlySavingsChange))
        onMonthlySavingsChange(layout.monthlySavingsSlider.progress)

        val onStartAmountChange = { progress: Int -> presenter.onStartAmountChanged(progress * 10_000) }
        layout.startAmountSlider.setOnSeekBarChangeListener(OnSeekBarChangeListenerAdapter(onStartAmountChange))
        onStartAmountChange(layout.startAmountSlider.progress)

        val onDurationChange = { progress: Int -> presenter.onDurationChanged(progress) }
        layout.durationSlider.setOnSeekBarChangeListener(OnSeekBarChangeListenerAdapter(onDurationChange))
        onDurationChange(layout.durationSlider.progress)
    }

    override fun onStart() {
        super.onStart()
        presenter.onStart()
    }

    override fun onStop() {
        super.onStop()
        presenter.onStop()
    }

    override fun onTapPlane(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        presenter.onPlaneTap(hitResult)
    }

    fun showNode(node: Node) {
        if (!arSceneView.scene.children.contains(node)) {
            arSceneView.scene.addChild(node)
        }
    }

    fun onMonthlySavingsChanged(value: Int) {
        monthlySavingsValue.text = formatCurrency(value)
    }

    fun onStartAmountChanged(value: Int) {
        startAmountValue.text = formatCurrency(value)
    }

    fun onDurationChanged(value: Int) {
        durationValue.text = getString(R.string.value_years, value)
    }

    companion object {
        fun newInstance(): ArSavingsFragment = ArSavingsFragment()

        fun formatCurrency(value: Int): String {
            return DecimalFormat.getCurrencyInstance(Locale("sv", "SE"))
                .apply {
                    maximumFractionDigits = 0
                }
                .format(value)
        }
    }

    private class OnSeekBarChangeListenerAdapter(
        private val onProgress: (Int) -> Unit
    ) : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            onProgress(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
        }
    }
}
