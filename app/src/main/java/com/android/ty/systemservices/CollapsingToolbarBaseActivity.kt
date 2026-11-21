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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.drawable.toBitmap
import com.android.ty.systemservices.compose.ui.theme.TyTheme
import com.android.ty.systemservices.compose.ui.widget.setting.BigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveCollapsingToolbarLayout
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveCollapsingToolbarLayout_Two
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveMenuItem
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveToolbarMenu
import com.android.ty.systemservices.compose.ui.widget.setting.MainTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.loadBigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.loadMainTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.mainImplementation
import com.android.ty.systemservices.compose.ui.widget.setting.rootImplementation
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import org.xmlpull.v1.XmlPullParser

/**
 * 头部（大标题）采用哪一套实现。
 *
 * 两套折叠效果由两个独立的持久化设置控制，互不影响：
 * - [MAIN]：主界面体系，[MainTitleImpl] 两档（SCALE 缩放 / FADE 淡出），布局 160dp；
 * - [PREFERENCE]：Preference 页体系，[BigTitleImpl] 三档（含 ORIGINAL 普通顶栏），布局 200dp。
 */
enum class TitleStyle {
    MAIN,
    PREFERENCE,
}

/**
 * 模仿 Android 12 Settings 的“大标题”基类（全项目唯一实现）。
 *
 * 头部（AppBarLayout + CollapsingToolbarLayout）始终浮在内容上方，并通过 `canDrag() = false`
 * 关闭随列表滚动自动折叠的行为，展开/收起由子类通过 [setToolbarExpanded] 主动控制
 * （例如顶层页跳二级页时收起）。
 *
 * 子类通过构造参数 [titleStyle] 选择头部体系：
 * - 主界面（主页 / 实验室 / 开发调试 / 贡献者）传 [TitleStyle.MAIN]；
 * - Preference 页（[SettingsActivity] / OverlayActivity）用默认值 [TitleStyle.PREFERENCE]。
 *
 * 内容必须放进 `content_frame`：重写的 [setContentView] 只会替换内容区，
 * **不要**调用 Compose 的 `setContent {}`，那会替换掉整套布局（含标题栏）。
 */
