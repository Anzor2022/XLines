package com.xlines.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Icons are drawn as vectors so they look the same on every phone and never depend on the system font.
private fun stroke(width:Float)=Stroke(width=width,cap=StrokeCap.Round,join=StrokeJoin.Round)

@Composable fun PowerIcon(color:Color,size:Dp=72.dp,modifier:Modifier=Modifier){
 Canvas(modifier.size(size)){
  val s=this.size.minDimension;val w=s*0.09f;val r=s*0.34f;val c=Offset(s/2,s/2+s*0.04f)
  drawArc(color,-50f,280f,false,Offset(c.x-r,c.y-r),Size(r*2,r*2),style=stroke(w))
  drawLine(color,Offset(s/2,s*0.10f),Offset(s/2,s*0.50f),strokeWidth=w,cap=StrokeCap.Round)
 }
}

@Composable fun ArrowIcon(down:Boolean,color:Color,size:Dp=16.dp,modifier:Modifier=Modifier){
 Canvas(modifier.size(size)){
  val s=this.size.minDimension;val w=s*0.12f;val d=if(down)1f else -1f
  val top=if(down)s*0.15f else s*0.85f;val bottom=if(down)s*0.85f else s*0.15f
  drawLine(color,Offset(s/2,top),Offset(s/2,bottom),strokeWidth=w,cap=StrokeCap.Round)
  val p=Path().apply{moveTo(s*0.22f,bottom-d*s*0.28f);lineTo(s/2,bottom);lineTo(s*0.78f,bottom-d*s*0.28f)}
  drawPath(p,color,style=stroke(w))
 }
}

@Composable fun GearIcon(color:Color,size:Dp=26.dp,modifier:Modifier=Modifier){
 Canvas(modifier.size(size)){
  val s=this.size.minDimension;val c=Offset(s/2,s/2);val w=s*0.09f
  drawCircle(color,s*0.17f,c,style=stroke(w))
  drawCircle(color,s*0.32f,c,style=stroke(w))
  for(i in 0 until 8){
   val a=Math.toRadians(i*45.0);val x=Math.cos(a).toFloat();val y=Math.sin(a).toFloat()
   drawLine(color,Offset(c.x+x*s*0.32f,c.y+y*s*0.32f),Offset(c.x+x*s*0.46f,c.y+y*s*0.46f),strokeWidth=w*1.4f,cap=StrokeCap.Round)
  }
 }
}

@Composable fun DiamondIcon(color:Color,size:Dp=24.dp,modifier:Modifier=Modifier){
 Canvas(modifier.size(size)){
  val s=this.size.minDimension;val w=s*0.09f
  val outer=Path().apply{moveTo(s/2,s*0.06f);lineTo(s*0.94f,s/2);lineTo(s/2,s*0.94f);lineTo(s*0.06f,s/2);close()}
  drawPath(outer,color,style=stroke(w))
  val inner=Path().apply{moveTo(s/2,s*0.34f);lineTo(s*0.66f,s/2);lineTo(s/2,s*0.66f);lineTo(s*0.34f,s/2);close()}
  drawPath(inner,color)
 }
}

@Composable fun CheckIcon(color:Color,size:Dp=18.dp,modifier:Modifier=Modifier){
 Canvas(modifier.size(size)){
  val s=this.size.minDimension
  val p=Path().apply{moveTo(s*0.15f,s*0.55f);lineTo(s*0.4f,s*0.78f);lineTo(s*0.85f,s*0.25f)}
  drawPath(p,color,style=stroke(s*0.13f))
 }
}

@Composable fun ChevronIcon(up:Boolean,color:Color,size:Dp=16.dp,modifier:Modifier=Modifier){
 Canvas(modifier.size(size)){
  val s=this.size.minDimension;val y1=if(up)s*0.62f else s*0.38f;val y2=if(up)s*0.38f else s*0.62f
  val p=Path().apply{moveTo(s*0.2f,y1);lineTo(s/2,y2);lineTo(s*0.8f,y1)}
  drawPath(p,color,style=stroke(s*0.13f))
 }
}

@Composable fun DotIcon(color:Color,size:Dp=8.dp,modifier:Modifier=Modifier){
 Canvas(modifier.size(size)){drawCircle(color,this.size.minDimension/2)}
}
