package com.asfoundation.wallet.viewmodel;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.text.format.DateUtils;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.appcoins.wallet.gamification.repository.Levels;
import com.asf.wallet.BuildConfig;
import com.asfoundation.wallet.C;
import com.asfoundation.wallet.entity.Balance;
import com.asfoundation.wallet.entity.ErrorEnvelope;
import com.asfoundation.wallet.entity.NetworkInfo;
import com.asfoundation.wallet.entity.Wallet;
import com.asfoundation.wallet.interact.DefaultTokenProvider;
import com.asfoundation.wallet.interact.FetchTransactionsInteract;
import com.asfoundation.wallet.interact.FindDefaultNetworkInteract;
import com.asfoundation.wallet.interact.FindDefaultWalletInteract;
import com.asfoundation.wallet.interact.GetDefaultWalletBalance;
import com.asfoundation.wallet.repository.OffChainTransactions;
import com.asfoundation.wallet.router.AirdropRouter;
import com.asfoundation.wallet.router.ExternalBrowserRouter;
import com.asfoundation.wallet.router.ManageWalletsRouter;
import com.asfoundation.wallet.router.MyAddressRouter;
import com.asfoundation.wallet.router.MyTokensRouter;
import com.asfoundation.wallet.router.RewardsLevelRouter;
import com.asfoundation.wallet.router.SendRouter;
import com.asfoundation.wallet.router.SettingsRouter;
import com.asfoundation.wallet.router.TopUpRouter;
import com.asfoundation.wallet.router.TransactionDetailRouter;
import com.asfoundation.wallet.transactions.Transaction;
import com.asfoundation.wallet.transactions.TransactionsAnalytics;
import com.asfoundation.wallet.transactions.TransactionsMapper;
import com.asfoundation.wallet.ui.AppcoinsApps;
import com.asfoundation.wallet.ui.appcoins.applications.AppcoinsApplication;
import com.asfoundation.wallet.ui.gamification.GamificationInteractor;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import java.util.Collections;
import java.util.List;

public class TransactionsViewModel extends BaseViewModel {
  private static final long GET_BALANCE_INTERVAL = 10 * DateUtils.SECOND_IN_MILLIS;
  private static final long FETCH_TRANSACTIONS_INTERVAL = 12 * DateUtils.SECOND_IN_MILLIS;
  private final MutableLiveData<NetworkInfo> defaultNetwork = new MutableLiveData<>();
  private final MutableLiveData<Wallet> defaultWallet = new MutableLiveData<>();
  private final MutableLiveData<List<Transaction>> transactions = new MutableLiveData<>();
  private final MutableLiveData<Boolean> showAnimation = new MutableLiveData<>();
  private final MutableLiveData<List<AppcoinsApplication>> appcoinsApplications =
      new MutableLiveData<>();
  private final MutableLiveData<Balance> defaultWalletTokenBalance = new MutableLiveData<>();
  private final MutableLiveData<Balance> defaultWalletCreditBalance = new MutableLiveData<>();
  private final MutableLiveData<Double> gamificationMaxBonus = new MutableLiveData<>();
  private final MutableLiveData<Double> fetchTransactionsError = new MutableLiveData<>();
  private final FindDefaultNetworkInteract findDefaultNetworkInteract;
  private final FindDefaultWalletInteract findDefaultWalletInteract;
  private final FetchTransactionsInteract fetchTransactionsInteract;
  private final ManageWalletsRouter manageWalletsRouter;
  private final SettingsRouter settingsRouter;
  private final SendRouter sendRouter;
  private final TransactionDetailRouter transactionDetailRouter;
  private final MyAddressRouter myAddressRouter;
  private final MyTokensRouter myTokensRouter;
  private final ExternalBrowserRouter externalBrowserRouter;
  private final RewardsLevelRouter rewardsLevelRouter;
  private final CompositeDisposable disposables;
  private final DefaultTokenProvider defaultTokenProvider;
  private final GetDefaultWalletBalance getDefaultWalletBalance;
  private final TransactionsMapper transactionsMapper;
  private final AirdropRouter airdropRouter;
  private final AppcoinsApps applications;
  private final TopUpRouter topUpRouter;
  private final OffChainTransactions offChainTransactions;
  private final GamificationInteractor gamificationInteractor;
  private Handler handler = new Handler();
  private final Runnable startFetchTransactionsTask = () -> this.fetchTransactions(false);
  private final Runnable startGetTokenBalanceTask = this::getTokenBalance;
  private final Runnable startGetCreditsBalanceTask = this::getCreditsBalance;
  private final TransactionsAnalytics analytics;
  private boolean hasTransactions = false;

