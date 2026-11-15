package dev.pleos.roadcontext
/** Two distinct lane endings. Lateral car motion illustrates connectivity, never detected ego lane. */
internal class TwoStageLaneGeometry(val firstFrom:Double,val firstTo:Double,val secondFrom:Double,val secondTo:Double) {
 init { require(listOf(firstFrom,firstTo,secondFrom,secondTo).all { it.isFinite() });require(0<=firstFrom && firstFrom<firstTo && firstTo<secondFrom && secondFrom<secondTo) }
 private fun fraction(s:Double,a:Double,b:Double)=((s-a)/(b-a)).coerceIn(0.0,1.0)
 fun rightEdge(s:Double):Float=(2.5-fraction(s,firstFrom,firstTo)-fraction(s,secondFrom,secondTo)).toFloat()
 fun illustrativeLane(s:Double):Float {
  fun smooth(x:Double)=x*x*(3-2*x)
  return (2.0-smooth(fraction(s,firstFrom,firstTo))-smooth(fraction(s,secondFrom,secondTo))).toFloat()
 }
}
