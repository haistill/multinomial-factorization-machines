package io.github.qf6101.mfm.factorization

import breeze.linalg.{DenseMatrix, DenseVector}
import io.github.qf6101.mfm.base.Coefficients
import io.github.qf6101.mfm.util.GaussianRandom

import scala.math._

/**
  * Created by qfeng on 15-3-12.
  */

/**
  * Factorization Machine模型系数
 *
  * @param numFeatures 特征个数
  * @param numFactors 因子个数
  * @param k0 是否需要处理截距
  * @param k1 是否需要处理一阶参数
  * @param k2 是否需要处理二阶参数
  */
class FmCoefficients(val initMean: Double,
                     val initStdev: Double,
                     var numFeatures: Int,
                     var numInteractFeatures: Int,
                     var numFactors: Int,
                     val k0: Boolean,
                     val k1: Boolean,
                     val k2: Boolean) extends Coefficients {
  var w0 = GaussianRandom.rand(initMean, initStdev)
  var w = GaussianRandom.randDenseVector(initMean, initStdev, numFeatures)
  var v = GaussianRandom.randDenseMatrix(initMean, initStdev, numInteractFeatures, numFactors)

  /**
    * 用breeze稀疏向量和CSC稀疏矩阵初始化模型系数
 *
    * @param w0 0阶系数
    * @param w 1阶系数
    * @param v 2阶系数
    * @param k0 是否需要处理截距
    * @param k1 是否需要处理一阶参数
    * @param k2 是否需要处理二阶参数
    */
  def this(w0: Double, w: DenseVector[Double], v: DenseMatrix[Double], k0: Boolean, k1: Boolean, k2: Boolean) {
    this(0.0, 0.0, w.length, v.rows, v.cols, k0, k1, k2)
    this.w0 = w0
    this.w = w.copy
    this.v = v.copy
  }

  /**
    * 只复制this的结构（比如参数个数），不复制内容
 *
    * @return 复制的拷贝
    */
  override def copyEmpty(): Coefficients = new FmCoefficients(this.initMean, this.initMean,
    this.numFeatures, this.numInteractFeatures, this.numFactors, this.k0, this.k1, this.k2)

  /**
    * 对应系数加法，加至this上
 *
    * @param other 加数
    * @return this
    */
  override def +=(other: Coefficients): Coefficients = {
    val otherCoeffs = other.asInstanceOf[FmCoefficients]
    if (k0) this.w0 += otherCoeffs.w0
    if (k1) this.w += otherCoeffs.w
    if (k2) this.v += otherCoeffs.v
    this
  }

  /**
    * 对应系数减法，减至this上
 *
    * @param other 减数
    * @return this
    */
  override def -=(other: Coefficients): Coefficients = {
    val otherCoeffs = other.asInstanceOf[FmCoefficients]
    if (k0) this.w0 -= otherCoeffs.w0
    if (k1) this.w -= otherCoeffs.w
    if (k2) this.v -= otherCoeffs.v
    this
  }

  /**
    * 对应系数加上同一实数，加至复制this的类上
 *
    * @param addend 加数
    * @return 加法结果（拷贝）
    */
  override def +(addend: Double): Coefficients = {
    val result = this.copy.asInstanceOf[FmCoefficients]
    if (k0) result.w0 += addend
    if (k1) result.w += addend
    if (k2) result.v += addend
    result
  }

  /**
    * 对应系数乘上同一实数，加至复制this的类上
 *
    * @param multiplier  乘数
    * @return 乘法结果
    */
  override def *(multiplier: Double): Coefficients = {
    val result = this.copy.asInstanceOf[FmCoefficients]
    if (k0) result.w0 *= multiplier
    if (k1) result.w *= multiplier
    if (k2) result.v *= multiplier
    result
  }

  /**
    * 同时复制this的结构和内容
 *
    * @return 复制的拷贝
    */
  override def copy: Coefficients = {
    //从效率出发，参数设为0
    val coeffs = new FmCoefficients(this.initMean, this.initStdev, 0, 0, 0, this.k0, this.k1, this.k2)
    coeffs.numFeatures = this.numFeatures
    coeffs.numInteractFeatures = this.numInteractFeatures
    coeffs.numFactors = this.numFactors
    coeffs.w0 = this.w0
    coeffs.w = this.w.copy
    coeffs.v = this.v.copy
    coeffs
  }

  /**
    * 对应系数除上同一实数，加至复制this的类上
 *
    * @param dividend 除数
    * @return 除法结果
    */
  override def /(dividend: Double): Coefficients = {
    val result = this.copy.asInstanceOf[FmCoefficients]
    if (k0) result.w0 /= dividend
    if (k1) result.w /= dividend
    if (k2) result.v /= dividend
    result
  }

  /**
    * 计算L2的正则值
 *
    * @param reg 正则参数
    * @return 参数加权后的L2正则值
    */
  override def L2RegValue(reg: Array[Double]): Double = {
    val zeroRegValue = if (k0) w0 * w0 * reg(0) else 0.0
    val firstRegValue = if (k1 && w.activeSize > 0) w.activeValuesIterator.reduce(_ + Math.pow(_, 2)) * reg(1) else 0.0
    val secondRegValue = if (k2 && v.activeSize > 0) v.activeValuesIterator.reduce(_ + Math.pow(_, 2)) * reg(2) else 0.0
    0.5 * (zeroRegValue + firstRegValue + secondRegValue)
  }

  /**
    * 计算L2的正则梯度值
 *
    * @param reg 正则参数
    * @return 参数加权后的L2正则梯度值
    */
  override def L2RegGradient(reg: Array[Double]): Coefficients = {
    val result = this.copy.asInstanceOf[FmCoefficients]
    if (k0) result.w0 *= reg(0)
    if (k1) result.w *= reg(1)
    if (k2) result.v *= reg(2)
    result
  }

  /**
    * 用L1稀疏化系数
    *
    * @param regParam 正则参数值
    * @param stepSize 学习率
    * @return 稀疏化后的系数
    */
  override def L1Shrink(regParam: Array[Double], stepSize: Double): Coefficients = {
    //0阶参数
    if (k0) {
      val zeroShrinkageVal = regParam(0) * stepSize
      w0 = signum(w0) * max(0.0, abs(w0) - zeroShrinkageVal)
    }
    //1阶参数
    if (k1) {
      val firstShrinkageVal = regParam(1) * stepSize
      val newW = DenseVector.zeros[Double](w.length)
      w.activeIterator.foreach { case (index, weight) =>
        val newWeight = signum(weight) * max(0.0, abs(weight) - firstShrinkageVal)
        if (newWeight == 0) {
          Nil
        } else {
          newW.update(index, newWeight)
        }
      }
      w = newW
    }
    //2阶参数
    if (k2) {
      val secondShrinkageVal = regParam(2) * stepSize / numFactors
      val newV = DenseMatrix.zeros[Double](v.rows, v.cols)
      v.activeIterator.foreach { case ((rowIndex, colIndex), weight) =>
        val newWeight = signum(weight) * max(0.0, abs(weight) - secondShrinkageVal)
        if (newWeight == 0) {
          Nil
        } else {
          newV.update(rowIndex, colIndex, newWeight)
        }
      }
      v = newV
    }
    //全部更新完后，返回结果
    this
  }

  /**
    * 计算L1的正则值
 *
    * @param reg 正则参数
    * @return 参数绝对值加权后的L1正则值
    */
  override def L1RegValue(reg: Array[Double]): Double = {
    val zeroRegValue = if (k0) abs(w0) * reg(0) else 0.0
    val firstRegValue = if (k1 && w.activeSize > 0) w.activeIterator.foldLeft(0.0) { case (absSum, (_, weight)) =>
      absSum + abs(weight)
    } * reg(1)
    else 0.0
    val secondRegValue = if (k2 && v.activeSize > 0) v.activeIterator.foldLeft(0.0) { case (absSum, (_, weight)) =>
      absSum + abs(weight)
    } * reg(2)
    else 0.0
    zeroRegValue + firstRegValue + secondRegValue
  }

  /**
    * 计算系数的2范数
    * sum(abs(A).^p)^(1/p) where p=2
    *
    * @return 系数的2范数
    */
  override def norm: Double = {
    val zeroSum = if (k0) w0 * w0 else 0.0
    val firstSum = if (k1 && w.activeSize > 0) w.activeIterator.foldLeft(0.0) { case (sum: Double, (_, value: Double)) =>
      sum + value * value
    } else 0.0
    val secondSum = if (k2 && v.activeSize > 0) v.activeIterator.foldLeft(0.0) { case (sum: Double, (_, value: Double)) =>
      sum + value * value
    } else 0.0
    math.sqrt(zeroSum + firstSum + secondSum)
  }

  /**
    * 转成字符串描述，用于saveModel等方法
 *
    * @return 系数的字符串描述
    */
  override def toString(): String = {
    //各阶系数的尺寸，以及是否处理各阶系数的标识，用冒号隔开
    val sb = new StringBuilder()
    sb ++= w.length.toString
    sb ++= ":"
    sb ++= v.rows.toString
    sb ++= ","
    sb ++= v.cols.toString
    sb ++= ","
    sb ++= v.activeSize.toString
    sb ++= ":"
    sb ++= k0.toString
    sb ++= ","
    sb ++= k1.toString
    sb ++= ","
    sb ++= k2.toString
    sb ++= "\n"
    //0阶系数
    sb ++= w0.toString
    sb ++= "\n"
    //1阶系数（从大到小排序）
    w.iterator.toList.sortBy(-_._2).foreach { case(index, value) =>
      sb ++= index.toString
      sb ++= ","
      sb ++= value.toString
      sb ++= "\n"
    }
    //2阶系数
    v.activeIterator.foreach { case ((rowIndex, colIndex), value) =>
      sb ++= rowIndex.toString
      sb ++= ","
      sb ++= colIndex.toString
      sb ++= ","
      sb ++= value.toString
      sb ++= "\n"
    }
    //返回结果
    sb.deleteCharAt(sb.length - 1).toString()
  }
}

