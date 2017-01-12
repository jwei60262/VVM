package com.att.mobile.android.vvm.screen;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;

/**
 * Created by evinouze on 07/03/2016.
 */
public class PermissionsGoToSettingsFragment extends Fragment{

    public static final String TAG = "PermissionsGoToSettingsFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.goto_settings_permission_fragment, container, false);
    }

    public void onResume() {
        super.onResume();
        Logger.i(TAG, "onResume");

        ((PermissionsActivity) getActivity()).initTitleFontAndSize();
        initFont();
        setButtons();
    }

    private void setButtons() {
        Button enableButton = (Button) getActivity().findViewById(R.id.upperButton);
        enableButton.setText(R.string.enable_permissions);
        enableButton.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
        enableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Logger.d(TAG, "go to vvm permissions settings screen");
                    // Open the specific App Info page:
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    Uri selfPckageUri = Uri.parse("package:" + getActivity().getPackageName());
                    intent.setData(selfPckageUri);
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    //Open the generic Apps page:
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
            }
        });

        Button exitButton = (Button) getActivity().findViewById(R.id.lowerButton);
        exitButton.setText(R.string.exit);
        exitButton.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((PermissionsActivity) getActivity()).exitAppWithoutPermissions();
            }
        });
    }

    private void initFont() {
        ((TextView)  getActivity().findViewById(R.id.permission_fragment_title)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
        ((TextView)  getActivity().findViewById(R.id.permission_framgent_text)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
        ((TextView)  getActivity().findViewById(R.id.permission_framgent_goto_settings_text)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));

    }
}
