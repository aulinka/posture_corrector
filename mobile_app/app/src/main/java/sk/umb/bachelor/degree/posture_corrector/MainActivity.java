package sk.umb.bachelor.degree.posture_corrector;

import static java.lang.Float.NaN;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.color.MaterialColors;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    private boolean isServiceRunning = false;
    private Button connectButton;
    private TextView stateTextView;
    private AppDatabase db;

    TextView percentOfStretchTextView;
    TextView usageTimeTextView;
    TextView hunchTimeTextView;
    TextView hunchCountTextView;

    static class BluetoothServiceConnection implements ServiceConnection {
        public BluetoothService bluetoothService;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i("aaa", "onServiceConnected: ");
            BluetoothService.BluetoothServiceBinder binder = (BluetoothService.BluetoothServiceBinder) service;
            this.bluetoothService = binder.getService();

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }

    BluetoothServiceConnection serviceConnection = new BluetoothServiceConnection();


    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("connection-change")) {
                Log.d("AAA", "ConnectionChange");
                updateConnectedStateAndElements();
            } else if (intent.getAction().equals("data-change")) {
                updateStatistics();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (db == null) db = AppDatabase.getInstance(this);

        stateTextView = findViewById(R.id.stateTextView);
        connectButton = findViewById(R.id.connectButton);
        percentOfStretchTextView = findViewById(R.id.percentOfStretchTextView);
        usageTimeTextView = findViewById(R.id.usageTimeTextView);
        hunchTimeTextView = findViewById(R.id.hunchTimeTextView);
        hunchCountTextView = findViewById(R.id.hunchCountTextView);

        isServiceRunning = BluetoothService.isRunning(this);
        updateConnectedStateAndElements();
        if (isServiceRunning) {
            bindService(new Intent(this, BluetoothService.class),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
            );
        }

        connectButton.setOnClickListener(v -> {
            isServiceRunning = BluetoothService.isRunning(this);
            if (!isServiceRunning) {
                Intent serviceIntent = new Intent(this, BluetoothService.class);
                startForegroundService(serviceIntent);

                bindService(new Intent(this, BluetoothService.class),
                        serviceConnection,
                        Context.BIND_AUTO_CREATE
                );
            } else if (serviceConnection.bluetoothService != null) {
                if (serviceConnection.bluetoothService.getDevice() != null && serviceConnection.bluetoothService.getDevice().getState() == PostureCorrectorDevice.State.STATE_CONNECTED) {
                    serviceConnection.bluetoothService.disconnect();
                } else {
                    serviceConnection.bluetoothService.connect();
                }
            }
        });
        updateStatistics();
        updateChart();
    }

    private void updateChart() {
        DayStatisticDao dao = db.dayStatisticDao();
        BarChart barChart = findViewById(R.id.chart);

        ArrayList<BarEntry> entries = new ArrayList<>();
        List<String> xAxisLabels = new ArrayList<>();
        LocalDate currentDate = LocalDate.now().minusDays(4);
        for (int i = 0; i < 5; i++) {
            DayStatistic statistic = dao.getByDate(currentDate);
            if (statistic == null) statistic = new DayStatistic();
            entries.add(new BarEntry(i, calculatePercentOfStretch(statistic)));
            xAxisLabels.add(currentDate.getDayOfMonth() + "." + currentDate.getMonthValue() + ".");
            currentDate = currentDate.plusDays(1);
        }

        BarDataSet dataSet = new BarDataSet(entries, "% času vystierania");

        dataSet.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.BLACK));

        BarData barData = new BarData(dataSet);

        barChart.setData(barData);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xAxisLabels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1.0f);
        xAxis.setCenterAxisLabels(false);

        YAxis yAxis = barChart.getAxisLeft();
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(100f);

        barChart.getDescription().setEnabled(false);
        barChart.getBarData().setValueTextSize(15.0f);

        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            barChart.setBackgroundColor(Color.WHITE);
            barChart.setGridBackgroundColor(Color.DKGRAY);
            barChart.getDescription().setTextColor(Color.BLACK);
            barChart.getLegend().setTextColor(Color.BLACK);
        }

        // Refresh the chart
        barChart.invalidate();
    }

    private void updateStatistics() {
        DayStatisticDao dao = db.dayStatisticDao();
        DayStatistic dayStatistic = dao.getOrCreateToday();
        float percentOfStretch = calculatePercentOfStretch(dayStatistic);
        percentOfStretchTextView.setText("Približné % času vystierania: " + (int)Math.round(percentOfStretch) + "%");
        usageTimeTextView.setText("Počet minút používania dnes: " + (int)Math.floor(dayStatistic.usageDuration / 60) + " minút");
        hunchTimeTextView.setText("Počet minút zhrbenia dnes: " + (int)Math.floor(dayStatistic.hunchedPostureDuration / 60) + " minút");
        hunchCountTextView.setText("Počet zhrbení dnes: " + dayStatistic.hunchedCount);
    }

    private float calculatePercentOfStretch(DayStatistic dayStatistic) {
        float val = ((float)(dayStatistic.usageDuration - dayStatistic.hunchedPostureDuration) / dayStatistic.usageDuration) * 100;
        if (Float.isNaN(val)) return 0.0f;
        return val;
    }

    private void updateConnectedStateAndElements() {
        PostureCorrectorDevice.State state = PostureCorrectorDevice.State.STATE_DISCONNECTED;
        if (serviceConnection.bluetoothService != null && serviceConnection.bluetoothService.getDevice() != null) {
            state = serviceConnection.bluetoothService.getDevice().getState();
        }
        if (state == PostureCorrectorDevice.State.STATE_CONNECTED) {
            stateTextView.setText("Stav: Pripojené");
            connectButton.setEnabled(true);
            connectButton.setText("Odpojiť");
        } else if (state == PostureCorrectorDevice.State.STATE_DISCONNECTED) {
            stateTextView.setText("Stav: Odpojené");
            connectButton.setEnabled(true);
            connectButton.setText("Pripojiť");
        } else if (state == PostureCorrectorDevice.State.STATE_CONNECTING) {
            stateTextView.setText("Stav: Pripájanie");
            connectButton.setEnabled(false);
            connectButton.setText("Pripojiť");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction("connection-change");
        filter.addAction("data-change");
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
    }
}