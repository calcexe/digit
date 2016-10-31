package pl.calc_exe.wykop.model.rest.extras;

import org.greenrobot.eventbus.EventBus;

import javax.inject.Inject;

import pl.calc_exe.wykop.di.Pref;
import pl.calc_exe.wykop.di.components.DaggerMainComponent;
import pl.calc_exe.wykop.di.modules.NetworkModule;
import pl.calc_exe.wykop.events.ShowMessage;
import pl.calc_exe.wykop.extras.ErrorManager;
import pl.calc_exe.wykop.extras.Extras;
import pl.calc_exe.wykop.model.domain.Error;
import pl.calc_exe.wykop.model.domain.Profile;
import pl.calc_exe.wykop.model.domain.extras.IError;
import pl.calc_exe.wykop.model.rest.services.UserService;
import retrofit2.Retrofit;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Universal class to receive object. Supports re-login if userkey is out-of-date.
 * */
public class Receiver<T extends IError> {

    private IObservatorSupplier<T> supplier;
    private Action1<T> action;
    private Retrofit retrofit;
    private boolean loginAttempt = false;

    /**
     * @param supplier supplies an observable
     * @param action action to do when data is ready
     * */
    public Receiver(IObservatorSupplier<T> supplier, Action1<T> action){
        this.supplier = supplier;
        this.action = action;
        this.retrofit = new Injector().retrofit;
    }

    /**
     * Receives data from observable given by supplier.
     * */
    public void receive(){
        supplier.getObservator()
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new MainReceiver());
    }

    private void onError(Error error){
        EventBus.getDefault().post(new ShowMessage(error.toString()));
    }

    /**
     * Class to process when receiving requested object.
     * */
    private class MainReceiver extends Subscriber<T> {

        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            String message;
            if (e.getMessage() == null) {
                message = "Wystąpił nieznany błąd.";
            } else {
                message = e.getMessage();
                e.printStackTrace();
            }
            Receiver.this.onError(new Error(666, message));
        }

        @Override
        public void onNext(T value) {
            if (value == null){
                Receiver.this.onError(new Error(666, "Wystąpił nieznany błąd. Spróbuj zalogować się ponownie."));
                return;
            }

            switch (ErrorManager.parseError(value.getError())){

                case NONE:
                    action.call(value);
                    break;
                case USERKEY:
                    if (!loginAttempt){
                        loginAttempt = true;
                        UserService userService = retrofit.create(UserService.class);
                        userService
                                .loginObservable(Extras.APP_KEY, Pref.getInstance().get().getAccountKey())
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new LoginReceiver());
                    } else{
                        Receiver.this.onError(value.getError());
                    }
                    break;
                case OTHER:
                    Receiver.this.onError(value.getError());
                    break;
            }
        }
    }

    /**
     * Class to process when re-login is needed.
     * */
    private class LoginReceiver extends Subscriber<Profile>{

        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            String message;
            if (e.getMessage() == null) {
                message = "Wystąpił nieznany błąd.";
            } else {
                message = e.getMessage();
                e.printStackTrace();
            }
            Receiver.this.onError(new Error(666, message));
        }

        @Override
        public void onNext(Profile profile) {
            if (profile == null) {
                Receiver.this.onError(new Error(666, "Wystąpił nieznany błąd. Spróbuj zalogować się ponownie."));
                return;
            }

            if (profile.getError().getCode() != 0){
                Receiver.this.onError(profile.getError());
                return;
            }
            Pref.getInstance().get().login(profile);
            supplier.getObservator()
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new MainReceiver());
        }
    }

    /**
     * Static class witch allows Dagger injection to generic class.
     * */
    public static class Injector{
        @Inject Retrofit retrofit;
        Injector(){
            DaggerMainComponent.builder().networkModule(new NetworkModule()).build().inject(this);
        }
    }
}



