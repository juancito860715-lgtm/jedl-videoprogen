package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import kotlin.math.sqrt

// ============================================================
// SKINNED MESH — DEFORMACIÓN DE MALLA 2D CON PESOS (v0.5.0)
// ============================================================
// La foto del usuario se deforma SUAVEMENTE siguiendo el esqueleto:
// una grilla de triángulos (drawVertices + BitmapShader) cuyos
// vértices se ligan a los huesos.
//
// NOVEDAD v0.5.0 — SKINNING CON PESOS SUAVES (LBS, "Linear Blend
// Skinning", la matemática estándar de DragonBones/Spine/AE):
// antes cada vértice pertenecía a UN solo hueso (asignación dura)
// y las fronteras quedaban rígidas. Ahora cada vértice recibe
// PESOS de hasta 2 huesos según su distancia a cada segmento
// óseo (gaussiana), y su posición final es la media ponderada
// de las matrices: los codos y las costuras se doblan suaves.
//
// Beneficio visible: con la foto RECORtada (botón Recorte), los
// brazos/piernas reales de la persona siguen a los huesos del
// muñeco; sin recorte, la foto completa se deforma como un
// "puppet" (VectorMotion/After Effects). La malla nunca se
// rompe: los triángulos comparten vértices, no hay huecos.
//
// AUTOPRUEBA ANTI-LIENZO-NEGRO (se mantiene): si drawVertices
// no pintara nada en el dispositivo, caemos automáticamente a
// drawBitmapMesh (misma malla, garantizado desde Android 1.0).
// ============================================================

class SkinnedMesh(val photo: Bitmap) {

    // ---------- MALLA ----------
    private val cols = 10                      // celdas horizontales
    private val rows = 12                      // celdas verticales
    private val nCols = cols + 1
    private val nRows = rows + 1
    private val nVerts = nCols * nRows          // 143 vértices

    private val verts = FloatArray(nVerts * 2)  // posiciones en pantalla (unidades del muñeco)
    private val texs = FloatArray(nVerts * 2)   // coordenadas UV en PÍXELES de la foto
    private val indices = ShortArray(cols * rows * 6)

    // LBS: hasta 2 huesos por vértice con peso (w1 + w2 = 1)
    private val bone1 = arrayOfNulls<String>(nVerts)
    private val bone2 = arrayOfNulls<String>(nVerts)
    private val w1 = FloatArray(nVerts)
    private val local1X = FloatArray(nVerts)   // posición local respecto al hueso 1 (bind)
    private val local1Y = FloatArray(nVerts)
    private val local2X = FloatArray(nVerts)   // posición local respecto al hueso 2 (bind)
    private val local2Y = FloatArray(nVerts)

    // Pose de referencia (bind): esqueleto sin animación, raíz en (0,0)
    private val bindSolved = PuppetRig.HUMANOID.solve(emptyMap(), 0f, 0f)

    // Pinturas: con shader para drawVertices; LIMPIA para drawBitmapMesh
    private val paintMesh = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val paintPlain = Paint(Paint.FILTER_BITMAP_FLAG)

    private var frameCount = 0

    // Segmentos óseos del bind (joint -> tip en unidades del muñeco) con
    // su sigma de influencia (ancho del área suave). Coordenadas extraídas
    // del propio bindSolved: si el rig cambia, los pesos se recalculan solos.
    private class Seg(val bone: String, val ax: Float, val ay: Float, val bx: Float, val by: Float, val sigma: Float)

