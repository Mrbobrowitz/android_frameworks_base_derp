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

package com.android.internal.policy.impl;

import com.android.internal.R;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.RingSelector;
import com.android.internal.widget.RotarySelector;
import com.android.internal.widget.SlidingTab;
import com.android.internal.widget.WaveView;
import com.android.internal.widget.multiwaveview.MultiWaveView;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.Toast;
import android.util.Log;
import android.media.AudioManager;
import android.provider.MediaStore;
import android.provider.Settings;

import java.io.File;
import java.net.URISyntaxException;

/**
 * The screen within {@link LockPatternKeyguardView} that shows general
 * information about the device depending on its state, and how to get
 * past it, as applicable.
 */
class LockScreen extends LinearLayout implements KeyguardScreen {

    private static final int ON_RESUME_PING_DELAY = 500; // delay first ping until the screen is on
    private static final boolean DBG = false;
    private static final String TAG = "LockScreen";
    private static final String ENABLE_MENU_KEY_FILE = "/data/local/enable_menu_key";
    private static final int WAIT_FOR_ANIMATION_TIMEOUT = 0;
    private static final int STAY_ON_WHILE_GRABBED_TIMEOUT = 30000;

    private LockPatternUtils mLockPatternUtils;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private KeyguardScreenCallback mCallback;

    // current configuration state of keyboard and display
    private int mKeyboardHidden;
    private int mCreationOrientation;

    private boolean mSilentMode;
    private AudioManager mAudioManager;
    private boolean mEnableMenuKeyInLockScreen;
    private KeyguardStatusViewManager mStatusViewManager;
    private UnlockWidgetCommonMethods mUnlockWidgetMethods;
    private View mUnlockWidget;
	
