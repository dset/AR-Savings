package se.dset.android.arsavings

import android.content.Context
import com.google.ar.core.HitResult
import io.reactivex.rxjava3.disposables.CompositeDisposable

class ArSavingsPresenter(
    context: Context,
    private val view: ArSavingsFragment
) {
    private val model = ArSavingsModel(context)
    private val disposables = CompositeDisposable()

    fun onStart() {
        disposables.add(
            model.observeMonthlySavings()
                .subscribe(view::onMonthlySavingsChanged)
        )

        disposables.add(
            model.observeStartAmount()
                .subscribe(view::onStartAmountChanged)
        )

        disposables.add(
            model.observeDuration()
                .subscribe(view::onDurationChanged)
        )

        disposables.add(
            model.observeModelNode()
                .subscribe(view::showNode)
        )
    }

    fun onStop() {
        disposables.clear()
    }

    fun onMonthlySavingsChanged(value: Int) {
        model.onMonthlySavingsChanged(value)
    }

    fun onStartAmountChanged(value: Int) {
        model.onStartAmountChanged(value)
    }

    fun onDurationChanged(value: Int) {
        model.onDurationChanged(value)
    }

    fun onPlaneTap(hitResult: HitResult) {
        model.onPlaneTap(hitResult)
    }
}