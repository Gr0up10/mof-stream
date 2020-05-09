package com.minute_of_fame.stream.actors

import java.nio.ByteBuffer

import akka.actor.{Actor, ActorRef, Props}
import akka.util.ByteString
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

object Session {
  def props(handler: ActorRef) = Props(classOf[Session], handler)
}

class Session(handler: ActorRef) extends Actor with akka.actor.ActorLogging {
  val messagesTypes: Array[GeneratedMessageCompanion[_ <: GeneratedMessage]] = Array(packets.Packet, packets.Result, packets.InternalPacket)
  var client: Option[ActorRef] = None

  override def receive = {
    case "connected" =>
      log.info("connected")
      handler ! "connected"
      client = Some(sender())
    case data: Array[Byte] =>
      val buffer = ByteBuffer.allocate(data.length).put(data).position(0)
      while(buffer.position() < data.length) {
        val size = buffer.getInt
        val packRaw = new Array[Byte](size)
        buffer.get(packRaw)
        val pack = packets.PacketWrapper.parseFrom(packRaw)
        pack.message match {
          case Some(m) =>
            val pack = messagesTypes.find(t => m.is(t)).get
            handler ! m.unpack(pack)
          case None => log.warning("Pack {} does not contains message", pack)
        }
      }

    case m: scalapb.GeneratedMessage =>
      if(client.isDefined) {
        val bytes = packets.PacketWrapper(message = Some(com.google.protobuf.any.Any.pack(m))).toByteArray
        val buffer = ByteBuffer.allocate(bytes.length+5).position(5).put(bytes)
        buffer.put(0, 42)
        buffer.putInt(1, bytes.length)
        client.get ! ByteString(buffer.array())
      }
  }
}

