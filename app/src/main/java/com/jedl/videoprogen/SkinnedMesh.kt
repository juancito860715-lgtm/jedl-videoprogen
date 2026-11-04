package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log

// ============================================================
// SKINNED MESH — DEFORMACIÓN DE MALLA 2D (v0.4.2)
// ============================================================
// La foto del usuario se deforma SUAVEMENTE siguiendo el esqueleto:
// una grilla de triángulos (drawVertices + BitmapShader) cuyos
// vértices se "pegan" a los huesos. El hueso del torso controla el
// centro de la malla; el hueso de la cabeza controla la franja
// superior (se inclina al saludar). La malla nunca se rompe:
// los triángulos comparten vértices, no hay huecos negros.
//
// AUTOPRUEBA ANTI-LIENZO-NEGRO: antes de usar la foto, se dibuja
// un cuadrado de color de prueba con drawVertices a un bitmap
// fuera de pantalla y se verifican los píxeles. Si drawVertices
// no pintara nada en el dispositivo, caemos automáticamente a
// drawBitmapMesh (misma malla, misma deformación, garantizado por
// el API oficial desde Android 1.0).
// ============================================================

class SkinnedMesh(val photo: Bitmap) {

    // ---------- MALLA ----------
    private val cols = 6                       // celdas horizontales
    private val rows = 8                       // celdas verticales
    private val nCols = cols + 1
    private val nRows = rows + 1
    private val nVerts = nCols * nRows          // 63 vértices

    private val verts = FloatArray(nVerts * 2)  // posiciones en pantalla (unidades del muñeco)
    private val texs = FloatArray(nVerts * 2)   // coordenadas UV en PÍXELES de la foto
    private val indices = ShortArray(cols * rows * 6) // 96 triángulos
    private val boneOf = arrayOfNulls<String>(nVerts) // hueso dueño de cada vértice
    private val localX = FloatArray(nVerts)     // posición local respecto al hueso (bind pose)
    private val localY = FloatArray(nVerts)

    // Pose de referencia (bind): esqueleto sin animación, raíz en (0,0)
    private val bindSolved = PuppetRig.HUMANOID.solve(emptyMap(), 0f, 0f)

    // Pinturas: con shader para drawVertices; LIMPIA para drawBitmapMesh
    private val paintMesh = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val paintPlain = Paint(Paint.FILTER_BITMAP_FLAG)

    private var frameCount = 0

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
        for (bone in listOf("torso", "head")) {
            val m = Matrix()
            m.set(bindSolved[bone]!!.matrix)
            val inv = Matrix()
            if (!m.invert(inv)) Log.d("JEDL_MESH", "Aviso: bind de $bone no es invertible")
            inverses[bone] = inv
        }

        // 2) UVs + asignación de hueso + posición local (bind)
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
                // La franja superior de la foto pertenece a la CABEZA (se inclina
                // al saludar); el resto pertenece al TORSO (estable).
                val bone = if (wy < -0.28f) "head" else "torso"
                boneOf[v] = bone
                pt[0] = wx; pt[1] = wy
                inverses[bone]!!.mapPoints(pt)
                localX[v] = pt[0]
                localY[v] = pt[1]
            }
        }

        // 3) Triangulación: 2 triángulos por celda (comparten vértices = sin huecos)
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

        // 4) Posición inicial de los vértices + AUTOPRUEBA del dispositivo
        updateVertices(bindSolved)
        ensureTest()
    }

    // Recalcula la posición de cada vértice aplicando la matriz de su hueso.
    // Sin asignaciones por vértice: getValues una vez por hueso y aritmética escalar.
    fun updateVertices(solved: Map<String, BonePose>) {
        val mTorso = solved["torso"]?.matrix ?: return
        val mHead = solved["head"]?.matrix ?: return
        val m = FloatArray(9)
        mTorso.getValues(m)
        val t00 = m[0]; val t01 = m[1]; val t02 = m[2]
        val t10 = m[3]; val t11 = m[4]; val t12 = m[5]
        mHead.getValues(m)
        val h00 = m[0]; val h01 = m[1]; val h02 = m[2]
        val h10 = m[3]; val h11 = m[4]; val h12 = m[5]
        for (n in 0 until nVerts) {
            val lx = localX[n]
            val ly = localY[n]
            if (boneOf[n] == "head") {
                verts[n * 2]     = h00 * lx + h01 * ly + h02
                verts[n * 2 + 1] = h10 * lx + h11 * ly + h12
            } else {
                verts[n * 2]     = t00 * lx + t01 * ly + t02
                verts[n * 2 + 1] = t10 * lx + t11 * ly + t12
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
