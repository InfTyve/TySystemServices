package com.android.ty.systemservices.Overlay;

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManagerHidden;
import android.os.UserHandleHidden;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.ty.systemservices.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 忠实复刻 com.android.settings.development.OverlayCategoryPreferenceController。
 * <p>
 * 对照 smali 的实现：
 * <ul>
 *   <li>构造：PackageManager 来自 Context，IOverlayManager 来自
 *       ServiceManager.getService("overlay") + IOverlayManager.Stub.asInterface，
 *       mAvailable = (mOverlayManager != null && !getOverlayInfos().isEmpty())。</li>
 *   <li>getPreferenceKey() 返回 mCategory（即 XML 里 ListPreference 的 key）。</li>
 *   <li>displayPreference(screen)：findPreference(mCategory) 拿到 ListPreference 并保存。</li>
 *   <li>getOverlayInfos()：getOverlayInfosForTarget("android", 0)，按 OverlayInfo.category 过滤，
 *       按 OverlayInfo.priority 升序排序。</li>
 *   <li>updateState()：第一项固定 package_device_default，再追加每个 overlay 的包名(entryValue)
 *       与应用名(entry)；把当前 isEnabled() 的那一项设为 value/summary。</li>
 *   <li>onPreferenceChange() → setOverlay()：先取当前已启用包；若选 default 且 current 为空、
 *       或 newPkg 与 current 相同，直接返回 true；否则起 AsyncTask 真正下发切换，
 *       onPostExecute 里再 updateState。</li>
 * </ul>
 * IOverlayManager / OverlayInfo 均为隐藏 API，这里通过 HiddenApiRefinePlugin 的 hidden-api 存根模块
 * 以“链接方式”直接调用（compileOnly，运行时链接到框架真实类），不再使用反射。
 * 平台签名 + system uid 的 App 享有 hidden-api 豁免，可直接调用隐藏 API。
 */
