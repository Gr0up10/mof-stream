package com.minute_of_fame.stream.actors

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString


object Client {
  def props(remote: InetSocketAddress, replies: ActorRef) =
    Props(classOf[Client], remote, replies)
}

class Client(remote: InetSocketAddress, listener: ActorRef) extends Actor with ActorLogging{

  import Tcp._
  import context.system

  IO(Tcp) ! Connect(remote)

  def receive: PartialFunction[Any, Unit] = {
    case CommandFailed(_: Connect) =>
      log.error("Connection failed")
      context.stop(self)

    case c @ Connected(remote, local) =>
      listener ! "connected"
      val connection = sender()
      connection ! Register(self)
      context.become {
        case data: ByteString =>
          connection ! Write(data)
        case CommandFailed(w: Write) =>
          // O/S buffer was full
          log.warning("OS buffer full {}", w)
        case Received(data) =>
          listener ! data.toArray
        case cc: ConnectionClosed =>
          listener ! cc
          log.info("Connection closed")
          context.stop(self)
      }
  }
}