package com.example.retrying

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import java.util.concurrent.CountDownLatch

import collection.mutable.Buffer
import collection.mutable.Map
import collection.mutable.Set

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import com.example.UpdateMsg

class PruningRetryingActor(target: ActorRef) extends Actor with ActorLogging {
  val outstanding = Map.empty[Int, Buffer[UpdateMsg[_]]]

  override def preStart() {
    context.system.scheduler.schedule(1.second, 1.second, self, 'prune)
  }

  def receive = {
    case 'prune => {
      log.info("pruning outstanding messages")
      outstanding.foreach { case (id, msgs) =>
        val latest = msgs.maxBy(_.seq)
        val count = msgs.size - 1
        msgs.clear()
        msgs += latest
        log.warning(s"pruned ${count} outstanding messages for id ${id} and have ${msgs.size} remaining")
        target ! latest
      }
      log.info("done pruning outstanding messages")
    }

    case SeqAck(id, seq) => {
      log.info(s"got ack for seq ${seq} on id ${id}")
      outstanding.get(id).fold {
      } { msgs =>
        msgs --= msgs.filter(_.seq < seq)
        if (msgs.isEmpty) outstanding -= id
      }
    }

    case (id: Int, data: Int) => {
      val msg = UpdateMsg(id, data)
      log.info(s"sending msg $msg to target ${target.path}")
      outstanding.getOrElseUpdate(msg.id, Buffer.empty) +=  msg
      target ! msg
    }
  }
}

object PruningRetryingActor {
  def props(target: ActorRef) = Props(new PruningRetryingActor(target))
}

case class SeqAck(id: Int, seq: Int)

class FlakyReceivingActor extends Actor with ActorLogging {
  import FlakyReceivingActor._

  val acceptedIds = Set.empty[Int]

  def receive = {
    case msg @ UpdateMsg(id, seq, body: Int) => {
      if (body == flakeNumber) {
        if (!acceptedIds(id)) {
          log.info(s"acknowledging message: ${msg}")
          acceptedIds += id
          latch.countDown()
          sender ! SeqAck(id, seq)
        }
      } else {
        log.warning(s"ignoring msg ${msg}, because I'm flaky.")
      }
    }
  }
}

object FlakyReceivingActor {
  def props = Props(new FlakyReceivingActor)

  val latch: CountDownLatch = new CountDownLatch(3)

  val flakeNumber = 5
}



