package com.att.mobile.android.vvm.screen.inbox;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.LoadingProgressBar;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.model.db.VmContentProvider;
import com.att.mobile.android.vvm.model.db.inbox.VoicemailItemBase;
import com.att.mobile.android.vvm.model.inbox.AggregatedVoicemailItemRecyclerAdapter;
import com.att.mobile.android.vvm.model.inbox.ContactItemsRecyclerAdapter;
import com.att.mobile.android.vvm.model.inbox.ListItemCursorRecyclerAdapterBase;
import com.att.mobile.android.vvm.model.inbox.VoicemailItemRecyclerAdapter;
import com.att.mobile.android.vvm.screen.AggregatedActivity;
import com.att.mobile.android.vvm.screen.VVMRecyclerView;


/**
 * Created by drosenfeld on 01/03/2016.
 */
public class InboxFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>{

    private static final String TAG = InboxFragment.class.getSimpleName();

    public static final String FILTER_TYPE  = "FILTER_TYPE";
    public static final String PHONE_NUMBER = "PHONE_NUMBER";
    public static final String BY_CONTACT = "FOR_CONTACT";

    protected int filterType;
    protected boolean groupedByContact;
    protected String phoneNumber;
	protected boolean byContact;

    protected VVMRecyclerView mRecyclerView;

    private View mLoadingContainer;
    protected LoadingProgressBar mLoadingProgressBar;
    protected TextView mEmptyTextView;

    protected ListItemCursorRecyclerAdapterBase mAdapter;

    protected LinearLayoutManager mLayoutManager;
    protected ModelManager modelManager = ModelManager.getInstance();
    protected LoaderManager mLoaderManager;

    // Identifies a particular Loader being used in this component
    private static final int URL_INBOX_LOADER = 12;
    private static final int URL_SAVE_LOADER = 13;
    private int mLoaderId;

    protected ActionMode mActionMode;
    protected ActionMode.Callback mActionModeCallback;
    protected EditModeListener mEditModeListener;
    protected ActionListener mActionListener;
    protected ListListener mEmptyListListener;


    public interface ListListener {
        public void onListUpdated ( int type, int size );
    }

    public void setListListener ( ListListener emptyListListener ) {
        mEmptyListListener = emptyListListener;
    }

    protected class EditModeListener implements ListItemCursorRecyclerAdapterBase.ActionModeListener {

        @Override
        public void onItemLongClick(VoicemailItemBase item) {
            Logger.i(TAG, "EditModeListener.onItemLongClick item=" + item==null ? "null" : item.toString());
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            mActionMode = activity.startSupportActionMode(mActionModeCallback);
            Logger.i(TAG, "Start EditMode");
        }

        @Override
        public void onEmptySelection() {
            Logger.i(TAG, "EditModeListener.onEmptySelection");
            if(mActionMode != null){
                mActionMode.finish();
                mActionMode = null;
            }
        }
    }

    public interface ActionListener {

        public void onDeleteAction( Long[] ids );
        public void onSaveAction( Long[] ids );
        public void onUnsaveAction( Long[] ids );
        public void onDeleteAction( String[] phoneNumber );
        public void onSaveAction( String[] phoneNumber );
        public void onUnsaveAction( String[] phoneNumber );
    }

    protected class EditModeCallback implements ActionMode.Callback {

        private int mMenuRes;

        EditModeCallback ( int menuRes ) {
            mMenuRes = menuRes;
        }
        private  LinearLayout titleAndButtonsLayout;
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(mMenuRes, menu);

            AppCompatActivity activity =  (AppCompatActivity)getActivity();
            mode.setTitle(activity.getString(R.string.edit_message));
            titleAndButtonsLayout = (LinearLayout) activity.findViewById(R.id.toolbar_title_and_buttons);
            titleAndButtonsLayout.setVisibility(View.INVISIBLE);
            setBackArrowInToolbar(activity, false);
            ((Toolbar)activity.findViewById(R.id.inbox_toolbar)).getMenu().clear();
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

            Logger.i(TAG, "onActionItemClicked ItemId=" + item.getItemId());
            final FragmentActivity activity = getActivity();

            if(activity == null || activity.isFinishing()){
                return true;
            }

