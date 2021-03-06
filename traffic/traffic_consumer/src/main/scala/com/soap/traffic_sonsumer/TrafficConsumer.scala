package com.soap.traffic_sonsumer

import com.alibaba.fastjson.{JSON, JSONObject, TypeReference}
import com.soap.traffic_sonsumer.util.{PropertiesUtil, RedisPool}
import kafka.serializer.StringDecoder
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.DateTime


/**
  * Created by soap on 2018/4/11.
  */
@SerialVersionUID(1L) object TrafficConsumer extends Serializable {

  def main(args: Array[String]): Unit = {
    val properties = PropertiesUtil.getPeroperties()

    val sparkConf = new SparkConf().setAppName("TrafficStreaming")
      .setMaster("local[*]")
    val sc = new SparkContext(sparkConf)
    val ssc = new StreamingContext(sc, Seconds(5))
    ssc.checkpoint("./spark/checkpoint")

    val keyParams = Map[String, String](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> PropertiesUtil.getKey(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
      ConsumerConfig.GROUP_ID_CONFIG -> PropertiesUtil.getKey(ConsumerConfig.GROUP_ID_CONFIG),
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> kafka.api.OffsetRequest.LargestTimeString
    )
    val event = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc,
      keyParams,
      Set(PropertiesUtil.getKey("kafka.topics")))
      .map(_._2).map(line => {
      val jsonEvent = JSON.parseObject(line, new TypeReference[java.util.Map[String, String]]() {})
      //将Java的HashMap转为Scala的mutable.Map
      import scala.collection.JavaConverters._
      val lineScalaMap: collection.mutable.Map[String, String] = mapAsScalaMapConverter(jsonEvent).asScala
      lineScalaMap
    })

    //将数据转换为 montorid speed times (0001,(035,1))
    event.map { items => (items.getOrElse("monitor_id", ""), items.getOrElse("speed", "")) }
      .mapValues(v => (v.toInt, 1))
      .reduceByKeyAndWindow((v1: (Int, Int), v2: (Int, Int)) =>
        (v1._1 + v2._1, v1._2 + v2._2), Seconds(60), Seconds(60))
      .foreachRDD(
        rdd => {
          //          val redisPool = RedisPool.apply(PropertiesUtil.getKey("redis.host"), PropertiesUtil.getKey("redis.port").toInt, PropertiesUtil.getKey("redis.timeout").toInt)
          //            .pool.getResource
          //          redisPool.select(1)
          rdd.foreachPartition(rddPar => {
            //将数据存如Redis 连接必须在 foreachPartition ，在 foreachRDD 会产生闭包序列化问题
            val redisPool = RedisPool.apply(PropertiesUtil.getKey("redis.host"), PropertiesUtil.getKey("redis.port").toInt, PropertiesUtil.getKey("redis.timeout").toInt)
              .pool.getResource
            redisPool.select(1)
            val date = DateTime.now.toString("yyyyMMdd")
            val hourMinuteTime = DateTime.now.toString("HHmm")
            //  jedis.hset(date + "_" + monitorId, hourMinuteTime, sumOfSpeed + "_" + sumOfCarCount)
            rddPar.foreach(item => {
              //              val redisPool = RedisPool.apply(PropertiesUtil.getKey("redis.host"), PropertiesUtil.getKey("redis.port").toInt, PropertiesUtil.getKey("redis.timeout").toInt)
              //                           .pool.getResource
              //              redisPool.select(1)
              val monitorId = item._1
              val sumOfSpeed = item._2._1
              val sumOfCarCount = item._2._2
              println("monitorId" + monitorId + ",sumOfSpeed:" + sumOfSpeed + ",sumOfCarCount :" + sumOfCarCount)
              redisPool.hset(date + "_" + monitorId, hourMinuteTime, sumOfSpeed + "_" + sumOfCarCount)
              //              redisPool.close()
            })
            //close 方法会将连接放回连接池
            redisPool.close()
          })
          //          redisPool.close()
        }
      )

    //    rdd=>{
    //      rdd.foreachPartition(rddPar=>{
    //        //将数据存如Redis
    //        val redisPool = RedisPool.apply(PropertiesUtil.getKey("redis.host"),PropertiesUtil.getKey("redis.port").toInt,PropertiesUtil.getKey("redis.timeout").toInt)
    //        //         redisPool.pool.
    //        val date = DateTime.now.dayOfYear().toString
    //        val monitorId = rddPar
    //        //  jedis.hset(date + "_" + monitorId, hourMinuteTime, sumOfSpeed + "_" + sumOfCarCount)
    //      })
    //    }
    ssc.start()
    ssc.awaitTermination()

  }
}
