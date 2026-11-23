package com.android.ty.systemservices

import android.os.Bundle
import android.util.Xml
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toBitmap
import com.android.ty.systemservices.compose.ui.theme.TyTheme
import com.android.ty.systemservices.compose.ui.widget.setting.BigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveMenuItem
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveToolbarMenu
import com.android.ty.systemservices.compose.ui.widget.setting.LargeTitleHeader
import com.android.ty.systemservices.compose.ui.widget.setting.LargeTitleMode
import com.android.ty.systemservices.compose.ui.widget.setting.MainTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.loadBigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.loadMainTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.mainImplementation
import com.android.ty.systemservices.compose.ui.widget.setting.rootImplementation
import com.google.android.material.appbar.AppBarLayout
import org.xmlpull.v1.XmlPullParser

/**
 * 头部（大标题）采用哪一套实现。
 *
 * 两套折叠效果由两个独立的持久化设置控制，互不影响：
 * - [MAIN]：主界面体系，[MainTitleImpl] 两档（SCALE 缩放 / FADE 淡出），头部 160dp；
 * - [PREFERENCE]：Preference 页体系，[BigTitleImpl] 三档（含 ORIGINAL 普通顶栏），头部 200dp。
 */
enum class TitleStyle {
    MAIN,
    PREFERENCE,
}

/**
 * 模仿 Android 12 Settings 的「大标题」基类（全项目唯一实现）。
 *
 * 头部整块由 Compose 绘制（[LargeTitleHeader]），不再用 Material 的
 * `CollapsingToolbarLayout`——那套的标题插值在「边到边 + fitsSystemWindows」组合下
 * 会把大标题留在半截位置，滚动时观感很怪。现在只保留 View 侧的
 * `CoordinatorLayout + AppBarLayout` 负责滚动物理，折叠进度喂给 Compose 自己算。
 *
 * 折叠行程：布局里 `header_compose` 高度 160dp/200dp，`android:minHeight="56dp"`，
 * AppBarLayout 的 `getTotalScrollRange()` 会把 minHeight 减掉，
 * 因此收起后恰好剩下 56dp 顶栏，和 Material 的 pin 行为一致。
 *
 * 子类通过构造参数 [titleStyle] 选择头部体系：
 * - 主界面（主页 / 实验室 / 开发调试 / 贡献者 / 关于）传 [TitleStyle.MAIN]；
 * - Preference 页（[SettingsActivity] / [SecurityActivity] / XmlPreferenceActivity）用默认值。
 *
 * 内容必须放进 `content_frame`：重写的 [setContentView] 只会替换内容区，
 * **不要**调用 Compose 的 `setContent {}`，那会替换掉整套布局（含标题栏）。
 */
