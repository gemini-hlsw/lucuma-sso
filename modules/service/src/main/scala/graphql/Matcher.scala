// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.sso.service
package graphql

import cats.syntax.either.*
import grackle.Query.Binding
import grackle.Result
import grackle.Value

trait Matcher[A] {
  outer =>

  def validate(v: Value): Either[String, A]

  final def validate(b: Binding): Result[A] =
    validate(b.value) match
      case Left(error)  =>
        val msg = s"Argument '${b.name}' is invalid: $error"
        val msg0 = msg.replaceAll("' is invalid: Argument '", ".")
        Matcher.validationFailure(msg0)
      case Right(value) => Result(value)

  final def map[B](f: A => B): Matcher[B] = v =>
    outer.validate(v).map(f)

  final def emap[B](f: A => Either[String, B]): Matcher[B] = v =>
    outer.validate(v).flatMap(f)

  final def rmap[B](f: PartialFunction[A, Result[B]]): Matcher[B] = v =>
    outer.validate(v).flatMap: a =>
      f.lift(a) match
        case Some(r) => r.toEither.leftMap(_.fold(_.getMessage, _.head.message))
        case None    => Left(s"rmap: unhandled case; no match for $v")

  def unapply(b: Binding): Some[(String, Result[A])] =
    Some((b.name, validate(b)))

  final def unapply(kv: (String, Value)): Some[(String, Result[A])] =
    unapply(Binding(kv._1, kv._2))

  lazy val Option: Matcher[Option[A]] = {
    case Value.NullValue   => Right(None)
    case Value.AbsentValue => Right(None)
    case other             => outer.validate(other).map(Some.apply)
  }
}

object Matcher:

  def validationFailure(msg: String): Result[Nothing] =
    Result.failure(msg)