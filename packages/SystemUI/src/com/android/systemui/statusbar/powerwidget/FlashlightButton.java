package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.app.IntentService;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

public class FlashlightButton extends PowerButton {
    
    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.TORCH_STATE));
    }
    
    public static final String INTENT_TORCH_ON = "com.android.systemui.INTENT_TORCH_ON";
    public static final String INTENT_TORCH_OFF = "com.android.systemui.INTENT_TORCH_OFF";
    boolean mFastTorchOn;
    
    public FlashlightButton() { mType = BUTTON_FLASHLIGHT; }
    
    @Override
    protected void updateState() {
        boolean enabled = Settings.System.getInt(mView.getContext().getContentResolver(), Settings.System.TORCH_STATE, 0) == 1;
        if(enabled) {
            mIcon = R.drawable.stat_flashlight_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_flashlight_off;
            mState = STATE_DISABLED;
        }
    }
    
    @Override
    protected void toggleState() {
        boolean enabled = Settings.System.getInt(mView.getContext().getContentResolver(), Settings.System.TORCH_STATE, 0) == 1;
        if(enabled) {
            Context context = mView.getContext();
            Intent i = new Intent(INTENT_TORCH_ON);
            i.setAction(INTENT_TORCH_ON);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startService(i);
            mFastTorchOn = true;
        } else {
            Context context = mView.getContext();
            Intent i = new Intent(INTENT_TORCH_OFF);
            i.setAction(INTENT_TORCH_OFF);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startService(i);
            mFastTorchOn = false;
        }
    }
    
    @Override
    protected boolean handleLongClick() {
        return false;
    }
    
    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }
}