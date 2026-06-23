/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.rdsdatacacheproxy.gambling.repositories

import play.api.Logging
import play.api.db.NamedDatabase
import uk.gov.hmrc.rdsdatacacheproxy.gambling.models.{Regime, SubmittedReturnSingle}
import uk.gov.hmrc.rdsdatacacheproxy.gambling.repositories.RepositorySupport.{GTRDatabase, MGDDatabase}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait SubmittedReturnSingleDataSource {
  def getSubmittedReturnSingle(regNumber: String, consecNo: Int): Future[Option[SubmittedReturnSingle]]
}

@Singleton
class SubmittedReturnSingleDataCacheRepository @Inject() (@NamedDatabase("gambling") mgdDb: MGDDatabase,
                                                          @NamedDatabase("gambling.gtr") gtrDb: GTRDatabase
                                                         )(implicit
  ec: ExecutionContext
) extends SubmittedReturnSingleDataSource
    with RepositorySupport
    with Logging {

  override def getSubmittedReturnSingle(regNumber: String, consecNo: Int): Future[Option[SubmittedReturnSingle]] =
    Future {
      getDb(Regime.MGD, mgdDb, gtrDb).underlying.withConnection { conn =>
        val cs = conn.prepareCall("{ call MGD_DC_RTN_PCK.GET_SINGLE_RETURN_V2(?, ?, ?) }")

        try {
          cs.setString(1, regNumber)
          cs.setInt(2, consecNo)
          cs.registerOutParameter(3, java.sql.Types.NUMERIC) // consec_no
          cs.registerOutParameter(4, java.sql.Types.VARCHAR) // mgd_period
          cs.registerOutParameter(5, java.sql.Types.DATE) // submitted_date
          cs.registerOutParameter(6, java.sql.Types.VARCHAR) // ack_ref
          cs.registerOutParameter(7, java.sql.Types.NUMERIC) // no_of_machines_avail
          cs.registerOutParameter(8, java.sql.Types.DECIMAL) // net_takings_higher_rate
          cs.registerOutParameter(9, java.sql.Types.DECIMAL) // net_takings_std_rate
          cs.registerOutParameter(10, java.sql.Types.DECIMAL) // net_takings_lower_rate
          cs.registerOutParameter(11, java.sql.Types.DECIMAL) // total_due_higher_rate
          cs.registerOutParameter(12, java.sql.Types.DECIMAL) // total_due_std_rate
          cs.registerOutParameter(13, java.sql.Types.DECIMAL) // total_due_lower_rate
          cs.registerOutParameter(14, java.sql.Types.DECIMAL) // duty_payable
          cs.registerOutParameter(15, java.sql.Types.DECIMAL) // under_declared_duty
          cs.registerOutParameter(16, java.sql.Types.DECIMAL) // previous_return_amount
          cs.registerOutParameter(17, java.sql.Types.DECIMAL) // neg_amt_carry_forward
          cs.registerOutParameter(18, java.sql.Types.DECIMAL) // total_net_duty_payable
          cs.execute()

          optDecimalFromIndex(8, cs) match {
            case None => None
            case Some(_) =>
              Some(
                SubmittedReturnSingle(
                  consecNo                     = optInt(3, cs).getOrElse(0),
                  mgdPeriod                    = optString(4, cs).getOrElse(""),
                  submittedDate                = optDate(5, cs).getOrElse(LocalDate.of(1900, 1, 1)),
                  ackRef                       = optString(6, cs).getOrElse(""),
                  noOfMachines                 = optInt(7, cs).getOrElse(0),
                  netTakingsHigherRate         = optDecimalFromIndex(8, cs).getOrElse(0),
                  netTakingsStdRate            = optDecimalFromIndex(9, cs).getOrElse(0),
                  netTakingsLowerRate          = optDecimalFromIndex(10, cs).getOrElse(0),
                  totalDueHigherRate           = optDecimalFromIndex(11, cs).getOrElse(0),
                  totalDueStdRate              = optDecimalFromIndex(12, cs).getOrElse(0),
                  totalDueLowerRate            = optDecimalFromIndex(13, cs).getOrElse(0),
                  dutyPayable                  = optDecimalFromIndex(14, cs).getOrElse(0),
                  underDeclaredDuty            = optDecimalFromIndex(15, cs).getOrElse(0),
                  previousReturnAmount         = optDecimalFromIndex(16, cs).getOrElse(0),
                  negativeAmountCarriedForward = optDecimalFromIndex(17, cs).getOrElse(0),
                  totalNetDutyPayable          = optDecimalFromIndex(18, cs).getOrElse(0)
                )
              )
          }
        } finally {
          closeQuietly(cs)
        }
      }
    }(ec)

//          val rs = cs.getObject(3).asInstanceOf[java.sql.ResultSet]
//
//          if (rs == null) {
//            val msg = s"Null cursor returned for regNumber=$regNumber"
//            logger.error(s"[SubmittedReturnSingleDataCacheRepository] $msg")
//            throw new RuntimeException(msg)
//          }
//
//          try {
//            if (rs.next()) {
//
//              val result = for
//                consecNo                     <- Option(rs.getInt("consec_no"))
//                mgdPeriod                    <- Option(rs.getString("mgd_period"))
//                submittedDate                <- Option(rs.getDate("submitted_date")).map(_.toLocalDate)
//                ackRef                       <- Option(rs.getString("ack_ref"))
//                noOfMachines                 <- Option(rs.getInt("no_of_machines_avail"))
//                netTakingsHigherRate         <- optDecimalFromLabel("net_takings_higher_rate", rs)
//                netTakingsStdRate            <- optDecimalFromLabel("net_takings_std_rate", rs)
//                netTakingsLowerRate          <- optDecimalFromLabel("net_takings_lower_rate", rs)
//                totalDueHigherRate           <- optDecimalFromLabel("total_due_higher_rate", rs)
//                totalDueStdRate              <- optDecimalFromLabel("total_due_std_rate", rs)
//                totalDueLowerRate            <- optDecimalFromLabel("total_due_lower_rate", rs)
//                dutyPayable                  <- optDecimalFromLabel("duty_payable", rs)
//                underDeclaredDuty            <- optDecimalFromLabel("under_declared_duty", rs)
//                previousReturnAmount         <- optDecimalFromLabel("previous_return_amount", rs)
//                negativeAmountCarriedForward <- optDecimalFromLabel("neg_amt_carry_forward", rs)
//                totalNetDutyPayable          <- optDecimalFromLabel("total_net_duty_payable", rs)
//              yield SubmittedReturnSingle(
//                consecNo,
//                mgdPeriod,
//                submittedDate,
//                ackRef,
//                noOfMachines,
//                netTakingsHigherRate,
//                netTakingsStdRate,
//                netTakingsLowerRate,
//                totalDueHigherRate,
//                totalDueStdRate,
//                totalDueLowerRate,
//                dutyPayable,
//                underDeclaredDuty,
//                previousReturnAmount,
//                negativeAmountCarriedForward,
//                totalNetDutyPayable
//              )
//              result match {
//                case Some(_) => result.get
//                case None =>
//                  val msg = s"Unable to create SubmittedReturnSingle for regNumber=$regNumber"
//                  logger.error(s"[SubmittedReturnSingleDataCacheRepository] $msg")
//                  throw new RuntimeException(msg)
//              }
//
//            } else {
//              val msg = s"Empty result set for regNumber=$regNumber"
//              logger.error(s"[SubmittedReturnSingleDataCacheRepository] $msg")
//              throw new RuntimeException(msg)
//            }
//          } finally {
//            rs.close()
//          }
//        } finally {
//          cs.close()
//        }
//      }
//    }(ec)
}
