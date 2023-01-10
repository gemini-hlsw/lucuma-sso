// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service.graphql

import edu.gemini.grackle.Cursor
import edu.gemini.grackle.Query
import edu.gemini.grackle.Result
import edu.gemini.grackle.Type
import edu.gemini.grackle.circe.CirceMapping
import fs2.Stream
import io.circe.Encoder
import io.circe.Json
import org.tpolecat.sourcepos.SourcePos

trait StreamMapping[F[_]] { this: CirceMapping[F] =>

  /** A RootMapping that streams a result directly. */
  sealed abstract case class StreamRoot[A] private (
    fieldName: String,
    tpe: Type,
    pos: SourcePos,
    run: Cursor.Env => Stream[F, Result[Json]],
  ) extends RootMapping {
    def cursor(query: Query, env: Cursor.Env, resultName: Option[String]): fs2.Stream[F,Result[(Query, Cursor)]] =
      run(env).map { r => r.map(a => (query, CirceCursor(Cursor.Context(tpe, fieldName, resultName).getOrElse(Cursor.Context(tpe)), a, None, env))) }
    def mutation: Mutation = Mutation.None
    def withParent(tpe: Type): RootMapping = this
  }

  object StreamRoot {
    /** Construct a `StreamRoot` for the specified field and type. */
    def apply[A](fieldName: String, tpe: Type, run: Cursor.Env => Stream[F, Result[A]])(
      implicit pos: SourcePos,
               enc: Encoder[A]
    ): StreamRoot[A] =
      new StreamRoot[A](fieldName, tpe, pos, e => run(e).map(_.map(enc(_)))) {}
  }

}

