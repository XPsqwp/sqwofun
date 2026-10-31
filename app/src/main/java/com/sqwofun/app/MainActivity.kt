package com.sqwofun.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { GlassAnimeApp() } }
    }
}

private data class TabItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    TabItem("推荐", Icons.Filled.Home),
    TabItem("时间表", Icons.Filled.DateRange),
    TabItem("追番", Icons.Filled.Favorite),
    TabItem("我的", Icons.Filled.Settings),
)

private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

data class AnimeItem(
    val id: Int,
    val titleCn: String,
    val titleJp: String,
    val cover: String,
    val broadcast: String,
    val weekday: Int,
    val typeTag: String,
    val staff: String,
    val cast: String
) {
    val title: String get() = titleCn.ifBlank { titleJp }
    val weekdayName: String get() = WEEKDAYS.getOrElse(weekday - 1) { "未定档" }
}

/** 当季档期，例如 2025 年 10 月 → "202510" */
private fun currentSeason(): String {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val seasonMonth = when (month) {
        in 1..3 -> 1
        in 4..6 -> 4
        in 7..9 -> 7
        else -> 10
    }
    return "$year" + "0$seasonMonth".takeLast(2)
}

private fun stripTags(html: String): String =
    html.replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .trim()

/** yuc.wiki 当季新番表：纯动画情报，国内直连 */
private suspend fun fetchSeason(): Pair<String, List<AnimeItem>> = withContext(Dispatchers.IO) {
    val season = currentSeason()
    val conn = URL("https://yuc.wiki/$season/").openConnection() as HttpURLConnection
    conn.connectTimeout = 12000
    conn.readTimeout = 20000
    conn.setRequestProperty(
        "User-Agent",
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    )
    val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    conn.disconnect()

    val blocks = html.split("<!--#A").drop(1)
    val out = ArrayList<AnimeItem>()
    blocks.forEachIndexed { index, block ->
        val cover = Regex("data-src=\"([^\"]+)\"").find(block)?.groupValues?.get(1).orEmpty()
        if (cover.isBlank()) return@forEachIndexed
        val cn = Regex("class=\"title_cn_r\">([^<]*)<").find(block)?.groupValues?.get(1).orEmpty().trim()
        val jp = Regex("class=\"title_jp_r\">([^<]*)<").find(block)?.groupValues?.get(1).orEmpty().trim()
        val broadcast = Regex("class=\"broadcast_r\">([^<]*)<").find(block)?.groupValues?.get(1).orEmpty().trim()
        val typeTag = Regex("class=\"type_a_r\">([^<]*)<").find(block)?.groupValues?.get(1).orEmpty().trim()
        val staff = Regex("class=\"staff_r\">([\\s\\S]*?)</td>").find(block)?.groupValues?.get(1)?.let(::stripTags).orEmpty()
        val cast = Regex("class=\"cast_r\">([\\s\\S]*?)</td>").find(block)?.groupValues?.get(1)?.let(::stripTags).orEmpty()
        val weekday = WEEKDAYS.indexOfFirst { broadcast.contains(it) } + 1
        out.add(
            AnimeItem(
                id = index,
                titleCn = cn,
                titleJp = jp,
                cover = cover.replace("http://", "https://"),
                broadcast = broadcast,
                weekday = weekday.coerceAtLeast(0),
                typeTag = typeTag,
                staff = staff,
                cast = cast
            )
        )
    }
    season to out
}

@Composable
fun GlassAnimeApp() {
    var index by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<AnimeItem?>(null) }
    var items by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var season by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { fetchSeason() }
            .onSuccess { (s, list) ->
                season = s
                items = list
            }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    val backdrop = rememberLayerBackdrop()

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFEAF4E6), Color(0xFFF6F1E4), Color(0xFFE8EEF8))
                )
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            when (index) {
                0 -> RecommendPage(items, season, loading, error) { detail = it }
                1 -> SchedulePage(items, loading, error) { detail = it }
                2 -> PlaceholderPage("追番", "你的收藏会出现在这里")
                else -> PlaceholderPage("我的", "设置 · 同步 · 关于")
            }
        }

        LiquidBottomTabs(
            selectedTabIndex = { index },
            onTabSelected = { index = it },
            backdrop = backdrop,
            tabsCount = TABS.size,
            height = 64.dp,
            horizontalPadding = 5.dp,
            blurRadius = 14.dp,
            lensAmount = 24.dp,
            showDarkenedEdge = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            TABS.forEachIndexed { i, tab ->
                LiquidBottomTab({ index = i }) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF12261A)
                    )
                    Text(tab.label, fontSize = 11.sp, color = Color(0xFF12261A))
                }
            }
        }

        detail?.let { item ->
            DetailPage(item) { detail = null }
        }
    }
}

