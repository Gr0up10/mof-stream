package com.minute_of_fame.stream.models

import io.circe.generic.auto._
import io.circe.generic.extras._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, _}

object JsonPackets {

  implicit val config: Configuration =
    Configuration.default.withDiscriminator("type").withSnakeCaseMemberNames

  @ConfiguredJsonCodec case class CommandPacket(command: String, data: Command)

  @ConfiguredJsonCodec sealed trait Command
  case class Connect(offer: String, presenter: Boolean) extends Command
  case class IceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int, presenter: Boolean) extends Command
  //Queue command
  case class SetRtcStream(id: Int) extends Command


  case class SdpAnswer(answer: String, presenter: Boolean) extends Command
  case class Error(reason: String) extends Command


  object CommandPacketDecoder {
    implicit val encodeCommandPacket: Encoder[CommandPacket] = (a: CommandPacket) => Json.obj(
      ("command", Json.fromString(a.command)),
      ("data", a.data.asJson.withObject(_.filter(_._1 != "type").asJson))
    ) //implicit def decodeImp[CommandPacket]: Decoder[CommandPacket] = Decoder.instanceTry(c => {
    implicit val decodeCommandPacket: Decoder[CommandPacket] = (c: HCursor) =>
      c.downField("command").as[String] match {
        case Right(v) =>
          (v match {
            case "connect" => c.get[Connect]("data")
            case "ice_candidate" => c.get[IceCandidate]("data")
            case "sdp_answer" => c.get[SdpAnswer]("data")
            case "error" => c.get[Error]("data")
            case "set_rtc_stream" => c.get[SetRtcStream]("data")
          }) map (CommandPacket(v, _))
        case Left(e) => Left(e)
      }
  }
}