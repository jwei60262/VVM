package com.att.mobile.android.vvm.screen.inbox;


import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.screen.VVMFragmentPagerAdapter;

import java.util.List;

/**
 * Created by drosenfeld on 01/03/2016.
 */
public class InboxFragmentPagerAdapter extends VVMFragmentPagerAdapter {

    private static final String TAG = InboxFragmentPagerAdapter.class.getSimpleName();

    // Tab indexes and total number constants
    // In case of a need to add more tabs, also update sLastInboxTabIndex accordingly
    public static final int sAllInboxItemsTabIndex = 0;
    public static final int sSavedInboxItemsTabIndex = 1;
    private static final int sLastInboxTabIndex = sSavedInboxItemsTabIndex;
    private static final int sTotalTabsNum = sLastInboxTabIndex + 1;

    private InboxFragment.ActionListener mActionListener;
    private InboxFragment.ListListener mListListener;

    public InboxFragmentPagerAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        InboxFragment fragment = null;

        switch (position) {
            case sAllInboxItemsTabIndex:
                //Note: we can also save the name
                fragment = InboxFragment.newInstance(Constants.MessageFilter.TYPE_ALL, null, false);
                break;
            case sSavedInboxItemsTabIndex:
                //Note: we can also save the name
                fragment = InboxFragment.newInstance(Constants.MessageFilter.TYPE_SAVED, null, false);
                break;
        }

        fragment.setActionListener(mActionListener);
        fragment.setListListener(mListListener);
        return fragment;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Object itemObj = super.instantiateItem(container, position);
        InboxFragment inboxFragmentObj = (InboxFragment)itemObj;
        inboxFragmentObj.setActionListener(mActionListener);
        inboxFragmentObj.setListListener(mListListener);
        return itemObj;
    }

    @Override
    public int getCount() {
        return sTotalTabsNum;
    }

    @Override
    public CharSequence getPageTitle(int position) {

        Context context = VVMApplication.getContext();
        CharSequence Res = null;
        switch (position) {
            case sAllInboxItemsTabIndex:
                Res = context.getString(R.string.inbox_tab_title);
                break;
            case sSavedInboxItemsTabIndex:
                Res = context.getString(R.string.saved_tab_title);
                break;
        }
        return Res;
    }

    public CharSequence getContentDescription(int position) {
        //setContentDescription(R.string.tab_descr_inbox);
        Context context = VVMApplication.getContext();
        CharSequence Res = null;
        switch (position) {
            case sAllInboxItemsTabIndex:
                Res = context.getString(R.string.inbox_tab_content_description);
                break;
            case sSavedInboxItemsTabIndex:
                Res = context.getString(R.string.saved_tab_content_description);
                break;
        }
        return Res;
    }
    

    public void startGauge() {

        Logger.i(TAG, "startGauge");

        List<InboxFragment> fragmentList = getFragments();
        for (int i = 0; i < fragmentList.size(); i++) {

            InboxFragment fragment = fragmentList.get(i);
            if (fragment != null) {
                fragment.startLoadingProgressBar();
            }
        }
    }

    public void stopGauge() {

        Logger.i(TAG, "startGauge");

        List<InboxFragment> fragmentList = getFragments();
        for (int i = 0; i < fragmentList.size(); i++) {

            InboxFragment fragment = fragmentList.get(i);
            if (fragment != null) {
                fragment.stopLoadingProgressBar();
            }
        }
    }

    public void refreshList( boolean groupedByContact ) {

        Logger.i(TAG, "refreshList groupedByContact=" + groupedByContact);

        List<InboxFragment> fragmentList = getFragments();
        Logger.i(TAG, "refreshList fragmentList size=" + fragmentList.size());
        for (int i = 0; i < fragmentList.size(); i++) {
            InboxFragment fragment = fragmentList.get(i);
            if (fragment != null) {
                fragment.refreshList(groupedByContact);
            }
        }
    }

    public void setActionListener ( InboxFragment.ActionListener actionListener ) {

        Logger.i(TAG, "setActionListener");
        mActionListener = actionListener;

    }

    public void setListListener ( InboxFragment.ListListener listListener ) {

        mListListener = listListener;
    }
}
