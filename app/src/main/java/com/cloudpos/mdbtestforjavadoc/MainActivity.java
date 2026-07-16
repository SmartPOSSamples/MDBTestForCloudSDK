package com.cloudpos.mdbtestforjavadoc;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.cloudpos.DeviceException;
import com.cloudpos.OperationListener;
import com.cloudpos.OperationResult;
import com.cloudpos.POSTerminal;
import com.cloudpos.TimeConstants;
import com.cloudpos.card.Card;
import com.cloudpos.extboard.ExtBoardDevice;
import com.cloudpos.extboard.bean.MDBConfig;
import com.cloudpos.extboard.bean.MDBEvent;
import com.cloudpos.extboard.bean.MDBOption;
import com.cloudpos.mdbtestforjavadoc.fragment.HomeFragment;
import com.cloudpos.mdbtestforjavadoc.fragment.SniffsFragment;
import com.cloudpos.mdbtestforjavadoc.fragment.TransactionFragment;
import com.cloudpos.mdbtestforjavadoc.util.ByteUtils;
import com.cloudpos.mdbtestforjavadoc.util.FileHelper;
import com.cloudpos.rfcardreader.RFCardReaderDevice;
import com.cloudpos.rfcardreader.RFCardReaderOperationResult;
import com.cloudpos.sdk.common.SystemProperties;
import com.cloudpos.sdk.util.StringUtil;
import com.cloudpos.smartcardreader.SmartCardReaderDevice;
import com.google.android.material.navigation.NavigationView;
import com.cloudpos.mdbtestforjavadoc.util.ByteConvertStringUtil;
import com.cloudpos.mdbtestforjavadoc.util.LogHelper;
import com.cloudpos.mdbtestforjavadoc.util.MDBUtils;
import com.cloudpos.mdbtestforjavadoc.util.PreferenceHelper;
import com.cloudpos.mdbtestforjavadoc.values.MDBValues;
import com.cloudpos.mdbtestforjavadoc.values.OptionalFeature;
import com.cloudpos.mdbtestforjavadoc.values.PulseValues;

import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity implements DataSendListener {

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private TextView textView;
    private LinearLayout llFragmentContainer;
    private String subModel;

    private Handler handler;
    private Handler subThreadHandler;
//    private Thread mdbThread;
    private ExecutorService executorService;
    private Future<?> future;

    private MDBValues mdbValues = new MDBValues();
    private PulseValues pulseValues = new PulseValues();

    private Fragment selectedFragment = null;
    private volatile boolean running = true;
    private volatile boolean open = false;

    public boolean readCardDone = true;
    public Queue<Message> msgQueueReadCard = new LinkedList<>();
    private Context context;

    AlertDialog cardDialog;
    private Card rfCard;
    private RFCardReaderDevice rfCardReaderDevice = null;
    private SmartCardReaderDevice smartCardReaderDevice = null;
    private ExtBoardDevice extBoardDevice = null;

    private final String TAG = "MDBTest TAG";
    private final boolean TRIGGER_PULSE_ONE_SHOT = true;
    public static final String SUBMODEL_Q3MINI = "q3mini";
    public static final String SUBMODEL_Q3A7 = "q3a7";
    public static final String SUBMODEL_Q3V = "q3v";
    private Thread mdbThread;
    private boolean isNoSearchCard = false;
    private boolean snifferrunning = false;
    private FileHelper fileHelper;
    private int mFilterAddr = FILTER_ADDR[0];
    private boolean mHidePoll = false;

    private static final int[] FILTER_ADDR = {
            -1,    // All — no filter
            0x10,  // Cashless #1
            0x60,  // Cashless #2
            0x30,  // Bill Validator
            0x58,  // Coin Hopper #1
            0x70,  // Coin Hopper #2
    };
    private HandlerThread mSnifferThread;
    private Handler mSnifferHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView)findViewById(R.id.textviewaa);
        textView.setMovementMethod(ScrollingMovementMethod.getInstance());
        context = this;
        mdbValues.setOptionalFeature(new OptionalFeature());

        llFragmentContainer = findViewById(R.id.fragment_container);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        selectedFragment = new HomeFragment();
//        setFragmentContainerHeight(selectedFragment);
//        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, selectedFragment).commit();
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.nav_home) {
                    selectedFragment = getSupportFragmentManager().findFragmentByTag(HomeFragment.class.getSimpleName());
                    if (selectedFragment == null) {
                        selectedFragment = new HomeFragment();
                    }
                    setFragmentContainerHeight(selectedFragment);
                }else if (item.getItemId() == R.id.nav_transaction) {
                    selectedFragment = getSupportFragmentManager().findFragmentByTag(TransactionFragment.class.getSimpleName());
                    if (selectedFragment == null) {
                        selectedFragment = new TransactionFragment();
                    }
                    setFragmentContainerHeight(selectedFragment);
                }else if (item.getItemId() == R.id.nav_sniffer) {
                    selectedFragment = getSupportFragmentManager().findFragmentByTag(SniffsFragment.class.getSimpleName());
                    if (selectedFragment == null) {
                        selectedFragment = new SniffsFragment();
                    }
                    setFragmentContainerHeight(selectedFragment);
                }
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

                if(selectedFragment != null){
                    Fragment existingFragment = getSupportFragmentManager().findFragmentByTag(selectedFragment.getClass().getSimpleName());
                    if(existingFragment == null){
                        transaction.add(R.id.fragment_container, selectedFragment, selectedFragment.getClass().getSimpleName());
                    } else {
                        transaction.show(existingFragment);
                    }
                }
                for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                    if (fragment != selectedFragment) {
                        transaction.hide(fragment);
                    }
                }
                transaction.commit();
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
        });
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, selectedFragment).commit();
        navigationView.setCheckedItem(R.id.nav_home);
        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