public class OverlayCategoryPreferenceController implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "OverlayCategoryCtrl";

    /** 对应 smali 里的 PACKAGE_DEVICE_DEFAULT 常量 */
    static final String PACKAGE_DEVICE_DEFAULT = "package_device_default";

    private final Context mContext;
    private final String mCategory;
    private final IOverlayManager mOverlayManager;   // android.content.om.IOverlayManager
    private final PackageManager mPackageManager;
    private final boolean mAvailable;

    /** 对应 smali 的 mPreference:androidx.preference.ListPreference */
    private ListPreference mPreference;

    public OverlayCategoryPreferenceController(Context context, String category) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mCategory = category;
        mOverlayManager = getIOverlayManager();
        mAvailable = mOverlayManager != null && !getOverlayInfos().isEmpty();
    }

    // ------------------------------------------------------------------
    // smali: getPreferenceKey() / isAvailable()
    // ------------------------------------------------------------------

    /** 对应 getPreferenceKey()：返回 mCategory */
    public String getPreferenceKey() {
        return mCategory;
    }

    /** 对应 isAvailable() */
    public boolean isAvailable() {
        return mAvailable;
    }

    // ------------------------------------------------------------------
    // smali: displayPreference(PreferenceScreen) / setPreference(ListPreference)
    // ------------------------------------------------------------------

    /** 对应 displayPreference()：在 PreferenceScreen 里按 key 找到 ListPreference 并保存 */
    public void displayPreference(PreferenceScreen screen) {
        Preference p = screen.findPreference(getPreferenceKey());
        if (p instanceof ListPreference) {
            mPreference = (ListPreference) p;
            mPreference.setOnPreferenceChangeListener(this);
        }
    }

    // ------------------------------------------------------------------
    // smali: getOverlayInfos()
    // ------------------------------------------------------------------

    /**
     * 对应 getOverlayInfos()：getOverlayInfosForTarget("android", 0) 后按 category 过滤，
     * 再按 OverlayInfo.priority 升序排序。
     */
    private List<OverlayInfo> getOverlayInfos() {
        List<OverlayInfo> result = new ArrayList<>();
        if (mOverlayManager == null) {
            return result;
        }
        try {
            List<OverlayInfo> infos = mOverlayManager.getOverlayInfosForTarget("android", 0);
            for (OverlayInfo info : infos) {
                if (mCategory.equals(info.category)) {
                    result.add(info);
                }
            }
            // OVERLAY_INFO_COMPARATOR = Comparator.comparingInt(info -> info.priority)
            Collections.sort(result, new Comparator<OverlayInfo>() {
                @Override
                public int compare(OverlayInfo o1, OverlayInfo o2) {
                    return Integer.compare(o1.priority, o2.priority);
                }
            });
        } catch (RemoteException e) {
            // AIDL 调用底层系统服务：还原 smali 的 rethrowFromSystemServer()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                throw e.rethrowFromSystemServer();
            }
            throw new RuntimeException(e);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // smali: updateState(Preference)
    // 直接操作 ListPreference，与原实现一致
    // ------------------------------------------------------------------

    /**
     * 对应 updateState(Preference)：把 package_device_default 作为首项，
     * 再追加每个 overlay 的包名(entryValue)/应用名(entry)；把当前 isEnabled() 的那一项
     * 设为 ListPreference 的 value 与 summary。
     */
    public void updateState() {
        if (mPreference == null) {
            return;
        }
        List<String> entryValues = new ArrayList<>();
        List<String> entries = new ArrayList<>();

        // 首项：package_device_default + "设备默认"（原资源 0x7f120a7d）
        entryValues.add(PACKAGE_DEVICE_DEFAULT);
        entries.add(mContext.getString(R.string.theme_customization_device_default));

        String selValue = entryValues.get(0);
        String selSummary = entries.get(0);

        for (OverlayInfo info : getOverlayInfos()) {
            String pkg = info.packageName;
            entryValues.add(pkg);

            String label;
            try {
                ApplicationInfo ai = mPackageManager.getApplicationInfo(pkg, 0);
                label = ai.loadLabel(mPackageManager).toString();
            } catch (PackageManager.NameNotFoundException e) {
                // smali: catch NameNotFoundException 时用 packageName 作为 label
                label = pkg;
            }
            entries.add(label);

            if (info.isEnabled()) {
                selValue = pkg;
                selSummary = label;
            }
        }

        // smali: setEntries / setEntryValues / setValue / setSummary
        mPreference.setEntries(entries.toArray(new CharSequence[0]));
        mPreference.setEntryValues(entryValues.toArray(new CharSequence[0]));
        mPreference.setValue(selValue);
        mPreference.setSummary(selSummary);
    }

    // ------------------------------------------------------------------
    // smali: onPreferenceChange() -> setOverlay(String)
    // ------------------------------------------------------------------

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // smali: onPreferenceChange(p, value) -> setOverlay((String) value)
        return setOverlay((String) newValue);
    }

    /**
     * 对应 setOverlay(String)：
     * 1) 取当前已启用包 currentPkg（stream.filter(isEnabled).map(packageName).findFirst().orElse(null)）
     * 2) 若 PACKAGE_DEVICE_DEFAULT.equals(newPkg) 且 currentPkg 为空 → 直接返回 true
     *    若 newPkg 与 currentPkg 相同 → 直接返回 true
     * 3) 否则起 AsyncTask 真正下发：选 default 则禁用 currentPkg，否则 setEnabledExclusiveInCategory(newPkg)
     */
    private boolean setOverlay(final String newPkg) {
        String currentPkg = null;
        for (OverlayInfo info : getOverlayInfos()) {
            if (info.isEnabled()) {
                currentPkg = info.packageName;
                break;
            }
        }

        // smali: if (PACKAGE_DEVICE_DEFAULT.equals(newPkg) && TextUtils.isEmpty(currentPkg)) return true;
        if (PACKAGE_DEVICE_DEFAULT.equals(newPkg) && TextUtils.isEmpty(currentPkg)) {
            return true;
        }
        // smali: if (TextUtils.equals(newPkg, currentPkg)) return true;
        if (TextUtils.equals(newPkg, currentPkg)) {
            return true;
        }

        // smali: new OverlayCategoryPreferenceController$1(this, newPkg, currentPkg).execute()
        new SetOverlayTask(newPkg, currentPkg).execute();
        return true;
    }

    /** 对应 onDeveloperOptionsSwitchDisabled()：回到 package_device_default 再刷新 */
    public void onDisabled() {
        setOverlay(PACKAGE_DEVICE_DEFAULT);
        updateState();
    }

    // ------------------------------------------------------------------
    // OverlayCategoryPreferenceController$1 的等价实现
    // ------------------------------------------------------------------

    private class SetOverlayTask extends AsyncTask<Void, Void, Void> {
        private final String mNewPackage;
        private final String mCurrentPackage;

        SetOverlayTask(String newPackage, String currentPackage) {
            mNewPackage = newPackage;
            mCurrentPackage = currentPackage;
        }

        @Override
        protected Void doInBackground(Void... params) {
            // UserHandle.myUserId() 是 @hide 静态方法，经存根直接调用（与 smali 取当前用户一致）
            final int userId = UserHandleHidden.myUserId();
            try {
                if (mOverlayManager == null) {
                    return null;
                }
                if (PACKAGE_DEVICE_DEFAULT.equals(mNewPackage)) {
                    // 选“设备默认”：禁用当前已启用包
                    if (!TextUtils.isEmpty(mCurrentPackage)) {
                        mOverlayManager.setEnabled(mCurrentPackage, false, userId);
                    }
                } else {
                    // smali: mOverlayManager.setEnabledExclusiveInCategory(mNewPackage, userId)
                    mOverlayManager.setEnabledExclusiveInCategory(mNewPackage, userId);
                }
            } catch (RemoteException e) {
                Log.w(TAG, "setEnabled failed (RemoteException)", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            // 对应原实现 onPostExecute 里的 updateState(mPreference)
            updateState();
        }
    }

    // ------------------------------------------------------------------
    // 隐藏 API 访问（经 hidden-api 存根，直接链接方式调用）
    // ------------------------------------------------------------------

    /** 通过 ServiceManager.getService("overlay") + IOverlayManager.Stub.asInterface 获取 IOverlayManager */
    private static IOverlayManager getIOverlayManager() {
        IBinder binder = ServiceManagerHidden.getService("overlay");
        if (binder == null) {
            return null;
        }
        return IOverlayManager.Stub.asInterface(binder);
    }
}
