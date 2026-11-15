package com.jedl.videoprogen

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * First editable vector-art pack. Every id is a replaceable layer and is
 * transformed by its existing PuppetRig bone matrix.
 */
internal object CharacterPartPainter {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private const val OUTLINE = 0xFF5A302B.toInt()
    private const val SKIN_DEFAULT = 0xFFB87955.toInt()
    private const val HAIR_DEFAULT = 0xFF3A211C.toInt()
    private const val SHIRT_DEFAULT = 0xFF28AFA4.toInt()
    private const val PANTS_DEFAULT = 0xFF9E3736.toInt()
    private const val SHOE_DEFAULT = 0xFFE7E2D8.toInt()

    fun draw(canvas: Canvas, id: String, colors: Map<String, Int>): Boolean {
        val skin = colors["skin"] ?: SKIN_DEFAULT
        val hair = colors["hair"] ?: HAIR_DEFAULT
        val shirt = colors["shirt"] ?: SHIRT_DEFAULT
        val pants = colors["pants"] ?: PANTS_DEFAULT
        val shoes = colors["shoes"] ?: SHOE_DEFAULT
        when (id) {
            "head_base" -> oval(canvas, .05f,.02f,.95f,.99f,skin,OUTLINE,.035f)
            "ear_left" -> oval(canvas,-.08f,.43f,.20f,.70f,skin,OUTLINE,.025f)
            "ear_right" -> oval(canvas,.80f,.43f,1.08f,.70f,skin,OUTLINE,.025f)
            "eyes_pair" -> {
                oval(canvas,.23f,.40f,.40f,.53f,0xFFFFFFFF.toInt(),OUTLINE,.018f)
                oval(canvas,.60f,.40f,.77f,.53f,0xFFFFFFFF.toInt(),OUTLINE,.018f)
                oval(canvas,.285f,.425f,.345f,.505f,0xFF332522.toInt(),null,0f)
                oval(canvas,.655f,.425f,.715f,.505f,0xFF332522.toInt(),null,0f)
            }
            "brows_pair" -> {
                line(canvas,.20f,.34f,.41f,.31f,hair,.035f)
                line(canvas,.59f,.31f,.80f,.34f,hair,.035f)
            }
            "nose_small" -> line(canvas,.50f,.49f,.47f,.63f,OUTLINE,.022f)
            "mouth_smile" -> {
                val path=Path().apply { moveTo(.37f,.76f); quadTo(.50f,.86f,.64f,.75f) }
                stroke(canvas,path,0xFF873E43.toInt(),.035f)
            }
            "hair_back" -> oval(canvas,.02f,.00f,.98f,.55f,hair,OUTLINE,.025f)
            "hair_front" -> {
                val path=Path().apply {
                    moveTo(.08f,.38f); cubicTo(.04f,.10f,.28f,.02f,.48f,.12f)
                    cubicTo(.70f,.02f,.96f,.12f,.93f,.40f)
                    cubicTo(.78f,.28f,.68f,.32f,.56f,.25f)
                    cubicTo(.39f,.35f,.25f,.27f,.08f,.38f); close()
                }
                fill(canvas,path,hair,OUTLINE,.025f)
            }
            "cap_front" -> {
                val path=Path().apply {
                    moveTo(.10f,.38f); quadTo(.08f,.02f,.50f,.02f)
                    quadTo(.92f,.02f,.90f,.38f); lineTo(.10f,.38f); close()
                }
                fill(canvas,path,shirt,OUTLINE,.025f)
                val brim=Path().apply { moveTo(.12f,.35f); quadTo(.50f,.30f,1.05f,.46f); quadTo(.55f,.55f,.12f,.42f); close() }
                fill(canvas,brim,0xFF178E88.toInt(),OUTLINE,.018f)
            }
            "neck" -> roundRect(canvas,.34f,.02f,.66f,.98f,skin,.06f)
            "torso_base" -> {
                val path=Path().apply {
                    moveTo(.22f,.04f); lineTo(.78f,.04f); lineTo(.98f,.88f)
                    quadTo(.50f,1.02f,.02f,.88f); close()
                }
                fill(canvas,path,shirt,OUTLINE,.025f)
            }
            "shirt_overlay" -> {
                line(canvas,.18f,.16f,.82f,.16f,0xFF77D4C9.toInt(),.025f)
                line(canvas,.20f,.90f,.80f,.90f,0xFF168A83.toInt(),.018f)
            }
            "pants_base" -> {
                val path=Path().apply {
                    moveTo(.08f,.02f); lineTo(.92f,.02f); lineTo(.88f,.98f)
                    lineTo(.57f,.98f); lineTo(.50f,.40f); lineTo(.43f,.98f)
                    lineTo(.12f,.98f); close()
                }
                fill(canvas,path,pants,OUTLINE,.025f)
            }
            "upper_arm", "upper_arm_l", "upper_arm_r" -> capsule(canvas,.20f,.02f,.80f,.98f,.28f,skin)
            "sleeve", "sleeve_l", "sleeve_r" -> capsule(canvas,.10f,.02f,.90f,.46f,.36f,shirt)
            "forearm", "forearm_l", "forearm_r" -> capsule(canvas,.18f,.02f,.82f,.92f,.25f,skin)
            "hand", "hand_l", "hand_r" -> {
                oval(canvas,.12f,.10f,.88f,.88f,skin,OUTLINE,.025f)
                line(canvas,.30f,.25f,.22f,.02f,skin,.10f)
                line(canvas,.50f,.22f,.48f,-.02f,skin,.10f)
                line(canvas,.70f,.27f,.78f,.05f,skin,.10f)
            }
            "thigh", "thigh_l", "thigh_r" -> capsule(canvas,.18f,.00f,.82f,1.00f,.34f,pants)
            "shin", "shin_l", "shin_r" -> capsule(canvas,.23f,.00f,.77f,.86f,.27f,pants)
            "shoe", "shoe_l", "shoe_r" -> {
                val path=Path().apply {
                    moveTo(.15f,.52f); quadTo(.45f,.45f,.58f,.60f)
                    lineTo(.98f,.77f); quadTo(1.02f,.98f,.78f,.98f)
                    lineTo(.10f,.98f); quadTo(.00f,.80f,.15f,.52f); close()
                }
                fill(canvas,path,shoes,OUTLINE,.025f)
            }
            "glasses" -> {
                strokeRect(canvas,.08f,.34f,.43f,.62f,0xFF292D35.toInt(),.045f)
                strokeRect(canvas,.57f,.34f,.92f,.62f,0xFF292D35.toInt(),.045f)
                line(canvas,.43f,.45f,.57f,.45f,0xFF292D35.toInt(),.04f)
            }
            "chain" -> {
                val path=Path().apply { moveTo(.18f,.04f); quadTo(.50f,.75f,.82f,.04f) }
                stroke(canvas,path,0xFFD6B45D.toInt(),.045f)
            }
            "medal" -> {
                oval(canvas,.34f,.12f,.66f,.44f,0xFFD6B45D.toInt(),0xFF806126.toInt(),.025f)
                oval(canvas,.43f,.21f,.57f,.35f,0xFFB73D42.toInt(),null,0f)
            }
            "watch" -> {
                roundRect(canvas,.20f,.02f,.80f,.98f,0xFFD6B45D.toInt(),.12f)
                roundRect(canvas,.30f,.22f,.70f,.78f,0xFF292D35.toInt(),.06f)
            }
            "stick", "stick_l", "stick_r" -> line(canvas,.50f,.02f,.50f,.98f,0xFFE7D8B8.toInt(),.10f)
            "drum_shell" -> {
                roundRect(canvas,.04f,.12f,.96f,.90f,0xFFB83C35.toInt(),.12f)
                line(canvas,.08f,.22f,.92f,.22f,0xFFD8C8A0.toInt(),.035f)
                line(canvas,.08f,.80f,.92f,.80f,0xFFD8C8A0.toInt(),.035f)
            }
            "drum_head" -> oval(canvas,.02f,.02f,.98f,.30f,0xFFE8E2D4.toInt(),0xFF6D6A64.toInt(),.035f)
            "drum_stand" -> {
                line(canvas,.50f,.05f,.50f,.72f,0xFF777D84.toInt(),.055f)
                line(canvas,.50f,.65f,.12f,.98f,0xFF777D84.toInt(),.045f)
                line(canvas,.50f,.65f,.88f,.98f,0xFF777D84.toInt(),.045f)
            }
            else -> return false
        }
        return true
    }