  TransactionsViewModel(FindDefaultNetworkInteract findDefaultNetworkInteract,
      FindDefaultWalletInteract findDefaultWalletInteract,
      FetchTransactionsInteract fetchTransactionsInteract, ManageWalletsRouter manageWalletsRouter,
      SettingsRouter settingsRouter, SendRouter sendRouter,
      TransactionDetailRouter transactionDetailRouter, MyAddressRouter myAddressRouter,
      MyTokensRouter myTokensRouter, ExternalBrowserRouter externalBrowserRouter,
      DefaultTokenProvider defaultTokenProvider, GetDefaultWalletBalance getDefaultWalletBalance,
      TransactionsMapper transactionsMapper, AirdropRouter airdropRouter, AppcoinsApps applications,
      OffChainTransactions offChainTransactions, RewardsLevelRouter rewardsLevelRouter,
      GamificationInteractor gamificationInteractor, TopUpRouter topUpRouter,
      TransactionsAnalytics analytics) {
    this.findDefaultNetworkInteract = findDefaultNetworkInteract;
    this.findDefaultWalletInteract = findDefaultWalletInteract;
    this.fetchTransactionsInteract = fetchTransactionsInteract;
    this.manageWalletsRouter = manageWalletsRouter;
    this.settingsRouter = settingsRouter;
    this.sendRouter = sendRouter;
    this.transactionDetailRouter = transactionDetailRouter;
    this.myAddressRouter = myAddressRouter;
    this.myTokensRouter = myTokensRouter;
    this.externalBrowserRouter = externalBrowserRouter;
    this.rewardsLevelRouter = rewardsLevelRouter;
    this.defaultTokenProvider = defaultTokenProvider;
    this.getDefaultWalletBalance = getDefaultWalletBalance;
    this.transactionsMapper = transactionsMapper;
    this.airdropRouter = airdropRouter;
    this.applications = applications;
    this.offChainTransactions = offChainTransactions;
    this.gamificationInteractor = gamificationInteractor;
    this.topUpRouter = topUpRouter;
    this.analytics = analytics;
    this.disposables = new CompositeDisposable();
  }

  @Override protected void onCleared() {
    super.onCleared();
    hasTransactions = false;
    if (!disposables.isDisposed()) {
      disposables.dispose();
    }
    handler.removeCallbacks(startFetchTransactionsTask);
    handler.removeCallbacks(startGetTokenBalanceTask);
    handler.removeCallbacks(startGetCreditsBalanceTask);
  }

  public LiveData<NetworkInfo> defaultNetwork() {
    return defaultNetwork;
  }

  public LiveData<Wallet> defaultWallet() {
    return defaultWallet;
  }

  public LiveData<List<Transaction>> transactions() {
    return transactions;
  }

  public MutableLiveData<Balance> defaultWalletTokenBalance() {
    return defaultWalletTokenBalance;
  }

  public MutableLiveData<Balance> defaultWalletCreditsBalance() {
    return defaultWalletCreditBalance;
  }

  public void prepare() {
    progress.postValue(true);
    disposables.add(findDefaultNetworkInteract.find()
        .subscribe(this::onDefaultNetwork, this::onError));
    disposables.add(gamificationInteractor.hasNewLevel()
        .subscribe(showAnimation::postValue, this::onError));
  }

  private Completable publishMaxBonus() {
    if (fetchTransactionsError.getValue() != null) {
      return Completable.fromAction(
          () -> fetchTransactionsError.postValue(fetchTransactionsError.getValue()));
    }
    return gamificationInteractor.getLevels()
        .subscribeOn(Schedulers.io())
        .flatMap(levels -> {
          if (levels.getStatus()
              .equals(Levels.Status.OK)) {
            return Single.just(levels.getList()
                .get(levels.getList()
                    .size() - 1)
                .getBonus());
          }
          return Single.error(new IllegalStateException(levels.getStatus()
              .name()));
        })
        .doOnSuccess(bonus -> fetchTransactionsError.postValue(bonus))
        .ignoreElement();
  }

  public void fetchTransactions(boolean shouldShowProgress) {
    handler.removeCallbacks(startFetchTransactionsTask);
    progress.postValue(shouldShowProgress);
    /*For specific address use: new Wallet("0x60f7a1cbc59470b74b1df20b133700ec381f15d3")*/
    disposables.add(Observable.merge(fetchTransactionsInteract.fetch(defaultWallet.getValue())
        .flatMapSingle(transactionsMapper::map), findDefaultNetworkInteract.find()
        .filter(this::shouldShowOffChainInfo)
        .flatMapObservable(__ -> offChainTransactions.getTransactions()
            .toObservable()))
        .observeOn(AndroidSchedulers.mainThread())
        .flatMapCompletable(
            transactions -> publishMaxBonus().observeOn(AndroidSchedulers.mainThread())
                .andThen(onTransactions(transactions)))
        .onErrorResumeNext(throwable -> publishMaxBonus())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onTransactionsFetchCompleted, this::onError));

