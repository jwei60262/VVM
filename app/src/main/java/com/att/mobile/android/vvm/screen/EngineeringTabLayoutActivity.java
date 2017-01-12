package com.att.mobile.android.vvm.screen;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

import com.att.mobile.android.vvm.R;


public class EngineeringTabLayoutActivity extends TabActivity {
	    /** Called when the activity is first created. */
	    @Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
		    requestWindowFeature(Window.FEATURE_NO_TITLE); 
	        setContentView(R.layout.engineering_tab_layout);
	 
	        TabHost tabHost = getTabHost();
	 
	        // Tab for engineering screen
	        TabSpec engineeringScreenSpec = tabHost.newTabSpec("Engineering");
	        // setting Title and Icon for the Tab
	        engineeringScreenSpec.setIndicator("Engineering", getResources().getDrawable(R.drawable.ic_menu_settings));
	        Intent engineeringScreenIntent = new Intent(this, EngineeringScreen.class);
	        engineeringScreenSpec.setContent(engineeringScreenIntent);
	 
	 
	        // Tab for alu settings
	        TabSpec aluSpec = tabHost.newTabSpec("ALU");
	        aluSpec.setIndicator("ALU", getResources().getDrawable(R.drawable.ic_menu_settings));
	        Intent aluIntent = new Intent(this, DebugSettingsScreen.class);
	        aluSpec.setContent(aluIntent);
	        
	 
	        // Adding all TabSpec to TabHost
	        tabHost.addTab(engineeringScreenSpec); 
	        tabHost.addTab(aluSpec);
	    }
}
