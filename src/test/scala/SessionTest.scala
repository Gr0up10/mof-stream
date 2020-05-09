import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import com.minute_of_fame.stream.actors.Session
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

class SessionTest()
  extends TestKit(ActorSystem("MySpec"))
    with ImplicitSender
    with Matchers
    with FunSuiteLike
    with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  test("Session handler test"){
    val probe = TestProbe()
    val poll = system.actorOf(Session.props(probe.ref))
    poll ! "connected"
    probe.expectMsg("connected")
    var bytes = packets.PacketWrapper(message = Some(com.google.protobuf.any.Any.pack(packets.Packet()))).toByteArray
    val bytes2 = packets.PacketWrapper(message = Some(com.google.protobuf.any.Any.pack(packets.Packet(3)))).toByteArray
    var buffer = ByteBuffer.allocate(bytes.length+bytes2.length+8)
      .position(4).put(bytes).position(bytes.length+8).put(bytes2)
    buffer.putInt(0, bytes.length)
    buffer.putInt(bytes.length+4, bytes2.length)
    println(buffer.array().mkString(" "))
    poll ! buffer.array()
    probe.expectMsg(packets.Packet())
    probe.expectMsg(packets.Packet(3))
    poll ! packets.Packet()
    bytes = packets.PacketWrapper(message = Some(com.google.protobuf.any.Any.pack(packets.Packet()))).toByteArray
    buffer = ByteBuffer.allocate(bytes.length+5).position(5).put(bytes)
    buffer.put(0, 42)
    buffer.putInt(1, bytes.length)
    expectMsg(ByteString(buffer.array()))
  }
}
