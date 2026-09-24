package com.clearsky.weather

data class Hour(val time:String,val temp:Double,val feels:Double,val precip:Double,val pop:Int,val code:Int,val wind:Double,val windDir:Int,val gust:Double,val humidity:Int,val dew:Double,val pressure:Double,val uv:Double)
data class Day(val date:String,val hi:Double,val lo:Double,val pop:Int,val precip:Double,val sunrise:String,val sunset:String,val uv:Double,val code:Int)
data class Air(val usAqi:Int,val pm25:Double,val ozone:Double)
data class AlertItem(val id:String,val event:String,val headline:String,val severity:String,val description:String)
data class Weather(val name:String,val lat:Double,val lon:Double,val timezone:String,val currentTemp:Double,val apparent:Double,val humidity:Int,val wind:Double,val windDir:Int,val gust:Double,val pressure:Double,val code:Int,val hours:List<Hour>,val days:List<Day>,val air:Air?,val alerts:List<AlertItem>)
data class Place(val name:String,val lat:Double,val lon:Double)
