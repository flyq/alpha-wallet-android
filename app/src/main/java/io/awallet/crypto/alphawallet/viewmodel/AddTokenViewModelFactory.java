package io.awallet.crypto.alphawallet.viewmodel;

import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.support.annotation.NonNull;

import io.awallet.crypto.alphawallet.interact.AddTokenInteract;
import io.awallet.crypto.alphawallet.interact.FindDefaultNetworkInteract;
import io.awallet.crypto.alphawallet.interact.FindDefaultWalletInteract;
import io.awallet.crypto.alphawallet.interact.SetupTokensInteract;
import io.awallet.crypto.alphawallet.router.HomeRouter;

public class AddTokenViewModelFactory implements ViewModelProvider.Factory {

    private final AddTokenInteract addTokenInteract;
    private final FindDefaultWalletInteract findDefaultWalletInteract;
    private final HomeRouter homeRouter;
    private final SetupTokensInteract setupTokensInteract;
    private final FindDefaultNetworkInteract findDefaultNetworkInteract;

    public AddTokenViewModelFactory(
            AddTokenInteract addTokenInteract,
            FindDefaultWalletInteract findDefaultWalletInteract,
            HomeRouter homeRouter,
            SetupTokensInteract setupTokensInteract,
            FindDefaultNetworkInteract findDefaultNetworkInteract) {
        this.addTokenInteract = addTokenInteract;
        this.findDefaultWalletInteract = findDefaultWalletInteract;
        this.homeRouter = homeRouter;
        this.setupTokensInteract = setupTokensInteract;
        this.findDefaultNetworkInteract = findDefaultNetworkInteract;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new AddTokenViewModel(addTokenInteract, findDefaultWalletInteract, homeRouter, setupTokensInteract, findDefaultNetworkInteract);
    }
}