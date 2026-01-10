package com.example.campusnavpro

import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.DataType
import android.Manifest
import android.graphics.*
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.accompanist.permissions.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.*
import java.util.concurrent.Executors

data class FoodItem(val name: String, val price: String, val imageUrl: String)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Configuration.getInstance().userAgentValue = packageName
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    PermissionGuard(onSpeak = { speak(it) })
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGuard(onSpeak: (String) -> Unit) {
    val permissionState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA)
    )
    if (permissionState.allPermissionsGranted) {
        CampusMapScreen(onSpeak)
    } else {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Permissions required for GEHU AR Nav.", color = Color.White)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) { Text("Enable Sensors") }
        }
    }
}

@Composable
fun CameraWithAI(onBuildingDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val interpreter = remember {
        val fd = context.assets.openFd("model_unquant.tflite")
        Interpreter(FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength))
    }
    val labels = remember { context.assets.open("labels.txt").bufferedReader().readLines().map { it.replace(Regex("^[0-9]+\\s+"), "").trim() } }

    AndroidView(factory = { ctx ->
        val pv = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        ProcessCameraProvider.getInstance(ctx).addListener({
            val provider = ProcessCameraProvider.getInstance(ctx).get()
            val analysis = ImageAnalysis.Builder().setTargetResolution(Size(224, 224)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val bitmap = Bitmap.createScaledBitmap(proxy.toBitmap(), 224, 224, true)
                val tImg = TensorImage(DataType.FLOAT32).apply { load(bitmap) }
                val processed = ImageProcessor.Builder().add(NormalizeOp(0f, 255f)).build().process(tImg)
                val output = Array(1) { FloatArray(labels.size) }
                interpreter.run(processed.buffer, output)
                val maxIdx = output[0].indices.maxByOrNull { output[0][it] } ?: -1
                if (maxIdx != -1 && output[0][maxIdx] > 0.85f) onBuildingDetected(labels[maxIdx]) else onBuildingDetected("")
                proxy.close()
            }
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }, analysis)
        }, ContextCompat.getMainExecutor(ctx))
        pv
    }, modifier = Modifier.fillMaxSize())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusMapScreen(onSpeak: (String) -> Unit) {
    var currentBuildingByGPS by remember { mutableStateOf<String?>(null) }
    var detectedByAI by remember { mutableStateOf<String?>(null) }
    var navInstruction by remember { mutableStateOf("Ready to Navigate") }
    var lastSpoken by remember { mutableStateOf("") }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedStore by remember { mutableStateOf<String?>(null) }
    var showWarnings by remember { mutableStateOf(true) }

    val campusSpots = remember { mutableListOf<Marker>() }
    val pathSegments = remember { mutableListOf<Polyline>() }
    val navigationGraph = remember { mutableMapOf<String, MutableList<Pair<String, Polyline>>>() }
    val scaffoldState = rememberBottomSheetScaffoldState()

    LaunchedEffect(Unit) { delay(5000); showWarnings = false }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 80.dp,
        sheetContainerColor = Color.White,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContent = {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                CanteenOrderSection(selectedStore) { selectedStore = it }
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            CameraWithAI { detected -> detectedByAI = if (detected.isEmpty()) null else detected }

            // HUD
            Box(modifier = Modifier.statusBarsPadding().fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(visible = destinationMarker != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF6200EE)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Navigation, null, tint = Color.White)
                                    Spacer(Modifier.width(12.dp))
                                    Text(navInstruction, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                            // 🎯 STOP NAVIGATION BUTTON
                            Button(
                                onClick = {
                                    destinationMarker = null
                                    navInstruction = "Ready to Navigate"
                                    pathSegments.forEach {
                                        it.outlinePaint.color = android.graphics.Color.parseColor("#FFD700")
                                        it.outlinePaint.strokeWidth = 6f
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                modifier = Modifier.padding(top = 8.dp).height(40.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Stop Navigation", fontSize = 14.sp)
                            }
                        }
                    }

                    AnimatedVisibility(visible = detectedByAI != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.7f)), modifier = Modifier.padding(top = 8.dp).border(1.dp, Color.Cyan, RoundedCornerShape(12.dp))) {
                            Text("Seeing: $detectedByAI", color = Color.Cyan, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { isSearchOpen = true }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.9f))) {
                        Icon(Icons.Default.Search, null, tint = Color.Gray); Spacer(Modifier.width(8.dp))
                        Text(destinationMarker?.title ?: "Search Building...", color = Color.Gray)
                    }
                }

                // MAP
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 120.dp).size(160.dp, 220.dp).clip(RoundedCornerShape(20.dp)).border(2.dp, Color.White, RoundedCornerShape(20.dp)).background(Color.Black)) {
                    AndroidView(factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setBuiltInZoomControls(false)
                            minZoomLevel = 21.0; maxZoomLevel = 21.0
                            val locOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                            val bSize = 64
                            val bmap = Bitmap.createBitmap(bSize, bSize, Bitmap.Config.ARGB_8888)
                            val canv = Canvas(bmap)
                            val pnt = Paint().apply { color = android.graphics.Color.BLUE; style = Paint.Style.FILL; isAntiAlias = true }
                            val path = Path().apply { moveTo(32f, 0f); lineTo(16f, 64f); lineTo(32f, 48f); lineTo(48f, 64f); close() }
                            canv.drawPath(path, pnt)
                            locOverlay.setDirectionIcon(bmap); locOverlay.setPersonIcon(bmap)
                            locOverlay.enableMyLocation(); locOverlay.enableFollowLocation()
                            overlays.add(locOverlay)

                            try {
                                val json = JSONObject(ctx.resources.openRawResource(R.raw.campus_map).bufferedReader().readText())
                                val features = json.getJSONArray("features")
                                for (i in 0 until features.length()) {
                                    val f = features.getJSONObject(i); val geom = f.getJSONObject("geometry"); val props = f.optJSONObject("properties")
                                    if (geom.getString("type") == "Point") {
                                        val m = Marker(this).apply {
                                            position = GeoPoint(geom.getJSONArray("coordinates").getDouble(1), geom.getJSONArray("coordinates").getDouble(0))
                                            title = props.keys().next(); icon = ShapeDrawable(OvalShape()).apply { intrinsicWidth=10; intrinsicHeight=10; paint.color=android.graphics.Color.RED }
                                        }
                                        campusSpots.add(m); overlays.add(m)
                                    } else if (geom.getString("type") == "LineString") {
                                        val poly = Polyline(this).apply {
                                            val c = geom.getJSONArray("coordinates")
                                            setPoints(List(c.length()) { j -> GeoPoint(c.getJSONArray(j).getDouble(1), c.getJSONArray(j).getDouble(0)) })
                                            outlinePaint.color = android.graphics.Color.parseColor("#FFD700"); outlinePaint.strokeWidth = 6f
                                        }
                                        val parts = props.keys().next().lowercase().split(" to ")
                                        if (parts.size == 2) {
                                            navigationGraph.getOrPut(parts[0].trim()) { mutableListOf() }.add(parts[1].trim() to poly)
                                            navigationGraph.getOrPut(parts[1].trim()) { mutableListOf() }.add(parts[0].trim() to poly)
                                        }
                                        pathSegments.add(poly); overlays.add(poly)
                                    }
                                }
                            } catch (e: Exception) { Log.e("OSM", "Load Error") }

                            locOverlay.runOnFirstFix {
                                post { controller.setZoom(21.0); controller.animateTo(locOverlay.myLocation) }
                                postDelayed(object : Runnable {
                                    override fun run() {
                                        val myLoc = locOverlay.myLocation ?: return
                                        val userLatLng = LatLng(myLoc.latitude, myLoc.longitude)
                                        for (m in campusSpots) { if (SphericalUtil.computeDistanceBetween(userLatLng, LatLng(m.position.latitude, m.position.longitude)) < 30.0) currentBuildingByGPS = m.title }
                                        destinationMarker?.let { dest ->
                                            val start = currentBuildingByGPS?.lowercase()?.replace(" hostel", "")?.trim() ?: ""
                                            val route = findPath(start, dest.title.lowercase().replace(" hostel", "").trim(), navigationGraph)
                                            post {
                                                pathSegments.forEach { it.outlinePaint.color = android.graphics.Color.parseColor("#FFD700") }
                                                route.forEach { overlays.remove(it); it.outlinePaint.color = android.graphics.Color.parseColor("#A020F0"); it.outlinePaint.strokeWidth = 14f; overlays.add(it) }
                                                invalidate()
                                            }
                                            if (route.isNotEmpty()) {
                                                val dist = SphericalUtil.computeDistanceBetween(userLatLng, LatLng(route[0].actualPoints[1].latitude, route[0].actualPoints[1].longitude)).toInt()
                                                navInstruction = if (dist < 10) "Arriving at Destination" else "Go Straight for ${dist}m"
                                                if (navInstruction != lastSpoken) { onSpeak(navInstruction); lastSpoken = navInstruction }
                                            }
                                        }
                                        postDelayed(this, 1500)
                                    }
                                }, 1500)
                            }
                        }
                    }, modifier = Modifier.fillMaxSize())
                }
                Card(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 100.dp).width(160.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.8f))) {
                    Text(if (currentBuildingByGPS != null) "At: $currentBuildingByGPS" else "Finding GPS...", modifier = Modifier.padding(8.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // WARNING OVERLAY
            AnimatedVisibility(visible = showWarnings, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center).padding(24.dp).zIndex(99f)) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.9f)), shape = RoundedCornerShape(16.dp), modifier = Modifier.border(2.dp, Color.Yellow, RoundedCornerShape(16.dp))) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, null, tint = Color.Yellow)
                        Text("NAV PRE-CHECK", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("• GPS Location: ON\n• Use OUTDOORS only\n• NO Indoor use", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            if (isSearchOpen) {
                Dialog(onDismissRequest = { isSearchOpen = false }) {
                    Card(Modifier.fillMaxWidth().fillMaxHeight(0.7f).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Destination", style = MaterialTheme.typography.headlineSmall)
                            LazyColumn { items(campusSpots) { m -> Text(m.title, Modifier.fillMaxWidth().clickable { destinationMarker = m; isSearchOpen = false }.padding(12.dp)); Divider() } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CanteenOrderSection(selectedStore: String?, onStoreSelect: (String?) -> Unit) {
    val canteen = listOf(FoodItem("Paneer Patties", "₹25", "https://images.unsplash.com/photo-1626132647523-66f5bf380027?q=80&w=200&fit=crop"), FoodItem("Masala Dosa", "₹60", "https://images.unsplash.com/photo-1668236543090-82eba5ee5976?q=80&w=200&auto=format&fit=crop"))
    val pizza = listOf(FoodItem("Veggie Supreme", "₹299", "https://images.unsplash.com/photo-1513104890138-7c749659a591?q=80&w=200&auto=format&fit=crop"), FoodItem("Garlic Bread", "₹99", "https://images.unsplash.com/photo-1541745537411-b8046dc6d66c?q=80&w=200&auto=format&fit=crop"))
    val bakery = listOf(FoodItem("Chocolate Cake", "₹450", "https://images.unsplash.com/photo-1578985545062-69928b1d9587?q=80&w=200&auto=format&fit=crop"), FoodItem("Assorted Donuts", "₹120", "https://images.unsplash.com/photo-1551024601-bec78aea704b?q=80&w=200&auto=format&fit=crop"))

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (selectedStore == null) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item { WeatherEnvironmentBox(); Spacer(Modifier.height(16.dp)) }
                item { StoreCard("GEHU Canteen", Icons.Default.Restaurant, Color(0xFFFFE0B2)) { onStoreSelect("canteen") }; Spacer(Modifier.height(12.dp)) }
                item { StoreCard("Pizza Hut", Icons.Default.LocalPizza, Color(0xFFFFCDD2)) { onStoreSelect("pizzahut") }; Spacer(Modifier.height(12.dp)) }
                item { StoreCard("Let Me Bake", Icons.Default.Cake, Color(0xFFE1BEE7)) { onStoreSelect("letmebake") } }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onStoreSelect(null) }) { Icon(Icons.Default.ArrowBack, null) }
                Text(selectedStore.uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            val m = when(selectedStore) { "canteen" -> canteen; "pizzahut" -> pizza; else -> bakery }
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f)) {
                items(m) { PictorialFoodCard(it) }
            }
        }
    }
}

@Composable
fun WeatherEnvironmentBox() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Bhimtal, Uttarakhand", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.DarkGray); Text("Winter Mist", fontSize = 14.sp, color = Color.Gray) }
                Text("12°C", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1976D2))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                WeatherMetric(Icons.Default.WaterDrop, "72%", "Humidity")
                WeatherMetric(Icons.Default.Air, "8 km/h", "Wind")
                WeatherMetric(Icons.Default.Visibility, "4 km", "Visibility")
                WeatherMetric(Icons.Default.WbSunny, "Low", "UV Index")
            }
        }
    }
}

