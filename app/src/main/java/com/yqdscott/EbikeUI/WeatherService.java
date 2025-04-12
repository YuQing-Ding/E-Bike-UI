package com.yqdscott.EbikeUI;

import android.os.AsyncTask;
import android.util.Log;

import com.yqdscott.EbikeUI.model.CurrentWeatherResponse;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class WeatherService {
    private static final String TAG = "WeatherDebug";
    private static final String WEATHER_URL =
            "https://api.openweathermap.org/data/2.5/weather?id=1795940&appid=b82222794e7ae3c31e69c00093261f04&lang=zh_cn&units=metric";

    // 获取当前天气的方法
    public void getCurrentWeather(final CurrentWeatherCallback callback) {
        Log.d(TAG, "开始获取当前天气数据");
        new FetchCurrentWeatherTask(callback).execute();
    }

    // 异步任务类，用于在后台执行网络请求
    private class FetchCurrentWeatherTask extends AsyncTask<Void, Void, String> {
        private final CurrentWeatherCallback callback;
        private String errorMessage;

        public FetchCurrentWeatherTask(CurrentWeatherCallback callback) {
            this.callback = callback;
            Log.d(TAG, "创建 FetchCurrentWeatherTask");
        }

        @Override
        protected String doInBackground(Void... params) {
            Log.d(TAG, "开始后台任务");
            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                Log.d(TAG, "尝试连接: " + WEATHER_URL);
                URL url = new URL(WEATHER_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);

                Log.d(TAG, "开始连接");
                connection.connect();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "HTTP 响应代码: " + responseCode);

                if (responseCode != 200) {
                    errorMessage = "HTTP 错误: " + responseCode;
                    Log.e(TAG, errorMessage);
                    return null;
                }

                Log.d(TAG, "开始读取响应");
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                String result = response.toString();
                Log.d(TAG, "收到响应，长度: " + result.length() + " 字节");
                if (result.length() > 0) {
                    Log.d(TAG, "响应前 100 个字符: " + result.substring(0, Math.min(100, result.length())));
                }

                return result;

            } catch (IOException e) {
                Log.e(TAG, "IO 异常: " + e.getMessage(), e);
                errorMessage = "网络错误: " + e.getMessage();
                return null;
            } catch (Exception e) {
                Log.e(TAG, "未知异常: " + e.getMessage(), e);
                errorMessage = "错误: " + e.getMessage();
                return null;
            } finally {
                Log.d(TAG, "清理资源");
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(TAG, "关闭 reader 出错", e);
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "后台任务完成");

            if (result != null) {
                Log.d(TAG, "收到数据，尝试解析");
                try {
                    Gson gson = new Gson();
                    CurrentWeatherResponse response = gson.fromJson(result, CurrentWeatherResponse.class);

                    if (response != null && response.getWeather() != null && response.getWeather().length > 0) {
                        Log.d(TAG, "解析成功");
                        callback.onCurrentWeatherReceived(response);
                    } else {
                        Log.e(TAG, "天气数据为空或不完整");
                        callback.onError("天气数据为空或不完整");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析异常: " + e.getMessage(), e);
                    callback.onError("JSON 解析错误: " + e.getMessage());
                }
            } else {
                Log.e(TAG, "获取数据失败: " + errorMessage);
                callback.onError(errorMessage);
            }
        }
    }

    // 回调接口，用于返回天气数据或错误信息
    public interface CurrentWeatherCallback {
        void onCurrentWeatherReceived(CurrentWeatherResponse weather);
        void onError(String errorMessage);
    }
}