package com.att.mobile.android.vvm.screen;

import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.Fragment;
import android.view.ViewGroup;

import com.att.mobile.android.vvm.screen.inbox.InboxFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by drosenfeld on 13/03/2016.
 */
public abstract class VVMFragmentPagerAdapter extends FragmentPagerAdapter {

    private final ArrayList<InboxFragment> mFragments;
    private Fragment mPrimaryFragment;

    public VVMFragmentPagerAdapter(FragmentManager fm) {
        super(fm);
        mFragments = new ArrayList<>(getCount());
    }

    @Override public Object instantiateItem(ViewGroup container, int position) {
        Object object = super.instantiateItem(container, position);
        mFragments.add((InboxFragment) object);
        return object;
    }

    @Override public void destroyItem(ViewGroup container, int position, Object object) {
        mFragments.remove(object);
        super.destroyItem(container, position, object);
    }

    @Override public void setPrimaryItem(ViewGroup container, int position, Object object) {
        super.setPrimaryItem(container, position, object);
        mPrimaryFragment = (Fragment) object;
    }

    /** Returns currently visible (primary) fragment */
    public Fragment getPrimaryFragment() {
        return mPrimaryFragment;
    }

    /** Returned list can contain null-values for not created fragments */
    public List<InboxFragment> getFragments() {
        return Collections.unmodifiableList(mFragments);
    }

    public InboxFragment getFragment ( int position ) {
        if ( mFragments != null && position < mFragments.size()) {
            return mFragments.get(position);
        }
        return null;
    }
}