//                Log.d(TAG, "handleMessage: " + msg.what);
                switch (msg.what) {
                    case MDBUtils.ENABLE_ALL_UI:
                        if(msg.obj != null) {
                            enableAllUiExcept((int) msg.obj);
                        } else {
                            enableAllUiExcept();
                        }
                        break;
                    case MDBUtils.DISABLE_ALL_UI:
                        if(msg.obj != null) {
                            disableAllUiExcept((int) msg.obj);
                        } else {
                            disableAllUiExcept();
                        }
                        break;
                    case MDBUtils.BLACK_LOG:
                        LogHelper.appendBlackMsg((String) msg.obj, textView);
                        break;
                    case MDBUtils.BLUE_LOG:
                        LogHelper.infoAppendForAlert((String) msg.obj, textView);
                        break;
                    case MDBUtils.RED_LOG:
                        LogHelper.appendREDMsg((String) msg.obj, textView);
                        break;
                    case MDBUtils.GREEN_LOG:
                        LogHelper.appendGreenMsg((String) msg.obj, textView);
                        break;
                    case MDBUtils.DIALOG_CONFIRM:
                        MDBEvent itemDialog = (MDBEvent) msg.obj;
                        AlertDialog dialog = new AlertDialog.Builder(context)
                                .setTitle("Please confirm item and amount")
                                .setMessage("Vend product\n " + "Amount: $" + itemDialog.eventAmount + ",Item: " + itemDialog.eventItem)
                                .setNegativeButton("Deny", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        sendVendDenied();
                                        readCardDone = true;
                                        dialog.dismiss();
                                        processReadcard();
                                    }
                                })
                                .setPositiveButton("Approve", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        sendVendApproved(itemDialog);
                                        readCardDone = true;
                                        dialog.dismiss();
                                    }
                                }).create();
                        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                readCardDone = true;
                            }
                        });
                        dialog.show();
                        break;
                    case MDBUtils.DIALOG_ITEM:
                        AlertDialog dialog1 = new AlertDialog.Builder(context)
                                .setTitle("Dispense")
                                .setMessage(msg.obj + "")
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        processReadcard();
                                        dialog.dismiss();
                                    }
                                }).create();
                        dialog1.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                readCardDone = true;
                            }
                        });
                        dialog1.show();
                        break;
                    case MDBUtils.DIALOG_READCARD:
                        Message message = new Message();
                        message.what = msg.what;
                        message.obj = msg.obj;
                        msgQueueReadCard.add(message);
                        if(readCardDone) {
                            processReadcard();
                        }
                        break;
                    case MDBUtils.DIALOG_READCARD_CLOSE:
                        closeReadCardDialog();
                        break;
                    case MDBUtils.DIALOG_WAIT:
                        AlertDialog waitDialog = new AlertDialog.Builder(context)
                                .setTitle("Please wait")
                                .setMessage("Canceling...")
                                .setCancelable(false)
                                .create();
                        CountDownTimer timer = new CountDownTimer(6000, 1000) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                            }

                            @Override
                            public void onFinish() {
                                if (waitDialog.isShowing()) {
                                    subThreadHandler.obtainMessage(MDBUtils.SUB_THREAD_CMD_AFTER_WAIT).sendToTarget();
                                    waitDialog.dismiss();
                                }
                            }
                        };
                        waitDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                            @Override
                            public void onShow(DialogInterface dialog) {
                                timer.start();
                            }
                        });
                        waitDialog.show();
                        break;
                    case MDBUtils.TEST_MSG:
                        if (rfCardReaderDevice == null) {
                            rfCardReaderDevice = (RFCardReaderDevice) POSTerminal.getInstance(context)
                                    .getDevice("cloudpos.device.rfcardreader");
                        }
                        try {
                            rfCardReaderDevice.open();
                            SystemClock.sleep(5000);
                            readCardDone = true;
                            rfCardReaderDevice.close();
                        } catch (DeviceException e) {
                            Log.e(TAG, "open rfCardReaderDevice failed !");
//                            e.printStackTrace();
                        }
                        break;
                }
                return false;
            }
        });
        if(extBoardDevice == null) {
            extBoardDevice = (ExtBoardDevice) POSTerminal.getInstance(context)
                    .getDevice(POSTerminal.DEVICE_NAME_EXT_BOARD);
        }
        if(rfCardReaderDevice == null){
            rfCardReaderDevice = (RFCardReaderDevice) POSTerminal.getInstance(context)
                    .getDevice(POSTerminal.DEVICE_NAME_RF_CARD_READER);
        }
        subModel = SystemProperties.get("ro.wp.product.submodel");
        executorService = Executors.newSingleThreadExecutor();
        int selectedSpnPosMdbLevel = PreferenceHelper.getInstance(context).getIntValue("selectedSpnPositonLevel");
        if(selectedSpnPosMdbLevel == 0){
            mdbValues.setMdbLevel(2);
        }else if(selectedSpnPosMdbLevel == 1){
            mdbValues.setMdbLevel(3);
            mdbValues.getOptionalFeature().setAlwaysIdleMdb(true);
        }
        fileHelper = new FileHelper(new File("sdcard/mdbtest/sniffslog"));
    }

    @Override
    protected void onStop() {
        super.onStop();
        running = false;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        running = true;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(executorService != null && !executorService.isShutdown()){
            executorService.shutdownNow();
            executorService = null;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                close();
            }
        }).start();

    }

    public void processReadcard(){
        MDBEvent item = new MDBEvent();
        if(msgQueueReadCard.isEmpty()){
            readCardDone = true;
            return;
        }
        Log.d(TAG, "processing read card");
        readCardDone = false;
        if(!msgQueueReadCard.isEmpty()) {
            Message msgReadCard = msgQueueReadCard.poll();
            item = (MDBEvent) msgReadCard.obj;
        }
        if(cardDialog == null) {
            cardDialog = new AlertDialog.Builder(context)
                    .setTitle("Read card")
                    .setMessage("Please swipe card.")
                    .create();
            cardDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    readCardDone = true;
                    if (rfCardReaderDevice != null) {
                        try {
                            rfCardReaderDevice.close();
                        } catch (DeviceException e) {
                            Log.e(TAG, "close rfCardReaderDevice failed ! " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            });
            cardDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    sendVendDenied();
                }
            });
        }
