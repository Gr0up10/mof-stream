package com.minute_of_fame.stream.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.minute_of_fame.stream.models.JsonPackets
import org.kurento.client.{DispatcherOneToMany, HubPort, IceCandidate, KurentoClient, MediaPipeline, MediaProfileSpecType, RecorderEndpoint, WebRtcEndpoint}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object KurentoHandler {
  def props = Props(classOf[KurentoHandler])

  case class Connected()
  case class SetPresenter(id: Int)
  case class CreateUser(id: Int, offer: String, presenter: Boolean)
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
  }
}

class KurentoHandler extends Actor with ActorLogging {
  import com.minute_of_fame.stream.actors.KurentoHandler._

  private var session: ActorRef = _
  private val kurento = KurentoClient.create()
  private val pipeline: MediaPipeline = kurento.createMediaPipeline
  private val dispatcher = new DispatcherOneToMany.Builder(pipeline).build
  private var viewers = mutable.HashMap[Int, UserSession]()
  private var presenters = mutable.HashMap[Int, UserSession]()

  override def receive: Receive = {
    case Connected() =>
      session = sender()

    case SetPresenter(id) =>
      presenters.get(id) match {
        case Some(user) =>
          log.info("Set dispatcher source {}", id)
          dispatcher.removeSource()
          dispatcher.setSource(user.hubPort)
        case None => log.error("Cannot find user with id {}", id)
      }

    case CreateUser(id, offer, presenter) =>
      val user = UserSession(pipeline, dispatcher)
      user.endpoint.addIceCandidateFoundListener(event => session ! IceCandidateAnswer(id, event.getCandidate, presenter))

      val answer = user.endpoint.processOffer(offer)
      session ! SdpAnswer(id, answer, presenter)
      user.endpoint.gatherCandidates()
      if(!presenter)
        viewers += id -> user
      else {
        presenters += id -> user
        val recorder = new RecorderEndpoint.Builder(pipeline, s"file:///recs/${System.currentTimeMillis()}rec$id.webm").withMediaProfile(MediaProfileSpecType.WEBM).build()
        user.endpoint.connect(recorder)
        recorder.record()
      }
      log.info("User {} is connected, presenter: {}", id, presenter)

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
          user.endpoint.release()
          viewers -= id
        case None => log.warning("Cannot remove non existing user")
      }
  }
}
