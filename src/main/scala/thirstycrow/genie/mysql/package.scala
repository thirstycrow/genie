package thirstycrow.genie

import com.twitter.finagle.exp.mysql.{Client, Transactions}

package object mysql {

  type FinagleMysqlClient = Client with Transactions
}
