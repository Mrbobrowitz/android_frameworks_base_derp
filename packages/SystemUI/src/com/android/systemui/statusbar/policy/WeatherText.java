package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class WeatherText extends TextView {
	
    private boolean mAttached;
	private Handler mHandler;
	
    public static final String EXTRA_CITY = "city";
    public static final String EXTRA_ZIP = "zip";
    public static final String EXTRA_CONDITION = "condition";
    public static final String EXTRA_FORECAST_DATE = "forecase_date";
    public static final String EXTRA_TEMP_F = "temp_f";
    public static final String EXTRA_TEMP_C = "temp_c";
    public static final String EXTRA_HUMIDITY = "humidity";
    public static final String EXTRA_WIND = "wind";
    public static final String EXTRA_LOW = "todays_low";
    public static final String EXTRA_HIGH = "todays_high";
	
    BroadcastReceiver weatherReceiver = new BroadcastReceiver() {
	@Override
		public void onReceive(Context context, Intent intent) {
		setText(intent.getCharSequenceExtra("temp_f")+"°F " + " (" +intent.getCharSequenceExtra("temp_c") + "°C), "
				+ intent.getCharSequenceExtra(EXTRA_CONDITION));
		}
	};

	public WeatherText(Context context, AttributeSet attrs) {
		super(context, attrs);
			setText("");

	}

	public WeatherText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mHandler = new Handler();
		SettingsObserver settingsObserver = new SettingsObserver(mHandler);
		settingsObserver.observe();

		updateSettings();
	}


@Override
protected void onAttachedToWindow() {
super.onAttachedToWindow();
if (!mAttached) {
mAttached = true;
IntentFilter filter = new IntentFilter("com.android.settings.INTENT_WEATHER_UPDATE");
getContext().registerReceiver(weatherReceiver, filter, null, getHandler());

SettingsObserver so = new SettingsObserver(getHandler());
}
}

@Override
protected void onDetachedFromWindow() {
super.onDetachedFromWindow();
if (mAttached) {
getContext().unregisterReceiver(weatherReceiver);
mAttached = false;
}
}

	class SettingsObserver extends ContentObserver {
		SettingsObserver(Handler handler) {
			super(handler);
			observe();
		}
		
		void observe() {
			ContentResolver resolver = mContext.getContentResolver();
			resolver.registerContentObserver(
											 Settings.System.getUriFor(Settings.System.USE_WEATHER), false,
											 this);
			resolver.registerContentObserver(
											 Settings.System.getUriFor(Settings.System.STATUSBAR_WEATHER_COLOR), false, this);
		}
		
		@Override
		public void onChange(boolean selfChange) {
			updateSettings();
		}
	}

	private void updateSettings() {
	ContentResolver resolver = mContext.getContentResolver();
	
	int mColorChanger = Settings.System.getInt(resolver,
	Settings.System.STATUSBAR_WEATHER_COLOR, 0xFF33B5E5);

	setTextColor(mColorChanger);

	boolean useWeather = Settings.System.getInt(resolver, Settings.System.USE_WEATHER, 0) == 1;
			setVisibility(useWeather ? View.VISIBLE : View.GONE);
	}

}