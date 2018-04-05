package com.soap.spark_streaming.world_count

import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
  * Created by soap on 2018/4/5.
  */
object WorldCount {

  def main(args: Array[String]): Unit = {
    /**
      * 需要长期运行的接收器，因此必须多线程
      */
    val sc = new SparkConf().setAppName("streaming_word_count").setMaster("local[*]")
    //优雅停机
    //    sc.set("spark.streaming.stopGracefullyOnShutdown", "true")
    val ssc = new StreamingContext(sc, Seconds(5))
    loadFromHDFS(ssc)
    //    loadFormPort(ssc)
    ssc.start()
    ssc.awaitTermination()
    //    ssc.awaitTerminationOrTimeout(5000)
  }

  /**
    * 从HDFS文件中读取
    *
    * @param ssc
    */
  private def loadFromHDFS(ssc: StreamingContext) = {
    //    val file = ssc.textFileStream("hdfs://hadoop200:9000/spark/stream_input/")
    val file = ssc.textFileStream("C:\\Users\\yangf\\Desktop\\test\\")
    val wordCount = file.flatMap(_.split(" "))
      .map((_, 1))
      .reduceByKey(_ + _)
    wordCount.print(100)

  }

  /**
    * 监听端口数据
    *
    * @param ssc
    * @return
    */
  private def loadFormPort(ssc: StreamingContext) = {
    val socketText = ssc.socketTextStream("hadoop200", 9999)
    val wordCount = socketText.flatMap(_.split(" ")).map((_, 1)).reduceByKey(_ + _)
    wordCount.print()
  }
}
