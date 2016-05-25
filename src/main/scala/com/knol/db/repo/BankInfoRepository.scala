package com.knol.db.repo

import com.knol.db.connection.DBComponent
import com.knol.db.connection.MySqlDBComponent
import scala.concurrent.{Await, Future}
import concurrent.duration.Duration

trait LiftedHasId {
  def id: slick.lifted.Rep[Int]
}

trait HasId {
  def id: Option[Int]
}

trait GenericAction[T <: HasId]{this: DBComponent =>
  import driver.api._

  type QueryType <: slick.lifted.TableQuery[_ <: Table[T] with LiftedHasId]

  val tableQuery: QueryType

  @inline def deleteAsync(id: Int): Future[Int] = db.run { tableQuery.filter(_.id === id).delete }
  @inline def delete(id: Int): Int = Await.result(deleteAsync(id), Duration.Inf)

  @inline def deleteAllAsync(): Future[Int] = db.run { tableQuery.delete }
  @inline def deleteAll(): Int = Await.result(deleteAllAsync(), Duration.Inf)

  @inline def getAllAsync: Future[List[T]] = db.run { tableQuery.to[List].result }
  @inline def getAll: List[T] = Await.result(getAllAsync, Duration.Inf)

  @inline def getByIdAsync(id: Int): Future[Option[T]] =
    db.run { tableQuery.filter(_.id === id).result.headOption }

  @inline def getById(id: Int): Option[T] = Await.result(getByIdAsync(id), Duration.Inf)

  @inline def deleteById(id: Option[Int]): Unit =
    db.run { tableQuery.filter(_.id === id).delete }

  @inline def findAll: Future[List[T]] = db.run { tableQuery.to[List].result }




}
trait BankInfoRepository extends BankInfoTable  with GenericAction[BankInfo] { this: DBComponent =>

  import driver.api._

  type QueryType  = TableQuery[BankInfoTable]

  val tableQuery=bankInfoTableQuery

  def create(bankInfo: BankInfo): Future[Int] = db.run { bankTableInfoAutoInc += bankInfo }

  def update(bankInfo: BankInfo): Future[Int] = db.run { bankInfoTableQuery.filter(_.id === bankInfo.id.get).update(bankInfo) }

  /**
   * Get bank and info using foreign key relationship
   */
  def getBankWithInfo(): Future[List[(Bank, BankInfo)]] =
    db.run {
      (for {
        info <- bankInfoTableQuery
        bank <- info.bank
      } yield (bank, info)).to[List].result
    }

  /**
   * Get all bank and their info.It is possible some bank do not have their product
   */
  def getAllBankWithInfo(): Future[List[(Bank, Option[BankInfo])]] =
    db.run {
      bankTableQuery.joinLeft(bankInfoTableQuery).on(_.id === _.bankId).to[List].result
    }
}

private[repo] trait BankInfoTable extends BankTable{ this: DBComponent =>

  import driver.api._

  class BankInfoTable(tag: Tag) extends Table[BankInfo](tag, "bankinfo") with LiftedHasId {
    val id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    val owner = column[String]("owner")
    val bankId = column[Int]("bank_id")
    val branches = column[Int]("branches")
    def bank = foreignKey("bank_product_fk", bankId, bankTableQuery)(_.id)
    def * = (owner, branches, bankId, id.?) <> (BankInfo.tupled, BankInfo.unapply)

  }

  protected val bankInfoTableQuery = TableQuery[BankInfoTable]

  protected def bankTableInfoAutoInc = bankInfoTableQuery returning bankInfoTableQuery.map(_.id)

}

object BankInfoRepository extends BankInfoRepository with MySqlDBComponent

case class BankInfo(owner: String, branches: Int, bankId: Int, id: Option[Int] = None) extends HasId
