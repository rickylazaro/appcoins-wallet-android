package com.asfoundation.wallet.repository

import com.asfoundation.wallet.transactions.Transaction
import com.asfoundation.wallet.transactions.TransactionsMapper
import io.reactivex.Scheduler
import io.reactivex.Single
import retrofit2.HttpException

class OffChainTransactions(private val repository: OffChainTransactionsRepository,
                           private val mapper: TransactionsMapper,
                           private val versionCode: String, private val scheduler: Scheduler) {

  fun getTransactions(wallet: String, offChainOnly: Boolean): Single<List<Transaction>> {
    return repository.getTransactions(wallet, versionCode, offChainOnly)
        .subscribeOn(scheduler)
        .flatMap { channelHistoryResponse ->
          mapper.mapFromWalletHistory(
              channelHistoryResponse.result)
        }
  }

  fun getTransactions(wallet: String, startingDate: String? = null,
                      endingDate: String? = null, offset: Int): List<Transaction> {
    val transactions =
        repository.getTransactionsSync(wallet, versionCode, startingDate, endingDate, offset)
            .execute()

    if (transactions.isSuccessful) {
      return mapper.mapTransactionsFromWalletHistory(
          transactions.body()?.result)
    }
    throw HttpException(transactions)
  }
}