            switch (item.getItemId()) {
                case R.id.cm_trash:
                    if ( mAdapter.selectByIds() ) {
                        mActionListener.onDeleteAction(mAdapter.getSelectedIdsItems());
                    } else {
                        mActionListener.onDeleteAction(mAdapter.getSelectedPhonesItems());
                    }
                    break;

                case R.id.cm_save:
                    if ( mAdapter.selectByIds() ) {
                        mActionListener.onSaveAction(mAdapter.getSelectedIdsItems());
                    } else {
                        mActionListener.onSaveAction(mAdapter.getSelectedPhonesItems());
                    }
                    mode.finish();
                    break;

                case R.id.cm_unsave:
                    if ( mAdapter.selectByIds() ) {
                        mActionListener.onUnsaveAction(mAdapter.getSelectedIdsItems());
                    } else {
                        mActionListener.onUnsaveAction(mAdapter.getSelectedPhonesItems());
                    }
                    mode.finish();
                    break;

                default:
                    return false;
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {

            Logger.i(TAG, "onDestroyActionMode");
            final AppCompatActivity activity = (AppCompatActivity)getActivity();

            if(activity == null || activity.isFinishing()){
                return ;
            }

            mAdapter.turnEditModeOff();

            if(titleAndButtonsLayout != null) {
                titleAndButtonsLayout.postDelayed(new Runnable() {
                   @Override
                   public void run() {
                       titleAndButtonsLayout.setVisibility(View.VISIBLE);
                       setBackArrowInToolbar(activity, activity instanceof AggregatedActivity);
                       AppCompatActivity activity = (AppCompatActivity)getActivity();
                       if ( !(activity == null || activity.isFinishing() )) {
                           MenuInflater inflater = activity.getMenuInflater();
                           inflater.inflate(R.menu.inbox_menu, ((Toolbar) activity.findViewById(R.id.inbox_toolbar)).getMenu());
                       }
                   }
               } , 300);
            }
        }
    }

    private void setBackArrowInToolbar(AppCompatActivity activity , boolean shouldShowArrow) {
        ActionBar supportActionBar = activity.getSupportActionBar();
        supportActionBar.setHomeButtonEnabled(shouldShowArrow);
        supportActionBar.setDisplayHomeAsUpEnabled(shouldShowArrow);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {

        Log.i(TAG, "setUserVisibleHint close EditMode");
        if(!isVisibleToUser && mActionMode != null){
            mActionMode.finish();
        }
        if (isVisibleToUser && mEmptyListListener != null) {
            mEmptyListListener.onListUpdated(filterType, getItemCount());
        }
        super.setUserVisibleHint(isVisibleToUser);
    }

    public void setActionListener ( ActionListener actionListener ) {
        mActionListener = actionListener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        Bundle args = getArguments();
        filterType = args.getInt(FILTER_TYPE);
        phoneNumber = args.getString(PHONE_NUMBER);
        byContact = args.getBoolean(BY_CONTACT);
        groupedByContact = byContact ? false : ModelManager.getInstance().isGroupByContact();

        mActionModeCallback = new EditModeCallback( filterType == Constants.MessageFilter.TYPE_ALL ? R.menu.inbox_toolbar_menu : R.menu.save_toolbar_menu );
        mEditModeListener = new EditModeListener();
        mLoaderManager = getLoaderManager();
        mLoaderId = getLoaderId(filterType);
        Logger.i(TAG, "onCreateView filterType=" + filterType + " groupedByContact=" + groupedByContact + " mLoaderId=" + mLoaderId);

        View rootView = inflater.inflate(R.layout.message_list_fragment, container, false);

        initRecyclerViewAndElements(rootView);

        return rootView;
    }

    protected void startLoadingProgressBar() {
        mRecyclerView.startLoadingProgressBar();
    }

    protected void stopLoadingProgressBar() {
        mRecyclerView.stopLoadingProgressBar();
    }

    private void initRecyclerViewAndElements(View rootView) {

        Logger.i(TAG, "initRecyclerViewAndElements groupedByContact=" + groupedByContact);

        mRecyclerView = (VVMRecyclerView) rootView.findViewById(R.id.inbox_recycler_view);
        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);

        initEmptyTextView(rootView);
        initLoadingUIElements(rootView);

        mRecyclerView.setLoadingOrEmptyViewElements(mLoadingContainer, mLoadingProgressBar, mEmptyTextView);
        initInboxRecyclerAdapter(groupedByContact);

        mLoaderManager.initLoader(mLoaderId, null, this);
    }

    private void initLoadingUIElements(View rootView) {
        mLoadingContainer = rootView.findViewById(R.id.message_list_fragment_loading_container);
        mLoadingProgressBar = (LoadingProgressBar) rootView.findViewById(R.id.message_list_fragment__loading_pb);
    }

