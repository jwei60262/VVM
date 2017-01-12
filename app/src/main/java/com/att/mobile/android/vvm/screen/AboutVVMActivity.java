package com.att.mobile.android.vvm.screen;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;

import java.util.ArrayList;

public class AboutVVMActivity extends VVMActivity {
	Resources res;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.about_vvm);
		res = getResources();

		TextView title = (TextView) findViewById(R.id.header_title);
		title.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
		initActionBar(R.string.pref_about_terms_title , true);

		ListView list = (ListView) findViewById(R.id.settingsList);

		ArrayList<SettingsItem> items = initArrayItems();
		//create adapter
		SettingsAdapter settingsAdapter = new SettingsAdapter(this, items);
		list.setAdapter(settingsAdapter);
	}

    @Override
    protected void onResume() {
        super.onResume();

        if ( mustBackToSetupProcess() ) {
            return;
        }
    }

    class SettingsAdapter extends ArrayAdapter<SettingsItem> {
		public SettingsAdapter(Context context, ArrayList<SettingsItem> items) {
			super(context, 0, items);
		}
		public View getView(final int position, View convertView, ViewGroup parent) {
			SettingsItem item = getItem(position);
			if (convertView == null) {
				convertView = LayoutInflater.from(getContext()).inflate(R.layout.about_item, parent, false);
			}

			TextView tvHeader = (TextView) convertView.findViewById(R.id.itemTitle);
			TextView tvSummaryFirst = (TextView) convertView.findViewById(R.id.itemSummary);
			TextView tvSummarySecond = (TextView) convertView.findViewById(R.id.itemSummarySecondView);

			tvHeader.setText(item.getHeader());
			tvSummaryFirst.setText(Html.fromHtml(item.getSummary()));

			tvHeader.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
			tvSummaryFirst.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
			if(position == 1 ) {
				tvSummaryFirst.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
				tvSummaryFirst.setMovementMethod(LinkMovementMethod.getInstance());
				tvSummarySecond.setText(Html.fromHtml(item.getSummarySecondText()));
				tvSummarySecond.setVisibility(View.VISIBLE);
				tvSummarySecond.setMovementMethod(LinkMovementMethod.getInstance());
				tvSummarySecond.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
			}



			return convertView;
		}
	}

	private ArrayList<SettingsItem> initArrayItems() {
		ArrayList<SettingsItem> items = new ArrayList<SettingsItem>();
		items.add(new SettingsItem(res.getString(R.string.about_menu_title), res.getString(R.string.about_menu_summary) +" " + VVMApplication.getApplicationVersion(),""));
		String first_link = res.getString(R.string.terms_menu_summary_first_link);
		String second_link = res.getString(R.string.terms_menu_summary_second_link);
		items.add(new SettingsItem(res.getString(R.string.terms_menu),first_link ,second_link));
		return items;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	public class SettingsItem{
		private String header;
		private String summary;
		private String  summarySecondText;

		public SettingsItem(String headerText, String summaryText,String termsSecondTextViewText){
			header = headerText;
			summary = summaryText;
			summarySecondText = termsSecondTextViewText;
		}

		public String getHeader() {
			return header;
		}

		public String getSummary() {
			return summary;
		}

		public String getSummarySecondText() {
			return summarySecondText;
		}


	}
}