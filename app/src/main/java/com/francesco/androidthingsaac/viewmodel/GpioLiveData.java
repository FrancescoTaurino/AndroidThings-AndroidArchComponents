package com.francesco.androidthingsaac.viewmodel;

import android.arch.lifecycle.MutableLiveData;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;

public class GpioLiveData extends MutableLiveData<Gpio> {
    private static final String TAG = GpioLiveData.class.getSimpleName();
    private static final String A_BUTTON_NAME = "BCM21";

    private Gpio aButton;
    private final GpioCallback myGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            setValue(gpio);
            return true;
        }
    };

    @Override
    protected void onActive() {
        super.onActive();
        Log.d(TAG, "onActive");

        PeripheralManager peripheralManager = PeripheralManager.getInstance();

        try {
            aButton = peripheralManager.openGpio(A_BUTTON_NAME);
            aButton.setDirection(Gpio.DIRECTION_IN);
            aButton.setActiveType(Gpio.ACTIVE_LOW);
            aButton.setEdgeTriggerType(Gpio.EDGE_BOTH);
            aButton.registerGpioCallback(myGpioCallback);
        }
        catch (IOException e) {
            Log.d(TAG, "getMediatorLiveData: " + e);
        }
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        Log.d(TAG, "onInactive");

        if (aButton != null) {
            try {
                aButton.unregisterGpioCallback(myGpioCallback);
                aButton.close();

            } catch (IOException e) {
                Log.d(TAG, "onInactive: " + e);
            }
        }
        setValue(null);
    }
}
