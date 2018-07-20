
# Android Things e Android Architecture Components

## Pre-requisiti Hardware/Software
- [Android Studio 3.+](https://developer.android.com/studio/) con Android 8.1 (API Level 27);
- [Raspberry Pi 3](https://www.raspberrypi.org/products/);
- [Rainbow HAT](https://shop.pimoroni.com/products/rainbow-hat-for-android-things).

## Il problema delle God Activities
In Android Things, parte della programmazione, si concentra nel gestire la comunicazione con le perifieriche disponibili (ad es: LCD, tasti, led, sensori, ec...) e nel reagire ai loro cambiamenti.
Ogni volta che si apre una comunicazione con una qualsiasi periferica, bisogna preoccuparsi anche di chiuderla. Inoltre, questo processo di apertura/chiusura deve esere sensibile al ciclo di vita delle *Activity*.
Infine, una *Business Logic* (ovvero il nucleo elaborativo dell'applicazione) che esegue operazioni "lunghe" (ad es: accesso alla rete/database o scrittura/lettura da file, ecc...) deve essere spostata su un *Worker Thread* e, i suoi risultati, devono sempre rimanere sensibili al ciclo di vita delle Activity.

La maggior parte degli esempi riguardanti Android Things che si trovano in rete si concentrano su **come utilizzare** le API's e non **come strutturare** un'applicazione.
Quindi mi sono chiesto: i pattern architetturali per Android sono applicabili ad Android Things?

Gli esempi in questione, dal momento che implementano semplici e banali applicazioni, concentrano tutto il codice all'interno di una singola Activity, che prende il nome di *God Activity*.

In generale, nella programmazione ad oggetti, un [*God Object*](https://en.wikipedia.org/wiki/God_object) è un oggetto che:
> knows too much or does too much.

Ovvero, una classe che condensa in essa tutto il codice (o gran parte). Un pattern di questo tipo introduce vari problemi, ad esempio:
- riduce la modularità dell'applicazione;
- impedisce il riutilizzo di porzioni di codice;
- rende difficile la creazione di *Unit Test*;
- rende difficile il mantenimento e la leggibilità del codice.

Nel caso di Android, una God Activity, oltre ai problemi appena elencati, introduce ultreriori complicanze legate al ciclo di vita di un'Activity. Ogni Activity, infatti, viene creata e distrutta ripetutamente e, di conseguenza, eventuali oggetti istanziati in essa vengono anch'essi creati e distrutti.

Si consideri il seguente esempio: un'applicazione per Android Things che effettua un log su terminale ogni volta che il tasto `A` del *Rainbow HAT* viene premuto e rilasciato. Di seguito il codice: 

```Java
public class MainActivity extends Activity {
  ...
  private static final String A_BUTTON_NAME = "BCM21";
  
  private Gpio gpio;
  private final GpioCallback myGpioCallback = new GpioCallback() {  
    @Override
    public boolean onGpioEdge(Gpio gpio) {  
      try {  
        Log.d(TAG, "onGpioEdge: " + (gpio.getValue() ? "Pressed" : "Released"));  
      }  
      catch (IOException e) {  
        Log.d(TAG, "onGpioEdge: " + e);  
      }  
      return true;  
    }  
  };

  @Override  
  protected void onCreate(@Nullable Bundle savedInstanceState) {  
    super.onCreate(savedInstanceState);
    	
    PeripheralManager peripheralManager = PeripheralManager.getInstance();  

	// gpio setup
    try {  
      gpio = peripheralManager.openGpio(A_BUTTON_NAME);  
      gpio.setDirection(Gpio.DIRECTION_IN);
      gpio.setActiveType(Gpio.ACTIVE_LOW);
      gpio.setEdgeTriggerType(Gpio.EDGE_BOTH);
      gpio.registerGpioCallback(myGpioCallback);  
    }  
    catch (IOException e) {  
      Log.d(TAG, "onCreate: " + e);
    }  
  }

  @Override
  protected void onDestroy() {  
    super.onDestroy();  

	// gpio closure
    if (gpio != null) {
      try {  
        gpio.unregisterGpioCallback(myGpioCallback);  
        gpio.close();  
      }  
      catch (IOException e) {  
        Log.d(TAG, "onDestroy: " + e);  
      }  
    }
  }
}
```

Il codice dell'applicazione è tutto contenuto all'interno della sola `MainActivity`. Per applicazioni molto semplici tale struttura è sufficiente, ma quando l'applicazione diventa più complessa è necessario adottare un approccio diverso.

## Gli Android Architecture Components
Durante il Google I/O 2017, Android ha annunciato la disponibilità degli [*Android Architecture Components*](https://developer.android.com/topic/libraries/architecture/), ovvero delle librerie che aiutano gli sviluppatori nel costruire applicazioni robuste, mantenibili e testabili.
Di queste librerie, le classi principali sono: [`LiveData`](https://developer.android.com/topic/libraries/architecture/livedata) e [`ViewModel`](https://developer.android.com/topic/libraries/architecture/viewmodel).
>`LiveData` is an observable data holder class. Unlike a regular observable, `LiveData` is lifecycle-aware, meaning it respects the lifecycle of other app components, such as activities, fragments, or services. This awareness ensures `LiveData` only updates app component observers that are in an active lifecycle state. `LiveData` considers an observer to be in an active state if its lifecycle is in the `STARTED` or `RESUMED` state. `LiveData` only notifies active observers about updates. Inactive observers registered to watch `LiveData` objects aren't notified about changes.

> The `ViewModel` class is designed to store and manage UI-related data in a lifecycle conscious way. The `ViewModel` class allows data to survive configuration changes such as screen rotations.

---
**Nota Bene:**
Per poter utilizzare gli Android Architecture Components è necessario verificare che il proprio progetto per Android Things abbia le seguenti dipendenze nel file *Gradle* (livello applicazione):

```Java
dependencies {
  ...
  // Supporto a AppCompatActivity
  implementation 'com.android.support:appcompat-v7:27.1.1'
  // Supporto a LiveData e ViewModel
  implementation "android.arch.lifecycle:extensions:1.1.1"
}
```

Inoltre è necessario aggiungere al file `AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>  
<manifest
  ... >
  
  <!-- Accesso alle periferiche -->
  <uses-permission android:name="com.google.android.things.permission.USE_PERIPHERAL_IO" />
  
  <application  
    android:theme="@style/Theme.AppCompat">
    ...
  </application>  
</manifest>
```
---

### Primo miglioramento
Il primo miglioramento che è possibile effettuare all'applicazione precedente è il seguente: nascondere a `MainActivity` la gestione degli oggetti `gpio` e `myGpioCallback`, in modo tale che non sia essa a doversi preoccupare della loro creazione e gestione.  

Prima di tutto è necessario estendere la classe `MutableLiveData`, sottoclasse di `LiveData`.

```Java
public class GpioLiveData extends MutableLiveData<Gpio> {  
  ...
  
  private Gpio gpio;  
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
    ...
  
    PeripheralManager peripheralManager = PeripheralManager.getInstance();

    // gpio setup
    ...
  }
    
  @Override  
  protected void onInactive() {  
    super.onInactive();
    ...

    // gpio closure
    ... 
    
    setValue(null);  
  }  
}
```

La classe `GpioLiveData` è un data holder per oggetti di tipo `Gpio`.  Si noti che `gpio` viene costruito (apertura della comunicazione e registrazione della callback) nell'`onActive()`, ovvero quando l'Activity si trova in uno stato attivo; il contrario, invece, avviene nell'`onInactive()`. 

`onGpioEdge()` viene invocato ogni volta che il tasto `A` del Rainbow HAT viene premuto. La sua invocazione cambia l'oggetto di tipo `Gpio`  contenuto nella classe `GpioLiveData`. 

L'istanziazione della classe `GpioLiveData` non può avvenire direttamente all'interno dell'Activity (per il problema della creazione/distruzione), bensì all'interno del `ViewModel`, il quale sopravvive al ciclo di vita di una Activity.

```Java
public class MainActivityViewModel extends ViewModel {
  ...
  private GpioLiveData gpioLiveData;  

  public LiveData<Gpio> getGpioLiveData() {  
    if (gpioLiveData == null)  
      gpioLiveData = new GpioLiveData();  

    return gpioLiveData;
  }
}
```

Infine, è necessario fornire alla `MainActivity` una istanza del `MainActivityViewModel`. La creazione non avviene tramite costruttore, bensì tramite la classe `ViewModelProviders`.

---
**Nota bene:**
Si noti che ora `MainActivity` estende la classe `AppCompatActivity` e non `Activity`.

---

```Java
public class MainActivity extends AppCompatActivity {
  ...
  private MainActivityViewModel mainActivityViewModel;  

  @Override  
  protected void onCreate(Bundle savedInstanceState) {  
    super.onCreate(savedInstanceState);

    mainActivityViewModel = ViewModelProviders.of(this).get(MainActivityViewModel.class);  
    mainActivityViewModel.getGpioLiveData().observe(this, new Observer<Gpio>() {  
      @Override  
      public void onChanged(@Nullable Gpio aGpio) {
        if (aGpio != null) {
          try {
            Log.d(TAG, aGpio.getValue() ? "Pressed" : "Released");
          }
          catch (IOException e) {
            Log.d(TAG, "onChanged: " + e);
          }
        }
      }
    });  
  }
}
```

Ora `MainActivity` osserva l'oggetto `gpioLiveData` e, ad ogni notifica di cambiamento, effettua un log su terminale. Si noti, infine, che è stato raggiunto l'obiettivo prefissato ad inizio paragrafo: `MainActivity` non si deve più preoccupare degli oggetti `gpio` e `myGpioCallback`, la cui gestione è delegata alla classe `GpioLiveData`.

### Secondo miglioramento
Sebbene quest'ultimo risultato rappresenti una buona soluzione, è possibile effettuare un ulteriore miglioramento: si desidera nascondere classe `Gpio` a `MainActivity`, in modo tale che quest'ultima riceva direttamente il valore booleano per indicare `"Pressed"` o `"Released"`.

Per fare questo, è necessario introdurre la classe [`MediatorLiveData`](https://developer.android.com/reference/android/arch/lifecycle/MediatorLiveData):
> `LiveData` subclass which may observe other  `LiveData`  objects and react on  `OnChanged`  events from them. 
> This class correctly propagates its active/inactive states down to source  `LiveData`  objects.

Prima di tutto, quindi, è necessario estendere la classe `MediatorLiveData`.

```Java
public class GpioBooleanMediatorLiveData extends MediatorLiveData<Boolean> {
  ...
  
  private final GpioLiveData gpioLiveData;  
  private final Observer<Gpio> myObserver = new Observer<Gpio>() {  
    @Override  
    public void onChanged(@Nullable Gpio gpio) {  
      if (gpio != null) {  
        try {  
          setValue(gpio.getValue());  
        } 
        catch (IOException e) {  
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
    ...

    addSource(gpioLiveData, myObserver);
  }

  @Override  
  protected void onInactive() {
    super.onInactive();
    ...
    
    removeSource(gpioLiveData);  
    setValue(null);  
  }
}
```

La classe `GpioBooleanMediatorLiveData` ascolta i cambiamenti della classe `GpioLiveData` e mappa oggetti di tipo `Gpio` in `Boolean`.

A questo punto, bisogna modificare il `ViewModel`, in modo che istanzi e ritorni un oggetto `GpioBooleanMediatorLiveData`.

```Java
public class MainActivityViewModel extends ViewModel {
  ...
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
```

Infine, si modifica `MainActivity` in modo tale che osservi `gpioBooleanMediatorLiveData` e non più `gpioLiveData`.

```Java
public class MainActivity extends AppCompatActivity {
  ...
  
  @Override  
  protected void onCreate(Bundle savedInstanceState) {
    ...
    
	mainActivityViewModel.getMediatorLiveData().observe(this, new Observer<Boolean>() {
      @Override  
      public void onChanged(@Nullable Boolean aBoolean) {  
        if (aBoolean != null)  
          Log.d(TAG, aBoolean ? "Pressed" : "Released");
      }
    });
  }
}
```

## Verifica di corretto funzionamento
Per verificare che tutti i componenti funzionino correttamente, è stato leggermente modificato il metodo `onChange()` in `MainActivity`: quest'ultima viene ricreata ogni volta che il tasto `A` viene premuto e poi rilasciato.

```Java
public class MainActivity extends AppCompatActivity {
  ...
  
  @Override  
  protected void onCreate(Bundle savedInstanceState) {
    ...
    
	mainActivityViewModel.getMediatorLiveData().observe(this, new Observer<Boolean>() {
      @Override  
      public void onChanged(@Nullable Boolean aBoolean) {  
        if (aBoolean != null) { 
          Log.d(TAG, aBoolean ? "Pressed" : "Released");

          if (!aBoolean)
            recreate()
        }
      }
    });
  }
}
```

Lo screenshot seguente illustra come evolve il ciclo di vita di `MainActivity` alla pressione e rilascio del tasto `A` e come i componenti `GpioBooleanMediatorLiveData` e `GpioLiveData` siano reattivi a questa evoluzione.

In particolare: 
- dopo che l'Activity viene creata (`onCreate()`), entrambi i componenti si attivano (`onActive()`) e quindi sono pronti per notificare eventuali cambiamenti;
- prima che l'Activity viene distrutta (`onDestroy()`), entrambi i componenti si disattivano (`onInactive()`) e quindi non notificano più eventuali cambiamenti.

<p align="center">
  <img src="https://francescotaurino.github.io/MediaRepo/AndroidThingsAAC/log.png"></p>