abstract class CollapsingToolbarBaseActivity(
    private val titleStyle: TitleStyle = TitleStyle.PREFERENCE,
) : AppCompatActivity() {

    /**
     * 子类声明的菜单资源。两种渲染路径共用同一份 XML：
     * - 有 Compose 头部（大标题档）：动作项与溢出卡片都画在头部里；
     * - ORIGINAL 档（普通 Toolbar）：交给 [installExpressiveMenu] 画在 Toolbar 尾部。
     *
     * XML 里 `app:showAsAction="always"` / `ifRoom` 的项会变成图标按钮，
     * 其余（`never` 或未声明）收进溢出卡片；`android:group id` 变化处会插一条分隔线。
     */
    @get:MenuRes
    protected open val menuResId: Int = 0

    /**
     * 是否启用 Material 3 Expressive 的卡片式溢出菜单。
     *
     * 只对 ORIGINAL 档（没有大标题、只有普通 Toolbar）有意义；
     * 大标题档的菜单永远走 Compose 头部。
     */
    protected open val useExpressiveOverflowMenu: Boolean = false

    private var contentFrame: FrameLayout? = null
    private var appBarLayout: AppBarLayout? = null

    /** 大标题档的 Compose 头部；ORIGINAL 档为 null */
    private var headerCompose: ComposeView? = null

    /** 自绘菜单时持有的 Menu 实例，点击回调用它找回原始 MenuItem */
    private var expressiveMenu: Menu? = null

    private var headerMode: LargeTitleMode = LargeTitleMode.SCALE

    // 头部状态：Activity 改 View 侧字段，Compose 侧读 State 自动重组
    private val headerTitle = mutableStateOf("")
    private val headerFraction = mutableFloatStateOf(0f)
    private val headerBack = mutableStateOf(false)
    private val headerActions = mutableStateOf<List<ExpressiveMenuItem>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val spec = resolveHeaderSpec()
        super.setContentView(spec.layoutRes)

        contentFrame = findViewById(R.id.content_frame)
        appBarLayout = findViewById(R.id.app_bar)
        headerCompose = findViewById(R.id.header_compose)

        val toolbar = findViewById<Toolbar>(R.id.action_bar)
        if (toolbar != null) setSupportActionBar(toolbar)

        headerCompose?.let { compose ->
            headerMode = spec.mode ?: LargeTitleMode.SCALE
            bindHeader(compose)
        }

        appBarLayout?.let { bar ->
            bar.addOnOffsetChangedListener(
                AppBarLayout.OnOffsetChangedListener { layout, verticalOffset ->
                    val range = layout.totalScrollRange
                    headerFraction.floatValue =
                        if (range <= 0) 0f else (-verticalOffset).toFloat() / range
                },
            )
            // 监听器不会在注册时回调一次（旋转恢复、setExpanded 后重建都可能错过首帧），
            // 这里补一次，保证 Compose 的折叠进度和 AppBar 的当前 offset 对齐。
            // AppBar 折叠时整块被上移，所以 top 为负，取反就是已经折叠的距离。
            bar.post {
                val range = bar.totalScrollRange
                headerFraction.floatValue =
                    if (range <= 0) 0f else (-bar.top).coerceAtLeast(0).toFloat() / range
            }
        }

        when {
            headerCompose != null -> installHeaderMenu()
            useExpressiveOverflowMenu && toolbar != null -> installExpressiveMenu(toolbar)
        }
    }

    /** 头部布局 + 折叠模式：布局由持久化的大标题选项决定 */
    private fun resolveHeaderSpec(): HeaderSpec = when (titleStyle) {
        TitleStyle.MAIN -> {
            val impl = loadMainTitleImpl(this)
            mainImplementation = impl
            when (impl) {
                MainTitleImpl.ONE -> HeaderSpec(R.layout.activity_collapsing_toolbar_one, LargeTitleMode.SCALE)
                MainTitleImpl.TWO -> HeaderSpec(R.layout.activity_collapsing_toolbar, LargeTitleMode.FADE)
            }
        }

        TitleStyle.PREFERENCE -> {
            // 与 XmlPreferenceActivity 共用同一份持久化选择，屏幕内组件读全局 rootImplementation
            val impl = loadBigTitleImpl(this)
            rootImplementation = impl
            when (impl) {
                BigTitleImpl.ONE -> HeaderSpec(R.layout.activity_preference, LargeTitleMode.SCALE)
                BigTitleImpl.TWO -> HeaderSpec(R.layout.activity_preference_two, LargeTitleMode.FADE)
                BigTitleImpl.ORIGINAL -> HeaderSpec(R.layout.activity_preference_original, null)
            }
        }
    }

    /**
     * 折叠进度 → Compose。
     *
     * [AppBarLayout.OnOffsetChangedListener] 每帧回调，写 Compose 的 State 即可，
     * 不用再做一次 requestLayout。
     */
    private fun bindHeader(compose: ComposeView) {
        compose.setContent {
            TyTheme {
                LargeTitleHeader(
                    title = headerTitle.value,
                    fraction = headerFraction.floatValue,
                    mode = headerMode,
                    backVisible = headerBack.value,
                    actions = headerActions.value,
                    onActionClick = { dispatchMenuClick(it.itemId) },
                    onBack = { finish() },
                )
            }
        }
    }

    /** 子类调用 setContentView 时，把内容放到 content_frame，头部保持不变 */
    override fun setContentView(layoutResID: Int) {
        contentFrame?.let { frame ->
            frame.removeAllViews()
            LayoutInflater.from(this).inflate(layoutResID, frame, true)
        }
    }

    /** 设置展开态的大标题（ORIGINAL 档没有大标题，回退到顶部栏标题） */
    fun setLargeTitle(title: CharSequence) {
        if (headerCompose != null) {
            headerTitle.value = title.toString()
        } else {
            supportActionBar?.title = title
        }
    }

    fun setLargeTitle(titleRes: Int) {
        setLargeTitle(getString(titleRes))
    }

    /**
     * 统一控制返回按钮显隐。
     *
     * 按实际拿到的头部类型分支，而不是按配置项分支：
     * ORIGINAL 档（没有 Compose 头部）回退到系统返回箭头。
     */
    fun setBackButton(visible: Boolean) {
        if (headerCompose != null) {
            headerBack.value = visible
        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(visible)
        }
    }

    /** 主动展开 / 收起头部（Android 12 顶层页跳二级页时收起） */
    fun setToolbarExpanded(expanded: Boolean) {
        setToolbarExpanded(expanded, true)
    }

    fun setToolbarExpanded(expanded: Boolean, animate: Boolean) {
        appBarLayout?.setExpanded(expanded, animate)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ---------------------------------------------------------------- 菜单

    /**
     * 填充菜单内容。默认按 [menuResId] inflate；子类也可直接 add 代码菜单。
     * 两条渲染路径都会调用它，保证菜单定义只有一份。
     */
    protected open fun onCreateMenu(menu: Menu) {
        if (menuResId != 0) menuInflater.inflate(menuResId, menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // 菜单由 Compose 头部（或自绘 Toolbar）负责，不交给 AppCompat 再画一遍
        if (headerCompose != null || useExpressiveOverflowMenu) return false

        val handledByFragment = super.onCreateOptionsMenu(menu)
        onCreateMenu(menu)
        return handledByFragment || menu.size() > 0
    }

    /** 大标题档：把菜单解析成 Compose 头部右侧的动作项 + 溢出卡片 */
    private fun installHeaderMenu() {
        val menu = PopupMenu(this, View(this)).menu
        onCreateMenu(menu)
        if (menu.size() == 0) return
        expressiveMenu = menu
        headerActions.value = collectMenuItems(menu)
    }

    /**
     * 把菜单渲染到 Toolbar 尾部：`showAsAction` 的项 → 圆形图标按钮，其余 → 卡片式溢出菜单。
     * 只用于 ORIGINAL 档，保证「切回普通顶栏」时菜单不丢。
     *
     * 用 AppCompat 的 [PopupMenu] 只为借一个 `MenuBuilder` 来承载 inflate 结果，不显示弹窗本身。
     */
    private fun installExpressiveMenu(toolbar: Toolbar) {
        val menu = PopupMenu(this, View(this)).menu
        onCreateMenu(menu)
        if (menu.size() == 0) return
        expressiveMenu = menu

        val items = collectMenuItems(menu)
        if (items.isEmpty()) return

        val density = resources.displayMetrics.density
        val host = ComposeView(this).apply {
            layoutParams = Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                (56 * density).toInt(),
                Gravity.END or Gravity.CENTER_VERTICAL,
            )
            toolbar.addView(this)
        }
        host.setContent {
            TyTheme {
                ExpressiveToolbarMenu(
                    items = items,
                    onItemClick = { clicked -> dispatchMenuClick(clicked.itemId) },
                )
            }
        }
    }

    /**
     * MenuItem → Compose 侧模型。
     *
     * `MenuItem.getShowAsAction()` 是 @hide API，这里改为直接读菜单 XML 的 `showAsAction` 属性；
     * 子类用代码 add 的项 XML 里没有该属性，按 never 处理，全部进溢出卡片。
     */
    private fun collectMenuItems(menu: Menu): List<ExpressiveMenuItem> {
        val actionFlags = if (menuResId != 0) readShowAsActionFlags(menuResId) else emptyMap()
        var lastGroupId = Int.MIN_VALUE
        val items = ArrayList<ExpressiveMenuItem>(menu.size())
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (!item.isVisible) continue

            val flags = actionFlags[item.itemId] ?: MenuItem.SHOW_AS_ACTION_NEVER
            val wantAction = flags and MenuItem.SHOW_AS_ACTION_ALWAYS != 0 ||
                flags and MenuItem.SHOW_AS_ACTION_IF_ROOM != 0
            val icon = item.icon?.toBitmap()?.asImageBitmap()

            items += ExpressiveMenuItem(
                itemId = item.itemId,
                title = item.title ?: "",
                icon = icon,
                enabled = item.isEnabled,
                // 没有图标的项即使声明了 always 也只能文字显示，统一降级进卡片
                showAsAction = wantAction && icon != null,
                startsNewGroup = lastGroupId != Int.MIN_VALUE && item.groupId != lastGroupId,
            )
            lastGroupId = item.groupId
        }
        return items
    }

    /**
     * 从菜单 XML 里读每个 item 的 `showAsAction` 位掩码。
     *
     * 公开 SDK 没有读这个属性的接口（`MenuItem.getShowAsAction()` 是 @hide），
     * 所以用 [android.content.res.Resources.getXml] 再解析一遍同一份资源。
     * 失败时返回空表，所有项退化为溢出卡片，功能不丢。
     */
    private fun readShowAsActionFlags(@MenuRes resId: Int): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        try {
            val parser = resources.getXml(resId)
            val attrs = Xml.asAttributeSet(parser)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    var itemId = 0
                    var flags = 0
                    for (i in 0 until attrs.attributeCount) {
                        if (attrs.getAttributeNameResource(i) == android.R.attr.id) {
                            itemId = attrs.getAttributeResourceValue(i, 0)
                        } else if (attrs.getAttributeName(i) == "showAsAction") {
                            // AppCompat 的 attr，按名字匹配即可，不依赖合并后的包名
                            flags = attrs.getAttributeIntValue(i, 0)
                        }
                    }
                    if (itemId != 0) out[itemId] = flags
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // 忽略：退化为全部进溢出卡片
        }
        return out
    }

    /** 点击自绘菜单项：先给当前 Fragment，再走 Activity 的 [onOptionsItemSelected] */
    private fun dispatchMenuClick(itemId: Int) {
        val item = expressiveMenu?.findItem(itemId) ?: return
        for (fragment in supportFragmentManager.fragments) {
            if (fragment.isVisible && fragment.onOptionsItemSelected(item)) return
        }
        onOptionsItemSelected(item)
    }

    /**
     * @property mode null 表示这一档没有大标题（ORIGINAL：普通 Toolbar）
     */
    private data class HeaderSpec(
        val layoutRes: Int,
        val mode: LargeTitleMode?,
    )
}