//        Log.d(TAG, "Read item data " + item.getItemPrice() + "," + item.getItemId());
        if (rfCardReaderDevice == null) {
            rfCardReaderDevice = (RFCardReaderDevice) POSTerminal.getInstance(context)
                    .getDevice("cloudpos.device.rfcardreader");
        }
        if (smartCardReaderDevice == null) {
            smartCardReaderDevice = (SmartCardReaderDevice) POSTerminal.getInstance(context)
                    .getDevice("cloudpos.device.smartcardreader");
        }
        try {
            rfCardReaderDevice.open();
            try {
                cardDialog.show();
                MDBEvent finalItem = item;
                OperationListener listener = new OperationListener() {
                    @Override
                    public void handleResult(OperationResult arg0) {
                        try {
                            if (arg0.getResultCode() == OperationResult.SUCCESS) {
                                rfCard = ((RFCardReaderOperationResult) arg0).getCard();
                                handler.obtainMessage(MDBUtils.BLACK_LOG, "amount: $" + finalItem.eventAmount + ",item: " + finalItem.eventItem).sendToTarget();
                                handler.obtainMessage(MDBUtils.DIALOG_CONFIRM, finalItem).sendToTarget();
                            } else {
                                printLogAndText('e', "find_card_failed");
                                readCardDone = true;
                            }
                        } catch (Exception e) {
                            readCardDone = true;
                            printLogAndText('e', "find_card_failed");
//                            e.printStackTrace();
                        } finally {
                            cardDialog.dismiss();
                        }
                    }
                };
                rfCardReaderDevice.listenForCardPresent(listener, TimeConstants.FOREVER);
            } catch (DeviceException e) {
                e.printStackTrace();
            }
        } catch (DeviceException e) {
            Log.e(TAG, "open rfCardReaderDevice failed !");
            e.printStackTrace();
        }

    }

    private void sendVendDenied() {
        MDBEvent event = new MDBEvent();
        event.eventType = MDBEvent.TYPE_VEND_DENIED;
        event.eventResult = -1;
        event.eventAmount = 0;
        try {
            extBoardDevice.respondEvent(event);
            handler.obtainMessage(MDBUtils.BLACK_LOG, this.getString(R.string.vend_denied)).sendToTarget();
        } catch (DeviceException e) {
            throw new RuntimeException(e);
        }
    }

    public void closeReadCardDialog(){
        Log.d(TAG, "closeReadCardDialog");
        if(cardDialog != null){
            cardDialog.dismiss();
        }
        if(rfCardReaderDevice != null) {
            try {
                rfCardReaderDevice.close();
            } catch (DeviceException e) {
                Log.w(TAG, "close rfCardReaderDevice failed !");
                e.printStackTrace();
            }
        }
    }

    private boolean compareByteArrayHead(byte[] byteArray, byte[] bytesHead, int nBytes){
        byte[] tmpBytesHead = Arrays.copyOfRange(byteArray, 0, nBytes);
        return Arrays.equals(tmpBytesHead, bytesHead);
    }

    private boolean compareByteArrayHeadByDevice(byte[] byteArray, byte[] bytesHead, int nBytes){
        if(mdbValues.getDeviceType() == 1){
            return compareByteArrayHead(byteArray, bytesHead, nBytes);
        }else {
            byte[] tmpBytesHead = Arrays.copyOfRange(byteArray, 0, nBytes);
            tmpBytesHead[1] = (byte) ((byteArray[1] & 0x0F) | 0x10);
            return Arrays.equals(tmpBytesHead, bytesHead);
        }
    }

    private void printLogWithBlueRead(String blue, byte[] black){
        handler.obtainMessage(MDBUtils.BLUE_LOG, blue).sendToTarget();
        handler.obtainMessage(MDBUtils.BLACK_LOG, ByteConvertStringUtil.buf2StringCompact(black)).sendToTarget();
    }

    public void startMdb(){
        detectMDBOffline();
        mdbThread = new Thread(() -> {
            Looper.prepare();
            open();
            subThreadHandler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    switch (msg.what) {
                        case MDBUtils.SUB_THREAD_CMD_AFTER_WAIT:
                            sendVendDenied();
                            break;
                        default:
                            break;
                    }
                }
            };
            try {
                setMdbConfig();
                setMdbOption();
                extBoardDevice.startLoop();
                handler.obtainMessage(MDBUtils.BLACK_LOG, "Init MDB").sendToTarget();
            } catch (DeviceException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            running = true;
            while (running && !Thread.currentThread().isInterrupted()){
                try {
                    handler.obtainMessage(MDBUtils.DISABLE_ALL_UI, R.id.btn_mdb_stop).sendToTarget();
                    handler.obtainMessage(MDBUtils.BLACK_LOG, "waiting master command...").sendToTarget();
                    MDBEvent response = extBoardDevice.pollEvent(-1);
                    handler.obtainMessage(MDBUtils.GREEN_LOG, "recv mdbEvent:" + response.eventType).sendToTarget();
                    switch (response.eventType){
                        case MDBEvent.TYPE_TRANSACTION_START:
                            if(mdbValues.getOptionalFeature().isAlwaysIdleMdb() && mdbValues.getOptionalFeature().isAlwaysIdleVmc()){
                                Log.d(TAG, "always idle");
                                handler.obtainMessage(MDBUtils.BLACK_LOG, "always idle").sendToTarget();
                            }else{
                                MDBEvent beginsessionEvent = new MDBEvent();
                                beginsessionEvent.eventType = MDBEvent.TYPE_BEGIN_SESSION;
                                beginsessionEvent.eventAmount = mdbValues.getBalance();
                                handler.obtainMessage(MDBUtils.GREEN_LOG, "send beginsession balance: " + beginsessionEvent.eventAmount).sendToTarget();
                                extBoardDevice.respondEvent(beginsessionEvent);
                            }
                            break;
                        case MDBEvent.TYPE_REMOTE_VEND:

                            break;
                        case MDBEvent.TYPE_VEND_REQUEST:
                            handler.obtainMessage(MDBUtils.GREEN_LOG, "amount:" + response.eventAmount + ", item:" + response.eventItem + ", " +this.getString(R.string.plead_wave_your_card)).sendToTarget();
                            if(isNoSearchCard){
                                sendVendApproved(response);
                                break;
                            }
                            handler.obtainMessage(MDBUtils.DIALOG_READCARD, response).sendToTarget();
                            break;
                        case MDBEvent.TYPE_VEND_CANCEL:
                            MDBEvent event = new MDBEvent();
                            handler.obtainMessage(MDBUtils.RED_LOG, this.getString(R.string.vend_cancel)).sendToTarget();
                            event.eventType = MDBEvent.TYPE_VEND_CANCEL;
                            event.eventResult = 0;
                            try {
                                extBoardDevice.respondEvent(event);
                            } catch (DeviceException e) {
                                e.printStackTrace();
                            }
                            break;
                        case MDBEvent.TYPE_VEND_SUCCESS:
                            handler.obtainMessage(MDBUtils.GREEN_LOG, this.getString(R.string.vend_success)).sendToTarget();
                            break;
                        case MDBEvent.TYPE_VEND_FAILURE:
                            handler.obtainMessage(MDBUtils.RED_LOG, this.getString(R.string.vend_failure)).sendToTarget();
                            break;
                        case MDBEvent.TYPE_VEND_CASHSALE:

                            break;
                        case MDBEvent.TYPE_NEGATIVE_VEND:

                            break;
                        case MDBEvent.TYPE_SEL_DENIED:

                            break;
                        case MDBEvent.TYPE_REVALUE:

                            break;
                    }
                } catch (DeviceException e) {
                    e.printStackTrace();
                }
            }
        });
        mdbThread.start();
    }

    private void sendVendApproved(MDBEvent request) {
        MDBEvent event = new MDBEvent();
        event.eventType = MDBEvent.TYPE_VEND_APPROVED;
        event.eventResult = 1;
        event.eventAmount = request.eventAmount;
        try {
            extBoardDevice.respondEvent(event);
            handler.obtainMessage(MDBUtils.BLACK_LOG, this.getString(R.string.vend_approved)).sendToTarget();
        } catch (DeviceException e) {
            e.printStackTrace();
        }
    }

    public void stopMdb(){
        cancelDetectMDB();
        new Thread(() -> {
//            SystemClock.sleep(500);
            try {
                running = false;
                extBoardDevice.stopLoop();
//                extBoardDevice.cancelPollEvent();
            } catch (DeviceException e) {
                e.printStackTrace();
            }finally {
                handler.obtainMessage(MDBUtils.DISABLE_ALL_UI).sendToTarget();
                handler.obtainMessage(MDBUtils.ENABLE_ALL_UI, R.id.btn_mdb_stop).sendToTarget();
                handler.obtainMessage(MDBUtils.BLACK_LOG, "Mdb stopped").sendToTarget();
                if(mdbThread != null && !mdbThread.isInterrupted()){
                    mdbThread.interrupt();
                    mdbThread = null;
                }
            }

        }).start();
    }

    boolean open() {
        boolean ret = false;
        if(open){
            return open;
        }
		try {
			extBoardDevice.open();
            extBoardDevice.setConfigStateCheck(false);
            onLogSent('d', TAG, "extBoardDevice open success!");
            ret = true;
            open = true;
		} catch (DeviceException e) {
            onLogSent('e', TAG,"extBoardDevice open failed!");
            e.printStackTrace();
//			throw new RuntimeException(e);
		}
        return ret;
    }

    void close() {
        if(open) {
            try {
                extBoardDevice.close();
                Log.d(TAG, "extBoardDevice close success!");
                open = false;
            } catch (DeviceException e) {
                Log.e(TAG, "extBoardDevice close failed!" + e.getMessage());
                e.printStackTrace();
            }
        }
        if(rfCardReaderDevice != null) {
            try {
                rfCardReaderDevice.close();
            } catch (DeviceException e) {
                Log.e(TAG, "close rfCardReaderDevice failed ! " + e.getMessage());
                e.printStackTrace();
            }
        }
        if(future != null){
            future.cancel(true);
            future = null;
        }
    }

    public void getVersion(){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    open();
                    handler.obtainMessage(MDBUtils.BLACK_LOG, "Mdb version:" + extBoardDevice.getBoardVersion()).sendToTarget();
                } catch (DeviceException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    public void getHardwareVersion(){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String version = SystemProperties.get("ro.wp.extboard.type");
                if(TextUtils.isEmpty(version)){
                    handler.obtainMessage(MDBUtils.RED_LOG, "Hardware version is not exist").sendToTarget();
                }else{
                    handler.obtainMessage(MDBUtils.BLACK_LOG, "Hardware version:" + version).sendToTarget();
                }
            }
        });
        thread.start();
    }

    public void setBalance(){
        try {
            int balance = MDBUtils.getBalanceFromBigDecimal(mdbValues.getActualPrice(), mdbValues.getX(), mdbValues.getY());
            if(balance > 65534){
                Log.e(TAG, "balance: " + balance);
                printLogAndText('e', "set balance failed, balance out of range");
            } else {
                mdbValues.setBalance(balance);
                printLogAndText('d', "balance value changed: " + mdbValues.getActualPrice());
                Log.d(TAG, mdbValues.toString());
            }
        } catch (ArithmeticException e) {
            printLogAndText('e', "set balance failed, balance/scale factor is illegal!");
        }
    }
    public void setBalance(BigDecimal bigDecimal){
        mdbValues.setActualPrice(bigDecimal);
        setBalance();
    }

    public void setCheckboxStatus(CheckBox cb){
        new Thread(()->{
            if(open()) {
                if(mdbValues.getCurrentVersion() == 0) {
                    getVersion();
                }
                if((isWhiteDemon() && mdbValues.getCurrentVersion() >=28)
                || (!isWhiteDemon() && mdbValues.getCurrentVersion() >= 5)) {
                    Log.d(TAG, "setCheckboxStatus: mdbValues.getDefaultActiveStatus(): " + mdbValues.getDefaultActiveStatus());
                    if (mdbValues.getDefaultActiveStatus() == 1) {
                        runOnUiThread(() -> {
                            cb.setChecked(false);
                        });
                    } else {
                        runOnUiThread(() -> {
                            cb.setChecked(true);
                        });
                    }
                    close();
                }
            }
        }).start();
    }

    public boolean isWhiteDemon(){
        if((subModel.equalsIgnoreCase(SUBMODEL_Q3A7) || subModel.equalsIgnoreCase(SUBMODEL_Q3V))){
            return true;
        } else if(subModel.equalsIgnoreCase(SUBMODEL_Q3MINI)){
            return false;
        } else {
            Log.e(TAG, "isWhiteDemon: unknown subModel: " + subModel);
            return false;
        }
    }

    public static byte[] subByteArray(byte[] byteArray, int length) {
        byte[] arrySub = new byte[length];
        if (length >= 0) System.arraycopy(byteArray, 0, arrySub, 0, length);
        return arrySub;
    }

    public static byte[] subByteArrayIgnore(byte[] byteArray, int startIndex, int length) {
        byte[] arrySub = new byte[length];
        if (length >= 0) System.arraycopy(byteArray, startIndex, arrySub, 0, length);
        return arrySub;
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
//        if(drawerLayout.isDrawerOpen(GravityCompat.START)){
//            drawerLayout.closeDrawer(GravityCompat.START);
//        }else {
            super.onBackPressed();
//        }
    }

    @Override
    public void onLogSent(char c, String tag, String log) {
        switch (c){
            case 'd':
                handler.obtainMessage(MDBUtils.BLACK_LOG, log).sendToTarget();
                Log.d(tag, log);
                break;
            case 'i':
                handler.obtainMessage(MDBUtils.BLACK_LOG, log).sendToTarget();
                Log.i(tag, log);
                break;
            case 'e':
                handler.obtainMessage(MDBUtils.RED_LOG, log).sendToTarget();
                Log.e(tag, log);
                break;
        }
    }

    @Override
    public void onIntValueSent(int type, int value) {
        switch(type){
            case MDBUtils.TYPE_PULSE_INTERVAL:
                pulseValues.setPulseInterval(value);
                break;
            case MDBUtils.TYPE_PULSE_FREQUENCY:
                pulseValues.setPulseFrequency(value);
                break;
            case MDBUtils.TYPE_PULSE_DURATION:
                pulseValues.setPulseDuration(value);
                break;
            case MDBUtils.TYPE_PULSE_VOLTAGE:
                pulseValues.setPulseVoltage(value);
                break;
            case MDBUtils.TYPE_SPN_MDB_LEVEL:
                mdbValues.setMdbLevel(value);
                break;
            case MDBUtils.ENABLE_ALL_UI:
                handler.obtainMessage(MDBUtils.ENABLE_ALL_UI).sendToTarget();
                break;
            case MDBUtils.DISABLE_ALL_UI:
                handler.obtainMessage(MDBUtils.DISABLE_ALL_UI).sendToTarget();
                break;
            case MDBUtils.TYPE_X:
                mdbValues.setX(value);
                break;
            case MDBUtils.TYPE_Y:
                mdbValues.setY(value);
                break;
            default:
                break;
        }
    }

    @Override
    public void onStringValueSent(int type, String value) {
        switch (type){
            case MDBUtils.TYPE_SPN_FIRMWARE_ITEM:
                mdbValues.setFirmwareName(value);
                break;
            default:
                break;
        }
    }

    @Override
    public void onBooleanValueSent(int type, boolean value) {
        switch (type){
            case MDBUtils.TYPE_CB_CHECK_ALWAYS_IDLE:
                LogHelper.massiveLog('d', TAG,
                        "onBooleanValueSent: TYPE_CB_CHECK_ALWAYS_IDLE: mdbValues.getOptionalFeature().setAlwaysIdleMdb: " + value);
                mdbValues.getOptionalFeature().setAlwaysIdleMdb(value);
                LogHelper.massiveLog('e', TAG, mdbValues.getOptionalFeature().toString());
                break;
            case MDBUtils.TYPE_CB_CHECK_32BIT_MONETARY:
                mdbValues.getOptionalFeature().setMonetaryFormat32Mdb(value);
                break;
            case MDBUtils.TYPE_CB_CHECK_NEGATIVE_VEND:
                mdbValues.getOptionalFeature().setNegativeVendAllowedMdb(value);
                break;
            default:
                break;
        }
    }

    @Override
    public void onBigDecimalValueSent(int type, BigDecimal value) {
        switch (type){
            case MDBUtils.TYPE_BALANCE:
                setBalance(value);
                break;
            default:
                break;
        }
    }

    @Override
    public void onOpenClicked() {
        open();
    }

    @Override
    public void onCloseClicked() {
        close();
    }

    @Override
    public void onGetVersionClicked() {
        getVersion();
    }

    @Override
    public void onMdbStartClicked() {
        startMdb();
    }

    @Override
    public void onMdbStopClicked() {
        stopMdb();
    }

    @Override
    public void onSniffsStartClicked() {
        mSnifferThread = new HandlerThread("MDBSnifferThread");
        mSnifferThread.start();
        mSnifferHandler = new Handler(mSnifferThread.getLooper());

        mSnifferHandler.post(() -> snifferLoop());
    }

    private void snifferLoop() {
        try {
            open();
            snifferrunning = true;
            extBoardDevice.setEnableSniffer(true);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    findViewById(R.id.btn_sniffs_start).setEnabled(false);
                    findViewById(R.id.btn_sniffs_stop).setEnabled(true);
                }
            });
            handler.obtainMessage(MDBUtils.BLACK_LOG,  "start Sniffer").sendToTarget();
            while (snifferrunning){
                int[] buffer = extBoardDevice.snifferMDBData( -1);
                if(!snifferrunning){
                    return;
                }
                StringBuilder sb = new StringBuilder();
                if (buffer.length == 0) {
                    continue;  // timeout (shouldn't happen with infinite timeout)
                }
                // ---- Device filter ----
                int filterAddr = mFilterAddr;  // volatile read once
                if (filterAddr >= 0 && buffer.length > 0) {
                    int addr = (buffer[0] & 0xFFFF) & 0xF8;
                    if (addr != filterAddr) {
                        continue;  // skip — doesn't match selected device
                    }
                }

                // ---- Hide POLL filter ----
                if (mHidePoll && buffer.length > 0) {
                    if (((buffer[0] & 0xFFFF) & 0x07) == 2) {
                        continue;  // skip POLL responses
                    }
                }

                // Format as hex
                for (int i = 0; i < buffer.length; i++) {
                    int v = buffer[i] & 0xFFFF;  // treat as unsigned
                    if (v == 0) {
                        sb.append("0x00 ");
                    } else {
                        sb.append(String.format("0x%X ", v));
                    }
                }
                final String line = sb.toString();
                fileHelper.write(line +"\n");
                handler.obtainMessage(MDBUtils.GREEN_LOG,  line).sendToTarget();
            }
        } catch (DeviceException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSniffsStopClicked() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    snifferrunning = false;
                    if (mSnifferThread != null) {
                        mSnifferThread.quitSafely();
                        try {
                            mSnifferThread.join(3000);
                        } catch (InterruptedException e) {
                            Log.w(TAG, "Interrupted waiting for sniffer thread");
                        }
                        mSnifferThread = null;
                    }
                    extBoardDevice.cancelSnifferMDBData();
                    extBoardDevice.setEnableSniffer(false);
                    handler.obtainMessage(MDBUtils.BLACK_LOG,  "stop Sniffer").sendToTarget();
                    close();
                    fileHelper.close();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(R.id.btn_sniffs_start).setEnabled(true);
                            findViewById(R.id.btn_sniffs_stop).setEnabled(false);
                        }
                    });
                } catch (DeviceException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onTestClicked() {
    }

    Handler delayHandler = new Handler();

    @Override
    public void onTriggerPulseClicked() {
    }

    @Override
    public void onSetPulseClicked() {
    }

    @Override
    public void onFactoryModeClicked() {

    }

    @Override
    public void onGetMdbConnStatus() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    open();
                    int status = extBoardDevice.getMDBConnStatus();
                    switch (status){
                        case 0: //offline
                            handler.obtainMessage(MDBUtils.RED_LOG, "MDB IS OFFLINE").sendToTarget();
                            break;
                        case 1: //online
                            handler.obtainMessage(MDBUtils.GREEN_LOG, "MDB IS ONLINE").sendToTarget();
                            break;
                        default:
                            break;
                    }
                } catch (DeviceException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onGetHardwareVersionClicked() {
        getHardwareVersion();
    }

    @Override
    public void onDiagnoseHardwareClicked() {

    }

    @Override
    public void onTransactionFragmentViewCreated(CheckBox cb) {
        setCheckboxStatus(cb);
    }

    @Override
    public void onSelectFilter(int position) {
        mFilterAddr = FILTER_ADDR[position];
    }

    @Override
    public void onHidePoll(boolean isChecked) {
        mHidePoll = isChecked;
    }

    @Override
    public void onClear() {
        textView.setText("");
    }

    public void printLogAndText(char c, String s){
        onLogSent(c, TAG, s);
    }

    public void setFragmentContainerHeight(Fragment fragment){
//        float density = getResources().getDisplayMetrics().density;
        int widthInPx = llFragmentContainer.getWidth();
        if(fragment instanceof HomeFragment){
            int heightInPx = getResources().getDimensionPixelSize(R.dimen.fragment_height_home);
            LinearLayout.LayoutParams llParams = new LinearLayout.LayoutParams(widthInPx, heightInPx);
            llFragmentContainer.setLayoutParams(llParams);
        }else if (fragment instanceof TransactionFragment) {
            int heightInPx = getResources().getDimensionPixelSize(R.dimen.fragment_height_transaction);
            LinearLayout.LayoutParams llParams = new LinearLayout.LayoutParams(widthInPx, heightInPx);
            llFragmentContainer.setLayoutParams(llParams);
        }
    }

    private void disableAllUiExcept(int... exceptIds) {
        NavigationView navigationView = findViewById(R.id.nav_view);
        MenuItem menuItem = navigationView.getCheckedItem();
        if(menuItem != null){
            int itemId = menuItem.getItemId();
            if(itemId == R.id.nav_home){
                disableHomeUiExcept(exceptIds);
            }else if(itemId == R.id.nav_transaction){
                disableTransactionUiExcept(exceptIds);
            }
        }
        toggle.setDrawerIndicatorEnabled(false);
        toggle.syncState();
    }

    private void disableTransactionUiExcept(int... exceptIds) {
        disableTransactionUi();
        for (int id : exceptIds) {
            switch (id) {
                case R.id.btn_set_balance:
                    Button btnSetBalance = selectedFragment.getView().findViewById(R.id.btn_set_balance);
                    btnSetBalance.setEnabled(true);
                    break;
                case R.id.et_balance:
                    EditText etBalance = selectedFragment.getView().findViewById(R.id.et_balance);
                    etBalance.setEnabled(true);
                    break;
                case R.id.spn_mdb_level:
                    Spinner spnMdbLevel = selectedFragment.getView().findViewById(R.id.spn_mdb_level);
                    spnMdbLevel.setEnabled(true);
                    break;
                default:
                    break;
            }
        }
    }

    private void enableAllUiExcept(int... exceptIds){
        NavigationView navigationView = findViewById(R.id.nav_view);
        MenuItem menuItem = navigationView.getCheckedItem();
        if(menuItem != null){
            int itemId = menuItem.getItemId();
            if(itemId == R.id.nav_home){
                enableHomeUiExcept(exceptIds);
            }else if(itemId == R.id.nav_transaction){
                enableTransactionUiExcept(exceptIds);
            }
        }
        toggle.setDrawerIndicatorEnabled(true);
        toggle.syncState();
    }

    private void enableAllUi(){
        NavigationView navigationView = findViewById(R.id.nav_view);
        MenuItem menuItem = navigationView.getCheckedItem();
        if(menuItem != null){
            int itemId = menuItem.getItemId();
            if(itemId == R.id.nav_home){
                enableHomeUi();
            }else if(itemId == R.id.nav_transaction){
                enableTransactionUi();
            }
        }
        toggle.setDrawerIndicatorEnabled(true);
        toggle.syncState();
    }
    private void disableTransactionUi(){
        Button btnSetBalance = selectedFragment.getView().findViewById(R.id.btn_set_balance);
        btnSetBalance.setEnabled(false);
        EditText etBalance = selectedFragment.getView().findViewById(R.id.et_balance);
        etBalance.setEnabled(false);
        Spinner spnMdbLevel = selectedFragment.getView().findViewById(R.id.spn_mdb_level);
        spnMdbLevel.setEnabled(false);
    }

    private void enableTransactionUiExcept(int... exceptIds){
        enableTransactionUi();
        for (int id : exceptIds) {
            switch (id) {
                case R.id.btn_set_balance:
                    Button btnSetBalance = selectedFragment.getView().findViewById(R.id.btn_set_balance);
                    btnSetBalance.setEnabled(false);
                    break;
                case R.id.et_balance:
                    EditText etBalance = selectedFragment.getView().findViewById(R.id.et_balance);
                    etBalance.setEnabled(false);
                    break;
                case R.id.spn_mdb_level:
                    Spinner spnMdbLevel = selectedFragment.getView().findViewById(R.id.spn_mdb_level);
                    spnMdbLevel.setEnabled(false);
                    break;
                default:
                    break;
            }
        }
    }

    private void enableTransactionUi(){
        Button btnSetBalance = selectedFragment.getView().findViewById(R.id.btn_set_balance);
        btnSetBalance.setEnabled(true);
        EditText etBalance = selectedFragment.getView().findViewById(R.id.et_balance);
        etBalance.setEnabled(true);
        Spinner spnMdbLevel = selectedFragment.getView().findViewById(R.id.spn_mdb_level);
        spnMdbLevel.setEnabled(true);
    }

    private void disableHomeUiExcept(int... exceptIds) {
        disableHomeUi();
        for (int ids : exceptIds) {
            switch (ids) {
                case R.id.btn_get_version:
                    Button btnGetVersion = selectedFragment.getView().findViewById(R.id.btn_get_version);
                    btnGetVersion.setEnabled(true);
                    break;
                case R.id.btn_factory_mode:
                    Button btnFactoryMode = selectedFragment.getView().findViewById(R.id.btn_factory_mode);
                    btnFactoryMode.setEnabled(true);
                    break;
                case R.id.btn_get_hardware_version:
                    Button btnGetHardwareVersion = selectedFragment.getView().findViewById(R.id.btn_get_hardware_version);
                    btnGetHardwareVersion.setEnabled(true);
                    break;
                case R.id.btn_mdb_start:
                    Button btnStartMdb = selectedFragment.getView().findViewById(R.id.btn_mdb_start);
                    btnStartMdb.setEnabled(true);
                    break;
                case R.id.btn_mdb_stop:
                    Button btnStopMdb = selectedFragment.getView().findViewById(R.id.btn_mdb_stop);
                    btnStopMdb.setEnabled(true);
                    break;
                default:
                    break;
            }
        }
    }

    private void disableHomeUi(){
        Button btnGetVersion = selectedFragment.getView().findViewById(R.id.btn_get_version);
        btnGetVersion.setEnabled(false);
        Button btnFactoryMode = selectedFragment.getView().findViewById(R.id.btn_factory_mode);
        btnFactoryMode.setEnabled(false);
        Button btnGetHardwareVersion = selectedFragment.getView().findViewById(R.id.btn_get_hardware_version);
        btnGetHardwareVersion.setEnabled(false);
        Button btnStartMdb = selectedFragment.getView().findViewById(R.id.btn_mdb_start);
        btnStartMdb.setEnabled(false);
        Button btnStopMdb = selectedFragment.getView().findViewById(R.id.btn_mdb_stop);
        btnStopMdb.setEnabled(false);
    }

    private void enableHomeUiExcept(int... exceptIds){
        enableHomeUi();
        for (int id : exceptIds) {
            switch (id) {
                case R.id.btn_get_version:
                    Button btnGetVersion = selectedFragment.getView().findViewById(R.id.btn_get_version);
                    btnGetVersion.setEnabled(false);
                    break;
                case R.id.btn_factory_mode:
                    Button btnFactoryMode = selectedFragment.getView().findViewById(R.id.btn_factory_mode);
                    btnFactoryMode.setEnabled(false);
                    break;
                case R.id.btn_get_hardware_version:
                    Button btnGetHardwareVersion = selectedFragment.getView().findViewById(R.id.btn_get_hardware_version);
                    btnGetHardwareVersion.setEnabled(false);
                    break;
                case R.id.btn_mdb_start:
                    Button btnStartMdb = selectedFragment.getView().findViewById(R.id.btn_mdb_start);
                    btnStartMdb.setEnabled(false);
                    break;
                case R.id.btn_mdb_stop:
                    Button btnStopMdb = selectedFragment.getView().findViewById(R.id.btn_mdb_stop);
                    btnStopMdb.setEnabled(false);
                    break;
                default:
                    break;
            }
        }
    }

    private void enableHomeUi(){
        Button btnGetVersion = selectedFragment.getView().findViewById(R.id.btn_get_version);
        btnGetVersion.setEnabled(true);
        Button btnFactoryMode = selectedFragment.getView().findViewById(R.id.btn_factory_mode);
        btnFactoryMode.setEnabled(true);
        Button btnGetHardwareVersion = selectedFragment.getView().findViewById(R.id.btn_get_hardware_version);
        btnGetHardwareVersion.setEnabled(true);
        Button btnStartMdb = selectedFragment.getView().findViewById(R.id.btn_mdb_start);
        btnStartMdb.setEnabled(true);
        Button btnStopMdb = selectedFragment.getView().findViewById(R.id.btn_mdb_stop);
        btnStopMdb.setEnabled(true);
        setMdbConfig();
        setMdbOption();
    }

    private void setMdbConfig(){
        try {
            MDBConfig mdbConfig = new MDBConfig();
            mdbConfig.level = mdbValues.getMdbLevel();
            mdbConfig.scaleFactor = mdbValues.getX();
            mdbConfig.decimalPlace = mdbValues.getY();
            extBoardDevice.setConfig(mdbConfig);
        } catch (DeviceException e) {
            e.printStackTrace();
        }
    }

    private void setMdbOption(){
        try {
            MDBOption mdbOption = new MDBOption();
            mdbOption.enableAlwaysIdle = mdbValues.getOptionalFeature().isAlwaysIdleMdb();
            mdbOption.enableExpandedCurrency = mdbValues.getOptionalFeature().isMonetaryFormat32Mdb();
            mdbOption.enableNegativeVend = mdbValues.getOptionalFeature().isNegativeVendAllowedMdb();
            extBoardDevice.setOption(mdbOption);
        } catch (DeviceException e) {
            e.printStackTrace();
        }
    }

    private void detectMDBOffline(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (running){
                    try {
                        int status = extBoardDevice.detectMDBOffline(-1);
                        switch (status){
                            case 0: //offline
                                handler.obtainMessage(MDBUtils.RED_LOG, "MDB IS OFFLINE").sendToTarget();
                                break;
                            case 1: //online
                                handler.obtainMessage(MDBUtils.GREEN_LOG, "MDB IS ONLINE").sendToTarget();
                                break;
                            default:
                                break;
                        }
                    } catch (DeviceException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void cancelDetectMDB(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    extBoardDevice.cancelDetectMDB();
                } catch (DeviceException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}