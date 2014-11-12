package com.example.retrying

import collection.mutable.Map

import akka.actor.{Actor, ActorRef, Props}

import com.example.Msg

class RetryingActor(target: ActorRef) extends Actor {
  val outstanding = Map.empty[Int, Msg[_]]

  def receive = {
    case Ack(id) => {
      println(s"got ack for id ${id}")
      outstanding -= id
    }

    case data => {
      val msg = Msg(data)
      println(s"sending msg $msg to target ${target.path}")
      outstanding += msg.id -> msg
      target ! msg
    }
  }
}

object RetryingActor {
  def props(target: ActorRef) = Props(new RetryingActor(target))
}

case class Ack(id: Int)

class ReceivingActor extends Actor {
  def receive = {
    case msg @ Msg(id, body) => {
      sender ! Ack(msg.id)
      println(s"received message: ${msg}")
    }
  }
}

object ReceivingActor {
  def props = Props(new ReceivingActor)
}
