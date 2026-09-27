package com.clearsky.weather

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

class MainActivity : ComponentActivity() {
 private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
 private val perm=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){locate()}
 private val notifyPerm=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
 private var notificationAlert by mutableStateOf<AlertItem?>(null)
 private var supportProducts by mutableStateOf<List<SupportProduct>>(emptyList())
 private var supportPurchased by mutableStateOf(false)
 private lateinit var supportBilling: SupportBillingManager

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("prefs", 0)
  supportPurchased = prefs.getBoolean("support_completed", false)
  supportBilling = SupportBillingManager(
   this,
   onProducts = { products -> runOnUiThread { supportProducts = products } },
   onSupportRecognized = {
    runOnUiThread {
     supportPurchased = true
     prefs.edit().putBoolean("support_completed", true).apply()
    }
   }
  )
  supportBilling.start()
  notificationAlert = alertFromIntent(intent)
  AlertWorker.schedule(this)
  WeatherStatusWorker.schedule(this)
  if (Build.VERSION.SDK_INT >= 33 &&
   ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
  ) {
   notifyPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
  setContent { App() }
 }

 override fun onDestroy() {
  if (::supportBilling.isInitialized) supportBilling.close()
  super.onDestroy()
 }

 override fun onNewIntent(intent: Intent) {
  super.onNewIntent(intent)
  setIntent(intent)
  notificationAlert = alertFromIntent(intent)
 }

 @Composable
 private fun App() {
  val prefs = remember { getSharedPreferences("prefs", 0) }
  var theme by remember { mutableStateOf(prefs.getString("theme", "system") ?: "system") }
  val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
  val now = System.currentTimeMillis()
  val thirtyDays = 30L * 24 * 60 * 60 * 1000
  val oneYear = 365L * 24 * 60 * 60 * 1000
  val installedAt = remember {
   val saved = prefs.getLong("donation_install_time", 0L)
   if (saved > 0L) saved else now.also {
    prefs.edit().putLong("donation_install_time", it).apply()
   }
  }
  val nextPromptAt = remember {
   val saved = prefs.getLong("support_next_prompt_at", 0L)
   if (saved > 0L) {
    saved
   } else {
    val migrated = if (prefs.getBoolean("donation_prompt_shown", false)) now + oneYear else installedAt + thirtyDays
    prefs.edit().putLong("support_next_prompt_at", migrated).apply()
    migrated
   }
  }
  var showSupport by remember { mutableStateOf(false) }
  var automaticPrompt by remember { mutableStateOf(false) }
  val promptDue = !supportPurchased && supportProducts.isNotEmpty() && now >= nextPromptAt

  LaunchedEffect(promptDue) {
   if (promptDue) {
    automaticPrompt = true
    showSupport = true
   }
  }
  LaunchedEffect(supportPurchased) {
   if (supportPurchased) showSupport = false
  }

  fun dismissSupport() {
   if (automaticPrompt && !supportPurchased) {
    prefs.edit().putLong("support_next_prompt_at", System.currentTimeMillis() + oneYear).apply()
   }
   showSupport = false
   automaticPrompt = false
  }

  MaterialTheme(
   colorScheme = if (dark) {
    darkColorScheme(primary = Color(0xFF8FCBFF), surface = Color(0xFF101820))
   } else {
    lightColorScheme(primary = Color(0xFF00639A), surface = Color(0xFFF6FAFD))
   }
  ) {
   if (showSupport) {
    SupportDialog(
     products = supportProducts,
     supported = supportPurchased,
     onSupport = { supportBilling.purchase(it) },
     onDismiss = { dismissSupport() }
    )
   }
   notificationAlert?.let { AlertDetailsDialog(it) { notificationAlert = null } }
   WeatherScreen(
    theme = theme,
    setTheme = {
     theme = it
     prefs.edit().putString("theme", it).apply()
    },
    supported = supportPurchased,
    onSupport = {
     automaticPrompt = false
     showSupport = true
    }
   )
  }
 }
 @Composable private fun WeatherScreen(theme:String,setTheme:(String)->Unit,supported:Boolean,onSupport:()->Unit){
  var places by remember{mutableStateOf(readPlaces())};var search by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)};var results by remember{mutableStateOf<List<Place>>(emptyList())};var q by remember{mutableStateOf("")};val scope=rememberCoroutineScope();val pager=rememberPagerState(pageCount={places.size.coerceAtLeast(1)})
  LaunchedEffect(Unit){if(places.isEmpty()&&ContextCompat.checkSelfPermission(this@MainActivity,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestLocation()}}
  Scaffold(containerColor=MaterialTheme.colorScheme.surface){pad->Column(Modifier.fillMaxSize().padding(pad)){
   Row(Modifier.fillMaxWidth().padding(12.dp,8.dp),verticalAlignment=Alignment.CenterVertically){Text("ClearSky",fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={search=!search;settings=false}){Text(if(search)"Close" else "Locations")};TextButton(onClick={settings=!settings;search=false}){Text(if(settings)"Close" else "Settings")}}
   AnimatedVisibility(settings){Column(Modifier.padding(horizontal=16.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Appearance",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("system" to "System","light" to "Light","dark" to "Dark").forEach{(v,l)->FilterChip(selected=theme==v,onClick={setTheme(v)},label={Text(l)})}};AboutCard(supported,onSupport)}}
   AnimatedVisibility(search){Column(Modifier.padding(horizontal=16.dp)){OutlinedTextField(q,{q=it},label={Text("City or ZIP/place name")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(onClick={scope.launch{results=withContext(Dispatchers.IO){WeatherRepository.search(q)}}},modifier=Modifier.fillMaxWidth()){Text("Search")};OutlinedButton(onClick={requestLocation()},modifier=Modifier.fillMaxWidth()){Text("Use my current location")};results.forEach{r->Card(onClick={places=addSaved(r);search=false;scope.launch{pager.animateScrollToPage(places.indexOfFirst{same(it,r)}.coerceAtLeast(0))}},modifier=Modifier.fillMaxWidth().padding(vertical=3.dp)){Text("Save & view  ${r.name}",Modifier.padding(14.dp))}}}}
   if(places.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center){Card(shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("Set your location",fontSize=22.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text("ClearSky needs location permission to show weather for your current location. You can also use Locations above to search for a city manually.",textAlign=TextAlign.Center);Spacer(Modifier.height(16.dp));Button(onClick={requestLocation()},modifier=Modifier.fillMaxWidth()){Text("Allow location")};OutlinedButton(onClick={search=true;settings=false},modifier=Modifier.fillMaxWidth()){Text("Search for a location instead")}}}} else HorizontalPager(state=pager,modifier=Modifier.weight(1f)){i->WeatherPage(places[i],i,places.size,{if(i>0){places=removeSaved(places[i]);scope.launch{pager.scrollToPage((i-1).coerceAtLeast(0))}}})}
   if(places.size>1) Row(Modifier.align(Alignment.CenterHorizontally).padding(7.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){repeat(places.size){i->Box(Modifier.size(if(i==pager.currentPage)8.dp else 6.dp).clip(CircleShape).background(if(i==pager.currentPage)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))}}
  }}
 }
 @OptIn(ExperimentalMaterial3Api::class)
 @Composable
 private fun WeatherPage(place: Place, index: Int, count: Int, onDelete: () -> Unit) {
  var weather by remember(place) { mutableStateOf<Weather?>(null) }
  var error by remember(place) { mutableStateOf<String?>(null) }
  var refreshing by remember(place) { mutableStateOf(false) }
  var loading by remember(place) { mutableStateOf(false) }
  var refreshRequest by remember(place) { mutableIntStateOf(0) }
  var selectedDay by remember(place) { mutableStateOf<Day?>(null) }
  var selectedAlert by remember(place) { mutableStateOf<AlertItem?>(null) }
  val scope = rememberCoroutineScope()

  suspend fun refreshWeather(showRefreshIndicator: Boolean) {
   if (loading) return
   loading = true
   refreshing = showRefreshIndicator

   val result = withContext(Dispatchers.IO) {
    runCatching { WeatherRepository.load(place.name, place.lat, place.lon) }
   }

   result.onSuccess { updated ->
    weather = updated
    error = null
    if (index == 0) save(updated)
   }.onFailure { failure ->
    error = failure.message ?: "Weather update failed"
   }

   refreshing = false
   loading = false
  }

  LaunchedEffect(place, refreshRequest) {
   refreshWeather(showRefreshIndicator = false)
   while (isActive) {
    delay(15 * 60 * 1000L)
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
     refreshWeather(showRefreshIndicator = false)
    }
   }
  }

  DisposableEffect(place) {
   val observer = LifecycleEventObserver { _, event ->
    if (event == Lifecycle.Event.ON_RESUME && weather != null) {
     refreshRequest++
    }
   }
   lifecycle.addObserver(observer)
   onDispose { lifecycle.removeObserver(observer) }
  }

  PullToRefreshBox(
   isRefreshing = refreshing,
   onRefresh = { scope.launch { refreshWeather(showRefreshIndicator = true) } },
   modifier = Modifier.fillMaxSize()
  ) {
   LazyColumn(
    Modifier.fillMaxSize().padding(horizontal = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(bottom = 24.dp)
   ) {
    if (error != null && weather == null) {
     item { Text("Couldn't load weather: $error") }
    }
    if (weather == null && error == null) {
     item {
      Box(
       Modifier.fillParentMaxHeight(.7f),
       contentAlignment = Alignment.Center
      ) { CircularProgressIndicator() }
     }
    }
    weather?.let { x ->
     if (error != null) {
      item {
       Text(
        "Last update failed. Pull down to try again.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
       )
      }
     }
     item { Hero(x, index, count, onDelete) }
     if (x.alerts.isNotEmpty()) item {
      Card(
       colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
       shape = RoundedCornerShape(22.dp)
      ) {
       Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⚠  Active weather alerts", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        x.alerts.take(4).forEach { alert ->
         Card(
          onClick = { selectedAlert = alert },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          shape = RoundedCornerShape(14.dp)
         ) {
          Column(Modifier.fillMaxWidth().padding(10.dp)) {
           Text(alert.event, fontWeight = FontWeight.Bold)
           Text(alert.headline, fontSize = 13.sp)
           Text("Tap to read full alert", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
         }
        }
       }
      }
     }
     item {
      Section("Next 24 hours") {
       LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(upcomingHours(x)) { h ->
         Card(shape = RoundedCornerShape(18.dp)) {
          Column(
           Modifier.width(105.dp).padding(10.dp),
           horizontalAlignment = Alignment.CenterHorizontally
          ) {
           Text(hour(h.time), fontSize = 12.sp)
           Text(weatherIcon(h.code), fontSize = 28.sp)
           Text("${h.temp.toInt()}°", fontSize = 23.sp, fontWeight = FontWeight.Bold)
           Text("☂ ${h.pop}%", fontSize = 12.sp)
           Text("${WeatherRepository.compass(h.windDir)} ${h.wind.toInt()} mph", fontSize = 11.sp)
          }
         }
        }
       }
      }
     }
     item {
      Section("10-day forecast") {
       val min = x.days.minOfOrNull { it.lo } ?: 0.0
       val max = x.days.maxOfOrNull { it.hi } ?: 100.0
       x.days.forEach { d -> DailyRow(d, min, max) { selectedDay = d } }
      }
     }
     item {
      Section("Details") {
       val h = upcomingHours(x).firstOrNull()
       Metric("Humidity", x.humidity.toDouble(), 100.0, "${x.humidity}%", listOf(Color(0xFF64B5F6), Color(0xFF1565C0)))
       Metric(
        "Wind / gust",
        x.gust.coerceAtLeast(x.wind),
        50.0,
        "${WeatherRepository.compass(x.windDir)} ${x.wind.toInt()} mph  •  gust ${x.gust.toInt()}",
        listOf(Color(0xFF80CBC4), Color(0xFF00897B))
       )
       Text("Pressure ${x.pressure.toInt()} hPa  •  Dew point ${h?.dew?.toInt() ?: 0}°F\nPrecipitation ${h?.precip ?: 0.0} in", fontSize = 14.sp)
       Spacer(Modifier.height(10.dp))
       UvMeter(h?.uv ?: 0.0)
      }
     }
     item {
      Section("Air quality") {
       x.air?.let {
        AqiMeter(it.usAqi)
        Spacer(Modifier.height(8.dp))
        Text("PM2.5 ${"%.1f".format(it.pm25)} µg/m³  •  Ozone ${"%.0f".format(it.ozone)} µg/m³", fontSize = 14.sp)
        it.pollen?.let { pollen ->
         Spacer(Modifier.height(12.dp))
         PollenMeter(pollen)
        }
        Spacer(Modifier.height(12.dp))
        AllergyConditions(x)
       } ?: Text("Air-quality data unavailable")
      }
     }
     item { Section("Sun") { SunArc(x.days.firstOrNull()) } }
     item {
      RadarCard {
       startActivity(
        Intent(this@MainActivity, RadarActivity::class.java)
         .putExtra("lat", x.lat)
         .putExtra("lon", x.lon)
       )
      }
     }
    }
   }

   selectedDay?.let { d ->
    DayDetailsDialog(d, weather?.hours.orEmpty()) { selectedDay = null }
   }
   selectedAlert?.let { alert ->
    AlertDetailsDialog(alert) { selectedAlert = null }
   }
  }
 }
 @Composable private fun Hero(x:Weather,index:Int,count:Int,onDelete:()->Unit){val night=isNight(x.days.firstOrNull());val colors=if(night) listOf(Color(0xFF172554),Color(0xFF334155)) else when(x.code){in 51..99->listOf(Color(0xFF355C7D),Color(0xFF6C7A89));in 1..3->listOf(Color(0xFF4B79A1),Color(0xFF8CBED6));else->listOf(Color(0xFF1976D2),Color(0xFF64B5F6))};Card(shape=RoundedCornerShape(28.dp)){Column(Modifier.fillMaxWidth().background(Brush.linearGradient(colors)).padding(20.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(x.name,color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold);if(count>1)Text("Swipe left or right for saved locations",color=Color.White.copy(.8f),fontSize=11.sp)};if(index>0)TextButton(onClick=onDelete){Text("Remove",color=Color.White)}};Row(verticalAlignment=Alignment.CenterVertically){Text("${x.currentTemp.toInt()}°",color=Color.White,fontSize=70.sp,fontWeight=FontWeight.Light);Spacer(Modifier.weight(1f));Text(weatherIcon(x.code),fontSize=62.sp)};Text(WeatherRepository.label(x.code),color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Medium);Text("Feels ${x.apparent.toInt()}°  •  Humidity ${x.humidity}%",color=Color.White.copy(.92f));Text("Wind ${WeatherRepository.compass(x.windDir)} ${x.wind.toInt()} mph  •  Gust ${x.gust.toInt()} mph",color=Color.White.copy(.92f),fontSize=13.sp)}}}
 @Composable
 private fun DailyRow(d: Day, min: Double, max: Double, onClick: () -> Unit) {
  Column(
   Modifier
    .fillMaxWidth()
    .clickable(onClick = onClick)
    .padding(vertical = 8.dp)
  ) {
   Row(verticalAlignment = Alignment.CenterVertically) {
    Text(day(d.date), Modifier.width(86.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    Text(weatherIcon(d.code), fontSize = 23.sp, modifier = Modifier.width(38.dp))
    Text(WeatherRepository.label(d.code), Modifier.weight(1f), fontSize = 13.sp)
    Text("☂ ${d.pop}%", fontSize = 12.sp)
    Spacer(Modifier.width(8.dp))
    Text("${d.lo.toInt()}°", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(5.dp))
    Box(
     Modifier.width(55.dp).height(7.dp).clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
     val span = (max - min).coerceAtLeast(1.0)
     val start = ((d.lo - min) / span).toFloat().coerceIn(0f, 1f)
     val width = ((d.hi - d.lo) / span).toFloat().coerceIn(.08f, 1f)
     Box(
      Modifier.fillMaxHeight().fillMaxWidth(width).offset(x = (55 * start).dp)
       .clip(CircleShape)
       .background(Brush.horizontalGradient(listOf(Color(0xFF64B5F6), Color(0xFFFFB74D))))
     )
    }
    Spacer(Modifier.width(5.dp))
    Text("${d.hi.toInt()}°", fontWeight = FontWeight.Bold)
    Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
 }

 @Composable
 private fun DayDetailsDialog(day: Day, allHours: List<Hour>, onDismiss: () -> Unit) {
  val hours = remember(day.date, allHours) { allHours.filter { it.time.startsWith(day.date) } }
  val timeLabels = remember(hours) { hours.map { hour(it.time) } }
  Dialog(onDismissRequest = onDismiss) {
   Card(shape = RoundedCornerShape(24.dp)) {
    LazyColumn(
     Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(18.dp),
     verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
     item {
      Row(verticalAlignment = Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) {
        Text(day(day.date), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
         "${WeatherRepository.label(day.code)}  •  ${day.lo.toInt()}° / ${day.hi.toInt()}°  •  Daily rain max ${day.pop}%",
         color = MaterialTheme.colorScheme.onSurfaceVariant
        )
       }
       TextButton(onClick = onDismiss) { Text("Close") }
      }
     }
     if (hours.isEmpty()) {
      item { Text("Hourly details are unavailable for this day.") }
     } else {
      item {
       Text("Temperature", fontWeight = FontWeight.Bold)
       Text(
        "Hourly temperatures for this day.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
       )
       WeatherGraph(hours.map { it.temp }, timeLabels, "°F")
      }
      item {
       Text("Hourly rain chance", fontWeight = FontWeight.Bold)
       Text(
        "0% to 100% shows the chance of measurable precipitation for each hour.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
       )
       WeatherGraph(hours.map { it.pop.toDouble() }, timeLabels, "%", fixedMin = 0.0, fixedMax = 100.0)
      }
      item { HourBlock("AM", hours.filter { hourOf(it.time) < 12 }) }
      item { HourBlock("PM", hours.filter { hourOf(it.time) >= 12 }) }
     }
    }
   }
  }
 }

 @Composable
 private fun WeatherGraph(
  values: List<Double>,
  labels: List<String>,
  unit: String,
  fixedMin: Double? = null,
  fixedMax: Double? = null
 ) {
  if (values.isEmpty()) return
  val rawLow = fixedMin ?: values.minOrNull() ?: 0.0
  val rawHigh = fixedMax ?: values.maxOrNull() ?: rawLow + 1.0
  val padding = if (fixedMin == null && fixedMax == null) ((rawHigh - rawLow) * 0.12).coerceAtLeast(2.0) else 0.0
  val low = fixedMin ?: floor(rawLow - padding)
  val high = fixedMax ?: ceil(rawHigh + padding)
  val mid = (low + high) / 2.0
  val span = (high - low).coerceAtLeast(1.0)
  val lineColor = MaterialTheme.colorScheme.primary
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val tickIndices = remember(labels) {
   buildList {
    if (labels.isNotEmpty()) add(0)
    var i = 4
    while (i < labels.lastIndex) {
     add(i)
     i += 4
    }
    if (labels.size > 1 && lastOrNull() != labels.lastIndex) add(labels.lastIndex)
   }
  }

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
   Row(Modifier.fillMaxWidth().height(116.dp)) {
    Column(
     Modifier.width(42.dp).fillMaxHeight(),
     verticalArrangement = Arrangement.SpaceBetween,
     horizontalAlignment = Alignment.End
    ) {
     Text("${high.toInt()}$unit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
     Text("${mid.toInt()}$unit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
     Text("${low.toInt()}$unit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.width(6.dp))
    Canvas(Modifier.weight(1f).fillMaxHeight()) {
     val step = if (values.size <= 1) 0f else size.width / (values.size - 1)
     val gridStroke = 1f
     drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = gridStroke)
     drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), strokeWidth = gridStroke)
     drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, size.height), end = androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = gridStroke)
     val path = Path()
     values.forEachIndexed { i, value ->
      val x = step * i
      val y = size.height - (((value - low) / span).toFloat().coerceIn(0f, 1f) * size.height)
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
     }
     drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
    }
   }
   if (tickIndices.size > 1) {
    Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
     tickIndices.forEach { index ->
      Text(labels[index], fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
   }
  }
 }

 @Composable
 private fun HourBlock(title: String, hours: List<Hour>) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
   Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
   if (hours.isEmpty()) {
    Text("No hourly data", color = MaterialTheme.colorScheme.onSurfaceVariant)
   } else {
    hours.forEach { h ->
     Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(hour(h.time), Modifier.width(58.dp), fontWeight = FontWeight.Medium)
      Text(weatherIcon(h.code), fontSize = 20.sp, modifier = Modifier.width(34.dp))
      Column(Modifier.weight(1f)) {
       Text("${WeatherRepository.label(h.code)}  •  ${h.temp.toInt()}°", fontSize = 13.sp)
       Text(
        "${WeatherRepository.compass(h.windDir)} ${h.wind.toInt()} mph  •  gust ${h.gust.toInt()} mph  •  rain ${h.pop}%",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
       )
      }
     }
    }
   }
  }
 }

 private fun hourOf(time: String): Int = runCatching { LocalDateTime.parse(time).hour }.getOrDefault(0)

 @Composable
 private fun AlertDetailsDialog(alert: AlertItem, onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
   Card(shape = RoundedCornerShape(24.dp)) {
    LazyColumn(
     Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(18.dp),
     verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
     item {
      Row(verticalAlignment = Alignment.CenterVertically) {
       Text(alert.event, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
       TextButton(onClick = onDismiss) { Text("Close") }
      }
     }
     if (alert.severity.isNotBlank()) item {
      Text("Severity: ${alert.severity}", fontWeight = FontWeight.Medium)
     }
     if (alert.headline.isNotBlank()) item {
      Text(alert.headline, fontSize = 15.sp, fontWeight = FontWeight.Medium)
     }
     item {
      Text(
       alert.description.ifBlank { "No additional alert details were provided." },
       fontSize = 14.sp
      )
     }
    }
   }
  }
 }

 @Composable private fun RadarCard(open:()->Unit){Card(onClick=open,shape=RoundedCornerShape(24.dp)){Box(Modifier.fillMaxWidth().height(125.dp).background(Brush.linearGradient(listOf(Color(0xFF183B56),Color(0xFF1B6B72),Color(0xFF4C956C))))){Box(Modifier.size(85.dp).offset(210.dp,20.dp).clip(CircleShape).background(Color(0x664CAF50)));Box(Modifier.size(55.dp).offset(175.dp,55.dp).clip(CircleShape).background(Color(0x66FFEB3B)));Column(Modifier.padding(18.dp)){Text("Animated radar",color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold);Text("6 radar & forecast modes",color=Color.White.copy(.9f));Spacer(Modifier.weight(1f));Text("Tap to open  ›",color=Color.White,fontWeight=FontWeight.Bold)}}}}
 @Composable private fun Metric(name:String,value:Double,max:Double,text:String,colors:List<Color>){Text("$name  •  $text",fontWeight=FontWeight.Medium);Spacer(Modifier.height(5.dp));Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)){Box(Modifier.fillMaxHeight().fillMaxWidth((value/max).toFloat().coerceIn(.03f,1f)).background(Brush.horizontalGradient(colors)))};Spacer(Modifier.height(10.dp))}
 @Composable private fun IndicatorBar(fraction:Float,colors:List<Color>){BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)){Box(Modifier.fillMaxWidth().height(14.dp).align(Alignment.Center).clip(CircleShape).background(Brush.horizontalGradient(colors)));val f=fraction.coerceIn(0f,1f);Box(Modifier.offset(x=(maxWidth*f-6.dp).coerceIn(0.dp,maxWidth-12.dp)).align(Alignment.CenterStart).size(12.dp).clip(CircleShape).background(Color.White).padding(2.dp)){Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black))}}}
 @Composable
 private fun SunArc(d: Day?) {
  if (d == null) {
   Text("Sun data unavailable")
   return
  }
  val sunriseLabel = clock(d.sunrise)
  val sunsetLabel = clock(d.sunset)
  val progress = runCatching {
   val sunrise = LocalDateTime.parse(d.sunrise)
   val sunset = LocalDateTime.parse(d.sunset)
   val now = LocalDateTime.now()
   when {
    now <= sunrise -> 0f
    now >= sunset -> 1f
    else -> {
     val total = java.time.Duration.between(sunrise, sunset).toMinutes().coerceAtLeast(1)
     java.time.Duration.between(sunrise, now).toMinutes().toFloat() / total
    }
   }
  }.getOrDefault(0f)

  Text("☀  Sunrise $sunriseLabel", fontWeight = FontWeight.Medium)
  Spacer(Modifier.height(6.dp))
  IndicatorBar(progress, listOf(Color(0xFF455A64), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFF455A64)))
  Spacer(Modifier.height(6.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
   Text("Sunrise")
   Text("Current time", fontSize = 12.sp, fontWeight = FontWeight.Bold)
   Text("Sunset")
  }
  Text("☾  Sunset $sunsetLabel")
  Text("Today's max UV ${"%.1f".format(d.uv)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
 }
 @Composable private fun AqiMeter(a:Int){val label=when{a<=50->"Good";a<=100->"Moderate";a<=150->"Unhealthy for sensitive groups";a<=200->"Unhealthy";a<=300->"Very unhealthy";else->"Hazardous"};Text("US AQI $a  •  $label",fontWeight=FontWeight.Bold);Spacer(Modifier.height(7.dp));IndicatorBar((a.coerceIn(0,500)/500f),listOf(Color(0xFF00A651),Color(0xFFFFD54F),Color(0xFFFF8F00),Color(0xFFE53935),Color(0xFF8E24AA)))}
 @Composable private fun UvMeter(uv:Double,title:String="UV index"){val label=when{uv<3->"Low";uv<6->"Moderate";uv<8->"High";uv<11->"Very high";else->"Extreme"};Text("$title ${"%.1f".format(uv)}  •  $label",fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));IndicatorBar((uv/15.0).toFloat().coerceIn(0f,1f),listOf(Color(0xFF2EAD5B),Color(0xFFFFD23F),Color(0xFFFF8C2A),Color(0xFFE53935),Color(0xFF8E24AA)))}

 @Composable
 private fun PollenMeter(pollen: Pollen) {
  val value = pollen.overall
  val (label, fraction) = when {
   value <= 0.0 -> "None" to 0.02f
   value < 10.0 -> "Low" to 0.2f
   value < 50.0 -> "Moderate" to 0.45f
   value < 150.0 -> "High" to 0.7f
   else -> "Very high" to 0.95f
  }
  Text(
   if (value <= 0.0) "Pollen • None"
   else "Pollen • $label  •  ${pollen.dominantName} ${"%.0f".format(pollen.dominantValue)} grains/m³",
   fontWeight = FontWeight.Bold
  )
  Spacer(Modifier.height(6.dp))
  IndicatorBar(
   fraction,
   listOf(Color(0xFF66BB6A), Color(0xFFFFEE58), Color(0xFFFFA726), Color(0xFFE53935), Color(0xFF8E24AA))
  )
  Spacer(Modifier.height(5.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
   Text("None", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
   Text("Low", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
   Text("Moderate", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
   Text("High", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
   Text("Very high", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }

 @Composable
 private fun AllergyConditions(weather: Weather) {
  val hour = upcomingHours(weather).firstOrNull()
  val month = runCatching {
   ZonedDateTime.now(ZoneId.of(weather.timezone)).monthValue
  }.getOrDefault(LocalDate.now().monthValue)
  val northernMonth = if (weather.lat >= 0.0) month else ((month + 5) % 12) + 1
  val temp = weather.currentTemp
  val humidity = weather.humidity
  val wind = weather.wind
  val recentRain = hour?.precip ?: 0.0

  var score = when (northernMonth) {
   3, 4, 5 -> 2
   6, 7 -> 2
   8, 9 -> 3
   10 -> 2
   else -> 0
  }
  if (temp >= 60.0) score++
  if (wind >= 10.0) score++
  if (recentRain >= 0.05) score--
  if (humidity >= 75 && northernMonth in 8..10) score++
  score = score.coerceIn(0, 4)

  val label = when (score) {
   0 -> "Low"
   1 -> "Low"
   2 -> "Moderate"
   3 -> "High"
   else -> "Very high"
  }
  val fraction = when (score) {
   0 -> 0.12f
   1 -> 0.25f
   2 -> 0.5f
   3 -> 0.72f
   else -> 0.94f
  }
  val factors = buildList {
   when (northernMonth) {
    3, 4, 5 -> add("tree pollen season")
    6, 7 -> add("grass pollen season")
    8, 9, 10 -> add("fall weed/ragweed season")
   }
   if (wind >= 10.0) add("breezy")
   if (recentRain >= 0.05) add("recent rain may reduce airborne pollen")
   if (humidity >= 75 && northernMonth in 8..10) add("damp conditions may favor mold")
  }

  Text("Allergy conditions • $label (estimated)", fontWeight = FontWeight.Bold)
  Spacer(Modifier.height(6.dp))
  IndicatorBar(
   fraction,
   listOf(Color(0xFF66BB6A), Color(0xFFFFEE58), Color(0xFFFFA726), Color(0xFFE53935), Color(0xFF8E24AA))
  )
  Spacer(Modifier.height(5.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
   Text("Low", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
   Text("Moderate", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
   Text("High", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
   Text("Very high", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
  if (factors.isNotEmpty()) {
   Spacer(Modifier.height(4.dp))
   Text(
    factors.joinToString(" • "),
    fontSize = 11.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )
  }
  Text(
   "Weather-based estimate, not a measured pollen count.",
   fontSize = 10.sp,
   color = MaterialTheme.colorScheme.onSurfaceVariant
  )
 }

 @Composable
 private fun SupportDialog(
  products: List<SupportProduct>,
  supported: Boolean,
  onSupport: (String) -> Unit,
  onDismiss: () -> Unit
 ) {
  AlertDialog(
   onDismissRequest = onDismiss,
   title = { Text(if (supported) "Thanks for supporting ClearSky" else "Support ClearSky") },
   text = {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
     if (supported) {
      Text("Your support is appreciated. ClearSky will not show automatic support reminders again.")
     } else {
      Text("ClearSky is free with no ads or locked features. If you'd like to help support continued development, you can make a one-time purchase through Google Play.")
      if (products.isEmpty()) {
       Text(
        "Support options are available when ClearSky is installed through Google Play.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
       )
      } else {
       products.forEach { product ->
        Button(onClick = { onSupport(product.id) }, modifier = Modifier.fillMaxWidth()) {
         Text("Support ${product.price}")
        }
       }
      }
     }
    }
   },
   confirmButton = {},
   dismissButton = { TextButton(onClick = onDismiss) { Text(if (supported) "Close" else "Not now") } }
  )
 }

 @Composable
 private fun AboutCard(supported: Boolean, onSupport: () -> Unit) {
  val signature = painterResource(R.drawable.zeus_signature)
  val signatureRatio = if (signature.intrinsicSize.height > 0f) {
   signature.intrinsicSize.width / signature.intrinsicSize.height
  } else 1f
  Card(shape = RoundedCornerShape(22.dp)) {
   Column(
    Modifier.fillMaxWidth().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
   ) {
    Text("About ClearSky", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
    Spacer(Modifier.height(8.dp))
    Text("Version ${BuildConfig.VERSION_NAME}", modifier = Modifier.align(Alignment.Start))
    Text("Free • No ads • No account • No analytics", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
    Spacer(Modifier.height(12.dp))
    androidx.compose.foundation.Image(
     painter = signature,
     contentDescription = "!!ZuEs!! signature",
     modifier = Modifier.fillMaxWidth().aspectRatio(signatureRatio).clip(RoundedCornerShape(16.dp)),
     contentScale = ContentScale.Fit
    )
    Spacer(Modifier.height(12.dp))
    Text("Weather and radar data come from their respective data providers. ClearSky itself has no advertising or analytics SDK.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Button(onClick = onSupport, enabled = !supported, modifier = Modifier.fillMaxWidth()) { Text(if (supported) "Thanks for supporting ClearSky" else "Support ClearSky") }
    OutlinedButton(onClick = { openUrl("https://github.com/cpugod55/ClearSky-Weather") }, modifier = Modifier.fillMaxWidth()) { Text("ClearSky on GitHub") }
   }
  }
 }
 private fun alertFromIntent(intent: Intent?): AlertItem? {
  val event = intent?.getStringExtra("alert_event") ?: return null
  return AlertItem(
   intent.getStringExtra("alert_id") ?: event,
   event,
   intent.getStringExtra("alert_headline").orEmpty(),
   intent.getStringExtra("alert_severity").orEmpty(),
   intent.getStringExtra("alert_description").orEmpty()
  )
 }
 private fun openUrl(url:String){runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
 private fun weatherIcon(c:Int)=when(c){0->"☀";1,2->"🌤";3->"☁";45,48->"🌫";in 51..57->"🌦";in 61..67->"🌧";in 71..77->"❄";in 80..82->"🌧";in 85..86->"🌨";in 95..99->"⛈";else->"☁"}
 private fun isNight(d:Day?):Boolean{if(d==null)return false;return runCatching{val now=LocalTime.now();val sr=LocalDateTime.parse(d.sunrise).toLocalTime();val ss=LocalDateTime.parse(d.sunset).toLocalTime();now<sr||now>ss}.getOrDefault(false)}
 private fun same(a:Place,b:Place)=abs(a.lat-b.lat)<.01&&abs(a.lon-b.lon)<.01
 private fun readPlaces():List<Place>{val p=getSharedPreferences("prefs",0);val la=p.getString("lat",null)?.toDoubleOrNull();val lo=p.getString("lon",null)?.toDoubleOrNull();val out=mutableListOf<Place>();if(la!=null&&lo!=null)out+=Place(p.getString("name","Current location")?:"Current location",la,lo);runCatching{val a=JSONArray(p.getString("saved_locations","[]"));for(i in 0 until a.length()){val o=a.getJSONObject(i);val x=Place(o.getString("name"),o.getDouble("lat"),o.getDouble("lon"));if(out.none{same(it,x)})out+=x}};return out}
 private fun addSaved(x:Place):List<Place>{val all=readPlaces().toMutableList();if(all.none{same(it,x)})all+=x;writeSaved(all.drop(1));return all}
 private fun removeSaved(x:Place):List<Place>{val all=readPlaces().filterNot{same(it,x)};writeSaved(all.drop(1));return all}
 private fun writeSaved(xs:List<Place>){val a=JSONArray();xs.forEach{a.put(JSONObject().put("name",it.name).put("lat",it.lat).put("lon",it.lon))};getSharedPreferences("prefs",0).edit().putString("saved_locations",a.toString()).apply()}
 private fun requestLocation(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)locate() else perm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))}
 private fun locate(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY,CancellationTokenSource().token).addOnSuccessListener{l->if(l!=null)CoroutineScope(Dispatchers.IO).launch{val n=WeatherRepository.reverseName(l.latitude,l.longitude);getSharedPreferences("prefs",0).edit().putString("lat",l.latitude.toString()).putString("lon",l.longitude.toString()).putString("name",n).apply();withContext(Dispatchers.Main){recreate()}}}}
 private fun save(w:Weather){WeatherUiStore.save(this,w);WeatherStatusWorker.show(this,w);WeatherWidget.refreshAll(this)}
}
@Composable fun Section(title:String,body: @Composable () -> Unit){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp)){Text(title,fontSize=20.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));body()}}}
fun upcomingHours(w: Weather): List<Hour> {
 val now = runCatching { ZonedDateTime.now(ZoneId.of(w.timezone)).toLocalDateTime().withMinute(0).withSecond(0).withNano(0) }
  .getOrElse { LocalDateTime.now().withMinute(0).withSecond(0).withNano(0) }
 val start = w.hours.indexOfFirst { runCatching { !LocalDateTime.parse(it.time).isBefore(now) }.getOrDefault(false) }
 return w.hours.drop(if (start >= 0) start else 0).take(24)
}

fun clock(s:String)=runCatching{LocalDateTime.parse(s).format(DateTimeFormatter.ofPattern("h:mm a"))}.getOrDefault(s.substringAfter("T", s))
fun hour(s:String)=runCatching{LocalDateTime.parse(s).format(DateTimeFormatter.ofPattern("h a"))}.getOrDefault(s.takeLast(5))
fun day(s:String)=runCatching{LocalDate.parse(s).format(DateTimeFormatter.ofPattern("EEE M/d"))}.getOrDefault(s)