@Composable
fun WeatherMetric(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color(0xFF1976D2))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun StoreCard(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).background(color).clickable { onClick() }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp), tint = Color.DarkGray); Spacer(Modifier.width(16.dp))
            Column { Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Tap to view menu", fontSize = 12.sp) }
        }
    }
}

@Composable
fun PictorialFoodCard(item: FoodItem) {
    Card(Modifier.padding(4.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column {
            AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(100.dp), contentScale = ContentScale.Crop)
            Column(Modifier.padding(8.dp)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.price, fontSize = 13.sp, color = Color.Gray); Icon(Icons.Default.AddCircle, null, tint = Color(0xFF4CAF50))
                }
            }
        }
    }
}

fun findPath(start: String, end: String, graph: Map<String, List<Pair<String, Polyline>>>): List<Polyline> {
    if (start.isEmpty() || end.isEmpty()) return emptyList()
    val s = graph.keys.find { it.contains(start) || start.contains(it) } ?: return emptyList()
    val e = graph.keys.find { it.contains(end) || end.contains(it) } ?: return emptyList()
    val q: Queue<List<String>> = LinkedList(); q.add(listOf(s))
    val v = mutableSetOf(s)
    while (q.isNotEmpty()) {
        val p = q.poll() ?: continue
        val curr = p.last()
        if (curr == e) {
            val res = mutableListOf<Polyline>()
            for (i in 0 until p.size - 1) res.add(graph[p[i]]!!.first { it.first == p[i+1] }.second)
            return res
        }
        graph[curr]?.forEach { (nb, _) -> if (nb !in v) { v.add(nb); q.add(p + nb) } }
    }
    return emptyList()
}