abstract class CollapsingToolbarBaseActivity(
    private val titleStyle: TitleStyle = TitleStyle.PREFERENCE,
) : AppCompatActivity() {

    /**
     * 子类声明的菜单资源。两种渲染路径共用同一份 XML：
     * - [useExpressiveOverflowMenu] = false：走 AppCompat 原生菜单，Toolbar 自己排版 action / overflow；
     * - [useExpressiveOverflowMenu] = true：由 [installExpressiveMenu] 自绘，见下。
     *
     * XML 里 `app:showAsAction="always"` / `ifRoom` 的项会变成 Toolbar 上的图标按钮，
     * 其余（`never` 或未声明）收进溢出卡片；`android:group id` 变化处会插一条分隔线。
     */
    @get:MenuRes
    protected open val menuResId: Int = 0

    /**
     * 是否启用 Material 3 Expressive 的卡片式溢出菜单。
     *
     * 默认关闭，只有 [com.android.ty.systemservices.compose.ui.preference.XmlPreferenceActivity]
     * 覆写为 true，其余页面保持原生 Toolbar 行为不受影响。
     */
    protected open val useExpressiveOverflowMenu: Boolean = false

    private var contentFrame: FrameLayout? = null
    private var collapsingToolbarLayout: CollapsingToolbarLayout? = null
    private var appBarLayout: AppBarLayout? = null

    /** 自绘菜单时持有的 Menu 实例，点击回调用它找回原始 MenuItem */
    private var expressiveMenu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 头部布局按对应体系的持久化选择决定，并把选择写进全局状态供屏幕内其它组件读取。
        val baseLayout = when (titleStyle) {
            TitleStyle.MAIN -> {
                val impl = loadMainTitleImpl(this)
                mainImplementation = impl
                when (impl) {
                    MainTitleImpl.ONE -> R.layout.activity_collapsing_toolbar_one
                    MainTitleImpl.TWO -> R.layout.activity_collapsing_toolbar
                }
            }

            TitleStyle.PREFERENCE -> {
                // 与 XmlPreferenceActivity 共用同一份持久化选择，屏幕内组件读全局 rootImplementation
                val impl = loadBigTitleImpl(this)
                rootImplementation = impl
                when (impl) {
                    BigTitleImpl.ONE -> R.layout.activity_preference
                    BigTitleImpl.TWO -> R.layout.activity_preference_two
                    BigTitleImpl.ORIGINAL -> R.layout.activity_preference_original
                }
            }
        }
        super.setContentView(baseLayout)

        contentFrame = findViewById(R.id.content_frame)
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar)
        appBarLayout = findViewById(R.id.app_bar)

        val toolbar = findViewById<Toolbar>(R.id.action_bar)
        setSupportActionBar(toolbar)

        appBarLayout?.let { disableAutoCollapse(it) }

        if (useExpressiveOverflowMenu) installExpressiveMenu(toolbar)
    }

    /** 子类调用 setContentView 时，把内容放到 content_frame，头部保持不变 */
    override fun setContentView(layoutResID: Int) {
        contentFrame?.let { frame ->
            frame.removeAllViews()
            LayoutInflater.from(this).inflate(layoutResID, frame, true)
        }
    }

    /** 设置展开态的大标题（ORIGINAL 档没有折叠栏，回退到顶部栏标题） */
    fun setLargeTitle(title: CharSequence) {
        val layout = collapsingToolbarLayout
        if (layout != null) {
            layout.setTitle(title)
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
     * 按实际拿到的折叠栏类型分支，而不是按配置项分支：
     * ORIGINAL 档（没有折叠栏）回退到系统返回箭头，
     * 这样即使配置项与实际布局不同步也不会空指针。
     */
    fun setBackButton(visible: Boolean) {
        when (val layout = collapsingToolbarLayout) {
            is ExpressiveCollapsingToolbarLayout -> layout.setBackButtonEnabled(visible)
            is ExpressiveCollapsingToolbarLayout_Two -> layout.setBackButtonEnabled(visible)
            else -> supportActionBar?.setDisplayHomeAsUpEnabled(visible)
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
        // 自绘模式下不交给 AppCompat，否则 Toolbar 会再画一份原生 action / overflow 图标
        if (useExpressiveOverflowMenu) return false

        val handledByFragment = super.onCreateOptionsMenu(menu)
        onCreateMenu(menu)
        return handledByFragment || menu.size() > 0
    }

    /**
     * 把菜单渲染到 Toolbar 尾部：`showAsAction` 的项 → 圆形图标按钮，其余 → 卡片式溢出菜单。
     *
     * 用 AppCompat 的 [PopupMenu] 只为借一个 `MenuBuilder` 来承载 inflate 结果（它能识别
     * `app:showAsAction`），不显示弹窗本身。
     * `MenuItem.getShowAsAction()` 是 @hide API，这里改为直接读菜单 XML 的 `showAsAction` 属性。
     */
    private fun installExpressiveMenu(toolbar: Toolbar) {
        val menu = PopupMenu(this, View(this)).menu
        onCreateMenu(menu)
        if (menu.size() == 0) return
        expressiveMenu = menu

        // 子类用代码 add 的项 XML 里没有 showAsAction，按 never 处理，全部进卡片
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

    private fun disableAutoCollapse(appBar: AppBarLayout) {
        val behavior = AppBarLayout.Behavior()
        behavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
            override fun canDrag(appBarLayout: AppBarLayout): Boolean = false
        })
        val lp = appBar.layoutParams as CoordinatorLayout.LayoutParams
        lp.behavior = behavior
    }
}
