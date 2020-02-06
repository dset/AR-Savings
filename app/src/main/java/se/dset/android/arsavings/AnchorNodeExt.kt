package se.dset.android.arsavings

import com.google.ar.sceneform.AnchorNode

fun AnchorNode.clear() {
    val children = ArrayList(children)
    for (child in children) {
        removeChild(child)
    }

    parent?.removeChild(this)
}
