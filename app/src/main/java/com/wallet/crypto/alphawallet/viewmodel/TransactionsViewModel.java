package com.wallet.crypto.alphawallet.viewmodel;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.text.format.DateUtils;
import android.widget.Toast;

import com.wallet.crypto.alphawallet.C;
import com.wallet.crypto.alphawallet.entity.ERC875ContractTransaction;
import com.wallet.crypto.alphawallet.entity.ErrorEnvelope;
import com.wallet.crypto.alphawallet.entity.NetworkInfo;
import com.wallet.crypto.alphawallet.entity.Token;
import com.wallet.crypto.alphawallet.entity.TokenTransaction;
import com.wallet.crypto.alphawallet.entity.Transaction;
import com.wallet.crypto.alphawallet.entity.TransactionInput;
import com.wallet.crypto.alphawallet.entity.TransactionDecoder;
import com.wallet.crypto.alphawallet.entity.TransactionOperation;
import com.wallet.crypto.alphawallet.entity.Wallet;
import com.wallet.crypto.alphawallet.interact.FetchTokensInteract;
import com.wallet.crypto.alphawallet.interact.FetchTransactionsInteract;
import com.wallet.crypto.alphawallet.interact.FindDefaultNetworkInteract;
import com.wallet.crypto.alphawallet.interact.FindDefaultWalletInteract;
import com.wallet.crypto.alphawallet.interact.GetDefaultWalletBalance;
import com.wallet.crypto.alphawallet.router.ExternalBrowserRouter;
import com.wallet.crypto.alphawallet.router.HomeRouter;
import com.wallet.crypto.alphawallet.router.ManageWalletsRouter;
import com.wallet.crypto.alphawallet.router.MarketBrowseRouter;
import com.wallet.crypto.alphawallet.router.MarketplaceRouter;
import com.wallet.crypto.alphawallet.router.MyAddressRouter;
import com.wallet.crypto.alphawallet.router.MyTokensRouter;
import com.wallet.crypto.alphawallet.router.NewSettingsRouter;
import com.wallet.crypto.alphawallet.router.SendRouter;
import com.wallet.crypto.alphawallet.router.SettingsRouter;
import com.wallet.crypto.alphawallet.router.TransactionDetailRouter;
import com.wallet.crypto.alphawallet.router.WalletRouter;

