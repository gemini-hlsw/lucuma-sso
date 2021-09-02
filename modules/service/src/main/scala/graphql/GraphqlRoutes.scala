// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service
package graphql

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import edu.gemini.grackle.skunk.SkunkMonitor
import lucuma.core.model.StandardUser
import lucuma.graphql.routes.GrackleGraphQLService
import lucuma.graphql.routes.GraphQLService
import lucuma.graphql.routes.{ Routes => LucumaGraphQLRoutes }
import lucuma.sso.client.SsoClient
import natchez.Trace
import org.http4s._
import org.typelevel.log4cats.Logger
import skunk.Session
import org.http4s.server.websocket.WebSocketBuilder

object GraphQLRoutes {

  def apply[F[_]: Async: Trace: Logger](
    client:  SsoClient[F, StandardUser],
    pool:    Resource[F, Session[F]],
    monitor: SkunkMonitor[F],
    wdb:     WebSocketBuilder[F],
  ): HttpRoutes[F] =
    LucumaGraphQLRoutes.forService[F](
      req => {
        OptionT(client.get(req)).flatMap { su =>
          OptionT.liftF(SsoMapping(pool, monitor).map(f => f(su)).map[GraphQLService[F]](new GrackleGraphQLService(_)))
        }.value
      },
      wdb
    )



}