    if (shouldShowProgress) {
      disposables.add(applications.getApps()
          .subscribeOn(Schedulers.io())
          .map(appcoinsApplications -> {
            Collections.shuffle(appcoinsApplications);
            return appcoinsApplications;
          })
          .observeOn(AndroidSchedulers.mainThread())
          .doOnSubscribe(disposable -> appcoinsApplications.postValue(Collections.emptyList()))
          .subscribe(appcoinsApplications::postValue, Throwable::printStackTrace));
    }
  }

  private boolean shouldShowOffChainInfo(NetworkInfo networkInfo) {
    return networkInfo.chainId == 3 && BuildConfig.DEBUG
        || networkInfo.chainId == 1 && !BuildConfig.DEBUG;
  }

  private void getTokenBalance() {
    disposables.add(getDefaultWalletBalance.getTokens(defaultWallet.getValue())
        .subscribe(value -> {
          defaultWalletTokenBalance.postValue(value);
          handler.removeCallbacks(startGetTokenBalanceTask);
          handler.postDelayed(startGetTokenBalanceTask, GET_BALANCE_INTERVAL);
        }, Throwable::printStackTrace));
  }

  private void getCreditsBalance() {
    disposables.add(findDefaultNetworkInteract.find()
        .filter(this::shouldShowOffChainInfo)
        .flatMapSingle(__ -> getDefaultWalletBalance.getCredits(defaultWallet.getValue()))
        .subscribe(value -> {
          defaultWalletCreditBalance.postValue(value);
          handler.removeCallbacks(startGetCreditsBalanceTask);
          handler.postDelayed(startGetCreditsBalanceTask, GET_BALANCE_INTERVAL);
        }, Throwable::printStackTrace));
  }

  private void onDefaultNetwork(NetworkInfo networkInfo) {
    defaultNetwork.postValue(networkInfo);
    disposables.add(findDefaultWalletInteract.find()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onDefaultWallet, this::onError));
  }

  private void onDefaultWallet(Wallet wallet) {
    defaultWallet.setValue(wallet);
    getTokenBalance();
    getCreditsBalance();
    fetchTransactions(true);
  }

  private Completable onTransactions(List<Transaction> transactions) {
    return Completable.fromAction(() -> {
      hasTransactions = (transactions != null && !transactions.isEmpty()) || hasTransactions;
      this.transactions.setValue(transactions);
      Boolean last = progress.getValue();
      if (transactions != null && transactions.size() > 0 && last != null && last) {
        progress.postValue(true);
      }
    });
  }

  private void onTransactionsFetchCompleted() {
    progress.postValue(false);
    if (!hasTransactions) {
      error.postValue(new ErrorEnvelope(C.ErrorCode.EMPTY_COLLECTION, "empty collection"));
    }
    handler.postDelayed(startFetchTransactionsTask, FETCH_TRANSACTIONS_INTERVAL);
  }

  public void showWallets(Context context) {
    manageWalletsRouter.open(context, false);
  }

  public void showSettings(Context context) {
    settingsRouter.open(context);
  }

  public void showSend(Context context) {
    defaultTokenProvider.getDefaultToken()
        .doOnSuccess(defaultToken -> sendRouter.open(context, defaultToken))
        .subscribe();
  }

  public void showDetails(Context context, Transaction transaction) {
    transactionDetailRouter.open(context, transaction);
  }

  public void showMyAddress(Context context) {
    myAddressRouter.open(context, defaultWallet.getValue());
  }

  public void showTokens(Context context) {
    myTokensRouter.open(context, defaultWallet.getValue());
  }

  public void pause() {
    handler.removeCallbacks(startFetchTransactionsTask);
    handler.removeCallbacks(startGetTokenBalanceTask);
    handler.removeCallbacks(startGetCreditsBalanceTask);
  }

  public void openDeposit(Context context, Uri uri) {
    externalBrowserRouter.open(context, uri);
  }

  public void showAirDrop(Context context) {
    airdropRouter.open(context);
  }

  public LiveData<List<AppcoinsApplication>> applications() {
    return appcoinsApplications;
  }

  public void onAppClick(AppcoinsApplication appcoinsApplication, Context context) {
    externalBrowserRouter.open(context,
        Uri.parse("https://" + appcoinsApplication.getUniqueName() + ".en.aptoide.com/"));
    analytics.openApp(appcoinsApplication.getUniqueName(), appcoinsApplication.getPackageName());
  }

  public void showRewardsLevel(Context context) {
    rewardsLevelRouter.open(context);
  }

  public MutableLiveData<Boolean> shouldShowGamificationAnimation() {
    return showAnimation;
  }

  public void showTopUp(Activity activity) {
    topUpRouter.open(activity);
  }

  public MutableLiveData<Double> gamificationMaxBonus() {
    return gamificationMaxBonus;
  }

  public MutableLiveData<Double> onFetchTransactionsError() {
    return fetchTransactionsError;
  }
}
