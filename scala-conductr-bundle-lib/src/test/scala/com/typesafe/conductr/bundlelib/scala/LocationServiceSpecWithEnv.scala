/*
 * Copyright © 2014-2015 Typesafe, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Typesafe, Inc.
 */

package com.typesafe.conductr.bundlelib.scala

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{ CacheDirectives, `Cache-Control`, Location }
import akka.http.scaladsl.model.{ HttpEntity, Uri, HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorFlowMaterializer
import akka.testkit.TestProbe
import com.typesafe.conductr._
import com.typesafe.conductr.AkkaUnitTest
import java.net.{ URL, InetSocketAddress }

import com.typesafe.conductr.bundlelib.scala.ConnectionContext.Implicits

import scala.concurrent.Await
import scala.util.{ Failure, Success }

class LocationServiceSpecWithEnv extends AkkaUnitTest("LocationServiceSpecWithEnv", "akka.loglevel = INFO") {

  import Implicits.global

  "The LocationService functionality in the library" should {
    "return the lookup url" in {
      LocationService.getLookupUrl("/whatever", "http://127.0.0.1/whatever") shouldBe "http://127.0.0.1:50008/services/whatever"
    }

    "be able to look up a named service" in {
      val serviceUri = "http://service_interface:4711/known"
      withServerWithKnownService(serviceUri) {
        val service = LocationService.lookup("/known")
        Await.result(service, timeout.duration) shouldBe Some(serviceUri)
      }
    }

    "be able to look up a named service using a cache" in {
      val serviceUri = "http://service_interface:4711/known"
      withServerWithKnownService(serviceUri) {
        val cache = LocationCache()
        val service = LocationService.lookup("/known", cache)
        Await.result(service, timeout.duration) shouldBe Some(serviceUri)
      }
    }

    "be able to look up a named service and return maxAge" in {
      val serviceUri = "http://service_interface:4711/known"
      withServerWithKnownService(serviceUri, Some(10)) {
        val service = LocationService.lookup("/known")
        Await.result(service, timeout.duration) shouldBe Some(serviceUri)
      }
    }

    "get back None for an unknown service" in {
      val serviceUrl = "http://service_interface:4711/known"
      withServerWithKnownService(serviceUrl) {
        val service = LocationService.lookup("/unknown")
        Await.result(service, timeout.duration) shouldBe None
      }
    }
  }

  def withServerWithKnownService(serviceUrl: String, maxAge: Option[Int] = None)(thunk: => Unit): Unit = {
    import system.dispatcher
    implicit val materializer = ActorFlowMaterializer()

    val probe = new TestProbe(system)

    val handler =
      path("services" / Rest) { serviceName =>
        get {
          complete {
            serviceName match {
              case "known" =>
                val uri = Uri(serviceUrl)
                val headers = Location(uri) :: (maxAge match {
                  case Some(maxAgeSecs) =>
                    `Cache-Control`(
                      CacheDirectives.`private`(Location.name),
                      CacheDirectives.`max-age`(maxAgeSecs)) :: Nil
                  case None =>
                    Nil
                })
                HttpResponse(StatusCodes.TemporaryRedirect, headers, HttpEntity(s"Located at $uri"))
              case _ =>
                HttpResponse(StatusCodes.NotFound)
            }
          }
        }
      }

    val url = new URL(Env.serviceLocator.get)
    val server = Http(system).bindAndHandle(handler, url.getHost, url.getPort)

    try {
      server.onComplete {
        case Success(binding) => probe.ref ! binding.localAddress
        case Failure(e)       => probe.ref ! e
      }

      val address = probe.expectMsgType[InetSocketAddress]
      address.getHostString should be(url.getHost)
      address.getPort should be(url.getPort)

      thunk
    } finally {
      server.foreach(_.unbind())
    }
  }
}
