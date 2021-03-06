/**
 * Copyright (C) 2015 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.pid

import java.io.Closeable

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging

import scala.util.Try

class PidGeneratorApp(wiring: ApplicationWiring) extends Closeable with DebugEnhancedLogging {

  def this(configuration: Configuration) = this(new ApplicationWiring(configuration))

  def exists(pidType: PidType, pid: Pid): Try[Boolean] = {
    wiring.databaseAccess.doTransaction(implicit connection => wiring.pidManager.exists(pidType, pid))
        .doIfSuccess {
          case true => logger.info(s"Checked the existance of $pidType $pid - did exist indeed")
          case false => logger.info(s"Checked the existance of $pidType $pid - did not exist")
        }
        .doIfFailure {
          // TODO other exceptions
          case e => logger.error(e.getMessage, e)
        }
  }

  def generate(pidType: PidType): Try[Pid] = {
    wiring.databaseAccess
      .doTransaction(implicit connection => wiring.pidManager.generate(pidType))
      .doIfSuccess(pid => logger.info(s"Minted a new $pidType: $pid"))
      .doIfFailure {
        case e: PidNotInitialized => logger.info(e.getMessage)
        case e: DuplicatePid => logger.info(e.getMessage)
        case e: DatabaseException => logger.error(e.getMessage, e)
        case e => logger.error(e.getMessage, e)
      }
  }

  def initialize(pidType: PidType, seed: Seed): Try[Unit] = {
    wiring.databaseAccess
      .doTransaction(implicit connection => wiring.pidManager.initialize(pidType, seed))
      .doIfSuccess(_ => logger.info(s"Pid type $pidType is seeded with $seed"))
      .doIfFailure {
        case e: PidAlreadyInitialized => logger.info(e.getMessage)
        case e: DatabaseException => logger.error(e.getMessage, e)
        case e => logger.error(e.getMessage, e)
      }
  }

  def init(): Try[Unit] = {
    wiring.databaseAccess.initConnectionPool()
  }

  override def close(): Unit = {
    wiring.databaseAccess.closeConnectionPool().unsafeGetOrThrow
  }
}