    init {
        val bw = photo.width.toFloat()
        val bh = photo.height.toFloat()
        val anchorX = bw / 2f                 // ancla: centro de la foto
        val anchorY = bh / 2f
        val scale = 0.85f / bh                // unidades de muñeco por píxel
        val chestX = 0f                       // el centro de la foto se clava en el pecho
        val chestY = -0.14f

        // 1) Inversas del bind: de coordenadas mundo -> local de cada hueso
        val inverses = HashMap<String, Matrix>()
        for (bone in PuppetRig.HUMANOID.bones) {
            val bp = bindSolved[bone.name] ?: continue
            val m = Matrix()
            m.set(bp.matrix)
            val inv = Matrix()
            if (m.invert(inv)) inverses[bone.name] = inv
        }

        // 2) Segmentos óseos con su zona de influencia
        val segs = ArrayList<Seg>()
        for (bone in PuppetRig.HUMANOID.bones) {
            val bp = bindSolved[bone.name] ?: continue
            if (bone.name == "head") {
                // la cabeza influye solo en la franja superior
                segs.add(Seg(bone.name, bp.joint.x, bp.joint.y, bp.tip.x, bp.tip.y, 0.105f))
            } else {
                // el resto: sigma proporcional al grosor del hueso
                segs.add(Seg(bone.name, bp.joint.x, bp.joint.y, bp.tip.x, bp.tip.y, bone.width * 1.1f))
            }
        }

        // 3) UVs + pesos LBS + posiciones locales (bind)
        val pt = FloatArray(2)
        for (j in 0 until nRows) {
            for (i in 0 until nCols) {
                val v = j * nCols + i
                val px = (i.toFloat() / cols) * bw
                val py = (j.toFloat() / rows) * bh
                texs[v * 2] = px
                texs[v * 2 + 1] = py
                // Posición mundo inicial del vértice (sobre el pecho del muñeco)
                val wx = chestX + (px - anchorX) * scale
                val wy = chestY + (py - anchorY) * scale

                // Pesos gaussianos por distancia al segmento de cada hueso
                var bestSeg: Seg? = null
                var bestD = Float.MAX_VALUE
                val scored = ArrayList<Pair<Seg, Float>>(segs.size)
                for (s in segs) {
                    val d = distToSeg(wx, wy, s)
                    if (d < bestD) { bestD = d; bestSeg = s }
                    val w = kotlin.math.exp(-(d * d) / (2f * s.sigma * s.sigma))
                    if (w > 0.02f) scored.add(Pair(s, w))
                }
                scored.sortByDescending { it.second }
                var s1: Seg? = null
                var s2: Seg? = null
                var weight1 = 1f
                if (scored.isEmpty()) {
                    // nada cerca: el vértice pertenece al hueso más cercano
                    s1 = bestSeg
                    weight1 = 1f
                } else if (scored.size == 1) {
                    s1 = scored[0].first
                    weight1 = 1f
                } else {
                    s1 = scored[0].first
                    s2 = scored[1].first
                    val sum = scored[0].second + scored[1].second
                    weight1 = scored[0].second / sum
                }
                val inv1 = s1?.let { inverses[it.bone] }
                val inv2 = s2?.let { inverses[it.bone] }
                if (inv1 != null && s1 != null) {
                    bone1[v] = s1.bone
                    w1[v] = weight1
                    pt[0] = wx; pt[1] = wy
                    inv1.mapPoints(pt)
                    local1X[v] = pt[0]
                    local1Y[v] = pt[1]
                }
                if (inv2 != null && s2 != null) {
                    bone2[v] = s2.bone
                    pt[0] = wx; pt[1] = wy
                    inv2.mapPoints(pt)
                    local2X[v] = pt[0]
                    local2Y[v] = pt[1]
                }
            }
        }

        // 4) Triangulación: 2 triángulos por celda (comparten vértices = sin huecos)
        var k = 0
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val v00 = (j * nCols + i).toShort()
                val v01 = (j * nCols + i + 1).toShort()
                val v11 = ((j + 1) * nCols + i + 1).toShort()
                val v10 = ((j + 1) * nCols + i).toShort()
                indices[k++] = v00; indices[k++] = v01; indices[k++] = v11
                indices[k++] = v00; indices[k++] = v11; indices[k++] = v10
            }
        }

        // 5) Posición inicial de los vértices + AUTOPRUEBA del dispositivo
        updateVertices(bindSolved)
        ensureTest()
    }

    // Distancia de un punto al segmento a->b (para los pesos del skinning)
    private fun distToSeg(px: Float, py: Float, s: Seg): Float {
        val vx = s.bx - s.ax
        val vy = s.by - s.ay
        val len2 = vx * vx + vy * vy
        if (len2 < 1e-9f) {
            val dx = px - s.ax
            val dy = py - s.ay
            return sqrt(dx * dx + dy * dy)
        }
        var u = ((px - s.ax) * vx + (py - s.ay) * vy) / len2
        u = u.coerceIn(0f, 1f)
        val cx = s.ax + u * vx
        val cy = s.ay + u * vy
        val dx = px - cx
        val dy = py - cy
        return sqrt(dx * dx + dy * dy)
    }

    // Recalcula cada vértice: LBS = media ponderada de las matrices de sus
    // huesos. Se extraen los 9 valores de cada matriz UNA vez por hueso.
    fun updateVertices(solved: Map<String, BonePose>) {
        // valores de todas las matrices de huesos que usa la malla
        val mat = HashMap<String, FloatArray>()
        for (n in 0 until nVerts) {
            val b1 = bone1[n] ?: continue
            if (!mat.containsKey(b1)) {
                val m = solved[b1]?.matrix
                if (m != null) { val arr = FloatArray(9); m.getValues(arr); mat[b1] = arr }
            }
            val b2 = bone2[n]
            if (b2 != null && !mat.containsKey(b2)) {
                val m = solved[b2]?.matrix
                if (m != null) { val arr = FloatArray(9); m.getValues(arr); mat[b2] = arr }
            }
        }
        for (n in 0 until nVerts) {
            val b1 = bone1[n] ?: continue
            val m1 = mat[b1] ?: continue
            val lx = local1X[n]
            val ly = local1Y[n]
            val x1 = m1[0] * lx + m1[1] * ly + m1[2]
            val y1 = m1[3] * lx + m1[4] * ly + m1[5]
            val b2 = bone2[n]
            if (b2 == null) {
                verts[n * 2] = x1
                verts[n * 2 + 1] = y1
            } else {
                val m2 = mat[b2]
                if (m2 == null) {
                    verts[n * 2] = x1
                    verts[n * 2 + 1] = y1
                } else {
                    val w = w1[n]
                    val x2 = m2[0] * local2X[n] + m2[1] * local2Y[n] + m2[2]
                    val y2 = m2[3] * local2X[n] + m2[4] * local2Y[n] + m2[5]
                    verts[n * 2] = x1 * w + x2 * (1f - w)
                    verts[n * 2 + 1] = y1 * w + y2 * (1f - w)
                }
            }
        }
    }

    // Dibuja la malla deformada. drawVertices + BitmapShader como motor
    // principal; drawBitmapMesh como respaldo si la autoprueba falló.
    fun draw(canvas: Canvas) {
        frameCount++
        if (frameCount % 30 == 1) {
            Log.d("JEDL_MESH", "Verts: " + verts.joinToString())
        }
        if (useVertices()) {
            canvas.drawVertices(
                Canvas.VertexMode.TRIANGLES, nVerts,
                verts, 0, texs, 0, null, 0,
                indices, 0, indices.size, paintMesh
            )
        } else {
            canvas.drawBitmapMesh(photo, cols, rows, verts, 0, null, 0, paintPlain)
        }
    }

    private fun useVertices(): Boolean = ensureTest()

    companion object {
        @Volatile private var tested: Boolean? = null

        // Autoprueba en el DISPOSITIVO: cuadrado de color 64x64 vía
        // drawVertices; si los píxeles salen negros -> fallback automático.
        fun ensureTest(): Boolean {
            val cached = tested
            if (cached != null) return cached
            val ok = testDrawVertices()
            tested = ok
            Log.d("JEDL_MESH", "Autoprueba drawVertices: " + if (ok) "OK (malla con BitmapShader)" else "FALLO -> usando drawBitmapMesh")
            return ok
        }

        private fun testDrawVertices(): Boolean {
            return try {
                val size = 64
                val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                src.eraseColor(Color.rgb(255, 60, 60))
                val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                out.eraseColor(Color.BLACK)
                val canvas = Canvas(out)
                val q = floatArrayOf(
                    0f, 0f, size.toFloat(), 0f,
                    size.toFloat(), size.toFloat(), 0f, size.toFloat()
                )
                val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val p = Paint().apply { this.shader = shader }
                val idx = shortArrayOf(0, 1, 2, 0, 2, 3)
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, 4, q, 0, q, 0, null, 0, idx, 0, 6, p)
                val px1 = out.getPixel(size / 2, size / 2)
                val px2 = out.getPixel(size / 4, size / 4)
                src.recycle()
                out.recycle()
                px1 != Color.BLACK || px2 != Color.BLACK
            } catch (e: Exception) {
                Log.d("JEDL_MESH", "Excepción en autoprueba: " + e.message)
                false
            }
        }
    }
}
