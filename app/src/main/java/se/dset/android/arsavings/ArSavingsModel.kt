package se.dset.android.arsavings

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.TextView
import com.google.ar.core.HitResult
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.functions.Function3
import io.reactivex.rxjava3.functions.Function4
import io.reactivex.rxjava3.functions.Function5
import io.reactivex.rxjava3.functions.Function6
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.text.DecimalFormat
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class ArSavingsModel(
    private val context: Context
) {
    private val monthlySavingsSubject = BehaviorSubject.createDefault(0)
    private val startAmountSubject = BehaviorSubject.createDefault(0)
    private val durationSubject = BehaviorSubject.createDefault(0)

    private val anchorNodeSubject = BehaviorSubject.create<AnchorNode>()

    fun observeMonthlySavings(): Observable<Int> {
        return monthlySavingsSubject.hide()
    }

    fun observeStartAmount(): Observable<Int> {
        return startAmountSubject.hide()
    }

    fun observeDuration(): Observable<Int> {
        return durationSubject.hide()
    }

    private fun observeAnchorNode(): Observable<AnchorNode> {
        return anchorNodeSubject.hide()
    }

    private fun observeTotalSavings(): Observable<Int> {
        return Observable.combineLatest(
            observeMonthlySavings(),
            observeStartAmount(),
            observeDuration(),
            Function3 { v1: Int, v2: Int, v3: Int -> Triple(v1, v2, v3) }
        )
            .map { (monthlySavings, startAmount, duration) ->
                val start = startAmount * (1 + RATE).pow(duration)
                val monthly = 12 * monthlySavings * ((1 + RATE).pow(duration) - 1) / RATE
                start + monthly
            }
            .map(Double::roundToInt)
    }

    private fun observeBaseMaterial(): Observable<Material> {
        return Observable.fromFuture(MaterialFactory.makeOpaqueWithColor(context, Color(1f, 1f, 1f)))
            .subscribeOn(Schedulers.io())
    }

    private fun observeFaceMaterial(): Observable<Material> {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.hundra)
        return Observable.fromFuture(Texture.builder().setSource(bitmap).build())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .flatMap { texture ->
                Observable.fromFuture(MaterialFactory.makeOpaqueWithTexture(context, texture))
                    .subscribeOn(Schedulers.io())
            }
    }

    private fun observeCoinBaseMaterial(): Observable<Material> {
        return Observable.fromFuture(MaterialFactory.makeOpaqueWithColor(context, Color(0.75f, 0.75f, 0.75f)))
            .map {
                it.setFloat(MaterialFactory.MATERIAL_METALLIC, 1f)
                it.setFloat(MaterialFactory.MATERIAL_ROUGHNESS, 0.2f)
                it
            }
            .subscribeOn(Schedulers.io())
    }

    private fun observeTextRenderable(): Observable<ViewRenderable> {
        val view = TextView(context)
        view.setTextColor(android.graphics.Color.WHITE)
        return Observable.fromFuture(ViewRenderable.builder().setView(context, view).build())
            .subscribeOn(Schedulers.io())
    }

    fun observeModelNode(): Observable<Node> {
        return Observable.combineLatest(
            observeAnchorNode(),
            observeTotalSavings(),
            observeBaseMaterial(),
            observeFaceMaterial(),
            observeCoinBaseMaterial(),
            observeTextRenderable(),
            Function6 { p1: AnchorNode, p2: Int, p3: Material, p4: Material, p5: Material, p6: ViewRenderable -> ModelData(p1, p2, p3, p4, p5, p6) }
        )
            .observeOn(AndroidSchedulers.mainThread())
            .map { (anchorNode, totalSavings, baseMaterial, faceMaterial, coinBaseMaterial, textRenderable) ->
                val numKrona = totalSavings - (totalSavings / 100) * 100
                val savings = totalSavings - numKrona
                val totalHeight = savings / 10_000f * 0.0125f
                val numPiles = ceil(totalHeight / MAX_PILE_HEIGHT).roundToInt()

                anchorNode.clear()

                var x = 0
                var y = 0
                var dx = 0
                var dy = -1

                var maxCoordinateY = 0f

                repeat(numPiles) { i ->
                    val pileHeight = if (i == numPiles - 1) {
                        totalHeight - MAX_PILE_HEIGHT * (numPiles - 1)
                    } else {
                        MAX_PILE_HEIGHT
                    }

                    val coordinateX = x * BILL_WIDTH + x * PILE_PADDING
                    val coordinateY = y * BILL_HEIGHT + y * PILE_PADDING
                    maxCoordinateY = max(maxCoordinateY, coordinateY)

                    val base = ShapeFactory.makeCube(Vector3(BILL_WIDTH, pileHeight, BILL_HEIGHT), Vector3(0f, 0f, 0f), baseMaterial)
                    val face = ShapeFactory.makeCube(Vector3(BILL_WIDTH, 0f, BILL_HEIGHT), Vector3(0f, 0f, 0f), faceMaterial)

                    val baseNode = Node()
                    baseNode.renderable = base
                    baseNode.localPosition = Vector3(coordinateX, pileHeight / 2f, coordinateY)
                    anchorNode.addChild(baseNode)

                    val faceNode = Node()
                    faceNode.renderable = face
                    faceNode.localPosition = Vector3(coordinateX, pileHeight + 0.001f, coordinateY)
                    anchorNode.addChild(faceNode)

                    if (x == y || (x < 0 && x == -y) || (x > 0 && x == 1 - y)) {
                        dy = dx.also { dx = -dy }
                    }
                    x += dx
                    y += dy
                }

                val coinHeight = numKrona * 0.00179f
                val coinBase = ShapeFactory.makeCylinder(0.00975f, coinHeight, Vector3(0f, 0f, 0f), coinBaseMaterial)
                val coinBaseNode = Node()
                coinBaseNode.renderable = coinBase
                coinBaseNode.localPosition = Vector3(0f, coinHeight / 2f, maxCoordinateY + BILL_HEIGHT)
                anchorNode.addChild(coinBaseNode)

                (textRenderable.view as TextView).text = ArSavingsFragment.formatCurrency(totalSavings)
                val textNode = Node()
                textNode.renderable = textRenderable
                textNode.localPosition = Vector3(0f, (if (numPiles == 1) totalHeight else MAX_PILE_HEIGHT) + TEXT_PADDING, 0f)
                anchorNode.addChild(textNode)

                anchorNode
            }
    }

    fun onMonthlySavingsChanged(value: Int) {
        monthlySavingsSubject.onNext(value)
    }

    fun onStartAmountChanged(value: Int) {
        startAmountSubject.onNext(value)
    }

    fun onDurationChanged(value: Int) {
        durationSubject.onNext(value)
    }

    fun onPlaneTap(hitResult: HitResult) {
        if (anchorNodeSubject.hasValue()) {
            anchorNodeSubject.value.clear()
        }

        anchorNodeSubject.onNext(AnchorNode(hitResult.createAnchor()))
    }

    companion object {
        private const val RATE = 0.0846
        private const val MAX_PILE_HEIGHT = 0.5f
        private const val BILL_WIDTH = 0.133f
        private const val BILL_HEIGHT = 0.066f
        private const val PILE_PADDING = 0.04f
        private const val TEXT_PADDING = 0.1f
    }
}

private data class ModelData(
    val anchorNode: AnchorNode,
    val savings: Int,
    val baseMaterial: Material,
    val faceMaterial: Material,
    val coinBaseMaterial: Material,
    val textRenderable: ViewRenderable
)