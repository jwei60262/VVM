package com.att.mobile.android.vvm.screen;

import android.annotation.TargetApi;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.PermissionUtils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.db.ModelManager;

/**
 * Created by evinouze on 07/03/2016.
 */
public class InitialPermissionActivityFragment extends Fragment {

    public static final String TAG = "InitialPermissionActivityFragment";
    private Button nextButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.initial_permission_fragment, container, false);
    }

    @Override
    @TargetApi(23)
    public void onResume() {
        super.onResume();
        Logger.i(TAG, "onResume");

        initFontAndButton();
    }

    private void initFontAndButton() {
        ((PermissionsActivity) getActivity()).initTitleFontAndSize();
        ((TextView) getActivity().findViewById(R.id.txtSubDesc4)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Regular));
        nextButton = (Button) getActivity().findViewById(R.id.nextButton);
        nextButton.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Logger.d(TAG, "request permissions");
                String[] neededPermissions = PermissionUtils.getNeededPermission();
                if(neededPermissions != null && neededPermissions.length > 0){
                    requestPermissions(neededPermissions, PermissionsActivity.PERMISSIONS_REQUEST_CODE);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        ModelManager.getInstance().setSharedPreference(Constants.KEYS.PERMISSION_EVER_REQUESTED, true);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
