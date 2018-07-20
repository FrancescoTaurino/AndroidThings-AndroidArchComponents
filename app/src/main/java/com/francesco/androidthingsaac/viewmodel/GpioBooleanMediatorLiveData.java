package com.francesco.androidthingsaac.viewmodel;

import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;

import java.io.IOException;

import static android.content.ContentValues.TAG;

public class GpioBooleanMediatorLiveData extends MediatorLiveData<Boolean> {
    private static final String TAG = GpioBooleanMediatorLiveData.class.getSimpleName();

    private final GpioLiveData gpioLiveData;
    private final Observer<Gpio> myObserver = new Observer<Gpio>() {
        @Override
        public void onChanged(@Nullable Gpio gpio) {
            if (gpio != null) {
                try {
                    setValue(gpio.getValue());
                } catch (IOException e) {
                    Log.d(TAG, "onChanged: " + e);
                }
            }
        }
    };

    public GpioBooleanMediatorLiveData(GpioLiveData gpioLiveData) {
        this.gpioLiveData = gpioLiveData;
    }

    @Override
    protected void onActive() {
        super.onActive();
        Log.d(TAG, "onActive");

        addSource(gpioLiveData, myObserver);
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        Log.d(TAG, "onInactive");

        removeSource(gpioLiveData);
        setValue(null);
    }
}
