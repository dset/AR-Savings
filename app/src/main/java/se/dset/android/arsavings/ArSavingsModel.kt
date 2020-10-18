package se.dset.android.arsavings

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.TextView
import com.google.ar.core.HitResult
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Function3
import io.reactivex.rxjava3.functions.Function4
import io.reactivex.rxjava3.functions.Function6
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.*
import kotlin.math.*

class ArSavingsModel(
    private val context: Context
) {
    private val visualizationTypeSubject = BehaviorSubject.createDefault(VisualizationType.CASH)
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

    private fun observeTextNode(): Observable<Node> {
        return Observable.combineLatest(
            observeTextRenderable(),
            observeTotalSavings(),
            BiFunction { t1: ViewRenderable, t2: Int -> Pair(t1, t2) }
        )
            .observeOn(AndroidSchedulers.mainThread())
            .map { (renderable, savings) ->
                (renderable.view as TextView).text = ArSavingsFragment.formatCurrency(savings)
                Node().apply {
                    this.renderable = renderable
                }
            }
    }

    private fun observeTransparentMaterial(): Observable<Material> {
        return Observable.fromFuture(MaterialFactory.makeTransparentWithColor(context, Color(1f, 1f, 1f, 0f)))
            .subscribeOn(Schedulers.io())
    }

    private fun observeAssetModel(file: String): Observable<ModelRenderable> {
        return Observable.fromFuture(
            ModelRenderable.Builder()
                .setSource(context, Uri.parse(file))
                .build()
        )
            .subscribeOn(Schedulers.io())
    }

    private fun observeAssetMaterials(file: String): Observable<List<Material>> {
        return Observable.fromFuture(
            ModelRenderable.Builder()
                .setSource(context, Uri.parse(file))
                .build()
        )
            .map { renderable ->
                (0 until renderable.submeshCount).map { i ->
                    renderable.getMaterial(i)
                }
            }
            .subscribeOn(Schedulers.io())
    }

    private fun observeAssetModelNode(file: String, price: Float, seed: Long): Observable<Node> {
        return Observable.combineLatest(
            observeAssetModel(file),
            observeAssetMaterials(file),
            observeTransparentMaterial(),
            observeTotalSavings(),
            Function4 {
                    t1: ModelRenderable, t2: List<Material>, t3: Material, t4: Int -> AssetModelData(t1, t2, t3, t4)
            }
        )
            .observeOn(AndroidSchedulers.mainThread())
            .map { (renderable, materials, transparent, savings) ->
                val numMeshes = min(renderable.submeshCount, (savings / price * renderable.submeshCount).toInt())
                (0 until renderable.submeshCount).shuffled(Random(seed)).forEachIndexed { i, submesh ->
                    if (i < numMeshes) {
                        renderable.setMaterial(submesh, materials[submesh])
                    } else {
                        renderable.setMaterial(submesh, transparent)
                    }
                }

                Node().apply {
                    this.renderable = renderable
                }
            }
    }

    private fun observeCarModelNode(): Observable<Node> {
        return Observable.combineLatest(
            observeAnchorNode(),
            observeAssetModelNode("car.sfb", 300_000f, 1L),
            observeTextNode(),
            Function3 { t1: AnchorNode, t2: Node, t3: Node -> Triple(t1, t2, t3) }
        )
            .map { (anchorNode, node, textNode) ->
                node.localScale = Vector3(0.35f, 0.35f, 0.35f)
                node.localRotation = Quaternion.axisAngle(Vector3(1f, 0f, 0f), -90f)
                node.localPosition = Vector3(0f, 0f, 0.6f)
                textNode.localPosition = Vector3(0f, 0.5f, 0f)

                anchorNode.clear()
                anchorNode.addChild(node)
                anchorNode.addChild(textNode)
                anchorNode
            }
    }

    private fun observeHomeModelNode(): Observable<Node> {
        return Observable.combineLatest(
            observeAnchorNode(),
            observeAssetModelNode("house.sfb", 1_000_000f, 1L),
            observeTextNode(),
            Function3 { t1: AnchorNode, t2: Node, t3: Node -> Triple(t1, t2, t3) }
        )
            .map { (anchorNode, node, textNode) ->
                node.localScale = Vector3(0.5f, 0.5f, 0.5f)
                node.localPosition = Vector3(0f, 0f, -0.3f)
                textNode.localPosition = Vector3(0f, 0.75f, 0f)

                anchorNode.clear()
                anchorNode.addChild(node)
                anchorNode.addChild(textNode)
                anchorNode
            }
    }

    private fun observeCashModelNode(): Observable<Node> {
        return Observable.combineLatest(
            observeAnchorNode(),
            observeTotalSavings(),
            observeBaseMaterial(),
            observeFaceMaterial(),
            observeCoinBaseMaterial(),
            observeTextNode(),
            Function6 { p1: AnchorNode, p2: Int, p3: Material, p4: Material, p5: Material, p6: Node -> CashModelData(p1, p2, p3, p4, p5, p6) }
        )
            .observeOn(AndroidSchedulers.mainThread())
            .map { (anchorNode, totalSavings, baseMaterial, faceMaterial, coinBaseMaterial, textNode) ->
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

                textNode.localPosition = Vector3(0f, (if (numPiles == 1) totalHeight else MAX_PILE_HEIGHT) + TEXT_PADDING, 0f)
                anchorNode.addChild(textNode)

                anchorNode
            }
    }

    fun observeModelNode(): Observable<Node> {
        return visualizationTypeSubject.switchMap {
            when (it) {
                VisualizationType.CASH -> observeCashModelNode()
                VisualizationType.CAR -> observeCarModelNode()
                VisualizationType.HOME -> observeHomeModelNode()
            }
        }
    }

    fun onVisualizationTypeChanged(type: VisualizationType) {
        visualizationTypeSubject.onNext(type)
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

private data class CashModelData(
    val anchorNode: AnchorNode,
    val savings: Int,
    val baseMaterial: Material,
    val faceMaterial: Material,
    val coinBaseMaterial: Material,
    val textNode: Node
)

private data class AssetModelData(
    val renderable: ModelRenderable,
    val materials: List<Material>,
    val transparent: Material,
    val savings: Int
)