package com.minute_of_fame.stream.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.minute_of_fame.stream.models.JsonPackets
import org.kurento.client.{DispatcherOneToMany, HubPort, IceCandidate, KurentoClient, MediaPipeline, MediaProfileSpecType, PlayerEndpoint, RecorderEndpoint, WebRtcEndpoint}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object KurentoHandler {
  def props = Props(classOf[KurentoHandler])

  case class Connected()
  case class SetPresenter(id: Int)
  case class CreateUser(id: Int, offer: String, presenter: Boolean, fake: Boolean)
  case class StopStream()
  case class CloseConnection(id: Int, presenter: Boolean)
  case class InIceCandidate(id: Int, iceCandidate: IceCandidate, presenter: Boolean)

  case class SdpAnswer(id: Int, answer: String, presenter: Boolean)
  case class IceCandidateAnswer(id: Int, candidate: IceCandidate, presenter: Boolean)

  private case class UserSession(pipeline: MediaPipeline, dispatcher: DispatcherOneToMany) {
    val endpoint: WebRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build
    val hubPort: HubPort = new HubPort.Builder(dispatcher).build

    hubPort.connect(endpoint)
    endpoint.connect(hubPort)

    def release(): Unit = {
      endpoint.release()
      hubPort.release()
    }
  }
}

class KurentoHandler extends Actor with ActorLogging {
  import com.minute_of_fame.stream.actors.KurentoHandler._

  private var session: ActorRef = _
  private val kurento = KurentoClient.create()
  private val pipeline: MediaPipeline = kurento.createMediaPipeline
  private val dispatcher = new DispatcherOneToMany.Builder(pipeline).build()
  private var viewers = mutable.HashMap[Int, UserSession]()
  private var presenters = mutable.HashMap[Int, UserSession]()
  private var currentStreamer = -1

  private val player = new PlayerEndpoint.Builder(pipeline, "file:///tst.mp4").build()
  private val playerHub = new HubPort.Builder(dispatcher).build()
  playerHub.connect(player)
  player.connect(playerHub)
  dispatcher.setSource(playerHub)
  player.play()
  player.addEndOfStreamListener(_ => if(currentStreamer == -1) player.play())
  println("Playing tst video")

  override def receive: Receive = {
    case Connected() =>
      session = sender()

    case SetPresenter(id) =>
      log.info("Setting presenter {}", id)
      presenters.get(id) match {
        case Some(user) =>
          log.info("Set dispatcher source {}", id)
          dispatcher.removeSource()
          dispatcher.setSource(user.hubPort)
        case None => log.error("Cannot find user with id {}", id)
      }

    case CreateUser(id, offer, presenter, fake) =>
      val user = UserSession(pipeline, dispatcher)
      user.endpoint.addIceCandidateFoundListener(event => session ! IceCandidateAnswer(id, event.getCandidate, presenter))

      val answer = user.endpoint.processOffer(offer)
      session ! SdpAnswer(id, answer, presenter)
      user.endpoint.gatherCandidates()
      if (!presenter) {
        viewers += id -> user
        user.endpoint.setMinOutputBitrate(1500)
      } else {
        presenters.get(id) match {
          case Some(v) =>
            v.release()
            presenters -= id
          case None =>
        }
        presenters += id -> user
        if(!fake) {
          user.endpoint.setMinVideoRecvBandwidth(1500)
          val recorder = new RecorderEndpoint
          .Builder(pipeline, s"file:///recs/${System.currentTimeMillis()}rec$id.webm")
            .withMediaProfile(MediaProfileSpecType.WEBM)
            .build()
          user.endpoint.connect(recorder)
          recorder.record()
          if (currentStreamer == -1) {
            dispatcher.setSource(user.hubPort)
            currentStreamer = id
          }
        }

      }
      log.info("User {} is connected, presenter: {}, fake {}", id, presenter, fake)

    case InIceCandidate(id, candidate, presenter) =>

      (if(presenter) presenters.get(id) else viewers.get(id)) match {
        case Some(user) => user.endpoint.addIceCandidate(candidate)
        case None => log.error("Cannot find user with id {}", id)
      }

    case StopStream() =>
      for((_, user) <- viewers) {
        user.endpoint.release()
      }
      pipeline.release()
      viewers.clear()

    case CloseConnection(id, presenter) =>
      (if(presenter) presenters.get(id) else viewers.get(id)) match {
        case Some(user) =>
          user.release()
        case None => log.warning("Cannot remove non existing user")
      }
      if(presenter) presenters -= id
      else viewers -= id

      if(presenter && id == currentStreamer) {
        dispatcher.setSource(playerHub)
        player.play()
        currentStreamer = -1
      }
  }
}
