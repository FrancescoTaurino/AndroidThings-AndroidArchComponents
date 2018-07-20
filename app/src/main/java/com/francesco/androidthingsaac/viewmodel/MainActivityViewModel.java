package com.francesco.androidthingsaac.viewmodel;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModel;
import android.media.RemoteController;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.things.pio.Gpio;

import java.io.IOException;

public class MainActivityViewModel extends ViewModel {
    private static final String TAG = MainActivityViewModel.class.getSimpleName();

    private GpioBooleanMediatorLiveData gpioBooleanMediatorLiveData;
    private GpioLiveData gpioLiveData;

    public LiveData<Boolean> getMediatorLiveData() {
        if (gpioBooleanMediatorLiveData == null) {
            if (gpioLiveData == null)
                gpioLiveData = new GpioLiveData();

            gpioBooleanMediatorLiveData = new GpioBooleanMediatorLiveData(gpioLiveData);
        }

        return gpioBooleanMediatorLiveData;
    }
}