    private void initEmptyTextView(View rootView) {
        mEmptyTextView = (TextView) rootView.findViewById(R.id.message_list_fragment__empty_inbox_message);
        mEmptyTextView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular)); //TODO: no specs - see if looks good (show to Anna)
    }

    //In case we have in the future other ways of constructing these adapters
    // - this method should be set as abstract and implemented in the different fragments
    protected void initInboxRecyclerAdapter( boolean newGroupedByContact ) {

        Logger.i(TAG, "initInboxRecyclerAdapter mAdapter=" + mAdapter + " groupedByContact=" + groupedByContact + " newGroupedByContact=" + newGroupedByContact);
        if ( mAdapter != null && groupedByContact == newGroupedByContact ) {
            Logger.i(TAG, "NOT initInboxRecyclerAdapters.");
            return;
        }

        Logger.i(TAG, "initInboxRecyclerAdapters.");
        if ( newGroupedByContact ) {
            mAdapter = new AggregatedVoicemailItemRecyclerAdapter(getActivity(), filterType, null);
        } else if ( byContact ) {
            mAdapter = new ContactItemsRecyclerAdapter(getActivity(), filterType, null);
        } else {
            mAdapter = new VoicemailItemRecyclerAdapter(getActivity(), filterType, null);
        }

        mAdapter.setActionModeListener(mEditModeListener);
        mRecyclerView.setAdapter(mAdapter);
        groupedByContact = newGroupedByContact;
    }

    public int getItemCount () {

        if ( mAdapter != null ) {
            return mAdapter.getItemCount();
        }
        return 0;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {

        Logger.i(TAG, "onCreateLoader loaderID=" + loaderID);
        // Takes action based on the ID of the Loader that's being created
        if (loaderID == mLoaderId ) {
            Loader<Cursor> lC = createCursorLoader();
            lC.forceLoad();
            return lC;
        } else {
            // An invalid id was passed in
            return null;
        }

    }

    @NonNull
    private Loader<Cursor> createCursorLoader() {

        Uri uri = groupedByContact ? VmContentProvider.CONTENT_GROUPED_URI : VmContentProvider.CONTENT_URI;
        String[] projection = groupedByContact ? ModelManager.AGGREGATED_VM : ModelManager.COLUMNS_VM_LIST;
        String where = phoneNumber == null ? ( filterType == Constants.MessageFilter.TYPE_SAVED ? ModelManager.WHERE_SAVED : ModelManager.WHERE_SAVED_NOT ) :
                                             ( filterType == Constants.MessageFilter.TYPE_SAVED ? ModelManager.WHERE_PHONE_NUMBER_AND_SAVED : ModelManager.WHERE_PHONE_NUMBER_AND_SAVED_NOT );
        String[] selectArgs = phoneNumber == null ? new String[]{ String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)} :
                                                    new String[]{ String.valueOf(phoneNumber), String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)};

        Logger.i(TAG, "createCursorLoader filterType=" + filterType + " where=" + where);

        return new CursorLoader(
                getActivity(),                                  // Parent activity context
                uri,                                            // Table to query
                projection,                                     // Projection to return
                where,                                          // No selection clause
                selectArgs,                                     // No selection arguments
                ModelManager.Inbox.KEY_TIME_STAMP + " desc"     // Default sort order
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {

        Logger.i(TAG, "onLoadFinished cursor=" + cursor);
        // Moves the query results into the adapter, causing the ListView fronting this adapter to re-display
        mAdapter.changeCursor(cursor);

        if ( mEmptyListListener != null ) {
            mEmptyListListener.onListUpdated(filterType, mAdapter.getItemCount());
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

        // Clears out the adapter's reference to the Cursor. This prevents memory leaks.
        mAdapter.changeCursor(null);
    }

    //public void refreshList( boolean newGroupedByContact ) {
    //    refreshList(newGroupedByContact, false);
    //}

    public void refreshList( boolean newGroupedByContact ) {

        Logger.i(TAG, "refreshList newGroupedByContact=" + newGroupedByContact );
        initInboxRecyclerAdapter(newGroupedByContact);

        mLoaderManager.restartLoader(mLoaderId, null, this);
    }

    public int getFilterType () {
        return filterType;
    }

    public void closeEditMode () {

        Logger.i(TAG, "closeEditMode");
        if ( mActionMode != null ) {
            mActionMode.finish();
        }
    }

    public static InboxFragment newInstance(int filterType, String phoneNumber, boolean byContact ) {

        Bundle args = new Bundle();
        args.putInt(FILTER_TYPE, filterType);
        args.putString(PHONE_NUMBER, phoneNumber);
        args.putBoolean(BY_CONTACT, byContact);
        InboxFragment fragment = new InboxFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private int getLoaderId (int filterType) {
        return filterType == Constants.MessageFilter.TYPE_SAVED ? URL_SAVE_LOADER : URL_INBOX_LOADER;
    }
	
}