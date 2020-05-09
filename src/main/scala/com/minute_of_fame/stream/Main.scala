package com.minute_of_fame.stream

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import com.minute_of_fame.stream.actors.{Client, KurentoHandler, Session, StreamHandler}


object Main extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()

  var handler = actorSystem.actorOf(KurentoHandler.props, "kurento_handler")
  val protocol = actorSystem.actorOf(StreamHandler.props(handler), "protocol")
  val session = actorSystem.actorOf(Session.props(protocol), "session")
  val host = scala.util.Properties.envOrElse("HANDLER_HOST", "localhost")
  val port = scala.util.Properties.envOrElse("HANDLER_PORT", "58008").toInt
  println("Connecting to "+host)
  val mainActor = actorSystem.actorOf(Client.props(new InetSocketAddress(host, port), session), "client")
}