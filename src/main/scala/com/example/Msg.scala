package com.example

case class Msg[T](id: Int, body: T)

object Msg {
  private var id: Int = 0

  def apply[T](body: T): Msg[T] = {
    id += 1
    new Msg(id, body)
  }
}

case class UpdateMsg[T](id: Int, seq: Int, body: T)

object UpdateMsg {
  private var seq: Int = 0

  def apply[T](id: Int, body: T): UpdateMsg[T] = {
    seq += 1
    new UpdateMsg(id, seq, body)
  }
}