object FmCoefficients {
  /**
    * 根据字符串数组构造分解机系数
    *
    * @param content 字符串数组
    * @return 分解机系数
    */
  def apply(content: Array[String]): FmCoefficients = {
    //获取各阶系数的尺寸
    val codeArray = content(0).split(":")
    val wLength = codeArray(0).trim.toInt
    val sizeCodeArray = codeArray(1).split(",")
    val vRows = sizeCodeArray(0).toInt
    val vCols = sizeCodeArray(1).toInt
    val vActiveSize = sizeCodeArray(2).toInt
    //获取是否处理各阶系数的标识
    val identifierCodeArray = codeArray(2).split(",")
    val k0 = identifierCodeArray(0).toBoolean
    val k1 = identifierCodeArray(1).toBoolean
    val k2 = identifierCodeArray(2).toBoolean
    //获取0阶系数
    val w0 = content(1).trim.toDouble
    //获取1阶系数
    val w = DenseVector.zeros[Double](wLength)
    for (i <- 0 until wLength) {
      val codes = content(i + 2).trim.split(",")
      w.update(codes(0).toInt, codes(1).toDouble)
    }
    //获取2阶系数
    val v = DenseMatrix.zeros[Double](vRows, vCols)
    for (i <- 0 until vActiveSize) {
      val codeArray = content(i + wLength + 2).split(",")
      val rowIndex = codeArray(0).toInt
      val colIndex = codeArray(1).toInt
      val value = codeArray(2).toDouble
      v.update(rowIndex, colIndex, value)
    }
    //返回结果
    new FmCoefficients(w0, w, v, k0, k1, k2)
  }
}