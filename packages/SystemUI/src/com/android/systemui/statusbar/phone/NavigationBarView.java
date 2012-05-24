/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.LinearLayout;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.StringBuilder;

import com.android.internal.statusbar.IStatusBarService;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.KeyButtonView;

public class NavigationBarView extends LinearLayout {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";

    final static boolean DEBUG_DEADZONE = false;

    final static boolean NAVBAR_ALWAYS_AT_RIGHT = true;

    final static boolean ANIMATE_HIDE_TRANSITION = false; // turned off because it introduces unsightly delay when videos goes to full screen
	
    protected IStatusBarService mBarService;
    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];
	
    int mBarSize;
    boolean mVertical;
	
    boolean mHidden, mLowProfile, mShowMenu, mNavStyles;
    int mDisabledFlags = 0;
	
	
	public final static int SHOW_LEFT_MENU = 1;
	public final static int SHOW_RIGHT_MENU = 0;
	public final static int SHOW_BOTH_MENU = 2;
	
	public final static int VISIBILITY_SYSTEM = 0;
	public final static int VISIBILITY_NEVER = 1;
	public final static int VISIBILITY_ALWAYS = 2;
	
	public final static int STOCK_NAV_BUTTONS = 3;
	public final static int RECENTS_FOR_SEARCH = 4;
	public final static int ADD_SEARCH_TO_NAV = 5;
	
	public final static int STOCK_STYLE = 0;
	public final static int HONEYCOMB_STYLE = 1;
	public final static int ZENYTH_STYLE = 2;
	public final static int AIRBRUSH_STYLE = 3;
	public final static int BALLOON_STYLE = 4;
	public final static int METRO_STYLE = 5;
	public final static int PLAYSTATION_STYLE = 6;
	public final static int TEXT_STYLE = 7;
	
	private int mNavButtons = STOCK_NAV_BUTTONS;
		
	public void updateButtons() {
        String saved = Settings.System.getString(mContext.getContentResolver(), Settings.System.NAV_BUTTONS);
        if (saved == null) {
            saved = "back|home|recent|search0";
        }
        boolean isPortrait = mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        int[] ids = {R.id.one,R.id.two,R.id.three,R.id.four};
        
		SettingsObserver settingsObserver = new SettingsObserver(new Handler());
		settingsObserver.observe();
		
        int cc =0;
        //Reset all paddings to invisible
        ViewGroup navPanel = ((ViewGroup) mCurrentView.findViewById(R.id.nav_buttons));
        for (int cv = 0; cv < navPanel.getChildCount(); cv++) {
            if (!(navPanel.getChildAt(cv) instanceof KeyButtonView)) {
                navPanel.getChildAt(cv).setVisibility(View.INVISIBLE);
            }
        }
        for (String buttons : saved.split("\\|") ){
            KeyButtonView cView = (KeyButtonView) mCurrentView.findViewById(ids[cc]);
            if (buttons.equals("back")){
                cView.setTag("back");
                cView.setContentDescription(mContext.getResources().getString(R.string.accessibility_back));
                cView.setMCode(KeyEvent.KEYCODE_BACK);
                if (isPortrait) {
                    cView.setImageResource(R.drawable.ic_sysbar_back);
                } else {
                    cView.setImageResource(R.drawable.ic_sysbar_back_land);
                }
            } else if (buttons.equals("home")){
                cView.setTag("home");
                cView.setContentDescription(mContext.getResources().getString(R.string.accessibility_home));
                cView.setMCode(KeyEvent.KEYCODE_HOME);
                if (isPortrait) {
                    cView.setImageResource(R.drawable.ic_sysbar_home);
                } else {
                    cView.setImageResource(R.drawable.ic_sysbar_home_land);
                }
            } else if (buttons.equals("recent")){
                cView.setTag("recent");
                cView.setContentDescription(mContext.getResources().getString(R.string.accessibility_recent));
                cView.setMCode(0);
                if (isPortrait) {
                    cView.setImageResource(R.drawable.ic_sysbar_recent);
                } else {
                    cView.setImageResource(R.drawable.ic_sysbar_recent_land);
                }
				//Hide recents button padding }
                if (mNavButtons == STOCK_NAV_BUTTONS) {
                    navPanel.getChildAt(navPanel.indexOfChild(cView) - 1).setVisibility(View.VISIBLE);
                } else if (mNavButtons == RECENTS_FOR_SEARCH) {
                    navPanel.getChildAt(navPanel.indexOfChild(cView) - 1).setVisibility(View.GONE);
                } else if (mNavButtons == ADD_SEARCH_TO_NAV) {
                    navPanel.getChildAt(navPanel.indexOfChild(cView) - 1).setVisibility(View.VISIBLE);
                }
            } else {
                cView.setTag("search");
                cView.setContentDescription(mContext.getResources().getString(R.string.accessibility_search));
                cView.setMCode(KeyEvent.KEYCODE_SEARCH);
                if (isPortrait) {
                    cView.setImageResource(R.drawable.ic_sysbar_search);
                } else {
                    cView.setImageResource(R.drawable.ic_sysbar_search_land);
                }
                //Hide search button padding
                if (mNavButtons == STOCK_NAV_BUTTONS) {
                    navPanel.getChildAt(navPanel.indexOfChild(cView) - 1).setVisibility(View.GONE);
                } else if (mNavButtons == RECENTS_FOR_SEARCH) {
                    navPanel.getChildAt(navPanel.indexOfChild(cView) - 1).setVisibility(View.VISIBLE);
                } else if (mNavButtons == ADD_SEARCH_TO_NAV) {
                    navPanel.getChildAt(navPanel.indexOfChild(cView) - 1).setVisibility(View.VISIBLE);
                }            }
            cc++;
        }
        mCurrentView.invalidate();
    }	

    public View getRecentsButton() {
        return mCurrentView.findViewWithTag("recent");
    }
	
	public View getLeftMenuButton() {
		return mCurrentView.findViewById(R.id.menu_left);
	}

    public View getRightMenuButton() {
        return mCurrentView.findViewById(R.id.menu);
    }

    public View getBackButton() {
        return mCurrentView.findViewWithTag("back");
    }

    public View getHomeButton() {
        return mCurrentView.findViewWithTag("home");
    }
	
	public View getSearchButton() {
		return mCurrentView.findViewWithTag("search");
	}
	
    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mHidden = false;
		

        mDisplay = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        final Resources res = mContext.getResources();
        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
		mNavStyles = false;
    }
	
    View.OnTouchListener mLightsOutListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                // even though setting the systemUI visibility below will turn these views
                // on, we need them to come up faster so that they can catch this motion
                // event
                setLowProfile(false, false, false);

                try {
                    mBarService.setSystemUiVisibility(0);
                } catch (android.os.RemoteException ex) {
                }
            }
            return false;
        }
    };

	public void setIconStyle(final boolean style) {
		setIconStyle(style, false);
	}	

	public void setIconStyle(final boolean style, final boolean force) {
		if (!force && mNavStyles == style) return;
			mNavStyles = style;
			boolean localStyle = style;
		
		int currentStyle = Settings.System.getInt(mContext.getContentResolver(),
						Settings.System.NAVBAR_STYLE_ICON, STOCK_STYLE);

		switch (currentStyle) {
			default:
			case STOCK_STYLE:
				((ImageView) getLeftMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
					: R.drawable.ic_sysbar_menu);
				((ImageView) getRightMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
					: R.drawable.ic_sysbar_menu);
				((ImageView) getBackButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_back_land
					: R.drawable.ic_sysbar_back);
				((ImageView) getRecentsButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_recent_land
					: R.drawable.ic_sysbar_recent);
				((ImageView) getHomeButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_home_land
					: R.drawable.ic_sysbar_home);
				((ImageView) getSearchButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_search_land
					: R.drawable.ic_sysbar_search);
			break;

			case HONEYCOMB_STYLE:
				((ImageView) getLeftMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_hc
					: R.drawable.ic_sysbar_menu_hc);
				((ImageView) getRightMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_hc
					: R.drawable.ic_sysbar_menu_hc);
				((ImageView) getBackButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_back_land_hc
					: R.drawable.ic_sysbar_back_hc);
				((ImageView) getRecentsButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_recent_land_hc
					: R.drawable.ic_sysbar_recent_hc);
				((ImageView) getHomeButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_home_land_hc
					: R.drawable.ic_sysbar_home_hc);
				((ImageView) getSearchButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_search_land_hc
					: R.drawable.ic_sysbar_search_hc);
			localStyle = true;
			break;

			case ZENYTH_STYLE:
				((ImageView) getLeftMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_zen
					: R.drawable.ic_sysbar_menu_zen);
				((ImageView) getRightMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_zen
					: R.drawable.ic_sysbar_menu_zen);
				((ImageView) getBackButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_back_land_zen
					: R.drawable.ic_sysbar_back_zen);
				((ImageView) getRecentsButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_recent_land_zen
					: R.drawable.ic_sysbar_recent_zen);
				((ImageView) getHomeButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_home_land_zen
					: R.drawable.ic_sysbar_home_zen);
				((ImageView) getSearchButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_search_land_zen
					: R.drawable.ic_sysbar_search_zen);
			localStyle = true;
			break;

			case AIRBRUSH_STYLE:
				((ImageView) getLeftMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_air
					: R.drawable.ic_sysbar_menu_air);
				((ImageView) getRightMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_air
					: R.drawable.ic_sysbar_menu_air);
				((ImageView) getBackButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_back_land_air
					: R.drawable.ic_sysbar_back_air);
				((ImageView) getRecentsButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_recent_land_air
					: R.drawable.ic_sysbar_recent_air);
				((ImageView) getHomeButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_home_land_air
					: R.drawable.ic_sysbar_home_air);
				((ImageView) getSearchButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_search_land_air
					: R.drawable.ic_sysbar_search_air);
			localStyle = true;
			break;

			case BALLOON_STYLE:
				((ImageView) getLeftMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_ball
				: R.drawable.ic_sysbar_menu_ball);
				((ImageView) getRightMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_ball
				: R.drawable.ic_sysbar_menu_ball);
				((ImageView) getBackButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_back_land_ball
				: R.drawable.ic_sysbar_back_ball);
				((ImageView) getRecentsButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_recent_land_ball
				: R.drawable.ic_sysbar_recent_ball);
				((ImageView) getHomeButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_home_land_ball
				: R.drawable.ic_sysbar_home_ball);
				((ImageView) getSearchButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_search_land_ball
				: R.drawable.ic_sysbar_search_ball);
				localStyle = true;
			break;

			case METRO_STYLE:
				((ImageView) getLeftMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_metro
				: R.drawable.ic_sysbar_menu_metro);
				((ImageView) getRightMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_metro
				: R.drawable.ic_sysbar_menu_metro);
				((ImageView) getBackButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_back_land_metro
				: R.drawable.ic_sysbar_back_metro);
				((ImageView) getRecentsButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_recent_land_metro
				: R.drawable.ic_sysbar_recent_metro);
				((ImageView) getHomeButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_home_land_metro
				: R.drawable.ic_sysbar_home_metro);
				((ImageView) getSearchButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_search_land_metro
				: R.drawable.ic_sysbar_search_metro);
			localStyle = true;
			break;

			case PLAYSTATION_STYLE:
				((ImageView) getLeftMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
				: R.drawable.ic_sysbar_menu);
				((ImageView) getRightMenuButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
				: R.drawable.ic_sysbar_menu);
				((ImageView) getBackButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_back_land_play
				: R.drawable.ic_sysbar_back_play);
				((ImageView) getRecentsButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_recent_land_play
				: R.drawable.ic_sysbar_recent_play);
				((ImageView) getHomeButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_home_land_play
				: R.drawable.ic_sysbar_home_play);
				((ImageView) getSearchButton())
				.setImageResource(mVertical ? R.drawable.ic_sysbar_search_land_play
				: R.drawable.ic_sysbar_search_play);
			localStyle = true;
			break;

		case TEXT_STYLE:
			((ImageView) getLeftMenuButton())
			.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_text
			: R.drawable.ic_sysbar_menu_text);
			((ImageView) getRightMenuButton())
			.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land_text
			: R.drawable.ic_sysbar_menu_text);
			((ImageView) getBackButton())
			.setImageResource(mVertical ? R.drawable.ic_sysbar_back_land_text
			: R.drawable.ic_sysbar_back_text);
			((ImageView) getRecentsButton())
			.setImageResource(mVertical ? R.drawable.ic_sysbar_recent_land_text
			: R.drawable.ic_sysbar_recent_text);
			((ImageView) getHomeButton())
			.setImageResource(mVertical ? R.drawable.ic_sysbar_home_land_text
			: R.drawable.ic_sysbar_home_text);
			((ImageView) getSearchButton())
			.setImageResource(mVertical ? R.drawable.ic_sysbar_search_land_text
			: R.drawable.ic_sysbar_search_text);
		localStyle = true;
		break;
		}
	}
						
    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0);

        getBackButton()   .setVisibility(disableBack       ? View.INVISIBLE : View.VISIBLE);
        getHomeButton()   .setVisibility(disableHome       ? View.INVISIBLE : View.VISIBLE);
        
		if (mNavButtons == STOCK_NAV_BUTTONS) {
			getRecentsButton().setVisibility(disableRecent     ? View.INVISIBLE : View.VISIBLE);
			getSearchButton() .setVisibility(View.GONE);

		} else if (mNavButtons == RECENTS_FOR_SEARCH) {
			getRecentsButton().setVisibility(View.GONE);
			getSearchButton() .setVisibility(disableRecent     ? View.INVISIBLE : View.VISIBLE);


		} else if (mNavButtons == ADD_SEARCH_TO_NAV) {
			getRecentsButton().setVisibility(disableRecent     ? View.INVISIBLE : View.VISIBLE);
			getSearchButton() .setVisibility(disableRecent     ? View.INVISIBLE : View.VISIBLE);
		}
	}

	public void setMenuVisibility(final boolean show) {
		setMenuVisibility(show, false);
	}

	public void setMenuVisibility(final boolean show, final boolean force) {
		if (!force && mShowMenu == show) return;
			mShowMenu = show;
			boolean localShow = show;

		int currentSetting = Settings.System.getInt(mContext.getContentResolver(),
				Settings.System.MENU_LOCATION, SHOW_RIGHT_MENU);

		int currentVisibility = Settings.System.getInt(mContext.getContentResolver(),
				Settings.System.MENU_VISIBILITY, VISIBILITY_SYSTEM);

		switch (currentVisibility) {
			default:
			case VISIBILITY_SYSTEM:
				((ImageView) getLeftMenuButton())
						.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
								: R.drawable.ic_sysbar_menu);
				((ImageView) getRightMenuButton())
						.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
								: R.drawable.ic_sysbar_menu);
				break;
			case VISIBILITY_ALWAYS:
				((ImageView) getLeftMenuButton())
						.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
								: R.drawable.ic_sysbar_menu);
				((ImageView) getRightMenuButton())
						.setImageResource(mVertical ? R.drawable.ic_sysbar_menu_land
								: R.drawable.ic_sysbar_menu);
				localShow = true;
				break;
			case VISIBILITY_NEVER:
				((ImageView) getLeftMenuButton())
						.setImageResource(R.drawable.ic_sysbar_menu_inviz);
				((ImageView) getRightMenuButton())
						.setImageResource(R.drawable.ic_sysbar_menu_inviz);
				localShow = true;
				break;
			}

			switch (currentSetting) {
				case SHOW_BOTH_MENU:
					getLeftMenuButton().setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
					getRightMenuButton().setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
					break;
				case SHOW_LEFT_MENU:
					getLeftMenuButton().setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
					getRightMenuButton().setVisibility(View.INVISIBLE);
					break;
				default:
				case SHOW_RIGHT_MENU:
					getLeftMenuButton().setVisibility(View.INVISIBLE);
					getRightMenuButton().setVisibility(localShow ? View.VISIBLE : View.INVISIBLE);
					break;
			}
	}

    public void setLowProfile(final boolean lightsOut) {
        setLowProfile(lightsOut, true, false);
    }

    public void setLowProfile(final boolean lightsOut, final boolean animate, final boolean force) {
        if (!force && lightsOut == mLowProfile) return;

        mLowProfile = lightsOut;

        if (DEBUG) Slog.d(TAG, "setting lights " + (lightsOut?"out":"on"));

        final View navButtons = mCurrentView.findViewById(R.id.nav_buttons);
        final View lowLights = mCurrentView.findViewById(R.id.lights_out);

		lowLights.findViewById(R.id.extraDot).setVisibility(mNavButtons == STOCK_NAV_BUTTONS || mNavButtons == RECENTS_FOR_SEARCH ? View.GONE : View.VISIBLE);

        // ok, everyone, stop it right there
        navButtons.animate().cancel();
        lowLights.animate().cancel();

        if (!animate) {
            navButtons.setAlpha(lightsOut ? 0f : 1f);

            lowLights.setAlpha(lightsOut ? 1f : 0f);
            lowLights.setVisibility(lightsOut ? View.VISIBLE : View.GONE);
        } else {
            navButtons.animate()
                .alpha(lightsOut ? 0f : 1f)
                .setDuration(lightsOut ? 600 : 200)
                .start();

            lowLights.setOnTouchListener(mLightsOutListener);
            if (lowLights.getVisibility() == View.GONE) {
                lowLights.setAlpha(0f);
                lowLights.setVisibility(View.VISIBLE);
            }
            lowLights.animate()
                .alpha(lightsOut ? 1f : 0f)
                .setStartDelay(lightsOut ? 500 : 0)
                .setDuration(lightsOut ? 1000 : 300)
                .setInterpolator(new AccelerateInterpolator(2.0f))
                .setListener(lightsOut ? null : new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator _a) {
                        lowLights.setVisibility(View.GONE);
                    }
                })
                .start();
        }
    }

    public void setHidden(final boolean hide) {
        if (hide == mHidden) return;

        mHidden = hide;
        Slog.d(TAG,
            (hide ? "HIDING" : "SHOWING") + " navigation bar");

        // bring up the lights no matter what
        setLowProfile(false);
    }

    public void onFinishInflate() {
        mRotatedViews[Surface.ROTATION_0] = 
        mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);

        mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);
        
        mRotatedViews[Surface.ROTATION_270] = NAVBAR_ALWAYS_AT_RIGHT
                                                ? findViewById(R.id.rot90)
                                                : findViewById(R.id.rot270);

        for (View v : mRotatedViews) {
            // this helps avoid drawing artifacts with glowing navigation keys 
            ViewGroup group = (ViewGroup) v.findViewById(R.id.nav_buttons);
            group.setMotionEventSplittingEnabled(false);
        }
        mCurrentView = mRotatedViews[Surface.ROTATION_0];
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i=0; i<4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }
        mCurrentView = mRotatedViews[rot];
        mCurrentView.setVisibility(View.VISIBLE);
        mVertical = (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270);
		
		updateButtons();
        // force the low profile & disabled states into compliance
        setLowProfile(mLowProfile, false, true /* force */);
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);
		setIconStyle(mNavStyles, true /* force */);

        if (DEBUG_DEADZONE) {
            mCurrentView.findViewById(R.id.deadzone).setBackgroundColor(0x808080FF);
        }

        if (DEBUG) {
            Slog.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }
    }

    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = mContext.getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                        + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > mDisplay.getRawWidth()
            || r.bottom > mDisplay.getRawHeight();
        pw.println("      window: " 
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                        getResourceName(mCurrentView.getId()),
                        mCurrentView.getWidth(), mCurrentView.getHeight(),
                        visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s hidden=%s low=%s menu=%s",
                        mDisabledFlags,
                        mVertical ? "true" : "false",
                        mHidden ? "true" : "false",
                        mLowProfile ? "true" : "false",
                        mShowMenu ? "true" : "false"));

        final View back = getBackButton();
        final View home = getHomeButton();
        final View recent = getRecentsButton();
        final View menu = getRightMenuButton();

        pw.println("      back: "
                + PhoneStatusBar.viewInfo(back)
                + " " + visibilityToString(back.getVisibility())
                );
        pw.println("      home: "
                + PhoneStatusBar.viewInfo(home)
                + " " + visibilityToString(home.getVisibility())
                );
        pw.println("      rcnt: "
                + PhoneStatusBar.viewInfo(recent)
                + " " + visibilityToString(recent.getVisibility())
                );
        pw.println("      menu: "
                + PhoneStatusBar.viewInfo(menu)
                + " " + visibilityToString(menu.getVisibility())
                );
        pw.println("    }");
    }
	
	class SettingsObserver extends ContentObserver {
		SettingsObserver(Handler handler) {
			super(handler);
		}
    
		void observe() {
			ContentResolver resolver = mContext.getContentResolver();
			resolver.registerContentObserver(
										 Settings.System.getUriFor(Settings.System.NAV_BUTTON_CONFIG), false,
										 this);
			updateSettings();
		}
    
		public void onChange(boolean selfChange) {
			updateSettings();
		}
	}

	protected void updateSettings() {
		ContentResolver resolver = mContext.getContentResolver();

		mNavButtons = Settings.System.getInt(resolver,
		Settings.System.NAV_BUTTON_CONFIG, STOCK_NAV_BUTTONS);

	}
}