	// lockscreen toggles
	private boolean mForceSoundIcon = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.LOCKSCREEN_FORCE_SOUND_ICON, 0) == 1);
	private boolean mLockscreenCustom = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.LOCKSCREEN_EXTRA_ICONS, 0) == 1);
    private boolean mUseSlider = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.LOCKSCREEN_TYPE, 0) == 1);
    private boolean mUseRotary = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.LOCKSCREEN_TYPE, 0) == 2);
    private boolean mUseRings = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.LOCKSCREEN_TYPE, 0) == 3);
	private boolean mUseHoneyComb = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.LOCKSCREEN_TYPE, 0) == 4);
	
	
	// custom apps made easy!
    private String mCustomOne = (Settings.System.getString(mContext.getContentResolver(), Settings.System.LOCKSCREEN_CUSTOM_ONE));
	// hide rotary arrows
    private boolean mHideArrows = (Settings.System.getInt(mContext.getContentResolver(), Settings.System.LOCKSCREEN_HIDE_ARROWS, 0) == 1);
	
    //omg ring lock?!
    private String[] mCustomRingAppActivities = new String[] {
	Settings.System.getString(mContext.getContentResolver(),
							  Settings.System.LOCKSCREEN_CUSTOM_RING_APP_ACTIVITIES[0]),		    
	Settings.System.getString(mContext.getContentResolver(),
							  Settings.System.LOCKSCREEN_CUSTOM_RING_APP_ACTIVITIES[1]),	
	Settings.System.getString(mContext.getContentResolver(),
							  Settings.System.LOCKSCREEN_CUSTOM_RING_APP_ACTIVITIES[2]),
	Settings.System.getString(mContext.getContentResolver(),
							  Settings.System.LOCKSCREEN_CUSTOM_RING_APP_ACTIVITIES[3])
	};
	
    private Bitmap mCustomAppIcon;
    private String mCustomAppName;
    private Bitmap[] mCustomRingAppIcons = new Bitmap[4];

    private interface UnlockWidgetCommonMethods {
        // Update resources based on phone state
        public void updateResources();

        // Get the view associated with this widget
        public View getView();

        // Reset the view
        public void reset(boolean animate);

        // Animate the widget if it supports ping()
        public void ping();
    }

    class SlidingTabMethods implements SlidingTab.OnTriggerListener, UnlockWidgetCommonMethods {
        private final SlidingTab mSlidingTab;

        SlidingTabMethods(SlidingTab slidingTab) {
            mSlidingTab = slidingTab;
        }

        public void updateResources() {
            boolean vibe = mSilentMode
                && (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);

            mSlidingTab.setRightTabResources(
                    mSilentMode ? ( vibe ? R.drawable.ic_jog_dial_vibrate_on
                                         : R.drawable.ic_jog_dial_sound_off )
                                : R.drawable.ic_jog_dial_sound_on,
                    mSilentMode ? R.drawable.jog_tab_target_yellow
                                : R.drawable.jog_tab_target_gray,
                    mSilentMode ? R.drawable.jog_tab_bar_right_sound_on
                                : R.drawable.jog_tab_bar_right_sound_off,
                    mSilentMode ? R.drawable.jog_tab_right_sound_on
                                : R.drawable.jog_tab_right_sound_off);
        }

        /** {@inheritDoc} */
        public void onTrigger(View v, int whichHandle) {
            if (whichHandle == SlidingTab.OnTriggerListener.LEFT_HANDLE) {
                mCallback.goToUnlockScreen();
            } else if (whichHandle == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
                toggleRingMode();
				mUnlockWidgetMethods.updateResources();
                mCallback.pokeWakelock();
            }
        }

        /** {@inheritDoc} */
        public void onGrabbedStateChange(View v, int grabbedState) {
            if (grabbedState == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
                mSilentMode = isSilentMode();
                mSlidingTab.setRightHintText(mSilentMode ? R.string.lockscreen_sound_on_label
                        : R.string.lockscreen_sound_off_label);
            }
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (grabbedState != SlidingTab.OnTriggerListener.NO_HANDLE) {
                mCallback.pokeWakelock();
            }
        }

        public View getView() {
            return mSlidingTab;
        }

        public void reset(boolean animate) {
            mSlidingTab.reset(animate);
        }

        public void ping() {
        }
    }

	class RotarySelectorMethods implements RotarySelector.OnDialTriggerListener, UnlockWidgetCommonMethods {
        private final RotarySelector mRotarySelector;
		
        RotarySelectorMethods(RotarySelector rotarySelector) {
            mRotarySelector = rotarySelector;
        }
		
        public void updateResources() {
            boolean vibe = mSilentMode
			&& (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);
			
            mRotarySelector.setRightHandleResource(mSilentMode ? (vibe ? R.drawable.ic_jog_dial_vibrate_on
																  : R.drawable.ic_jog_dial_sound_off) : R.drawable.ic_jog_dial_sound_on);
        }
		
        /** {@inheritDoc} */
        public void onDialTrigger(View v, int whichHandle) {
            if (whichHandle == RotarySelector.OnDialTriggerListener.LEFT_HANDLE) {
                mCallback.goToUnlockScreen();
            } else if (whichHandle == RotarySelector.OnDialTriggerListener.RIGHT_HANDLE) {
                toggleRingMode();
                mUnlockWidgetMethods.updateResources();
                mCallback.pokeWakelock();
            }
        }
		
        /** {@inheritDoc} */
        public void onGrabbedStateChange(View v, int grabbedState) {
            if (grabbedState == RotarySelector.OnDialTriggerListener.RIGHT_HANDLE) {
                mSilentMode = isSilentMode();
            }
        }
		
        public View getView() {
            return mRotarySelector;
        }
		
        public void reset(boolean animate) {
            mRotarySelector.reset();
        }
		
        public void ping() {
        }
    }
	
    class WaveViewMethods implements WaveView.OnTriggerListener, UnlockWidgetCommonMethods {

        private final WaveView mWaveView;

        WaveViewMethods(WaveView waveView) {
            mWaveView = waveView;
        }
        /** {@inheritDoc} */
        public void onTrigger(View v, int whichHandle) {
            if (whichHandle == WaveView.OnTriggerListener.CENTER_HANDLE) {
                requestUnlockScreen();
            }
        }

        /** {@inheritDoc} */
        public void onGrabbedStateChange(View v, int grabbedState) {
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (grabbedState == WaveView.OnTriggerListener.CENTER_HANDLE) {
                mCallback.pokeWakelock(STAY_ON_WHILE_GRABBED_TIMEOUT);
            }
        }

        public void updateResources() {
        }

        public View getView() {
            return mWaveView;
        }
        public void reset(boolean animate) {
            mWaveView.reset();
        }
        public void ping() {
        }
    }

    class MultiWaveViewMethods implements MultiWaveView.OnTriggerListener,
            UnlockWidgetCommonMethods {

        private final MultiWaveView mMultiWaveView;
        private boolean mCameraDisabled;

        MultiWaveViewMethods(MultiWaveView multiWaveView) {
            mMultiWaveView = multiWaveView;
            final boolean cameraDisabled = mLockPatternUtils.getDevicePolicyManager()
                    .getCameraDisabled(null);
            if (cameraDisabled || mForceSoundIcon) {
                Log.v(TAG, "Camera disabled by Device Policy");
                mCameraDisabled = true;
            } else {
                // Camera is enabled if resource is initially defined for MultiWaveView
                // in the lockscreen layout file
                mCameraDisabled = mMultiWaveView.getTargetResourceId()
                        != R.array.lockscreen_targets_with_camera;
            }
        }

        public void updateResources() {
            int resId;
			if (mCameraDisabled) {
				 resId = R.array.zzlockscreen_extra_apps;
			}else if (mCameraDisabled) {
				// Fall back to showing ring/silence if camera is disabled by DPM...
				if (mLockscreenCustom) {
					 resId = mSilentMode ? R.array.zzlockscreen_when_silent
					: R.array.zzlockscreen_extra_apps_soundon;
					} else {
						
                resId = mSilentMode ? R.array.lockscreen_targets_when_silent
                    : R.array.lockscreen_targets_when_soundon;
						}
				} else if (!mCameraDisabled && mLockscreenCustom) {
					resId = R.array.zzlockscreen_extra_apps;
            } else {
                resId = R.array.lockscreen_targets_with_camera;
            }
            mMultiWaveView.setTargetResources(resId);
        }

        public void onGrabbed(View v, int handle) {

        }

        public void onReleased(View v, int handle) {

        }

        public void onTrigger(View v, int target) {
			if (mLockscreenCustom){
			if (target == 0) { // 0 = unlock on the right
				mCallback.goToUnlockScreen();
			} else if (target == 1) { // 1 = Custom App, default mms
				String isCustom = Settings.System.getString(mContext.getContentResolver(), Settings.System.LOCKSCREEN_CUSTOM_ONE);
				if (isCustom == null) {
					Intent customMms = new Intent (Intent.ACTION_MAIN);
					customMms.setClassName("com.android.mms", "com.android.mms.ui.ConversationList");
					customMms.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					mContext.startActivity(customMms);
					mCallback.goToUnlockScreen();
				} else {
					Intent customOne;
					try {
						customOne = Intent.parseUri(isCustom, 0);
						customOne.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						mContext.startActivity(customOne);
						mCallback.goToUnlockScreen();
					} catch (URISyntaxException e) {
					}
				}              
			} else if (target == 2) { // 2 = Custom App, default to phone
				String isCustom = Settings.System.getString(mContext.getContentResolver(), Settings.System.LOCKSCREEN_CUSTOM_TWO);
				if (isCustom == null){
					Intent customPhone = new Intent (Intent.ACTION_MAIN);
					customPhone.setClassName("com.android.contacts", "com.android.contacts.activities.DialtactsActivity");
					customPhone.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					mContext.startActivity(customPhone);
					mCallback.goToUnlockScreen();
				} else {
					Intent customTwo;
					try {
						customTwo = Intent.parseUri(isCustom, 0);
						customTwo.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						mContext.startActivity(customTwo);
						mCallback.goToUnlockScreen();
					} catch (URISyntaxException e) {
					}
				} 
			}else if (target == 3) {
				if (!mCameraDisabled) {
					// Start the Camera
					Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					mContext.startActivity(intent);
					mCallback.goToUnlockScreen();
				} else {
					toggleRingMode();
					mUnlockWidgetMethods.updateResources();
					mCallback.pokeWakelock();
				}
			}
		} else {
			if (target == 0 || target == 1) { // 0 = unlock/portrait, 1 = unlock/landscape
				mCallback.goToUnlockScreen();
			} else if (target == 2 || target == 3) { // 2 = alt/portrait, 3 = alt/landscape
				if (!mCameraDisabled) {
					// Start the Camera
					Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					mContext.startActivity(intent);
					mCallback.goToUnlockScreen();
				} else {
					toggleRingMode();
					mUnlockWidgetMethods.updateResources();
					mCallback.pokeWakelock();
				}
            }
        }
    }

        public void onGrabbedStateChange(View v, int handle) {
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (handle != MultiWaveView.OnTriggerListener.NO_HANDLE) {
                mCallback.pokeWakelock();
            }
        }

        public View getView() {
            return mMultiWaveView;
        }

        public void reset(boolean animate) {
            mMultiWaveView.reset(animate);
        }

        public void ping() {
            mMultiWaveView.ping();
        }
    }

	class RingSelectorMethods implements RingSelector.OnRingTriggerListener, UnlockWidgetCommonMethods {
        private final RingSelector mRingSelector;
		
        RingSelectorMethods(RingSelector ringSelector) {
            mRingSelector = ringSelector;
        }
		
        public void updateResources() {
            boolean vibe = mSilentMode
			&& (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);
			
            mRingSelector.setRightRingResources(
												mSilentMode ? ( vibe ? R.drawable.ic_jog_dial_vibrate_on
															   : R.drawable.ic_jog_dial_sound_off )
												: R.drawable.ic_jog_dial_sound_on,
												mSilentMode ? R.drawable.jog_tab_target_yellow
												: R.drawable.jog_tab_target_gray,
												mSilentMode ? R.drawable.jog_ring_ring_yellow
												: R.drawable.jog_ring_ring_gray);
        }
		
        /** {@inheritDoc} */
        public void onRingTrigger(View v, int whichRing, int whichApp) {
            if (whichRing == RingSelector.OnRingTriggerListener.LEFT_RING) {
                mCallback.goToUnlockScreen();
            } else if (whichRing == RingSelector.OnRingTriggerListener.RIGHT_RING) {
                toggleRingMode();
                mUnlockWidgetMethods.updateResources();
                mCallback.pokeWakelock();
            } else if (whichRing == RingSelector.OnRingTriggerListener.MIDDLE_RING) {
                if (mCustomRingAppActivities[whichApp] != null) {
                    runActivity(mCustomRingAppActivities[whichApp]);
                }
            }
        }
		
        /** {@inheritDoc} */
        public void onGrabbedStateChange(View v, int grabbedState) {
            if (grabbedState == RingSelector.OnRingTriggerListener.RIGHT_RING) {
                mSilentMode = isSilentMode();
            }
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (grabbedState != RingSelector.OnRingTriggerListener.NO_RING) {
                mCallback.pokeWakelock();
            }
        }
		
        public View getView() {
            return mRingSelector;
        }
		
        public void reset(boolean animate) {
            mRingSelector.reset(animate);
        }
		
        public void ping() {
        }
    }
	
    private void requestUnlockScreen() {
        // Delay hiding lock screen long enough for animation to finish
        postDelayed(new Runnable() {
            public void run() {
                mCallback.goToUnlockScreen();
            }
        }, WAIT_FOR_ANIMATION_TIMEOUT);
    }

    private void toggleRingMode() {
        // toggle silent mode
        mSilentMode = !mSilentMode;
        if (mSilentMode) {
            final boolean vibe = (Settings.System.getInt(
                mContext.getContentResolver(),
                Settings.System.VIBRATE_IN_SILENT, 1) == 1);

            mAudioManager.setRingerMode(vibe
                ? AudioManager.RINGER_MODE_VIBRATE
                : AudioManager.RINGER_MODE_SILENT);
			//add a Toastbox to popup for sound on/off
			Toast.makeText(mContext, R.string.zzlockscreen_sound_off, Toast.LENGTH_SHORT).show();
        } else {
			//add a Toastbox to popup for sound on/off
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
			Toast.makeText(mContext, R.string.zzlockscreen_sound_on, Toast.LENGTH_SHORT).show();
        }
    }
	
	private void runActivity(String uri) {
        try {
            Intent i = Intent.parseUri(uri, 0);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					   | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            mContext.startActivity(i);
            mCallback.goToUnlockScreen();
        } catch (URISyntaxException e) {
        } catch (ActivityNotFoundException e) {
        }
    }
	

    /**
     * In general, we enable unlocking the insecure key guard with the menu key. However, there are
     * some cases where we wish to disable it, notably when the menu button placement or technology
     * is prone to false positives.
     *
     * @return true if the menu key should be enabled
     */
    private boolean shouldEnableMenuKey() {
        final Resources res = getResources();
        final boolean configDisabled = res.getBoolean(R.bool.config_disableMenuKeyInLockScreen);
        final boolean isTestHarness = ActivityManager.isRunningInTestHarness();
        final boolean fileOverride = (new File(ENABLE_MENU_KEY_FILE)).exists();
        return !configDisabled || isTestHarness || fileOverride;
    }

    /**
     * @param context Used to setup the view.
     * @param configuration The current configuration. Used to use when selecting layout, etc.
     * @param lockPatternUtils Used to know the state of the lock pattern settings.
     * @param updateMonitor Used to register for updates on various keyguard related
     *    state, and query the initial state at setup.
     * @param callback Used to communicate back to the host keyguard view.
     */
    LockScreen(Context context, Configuration configuration, LockPatternUtils lockPatternUtils,
            KeyguardUpdateMonitor updateMonitor,
            KeyguardScreenCallback callback) {
        super(context);
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;

        mEnableMenuKeyInLockScreen = shouldEnableMenuKey();

        mCreationOrientation = configuration.orientation;

        mKeyboardHidden = configuration.hardKeyboardHidden;

        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** CREATING LOCK SCREEN", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + " res orient=" + context.getResources().getConfiguration().orientation);
        }

        final LayoutInflater inflater = LayoutInflater.from(context);
        if (DBG) Log.v(TAG, "Creation orientation = " + mCreationOrientation);
		if (mCreationOrientation != Configuration.ORIENTATION_LANDSCAPE) {
            if (mUseSlider)
                inflater.inflate(R.layout.keyguard_screen_slider_unlock, this, true);
			else if (mUseRotary)
                inflater.inflate(R.layout.keyguard_screen_rotary_unlock, this, true);
			else if (mUseRings)
                inflater.inflate(R.layout.keyguard_screen_ring_unlock, this, true);
			else if (mUseHoneyComb)
				inflater.inflate(R.layout.keyguard_screen_honeycomb_unlock, this, true);
			else
                inflater.inflate(R.layout.keyguard_screen_tab_unlock, this, true);
			
        } else {
            if (mUseSlider)
                inflater.inflate(R.layout.keyguard_screen_slider_unlock_land, this, true);
            else if (mUseRotary)
                inflater.inflate(R.layout.keyguard_screen_rotary_unlock_land, this, true);		    
			else if (mUseRings)
                inflater.inflate(R.layout.keyguard_screen_ring_unlock_land, this, true);
			else if (mUseHoneyComb)
				inflater.inflate(R.layout.keyguard_screen_honeycomb_unlock, this, true);
			else
				inflater.inflate(R.layout.keyguard_screen_tab_unlock_land, this, true);
        }

        mStatusViewManager = new KeyguardStatusViewManager(this, mUpdateMonitor, mLockPatternUtils,
                mCallback, false);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mSilentMode = isSilentMode();

        mUnlockWidget = findViewById(R.id.unlock_widget);
        if (mUnlockWidget instanceof SlidingTab) {
            SlidingTab slidingTabView = (SlidingTab) mUnlockWidget;
            slidingTabView.setHoldAfterTrigger(true, false);
            slidingTabView.setLeftHintText(R.string.lockscreen_unlock_label);
            slidingTabView.setLeftTabResources(
                    R.drawable.ic_jog_dial_unlock,
                    R.drawable.jog_tab_target_green,
                    R.drawable.jog_tab_bar_left_unlock,
                    R.drawable.jog_tab_left_unlock);
            SlidingTabMethods slidingTabMethods = new SlidingTabMethods(slidingTabView);
            slidingTabView.setOnTriggerListener(slidingTabMethods);
            mUnlockWidgetMethods = slidingTabMethods;
		} else if (mUnlockWidget instanceof RotarySelector) {
			RotarySelector rotarySelectorView = (RotarySelector) mUnlockWidget;
			rotarySelectorView.setLeftHandleResource(
				R.drawable.ic_jog_dial_unlock);	 
			if (mHideArrows)
				rotarySelectorView.hideArrows(true);
			RotarySelectorMethods rotarySelectorMethods = new RotarySelectorMethods(rotarySelectorView);
			rotarySelectorView.setOnDialTriggerListener(rotarySelectorMethods);
			mUnlockWidgetMethods = rotarySelectorMethods;
        } else if (mUnlockWidget instanceof WaveView) {
            WaveView waveView = (WaveView) mUnlockWidget;
            WaveViewMethods waveViewMethods = new WaveViewMethods(waveView);
            waveView.setOnTriggerListener(waveViewMethods);
            mUnlockWidgetMethods = waveViewMethods;
        } else if (mUnlockWidget instanceof MultiWaveView) {
            MultiWaveView multiWaveView = (MultiWaveView) mUnlockWidget;
            MultiWaveViewMethods multiWaveViewMethods = new MultiWaveViewMethods(multiWaveView);
            multiWaveView.setOnTriggerListener(multiWaveViewMethods);
            mUnlockWidgetMethods = multiWaveViewMethods;
		} else if (mUnlockWidget instanceof RingSelector) {
            RingSelector ringSelectorView = (RingSelector) mUnlockWidget;
            float density = getResources().getDisplayMetrics().density;
            int ringAppIconSize = context.getResources().getInteger(R.integer.config_ringSecIconSizeDIP);
            for (int q = 0; q < 4; q++) {
                if (mCustomRingAppActivities[q] != null) {
                    ringSelectorView.showSecRing(q);
                    try {
                        Intent i = Intent.parseUri(mCustomRingAppActivities[q], 0);
                        PackageManager pm = context.getPackageManager();
                        ActivityInfo ai = i.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
                        if (ai != null) {
                            Bitmap iconBmp = ((BitmapDrawable) ai.loadIcon(pm)).getBitmap();
                            mCustomRingAppIcons[q] = Bitmap.createScaledBitmap(iconBmp,
																			   (int) (density * ringAppIconSize), (int) (density * ringAppIconSize), true);
                            ringSelectorView.setSecRingResources(q, mCustomRingAppIcons[q], R.drawable.jog_ring_secback_normal);
                        }
                    } catch (URISyntaxException e) {
                    }
                } else {
                    ringSelectorView.hideSecRing(q);
                }
            }
            ringSelectorView.enableMiddleRing(mLockscreenCustom);
            ringSelectorView.setLeftRingResources(
												  R.drawable.ic_jog_dial_unlock,
												  R.drawable.jog_tab_target_green,
												  R.drawable.jog_ring_ring_green);
            ringSelectorView.setMiddleRingResources(
													R.drawable.ic_jog_dial_custom,
													R.drawable.jog_tab_target_green,
													R.drawable.jog_ring_ring_green);
            RingSelectorMethods ringSelectorMethods = new RingSelectorMethods(ringSelectorView);
            ringSelectorView.setOnRingTriggerListener(ringSelectorMethods);
            mUnlockWidgetMethods = ringSelectorMethods;
        } else {
            throw new IllegalStateException("Unrecognized unlock widget: " + mUnlockWidget);
        }

        // Update widget with initial ring state
        mUnlockWidgetMethods.updateResources();

        if (DBG) Log.v(TAG, "*** LockScreen accel is "
                + (mUnlockWidget.isHardwareAccelerated() ? "on":"off"));
    }

    private boolean isSilentMode() {
        return mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
    }

    void updateConfiguration() {
        Configuration newConfig = getResources().getConfiguration();
        if (newConfig.orientation != mCreationOrientation) {
            mCallback.recreateMe(newConfig);
        } else if (newConfig.hardKeyboardHidden != mKeyboardHidden) {
            mKeyboardHidden = newConfig.hardKeyboardHidden;
            final boolean isKeyboardOpen = mKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
            if (mUpdateMonitor.isKeyguardBypassEnabled() && isKeyboardOpen) {
                mCallback.goToUnlockScreen();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** LOCK ATTACHED TO WINDOW");
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + getResources().getConfiguration());
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.w(TAG, "***** LOCK CONFIG CHANGING", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + newConfig);
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    public boolean needsInput() {
        return false;
    }

    /** {@inheritDoc} */
    public void onPause() {
        mStatusViewManager.onPause();
        mUnlockWidgetMethods.reset(false);
    }

    private final Runnable mOnResumePing = new Runnable() {
        public void run() {
            mUnlockWidgetMethods.ping();
        }
    };

    /** {@inheritDoc} */
    public void onResume() {
        mStatusViewManager.onResume();
        postDelayed(mOnResumePing, ON_RESUME_PING_DELAY);
    }

    /** {@inheritDoc} */
    public void cleanUp() {
        mUpdateMonitor.removeCallback(this); // this must be first
        mLockPatternUtils = null;
        mUpdateMonitor = null;
        mCallback = null;
    }

    /** {@inheritDoc} */
    public void onRingerModeChanged(int state) {
        boolean silent = AudioManager.RINGER_MODE_NORMAL != state;
        if (silent != mSilentMode) {
            mSilentMode = silent;
            mUnlockWidgetMethods.updateResources();
        }
    }

    public void onPhoneStateChanged(String newState) {
    }
}
