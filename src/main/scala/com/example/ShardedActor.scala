package com.example.sharding

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.contrib.pattern.ClusterSharding
import akka.contrib.pattern.ShardRegion

import com.example.Msg

class DispatchingActor extends Actor with ActorLogging {
  def shardRegion = ClusterSharding(context.system).shardRegion("shard")

  def receive = {
    case data => {
      log.info(s"dispatching data $data")
      shardRegion ! Msg(data)
    }
  }
}

object DispatchingActor {
  def props = Props(new DispatchingActor)
}

class ShardActor extends Actor with ActorLogging {
  def receive = {
    case data => {
      log.info(s"received data ${data} in ${self.path.address} on ${context.system.name} from ${sender.path.address}")
    }
  }
}

object ShardActor {
  def props = Props(new ShardActor)

  def start(system: ActorSystem) = {
    val idExtractor: ShardRegion.IdExtractor = {
      case Msg(id, payload) => (id.toString, payload)
    }

    val shardResolver: ShardRegion.ShardResolver = {
      case Msg(id, _) => (id % 10).toString
    }

    ClusterSharding(system).start(
      typeName = "shard",
      entryProps = Some(props),
      idExtractor = idExtractor,
      shardResolver = shardResolver
    )
  }
}