private fun seasonName(season: String): String {
    if (season.length != 6) return "当季"
    val year = season.take(4)
    val month = season.takeLast(2)
    val part = when (month) {
        "01" -> "冬季"
        "04" -> "春季"
        "07" -> "夏季"
        else -> "秋季"
    }
    return "$year$part"
}

@Composable
private fun RecommendPage(
    items: List<AnimeItem>,
    season: String,
    loading: Boolean,
    error: String?,
    onClick: (AnimeItem) -> Unit
) {
    if (loading || error != null || items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(color = Color(0xFF3E6B52))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败", fontSize = 16.sp, color = Color(0xFF12261A))
                    Spacer(Modifier.height(6.dp))
                    Text(error ?: "没有数据", fontSize = 12.sp, color = Color(0xFF6A7A70))
                }
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(3) }) {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(
                    seasonName(season) + "新番",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF12261A)
                )
                Text("共 ${items.size} 部 · 数据来自 yuc.wiki", fontSize = 11.sp, color = Color(0xFF6A7A70))
            }
        }
        items(items, key = { it.id }) { item ->
            PosterCard(item, onClick)
        }
    }
}

@Composable
private fun PosterCard(item: AnimeItem, onClick: (AnimeItem) -> Unit) {
    Column(Modifier.clickable { onClick(item) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x22000000))
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                item.weekdayName,
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            fontSize = 12.sp,
            color = Color(0xFF1B2B22),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SchedulePage(
    items: List<AnimeItem>,
    loading: Boolean,
    error: String?,
    onClick: (AnimeItem) -> Unit
) {
    if (loading || error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(color = Color(0xFF3E6B52))
            else Text("加载失败", fontSize = 16.sp, color = Color(0xFF12261A))
        }
        return
    }
    val grouped = items.groupBy { it.weekday }.toSortedMap()
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                "每周放送",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF12261A),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        grouped.forEach { (day, list) ->
            item(key = "h-$day") {
                Text(
                    WEEKDAYS.getOrElse(day - 1) { "未定档" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2C4438)
                )
            }
            listItems(list, key = { "s-${it.id}" }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1FFFFFFF))
                        .clickable { onClick(item) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.cover,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 46.dp, height = 64.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontSize = 14.sp, color = Color(0xFF1B2B22), maxLines = 2)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            listOf(item.broadcast, item.typeTag).filter { it.isNotBlank() }.joinToString("  ·  "),
                            fontSize = 11.sp,
                            color = Color(0xFF6A7A70)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPage(item: AnimeItem, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = item.cover,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x22000000), Color(0xDD0B1510), Color(0xF20B1510))
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 110.dp)
        ) {
            Text(item.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (item.titleJp.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.titleJp, fontSize = 13.sp, color = Color(0x99FFFFFF))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                listOf(item.weekdayName, item.broadcast, item.typeTag)
                    .filter { it.isNotBlank() }
                    .joinToString("  ·  "),
                fontSize = 12.sp,
                color = Color(0xCCFFFFFF)
            )
            if (item.staff.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(item.staff, fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xB3FFFFFF), maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            if (item.cast.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(item.cast, fontSize = 12.sp, lineHeight = 18.sp, color = Color(0x99FFFFFF), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            Icons.Filled.Close,
            contentDescription = "关闭",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .size(26.dp)
                .clickable { onClose() }
        )
    }
}

@Composable
private fun PlaceholderPage(title: String, sub: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12261A))
            Spacer(Modifier.height(6.dp))
            Text(sub, fontSize = 13.sp, color = Color(0xFF4A5B50))
        }
    }
}
