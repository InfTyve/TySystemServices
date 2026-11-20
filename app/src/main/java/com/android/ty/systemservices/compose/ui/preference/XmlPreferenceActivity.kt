package com.android.ty.systemservices.compose.ui.preference

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.android.ty.systemservices.AboutActivity
import com.android.ty.systemservices.BackButtonHelper
import com.android.ty.systemservices.CollapsingToolbarBaseActivity
import com.android.ty.systemservices.R
import com.android.ty.systemservices.SecurityActivity
import com.android.ty.systemservices.TitleStyle
import com.android.ty.systemservices.compose.ContributorsActivity
import com.android.ty.systemservices.terminal.TermuxActivity

/**
 * 从 XML 加载 Preference 层级的设置页宿主。
 *
 * 头部（大标题 / 返回按钮 / 布局档位）全部由 [CollapsingToolbarBaseActivity] 的
 * PREFERENCE 体系提供，这里只负责挂 Fragment 和处理菜单。
 *
 * 菜单是本项目里唯一启用 Material 3 Expressive 卡片式溢出菜单的页面：
 * - `useExpressiveOverflowMenu = true` 时，`showAsAction="always"` 的项变成 Toolbar 上的圆形图标按钮，
 *   其余项收进 28dp 圆角卡片，按 `group` 分组并带分隔线；
 * - 其它页面保持 `false`，仍走 AppCompat 原生菜单，互不影响。
 */
class XmlPreferenceActivity : CollapsingToolbarBaseActivity(TitleStyle.PREFERENCE) {

    override val useExpressiveOverflowMenu: Boolean = true

    override val menuResId: Int = R.menu.xml_preference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 大标题文字与返回按钮显隐（按是否从本应用其它页面进入判断）
        setLargeTitle(R.string.app_name)
        setBackButton(BackButtonHelper.isFromOwnActivity(this))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_contributors ->
                startActivity(Intent(this, ContributorsActivity::class.java))

            R.id.action_security ->
                startActivity(Intent(this, SecurityActivity::class.java))

            R.id.action_terminal ->
                startActivity(Intent(this, TermuxActivity::class.java))

            R.id.action_about ->
                startActivity(Intent(this, AboutActivity::class.java))

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
