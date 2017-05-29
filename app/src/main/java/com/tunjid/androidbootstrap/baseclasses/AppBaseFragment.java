package com.tunjid.androidbootstrap.baseclasses;

import android.annotation.SuppressLint;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentTransaction;

import com.tunjid.androidbootstrap.core.abstractclasses.BaseFragment;

/**
 * Bass fragment for sample app
 * <p>
 * Created by tj.dahunsi on 5/20/17.
 */

public class AppBaseFragment extends BaseFragment {

    protected void toogleToolbar(boolean show) {
        ((AppBaseActivity) getActivity()).toogleToolbar(show);
    }

    @Nullable
    @Override
    @SuppressLint("CommitTransaction")
    public FragmentTransaction provideFragmentTransaction(BaseFragment fragmentTo) {
        return getActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
