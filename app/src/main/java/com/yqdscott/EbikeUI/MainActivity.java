package com.yqdscott.EbikeUI;

import android.Manifest;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.bumptech.glide.Glide;
import com.example.carlauncher.R;
import com.yqdscott.EbikeUI.model.CurrentWeatherResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "WeatherDebug";

    // 应用图标包名列表
    private static final String[] TARGET_PACKAGES = {
            "com.autonavi.amapautolite",    // 高德地图
            "com.sonyericsson.music",       // 索尼音乐
            "com.yqdscott.dashcam",    // 高德地图
            "com.android.settings"     // 系统设置
    };

    private TextView timeTextView;
    private TextView weatherTextView;
    private ImageView weatherIcon;
    private TextView speedTextView;
    private TextView speedUnitTextView;
    private TableLayout appsContainer;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private WeatherService weatherService;

    private int speedTapCount = 0;
    private long lastTapTime = 0;
    private long lastWeatherUpdateTime = 0;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏设置及背景透明
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setContentView(R.layout.activity_main);

        // 设置系统壁纸作为背景
        final WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        try {
            Drawable wallpaperDrawable = wallpaperManager.getDrawable();
            View rootView = findViewById(android.R.id.content);
            rootView.setBackground(wallpaperDrawable);
        } catch (Exception e) {
            Log.e(TAG, "设置壁纸失败", e);
        }

        // 初始化控件
        timeTextView = findViewById(R.id.timeTextView);
        weatherTextView = findViewById(R.id.weatherTextView);
        weatherIcon = findViewById(R.id.weatherIcon);
        speedTextView = findViewById(R.id.speedTextView);
        speedUnitTextView = findViewById(R.id.speedUnitTextView);
        appsContainer = findViewById(R.id.appsContainer);

        // 确保速度单位显示和天气图标可见
        speedUnitTextView.setText(getString(R.string.speed_unit));
        speedUnitTextView.setVisibility(View.VISIBLE);
        weatherIcon.setVisibility(View.VISIBLE);

        // 设置速度控件点击事件
        View speedContainer = findViewById(R.id.speedContainer);
        speedContainer.setOnClickListener(v -> handleSpeedTap());

        // 初始化定位服务
        initLocationService();

        // 初始化天气服务
        weatherService = new WeatherService();

        // 更新时间显示
        updateTimeDisplay();

        // 加载缓存的天气数据
        loadWeatherFromCache();

        // 加载应用图标
        loadAppIcons();

        // 根据屏幕调整布局
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        adjustLayoutForScreenSize(displayMetrics.widthPixels, displayMetrics.heightPixels);

        // 应用启动时立即更新天气
        Log.d(TAG, "应用启动，立即更新天气");
        handler.post(() -> updateWeatherDisplay());
    }

    @Override
    protected void onResume() {
        super.onResume();
        CurrentWeatherResponse cachedWeather = loadWeatherFromCache();
        if (cachedWeather != null && cachedWeather.getWeather() != null &&
                cachedWeather.getWeather().length > 0) {

            // 立即设置天气图标，不等待网络更新
            String iconCode = cachedWeather.getWeather()[0].getIcon();
            setWeatherIconByCode(iconCode);  // 先设置本地图标

            // 如果要继续尝试从网络加载图标，确保使用http而非https
            String iconUrl = "http://openweathermap.org/img/wn/" + iconCode + ".png";
            try {
                // 使用无缓存方式加载，确保获取最新图片
                Glide.with(MainActivity.this)
                        .load(iconUrl)
                        .skipMemoryCache(true)  // 跳过内存缓存
                        .error(weatherIcon.getDrawable())  // 出错时保留当前图标
                        .into(weatherIcon);
            } catch (Exception e) {
                Log.e(TAG, "恢复时加载天气图标失败: " + e.getMessage());
            }
        }
        startLocationUpdates();
        handler.postDelayed(timeUpdateRunnable, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
        handler.removeCallbacks(timeUpdateRunnable);
    }

    // 根据屏幕尺寸调整布局
    private void adjustLayoutForScreenSize(int width, int height) {
        View topBar = findViewById(R.id.topBar);
        float minDimension = Math.min(width, height);
        float scaleFactor = minDimension / 1080f;

        timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(48, 48 * scaleFactor));
        weatherTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(28, 28 * scaleFactor));
        speedTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(56, 56 * scaleFactor));
        speedUnitTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(22, 22 * scaleFactor));

        ViewGroup.LayoutParams params = topBar.getLayoutParams();
        params.height = (int) (height * 0.15);
        topBar.setLayoutParams(params);
    }

    // 定时更新时间
    private final Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeDisplay();
            handler.postDelayed(this, 1000);
        }
    };

    // 更新时间显示（12小时制）
    private void updateTimeDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.getDefault());
        symbols.setAmPmStrings(new String[]{"AM", "PM"});
        sdf.setDateFormatSymbols(symbols);
        String currentTime = sdf.format(new Date());
        timeTextView.setText(currentTime);
    }

    // 初始化定位服务及回调
    private void initLocationService() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        float speedKmh = location.hasSpeed() ? location.getSpeed() * 3.6f : 0;
                        speedTextView.setText(String.valueOf(Math.round(speedKmh)));

                        long currentMillis = System.currentTimeMillis();
                        // 每15分钟更新一次天气数据
                        if (currentMillis - lastWeatherUpdateTime > 15 * 60 * 1000) {
                            updateWeatherDisplay();
                            lastWeatherUpdateTime = currentMillis;
                        }
                    }
                }
            }
        };
    }

    // 开始位置更新
    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    // 停止位置更新
    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    // 处理位置权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    // 根据天气图标代码设置图标
    private void setWeatherIconByCode(String iconCode) {
        int resourceId;
        if (iconCode != null) {
            if (iconCode.startsWith("01")) {  // 晴天
                resourceId = android.R.drawable.ic_menu_day;
            } else if (iconCode.startsWith("02") || iconCode.startsWith("03")) {  // 少云/多云
                resourceId = android.R.drawable.ic_menu_compass;
            } else if (iconCode.startsWith("04")) {  // 阴天
                resourceId = android.R.drawable.ic_menu_zoom;
            } else if (iconCode.startsWith("09") || iconCode.startsWith("10")) {  // 雨
                resourceId = android.R.drawable.ic_menu_call;
            } else if (iconCode.startsWith("11")) {  // 雷雨
                resourceId = android.R.drawable.ic_menu_camera;
            } else if (iconCode.startsWith("13")) {  // 雪
                resourceId = android.R.drawable.ic_menu_add;
            } else if (iconCode.startsWith("50")) {  // 雾
                resourceId = android.R.drawable.ic_menu_directions;
            } else {
                resourceId = android.R.drawable.ic_menu_help;
            }
        } else {
            resourceId = android.R.drawable.ic_menu_help;
        }
        weatherIcon.setImageResource(resourceId);
        weatherIcon.setVisibility(View.VISIBLE);
    }

    // 更新天气显示
    private void updateWeatherDisplay() {
        Log.d(TAG, "MainActivity: 开始更新天气");

        weatherService.getCurrentWeather(new WeatherService.CurrentWeatherCallback() {
            @Override
            public void onCurrentWeatherReceived(CurrentWeatherResponse weather) {
                Log.d(TAG, "收到当前天气数据");

                if (weather != null && weather.getWeather() != null && weather.getWeather().length > 0) {
                    Log.d(TAG, "处理天气数据");
                    CurrentWeatherResponse.Weather weatherItem = weather.getWeather()[0];
                    final float tempInCelsius = weather.getMain().getTemp(); // 摄氏度
                    final int temperature = Math.round(tempInCelsius);
                    final String condition = weatherItem.getDescription();
                    final String icon = weatherItem.getIcon();

                    Log.d(TAG, "温度: " + temperature + ", 天气: " + condition);
                    Log.d(TAG, "图标代码: " + icon);

                    runOnUiThread(() -> {
                        Log.d(TAG, "更新 UI");
                        if (weatherTextView != null) {
                            weatherTextView.setText("汕头市：" + temperature + "°C " + condition);
                            weatherTextView.setVisibility(View.VISIBLE);
                        }

                        if (weatherIcon != null) {
                            setWeatherIconByCode(icon);
                            try {
                                String iconUrl = "http://openweathermap.org/img/wn/" + icon + ".png";
                                Glide.with(MainActivity.this).load(iconUrl).override(25, 25).into(weatherIcon);
                                weatherIcon.post(() -> {
                                    weatherIcon.invalidate();
                                    weatherIcon.requestLayout();
                                });

                                Log.d(TAG, "尝试加载天气图标: " + iconUrl);
                            } catch (Exception e) {
                                Log.e(TAG, "加载天气图标失败: " + e.getMessage());
                            }
                        }

                        View parent = findViewById(R.id.timeWeatherContainer);
                        if (parent != null) {
                            parent.invalidate();
                            parent.requestLayout();
                        }
                    });

                    saveWeatherToCache(weather);
                    Log.d(TAG, "保存到缓存");
                } else {
                    Log.e(TAG, "天气数据为空或不完整");
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "天气更新错误: " + errorMessage);
                CurrentWeatherResponse cachedWeather = loadWeatherFromCache();
                if (cachedWeather != null) {
                    Log.d(TAG, "使用缓存数据");
                }
            }
        });
    }

    // 保存当前天气数据到缓存
    private void saveWeatherToCache(CurrentWeatherResponse weather) {
        try {
            Log.d(TAG, "保存天气数据到缓存");
            SharedPreferences prefs = getSharedPreferences("WeatherCache", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            Gson gson = new Gson();
            String weatherJson = gson.toJson(weather);

            editor.putString("last_weather", weatherJson);
            editor.putLong("timestamp", System.currentTimeMillis());
            editor.apply();

            Log.d(TAG, "天气数据缓存成功");
        } catch (Exception e) {
            Log.e(TAG, "缓存天气数据失败: " + e.getMessage(), e);
        }
    }

    // 从缓存加载当前天气数据
    private CurrentWeatherResponse loadWeatherFromCache() {
        try {
            Log.d(TAG, "尝试从缓存加载天气数据");
            SharedPreferences prefs = getSharedPreferences("WeatherCache", MODE_PRIVATE);
            String weatherJson = prefs.getString("last_weather", null);

            if (weatherJson != null) {
                Log.d(TAG, "找到缓存的天气数据");
                Gson gson = new Gson();
                CurrentWeatherResponse weather = gson.fromJson(weatherJson, CurrentWeatherResponse.class);

                if (weather != null && weather.getWeather() != null && weather.getWeather().length > 0) {
                    CurrentWeatherResponse.Weather weatherItem = weather.getWeather()[0];
                    final float tempInCelsius = weather.getMain().getTemp();
                    final int temperature = Math.round(tempInCelsius);
                    final String condition = weatherItem.getDescription();
                    final String icon = weatherItem.getIcon();

                    Log.d(TAG, "从缓存加载的天气数据: " + temperature + "°C " + condition);

                    runOnUiThread(() -> {
                        if (weatherTextView != null) {
                            weatherTextView.setText("汕头市：" + temperature + "°C " + condition);
                            weatherTextView.setVisibility(View.VISIBLE);
                        }

                        if (weatherIcon != null) {
                            setWeatherIconByCode(icon);
                            weatherIcon.setVisibility(View.VISIBLE);
                        }

                        View parent = findViewById(R.id.timeWeatherContainer);
                        if (parent != null) {
                            parent.invalidate();
                            parent.requestLayout();
                        }
                    });
                    return weather;
                }
            } else {
                Log.d(TAG, "没有找到缓存的天气数据");
            }
        } catch (Exception e) {
            Log.e(TAG, "加载缓存天气数据失败: " + e.getMessage(), e);
        }
        return null;
    }

    // 处理速度表点击事件（连续点击3次返回主屏幕）
    private void handleSpeedTap() {
        long now = System.currentTimeMillis();
        if (now - lastTapTime > 2000) {
            speedTapCount = 1;
        } else {
            speedTapCount++;
        }
        lastTapTime = now;
        if (speedTapCount >= 3) {
            returnToOriginalLauncher();
            speedTapCount = 0;
        }
    }

    // 返回原始启动器
    private void returnToOriginalLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        startActivity(intent);
    }

    // 加载应用图标
    private void loadAppIcons() {
        Log.d(TAG, "开始加载应用图标");
        PackageManager pm = getPackageManager();
        TableLayout tableLayout = findViewById(R.id.appsContainer);
        tableLayout.removeAllViews();

        TableRow currentRow = null;
        int appCount = 0;

        for (String packageName : TARGET_PACKAGES) {
            try {
                if (appCount % 2 == 0) {
                    currentRow = new TableRow(this);
                    tableLayout.addView(currentRow);
                }

                Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                if (launchIntent == null) {
                    Log.e(TAG, "无法获取启动意图: " + packageName);
                    continue;
                }

                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                String appName = pm.getApplicationLabel(appInfo).toString();
                Drawable appIcon = pm.getApplicationIcon(packageName);

                View appView = getLayoutInflater().inflate(R.layout.app_item, null);
                ImageView iconView = appView.findViewById(R.id.appIcon);
                TextView nameView = appView.findViewById(R.id.appName);

                iconView.setImageDrawable(appIcon);
                nameView.setText(appName);

                final String pkgName = packageName;
                appView.setOnClickListener(v -> {
                    try {
                        Intent intent = pm.getLaunchIntentForPackage(pkgName);
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "无法启动应用", Toast.LENGTH_SHORT).show();
                    }
                });

                TableRow.LayoutParams params = new TableRow.LayoutParams(
                        0, TableRow.LayoutParams.WRAP_CONTENT, 1.0f);
                params.setMargins(20, 20, 20, 20);
                appView.setLayoutParams(params);

                if (currentRow != null) {
                    currentRow.addView(appView);
                    appCount++;
                    Log.d(TAG, "成功添加应用: " + appName);
                }
            } catch (Exception e) {
                Log.e(TAG, "处理应用时出错: " + packageName, e);
            }
        }

        if (currentRow != null && currentRow.getChildCount() == 1) {
            View emptyView = new View(this);
            TableRow.LayoutParams params = new TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, 1.0f);
            emptyView.setLayoutParams(params);
            currentRow.addView(emptyView);
        }

        Log.d(TAG, "应用加载完成，共添加 " + appCount + " 个应用");
    }
}