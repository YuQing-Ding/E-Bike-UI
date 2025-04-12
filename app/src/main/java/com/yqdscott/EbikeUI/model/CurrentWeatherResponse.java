package com.yqdscott.EbikeUI.model;

import com.google.gson.annotations.SerializedName;

public class CurrentWeatherResponse {
    @SerializedName("weather")
    private Weather[] weather;

    @SerializedName("main")
    private Main main;

    public Weather[] getWeather() {
        return weather;
    }

    public Main getMain() {
        return main;
    }

    public static class Weather {
        @SerializedName("description")
        private String description;

        @SerializedName("icon")
        private String icon;

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }
    }

    public static class Main {
        @SerializedName("temp")
        private float temp;

        public float getTemp() {
            return temp;
        }
    }
}