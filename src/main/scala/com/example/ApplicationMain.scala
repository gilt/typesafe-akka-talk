package com.example

import retrying.ReceivingActor
import retrying.FlakyReceivingActor
import retrying.RetryingActor
import retrying.PruningRetryingActor

import sharding.ShardActor
import sharding.DispatchingActor

object SimpleRetryMain extends App {
  import akka.actor.ActorSystem
  import akka.actor.Props

  val system = ActorSystem("RetrySystem")
  val receive = system.actorOf(ReceivingActor.props)
  val retry = system.actorOf(RetryingActor.props(receive))

  (1 to 10).foreach { i =>
    retry ! i
  }

  system.shutdown()
}

object SimpleShardMain extends App {
  import akka.actor.ActorSystem
  import akka.actor.Props

  import com.typesafe.config.ConfigFactory

  val nodes = (1 to 3).map { i =>
    val system = ActorSystem(s"ShardSystem", ConfigFactory.parseString(s"""
      akka.remote.netty.tcp.port=${10000 + i}
      """).withFallback(ConfigFactory.load("cluster")))

    println(s"created ${system.name}")

    ShardActor.start(system)

    system
  }

  // give nodes time to join
  Thread.sleep(10000)

  val dispatcher = nodes.head.actorOf(DispatchingActor.props, "dispatcher")

  (1 to 10).foreach { i => dispatcher ! i }

  // sleep to wait on the messages
  Thread.sleep(10000)

  nodes.foreach(_.shutdown())
}

object PruningRetryMain extends App {
  import akka.actor.ActorSystem
  import akka.actor.Props

  val system = ActorSystem("PruningRetrySystem")
  val receive = system.actorOf(FlakyReceivingActor.props)
  val retry = system.actorOf(PruningRetryingActor.props(receive))

  (1 to 3).foreach { id =>
    (1 to FlakyReceivingActor.flakeNumber).foreach { data =>
      retry ! (id, data)
      Thread.sleep(100) // inject some latency so the pruner can actually run
    }
  }

  FlakyReceivingActor.latch.await()

  system.shutdown()
}

object ComplexShardMain extends App {
  import akka.actor.ActorSystem
  import akka.actor.Props
  import akka.contrib.pattern.ClusterSharding
  import akka.contrib.pattern.ShardRegion

  import com.typesafe.config.ConfigFactory

  val nodes = (1 to 3).map { i =>
    val system = ActorSystem(s"ShardSystem", ConfigFactory.parseString(s"""
    akka.remote.netty.tcp.port=${10000 + i}
    """).withFallback(ConfigFactory.load("cluster")))

    println(s"created ${system.name}")

    val idExtractor: ShardRegion.IdExtractor = {
      case msg @ UpdateMsg(id, _, _) => (id.toString, msg)
    }

    val shardResolver: ShardRegion.ShardResolver = {
      case UpdateMsg(id, _, _) => (id % 10).toString
    }

    ClusterSharding(system).start(
      typeName = "flakyShard",
      entryProps = Some(FlakyReceivingActor.props),
      idExtractor = idExtractor,
      shardResolver = shardResolver
    )

    system
  }

  // give nodes time to join
  Thread.sleep(10000)

  val primary = nodes.head

  val shardRegionRef = ClusterSharding(primary).shardRegion("flakyShard")

  val dispatcher = primary.actorOf(PruningRetryingActor.props(shardRegionRef), "dispatcher")

  (1 to 3).foreach { id =>
    (1 to FlakyReceivingActor.flakeNumber).foreach { data =>
      dispatcher ! (id, data)
      Thread.sleep(100) // inject some latency so the pruner can actually run
    }
  }

  FlakyReceivingActor.latch.await()

  nodes.foreach(_.shutdown())
}
