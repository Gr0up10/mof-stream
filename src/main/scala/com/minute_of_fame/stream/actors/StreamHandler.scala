package com.minute_of_fame.stream.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.minute_of_fame.stream.models.JsonPackets
import com.minute_of_fame.stream.models.JsonPackets.{Command, CommandPacket, SetRtcStream}
import io.circe.parser.decode
import org.kurento.client.IceCandidate
import io.circe.parser._
import io.circe.syntax._

import scala.collection.mutable

object StreamHandler {
  def props(kurentoHandler: ActorRef) = Props(classOf[StreamHandler], kurentoHandler)
}


class StreamHandler(kurentoHandler: ActorRef) extends Actor with ActorLogging {
  private var currentStreamer = -1
  private var session: ActorRef = _

  def packCommand(id: Int, name: String, cmd: Command) =
    packets.Packet(id, data = CommandPacket(name, cmd).asJson.noSpaces)

  override def receive: Receive = {
    case "connected" =>
      session = sender()
      session ! packets.Register("stream")
      kurentoHandler ! KurentoHandler.Connected()
      log.info("Starting service")

    case p: packets.Packet =>
      log.info(s"Start processing packet $p")
      p.data match {
        case "connected" =>

        case "disconnected" =>
          kurentoHandler ! KurentoHandler.CloseConnection(p.userId, presenter = true)
          kurentoHandler ! KurentoHandler.CloseConnection(p.userId, presenter = false)

        case json =>
          import com.minute_of_fame.stream.models.JsonPackets.CommandPacketDecoder._
          decode[CommandPacket](json) match {
            case Right(cmd) =>
              cmd.data match {
                case JsonPackets.Connect(offer, presenter, fake) =>
                  //log.info("Receive new offer {}", offer)
                  kurentoHandler ! KurentoHandler.CreateUser(p.userId, offer, presenter, fake)

                case JsonPackets.IceCandidate(candidate, sdpMid, sdpMLineIndex, presenter) =>
                  kurentoHandler ! KurentoHandler.InIceCandidate(p.userId,
                    new org.kurento.client.IceCandidate(candidate, sdpMid, sdpMLineIndex), presenter)

                case other => log.error("Unsupported json model {}", other)
              }

            case Left(err) =>
              log.error("Cant decode command json packet {}:\n{}", json, err)
          }
      }

    case p: packets.InternalPacket =>
      log.info(s"Got internal packet $p")
      if (p.sender == "queue") {
        import com.minute_of_fame.stream.models.JsonPackets.CommandPacketDecoder._
        decode[CommandPacket](p.message) match {
          case Right(command) =>
            command.data match {
              case SetRtcStream(id) =>
                kurentoHandler ! KurentoHandler.SetPresenter(id)

              case other => log.error("Unsupported command {} from {}", other, p.sender)
            }
          case Left(err) => log.error("Error while parsing internal message {}, message: {}", err, p)
        }
      }

    case KurentoHandler.IceCandidateAnswer(id, candidate, presenter) =>
      log.info("{} {} {}", id, candidate, presenter)
      session ! packCommand(id, "ice_candidate", JsonPackets.IceCandidate(candidate.getCandidate, candidate.getSdpMid, candidate.getSdpMLineIndex, presenter))

    case KurentoHandler.SdpAnswer(id, answer, presenter) =>
      log.info("Sending sdp answer id: {} presenter: {}", id, presenter)
      session ! packCommand(id, "sdp_answer", JsonPackets.SdpAnswer(answer, presenter))
  }
}