    private fun oval(c:Canvas,l:Float,t:Float,r:Float,b:Float,color:Int,stroke:Int?,sw:Float) {
        p.style=Paint.Style.FILL; p.color=color; c.drawOval(RectF(l,t,r,b),p)
        if(stroke!=null){p.style=Paint.Style.STROKE;p.strokeWidth=sw;p.color=stroke;c.drawOval(RectF(l,t,r,b),p)}
    }
    private fun roundRect(c:Canvas,l:Float,t:Float,r:Float,b:Float,color:Int,rad:Float) {
        p.style=Paint.Style.FILL;p.color=color;c.drawRoundRect(RectF(l,t,r,b),rad,rad,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=.025f;p.color=OUTLINE;c.drawRoundRect(RectF(l,t,r,b),rad,rad,p)
    }
    private fun line(c:Canvas,x1:Float,y1:Float,x2:Float,y2:Float,color:Int,w:Float) {
        p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeWidth=w;p.color=color;c.drawLine(x1,y1,x2,y2,p)
    }
    private fun capsule(c:Canvas,x1:Float,y1:Float,x2:Float,y2:Float,w:Float,color:Int) {
        line(c,x1,y1,x2,y2,OUTLINE,w+.035f); line(c,x1,y1,x2,y2,color,w)
    }
    private fun fill(c:Canvas,path:Path,color:Int,stroke:Int,sw:Float) {
        p.style=Paint.Style.FILL;p.color=color;c.drawPath(path,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=sw;p.color=stroke;c.drawPath(path,p)
    }
    private fun stroke(c:Canvas,path:Path,color:Int,sw:Float) {
        p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeWidth=sw;p.color=color;c.drawPath(path,p)
    }
    private fun strokeRect(c:Canvas,l:Float,t:Float,r:Float,b:Float,color:Int,sw:Float) {
        p.style=Paint.Style.STROKE;p.strokeWidth=sw;p.color=color;c.drawRect(l,t,r,b,p)
    }
}
