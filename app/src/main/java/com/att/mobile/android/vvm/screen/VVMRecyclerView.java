
package com.att.mobile.android.vvm.screen;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.LoadingProgressBar;
import com.att.mobile.android.infra.utils.Logger;

/**
 * Created by drosenfeld on 01/03/2016.
 */

public class VVMRecyclerView extends RecyclerView {

    private View  mLoadingOrEmptyViewContainer;
    private LoadingProgressBar mLoadingProgressBar;
    private TextView mEmptyTextView;
    private static final String TAG = VVMRecyclerView.class.getSimpleName();


    public VVMRecyclerView(Context context) {
        super(context);
    }

    public VVMRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VVMRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private AdapterDataObserver mDataObserver = new AdapterDataObserver() {
        @Override
        public void onChanged() {
            Logger.i(TAG, "onChanged");
            super.onChanged();
            updateEmptyView();
        }
    };

    public void setLoadingOrEmptyViewElements(View LoadingOrEmptyViewContainer, LoadingProgressBar loadingProgressBar, TextView emptyTextView) {
        mLoadingOrEmptyViewContainer = LoadingOrEmptyViewContainer;
        mLoadingProgressBar = loadingProgressBar;
        mEmptyTextView = emptyTextView;
    }

    @Override
    public void setAdapter(RecyclerView.Adapter adapter) {
        Logger.i(TAG, "setAdapter");
        if (getAdapter() != null) {
            getAdapter().unregisterAdapterDataObserver(mDataObserver);
        }
        if (adapter != null) {
            adapter.registerAdapterDataObserver(mDataObserver);
        }
        super.setAdapter(adapter);
        updateEmptyView();
    }

    private void updateEmptyView() {

        Logger.i(TAG, "updateEmptyView");
        if ((getAdapter() != null) && !mLoadingProgressBar.isStart()) {
            boolean showEmptyView = getAdapter().getItemCount() == 0;

            Logger.i(TAG, "updateEmptyView showEmptyView = " + showEmptyView);
            if (mLoadingOrEmptyViewContainer != null) {
                mLoadingOrEmptyViewContainer.setVisibility(showEmptyView ? VISIBLE : GONE);
            }
            if (mEmptyTextView != null) {
                mEmptyTextView.setVisibility(showEmptyView ? VISIBLE : GONE);
            }
            if (mLoadingProgressBar != null) {
                mLoadingProgressBar.setVisibility(GONE);
            }
            setVisibility(showEmptyView ? GONE : VISIBLE);
        }
    }

    public void startLoadingProgressBar() {
        Logger.i(TAG, "startLoadingProgressBar");

        if (getAdapter().getItemCount() == 0) {
            Logger.i(TAG, "startLoadingProgressBar - item count = 0");
            if (mLoadingOrEmptyViewContainer != null) {
                mLoadingOrEmptyViewContainer.setVisibility(VISIBLE);
            }
            if (mEmptyTextView != null) {
                Logger.i(TAG, "startLoadingProgressBar: setting empty text view visibility to GONE");
                mEmptyTextView.setVisibility(GONE);
            }
            setVisibility(GONE);
            if (mLoadingProgressBar != null) {
                Logger.i(TAG, "startLoadingProgressBar starting progress bar");
                mLoadingProgressBar.setVisibility(VISIBLE);
                mLoadingProgressBar.start();
            }
        }
    }

    public void stopLoadingProgressBar() {

        Logger.i(TAG, "stopLoadingProgressBar");
        if (mLoadingProgressBar != null) {
            Logger.i(TAG, "stopLoadingProgressBar: stopping progress bar");
            mLoadingProgressBar.stop();
            mLoadingProgressBar.setVisibility(GONE);
        }
        updateEmptyView();
    }
}