import org.web3j.utils.Numeric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class TransactionsViewModel extends BaseViewModel {
    private static final long GET_BALANCE_INTERVAL = 10 * DateUtils.SECOND_IN_MILLIS;
    private static final long FETCH_TRANSACTIONS_INTERVAL = 12 * DateUtils.SECOND_IN_MILLIS;
    private final MutableLiveData<NetworkInfo> defaultNetwork = new MutableLiveData<>();
    private final MutableLiveData<Wallet> defaultWallet = new MutableLiveData<>();
    private final MutableLiveData<Transaction[]> transactions = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> defaultWalletBalance = new MutableLiveData<>();

    private final FindDefaultNetworkInteract findDefaultNetworkInteract;
    private final FindDefaultWalletInteract findDefaultWalletInteract;
    private final GetDefaultWalletBalance getDefaultWalletBalance;
    private final FetchTransactionsInteract fetchTransactionsInteract;
    private final FetchTokensInteract fetchTokensInteract;

    private final ManageWalletsRouter manageWalletsRouter;
    private final SettingsRouter settingsRouter;
    private final SendRouter sendRouter;
    private final TransactionDetailRouter transactionDetailRouter;
    private final MyAddressRouter myAddressRouter;
    private final MyTokensRouter myTokensRouter;
    private final ExternalBrowserRouter externalBrowserRouter;
    private final MarketBrowseRouter marketBrowseRouter;
    private final WalletRouter walletRouter;
    private final MarketplaceRouter marketplaceRouter;
    private final NewSettingsRouter newSettingsRouter;
    private final HomeRouter homeRouter;

    @Nullable
    private Disposable getBalanceDisposable;
    @Nullable
    private Disposable fetchTransactionDisposable;
    @Nullable
    private Disposable fetchTokensDisposable;
    private Handler handler = new Handler();

    private Map<String, Transaction> txMap = new HashMap<>();
    private int tokenTxCount;
    private boolean stopTransactionRefresh = false;

    TransactionsViewModel(
            FindDefaultNetworkInteract findDefaultNetworkInteract,
            FindDefaultWalletInteract findDefaultWalletInteract,
            FetchTransactionsInteract fetchTransactionsInteract,
            FetchTokensInteract fetchTokensInteract,
            GetDefaultWalletBalance getDefaultWalletBalance,
            ManageWalletsRouter manageWalletsRouter,
            SettingsRouter settingsRouter,
            SendRouter sendRouter,
            TransactionDetailRouter transactionDetailRouter,
            MyAddressRouter myAddressRouter,
            MyTokensRouter myTokensRouter,
            ExternalBrowserRouter externalBrowserRouter,
            MarketBrowseRouter marketBrowseRouter,
            WalletRouter walletRouter,
            MarketplaceRouter marketplaceRouter,
            NewSettingsRouter newSettingsRouter,
            HomeRouter homeRouter) {
        this.findDefaultNetworkInteract = findDefaultNetworkInteract;
        this.findDefaultWalletInteract = findDefaultWalletInteract;
        this.getDefaultWalletBalance = getDefaultWalletBalance;
        this.fetchTransactionsInteract = fetchTransactionsInteract;
        this.manageWalletsRouter = manageWalletsRouter;
        this.settingsRouter = settingsRouter;
        this.sendRouter = sendRouter;
        this.transactionDetailRouter = transactionDetailRouter;
        this.myAddressRouter = myAddressRouter;
        this.myTokensRouter = myTokensRouter;
        this.externalBrowserRouter = externalBrowserRouter;
        this.marketBrowseRouter = marketBrowseRouter;
        this.walletRouter = walletRouter;
        this.marketplaceRouter = marketplaceRouter;
        this.newSettingsRouter = newSettingsRouter;
        this.homeRouter = homeRouter;
        this.fetchTokensInteract = fetchTokensInteract;
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        handler.removeCallbacks(startFetchTransactionsTask);
        handler.removeCallbacks(startGetBalanceTask);
        if (fetchTokensDisposable != null && !fetchTokensDisposable.isDisposed())
        {
            fetchTokensDisposable.dispose();
        }
    }

    public LiveData<NetworkInfo> defaultNetwork() {
        return defaultNetwork;
    }

    public LiveData<Wallet> defaultWallet() {
        return defaultWallet;
    }

    public LiveData<Transaction[]> transactions() {
        return transactions;
    }

    public LiveData<Map<String, String>> defaultWalletBalance() {
        return defaultWalletBalance;
    }

    public void prepare() {
        progress.postValue(true);
        disposable = findDefaultNetworkInteract
                .find()
                .subscribe(this::onDefaultNetwork, this::onError);
    }

    //1. Get normal transactions
    public void fetchTransactions(boolean shouldShowProgress) {
        txMap.clear();
        handler.removeCallbacks(startFetchTransactionsTask);
        progress.postValue(shouldShowProgress);
        /*For specific address use: new Wallet("0x60f7a1cbc59470b74b1df20b133700ec381f15d3")*/
        Observable<Transaction[]> fetch = fetchTransactionsInteract.fetch(defaultWallet.getValue());
        fetchTransactionDisposable = fetch
                .subscribe(this::onTransactions, this::onError, this::enumerateTokens);
    }

    //Called from fetchTransactions
    private void onTransactions(Transaction[] transactions) {
        for (Transaction t : transactions)
        {
            if (!txMap.containsKey(t.hash)) txMap.put(t.hash, t);
        }
        //this.transactions.setValue(transactions);
        Boolean last = progress.getValue();
        if (transactions != null && transactions.length > 0 && last != null && last) {
            progress.postValue(true);
        }
    }

    //Once we have fetched all user account related transactions we need to fill in all the contract transactions
    //First get a list of tokens, on each token see if it's an ERC875, if it is then scan the contract transactions
    //for any that relate to the current user account (given by wallet address)
    private void enumerateTokens()
    {
        fetchTokensInteract
                .fetch(defaultWallet.getValue())
                .subscribe(this::onTokens, this::onError, this::onFetchTokensCompletable);
    }

    //receive a list of user tokens
    private void onTokens(Token[] tokens) {
        tokenTxCount = 0;
        //see if there's any ERC875 tokens
        for (Token t : tokens)
        {
            if (t.tokenInfo.isStormbird)
            {
                tokenTxCount++;
                //kick off a task to fetch all the contract transactions for this contract
                fetchTransactionsInteract.fetch(new Wallet(t.tokenInfo.address), t)
                        .subscribe(this::onContractTokenTransactions, this::onError, this::onTokenTransactionsFetchCompleted);
            }
        }

        if (tokenTxCount == 0)
        {
            showTransactions();
        }
    }

    private void showTransactions()
    {
        if (txMap.size() > 0)
        {
            Transaction[] txArray = txMap.values().toArray(new Transaction[txMap.size()]);// .toArray();
            this.transactions.postValue(txArray);
        }
        else
        {
            error.postValue(new ErrorEnvelope(C.ErrorCode.EMPTY_COLLECTION, "empty collection"));
        }

        progress.postValue(false);

        if (!stopTransactionRefresh) {
            handler.postDelayed(
                    startFetchTransactionsTask,
                    FETCH_TRANSACTIONS_INTERVAL);

            stopTransactionRefresh = false;
        }
    }

    //receive a list of transactions for the contract
    private void onContractTokenTransactions(TokenTransaction[] transactions)
    {
        TransactionDecoder interpreter = new TransactionDecoder();
        //List<Transaction> txList = new ArrayList<>();
        //txList.addAll(Arrays.asList(this.transactions().getValue()));
        for (TokenTransaction thisTokenTrans : transactions)
        {
            Transaction thisTrans = thisTokenTrans.transaction;
            TransactionInput data = interpreter.decodeInput(thisTrans.input);
            if (walletInvolvedInTransaction(thisTrans, data))
            {
                //now display the transaction in the list
                TransactionOperation op = new TransactionOperation();
                ERC875ContractTransaction ct = new ERC875ContractTransaction();
                op.contract = ct;

                ct.address = thisTokenTrans.token.getAddress();
                ct.setIndicies(data.paramValues);
                ct.name = thisTokenTrans.token.getFullName();
                ct.operation = data.functionData.functionName;

                TransactionOperation[] newOps = new TransactionOperation[1];
                newOps[0] = op;

                Transaction newTransaction = new Transaction(thisTrans.hash,
                        thisTrans.error,
                        thisTrans.blockNumber,
                        thisTrans.timeStamp,
                        thisTrans.nonce,
                        thisTrans.from,
                        thisTrans.to,
                        thisTrans.value,
                        thisTrans.gas,
                        thisTrans.gasPrice,
                        thisTrans.input,
                        thisTrans.gasUsed,
                        newOps);

                if (txMap.containsKey(newTransaction.hash))
                {
                    txMap.remove(newTransaction.hash);
                }

                txMap.put(newTransaction.hash, newTransaction);

                //we could ecrecover the seller here
                switch (data.functionData.functionName)
                {
                    case "trade":
                        ct.operation = "Market purchase";
                        //until we can ecrecover from a signauture, we can't show our ticket as sold, but we can conclude it sold elsewhere, so this must be a buy
                        ct.type = 1; //buy/receive
                        break;
                    case "transferFrom":
                        ct.operation = "Redeem";
                        //one of our tickets was burned
                        ct.type = -1; //redeem
                        break;
                    case "transfer":
                        //this could be transfer to or from
                        //if addresses contains our address then it must be a recieve
                        if (data.containsAddress(defaultWallet().getValue().address))
                        {
                            ct.operation = "Receive From";
                            ct.type = 1; //buy/receive
                            ct.otherParty = thisTrans.from;
                        }
                        else
                        {
                            ct.operation = "Transfer To";
                            ct.type = -1; //sell
                            ct.otherParty = data.getFirstAddress();
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public void getBalance() {
        getBalanceDisposable = getDefaultWalletBalance
                .get(defaultWallet.getValue())
                .subscribe(values -> {
                    defaultWalletBalance.postValue(values);
                    handler.removeCallbacks(startGetBalanceTask);
                    handler.postDelayed(startGetBalanceTask, GET_BALANCE_INTERVAL);
                }, t -> {
                });
    }

    private void onDefaultNetwork(NetworkInfo networkInfo) {
        defaultNetwork.postValue(networkInfo);
        disposable = findDefaultWalletInteract
                .find()
                .subscribe(this::onDefaultWallet, this::onError);
    }

    private void onDefaultWallet(Wallet wallet) {
        defaultWallet.setValue(wallet);
        getBalance();
    }

    private void onFetchTokensCompletable() {

    }

    private boolean walletInvolvedInTransaction(Transaction trans, TransactionInput data)
    {
        boolean involved = false;
        if (data.functionData == null) return false; //early return
        String walletAddr = Numeric.cleanHexPrefix(defaultWallet().getValue().address);
        if (data.containsAddress(defaultWallet().getValue().address)) involved = true;
        if (trans.from.contains(walletAddr)) involved = true;
        return involved;
    }

    private void onTokenTransactionsFetchCompleted()
    {
        tokenTxCount--;
        if (tokenTxCount == 0)
        {
            showTransactions();
        }
    }

    public void showWallets(Context context) {
        manageWalletsRouter.open(context, false);
    }

    public void showSettings(Context context) {
        settingsRouter.open(context);
    }

    public void showNewSettings(Context context, int resId) {
        newSettingsRouter.open(context, resId);
    }

    public void showSend(Context context) {
        sendRouter.open(context, defaultNetwork.getValue().symbol);
    }

    public void showDetails(Context context, Transaction transaction) {
        transactionDetailRouter.open(context, transaction);
    }

    public void showMyAddress(Context context) {
        myAddressRouter.open(context, defaultWallet.getValue());
    }

    public void showMarketplace(Context context) {
        marketBrowseRouter.open(context);
    }

    public void showTokens(Context context) {
        myTokensRouter.open(context, defaultWallet.getValue());
    }

    public void showWalletFragment(Context context, int resId) {
        walletRouter.open(context, resId);
    }

    public void showMarketplaceFragment(Context context, int resId) {
        marketplaceRouter.open(context, resId);
    }

    public void showHome(Context context) {
        homeRouter.open(context, true);
    }

    public void openDeposit(Context context, Uri uri) {
        externalBrowserRouter.open(context, uri);
    }

    private final Runnable startFetchTransactionsTask = () -> this.fetchTransactions(false);

    private final Runnable startGetBalanceTask = this::getBalance;

    public void startTransactionRefresh() {
        stopTransactionRefresh = false;
        fetchTransactions(true);
    }

    public void stopTransactionRefresh() {
        stopTransactionRefresh = true;
        handler.removeCallbacks(startFetchTransactionsTask);
        if (fetchTokensDisposable != null && !fetchTokensDisposable.isDisposed())
        {
            fetchTokensDisposable.dispose();
        }
    }
}
