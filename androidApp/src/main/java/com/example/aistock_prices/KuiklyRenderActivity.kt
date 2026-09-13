package com.example.aistock_prices

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.css.ktx.toMap
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.example.aistock_prices.adapter.KRColorParserAdapter
import com.example.aistock_prices.adapter.KRFontAdapter
import com.example.aistock_prices.adapter.KRImageAdapter
import com.example.aistock_prices.adapter.KRLogAdapter
import com.example.aistock_prices.adapter.KRRouterAdapter
import com.example.aistock_prices.adapter.KRThreadAdapter
import com.example.aistock_prices.adapter.KRUncaughtExceptionHandlerAdapter
import com.example.aistock_prices.module.KRBridgeModule
import com.example.aistock_prices.module.KRShareModule
import org.json.JSONObject

class KuiklyRenderActivity : AppCompatActivity(), KuiklyRenderViewBaseDelegatorDelegate {

    private lateinit var hrContainerView: ViewGroup
    private lateinit var loadingView: View
    private lateinit var errorView: View

    private val kuiklyRenderViewDelegator = KuiklyRenderViewBaseDelegator(this)

    private val pageName: String
        get() {
            val pn = intent.getStringExtra(KEY_PAGE_NAME) ?: ""
            return if (pn.isNotEmpty()) {
                return pn
            } else {
                "stock_list"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_hr)
        setupImmersiveMode()
        hrContainerView = findViewById(R.id.hr_container)
        loadingView = findViewById(R.id.hr_loading)
        errorView = findViewById(R.id.hr_error)
        setupSoftInputKeyboard()
        kuiklyRenderViewDelegator.onAttach(hrContainerView, "", pageName, createPageData())
    }

    /**
     * 软键盘遮挡输入框修复：
     * 页面采用沉浸式布局（内容延伸到状态栏下，Kuikly 顶部自行 padding statusBarHeight），
     * 该模式下 windowSoftInputMode=adjustResize 不会压缩窗口高度 → 键盘升起会盖住底部输入条。
     * 这里监听窗口可见区域，键盘弹出时把 hr_container 底部 padding 设为键盘高度，
     * 使 Kuikly 容器实际高度被压缩（触发重新布局），底部输入栏被顶到键盘上方。
     */
    private fun setupSoftInputKeyboard() {
        val decor = window.decorView
        val minKbPx = (100 * resources.displayMetrics.density).toInt() // 阈值：过滤底部导航栏等小差值
        hrContainerView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            decor.getWindowVisibleDisplayFrame(rect)
            val keyboardH = decor.height - rect.bottom
            val target = if (keyboardH > minKbPx) keyboardH else 0
            if (hrContainerView.paddingBottom != target) {
                hrContainerView.setPadding(0, 0, 0, target)
            }
        }
    }

    /**
     * 点击输入框以外的区域收起键盘：
     * Kuikly 渲染层不会在点击空白时主动收键盘（只有业务显式调 Input.blur() 才会），
     * 键盘弹出后会一直停留在屏幕上遮住下方内容，用户会以为「点哪都没反应」。
     * 这里在事件分发的最前端拦截（早于所有子 View，滚动列表内部也覆盖得到）：
     * 按下时若存在聚焦中的输入框、且落点不在任何输入框内，则清焦点并隐藏输入法。
     * 清焦点会触发 KRTextFieldView 的 onFocusChanged，行为与 Kuikly 官方 blur() 一致。
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            dismissKeyboardOnTouchOutside(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun dismissKeyboardOnTouchOutside(ev: MotionEvent) {
        if (!::hrContainerView.isInitialized) return
        val root: View = window.decorView
        // 落点在任意输入框内（含正在编辑的这个）都放行：点击输入框应保持/进入编辑态
        if (isTouchInsideEditText(root, ev)) return
        val focused = findFocusedEditText(root)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        // 拿不到聚焦框（如渲染层刚清了焦点）时用容器 token 兜底，保证键盘一定收起
        val token = (focused ?: hrContainerView).windowToken
        focused?.clearFocus()
        // ⚠️ 必须延迟再隐藏：与 Kuikly 渲染层 KRTextFieldView.setBlur() 的做法一致
        // （clearFocus() 后 post 再 hideSoftInput）。同步隐藏时渲染层会在同一帧把键盘弹回来，
        // 表现为「收键盘时灵时不灵」。用 post 保证在渲染层的焦点处理之后执行。
        hrContainerView.post { imm?.hideSoftInputFromWindow(token, 0) }
    }

    /** 递归查找当前聚焦且可见的输入框（Kuikly 的 Input/TextArea 都是 EditText 子类） */
    private fun findFocusedEditText(view: View): EditText? {
        if (view is EditText) {
            return if (view.isFocused && view.isShown) view else null
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findFocusedEditText(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** 触摸点是否落在某个可见输入框的可见区域内（坐标为屏幕绝对值，与 rawX/rawY 同一坐标系） */
    private fun isTouchInsideEditText(view: View, ev: MotionEvent): Boolean {
        if (view is EditText) {
            if (view.isShown) {
                val rect = Rect()
                if (view.getGlobalVisibleRect(rect) &&
                    rect.contains(ev.rawX.toInt(), ev.rawY.toInt())
                ) {
                    return true
                }
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (isTouchInsideEditText(view.getChildAt(i), ev)) return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        kuiklyRenderViewDelegator.onDetach()
    }

    override fun onPause() {
        super.onPause()
        kuiklyRenderViewDelegator.onPause()
    }

    override fun onResume() {
        super.onResume()
        kuiklyRenderViewDelegator.onResume()
    }

    override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalModule(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            moduleExport(KRBridgeModule.MODULE_NAME) {
                KRBridgeModule()
            }
            moduleExport(KRShareModule.MODULE_NAME) {
                KRShareModule()
            }
        }
    }

    override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalRenderView(kuiklyRenderExport)
        with(kuiklyRenderExport) {

        }
    }

    private fun createPageData(): Map<String, Any> {
        val param = argsToMap()
        param["appId"] = 1
        // 主题注入：isNightMode（实际是否夜间）驱动页面配色；themeMode（三态）供菜单展示当前模式
        // （键名须与 shared BasePager 读取一致：BasePager.IS_NIGHT_MODE_KEY / IS_THEME_MODE_KEY）
        param["isNightMode"] = ThemeController.currentNight()
        param["themeMode"] = ThemeController.mode
        return param
    }

    private fun argsToMap(): MutableMap<String, Any> {
        val jsonStr = intent.getStringExtra(KEY_PAGE_DATA) ?: return mutableMapOf()
        return JSONObject(jsonStr).toMap()
    }

    private fun setupImmersiveMode() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window?.statusBarColor = Color.TRANSPARENT
            var vis = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            // 状态栏图标颜色跟随主题：夜间用浅色图标，日间用深色图标
            if (!ThemeController.currentNight()) {
                vis = vis or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            decorView.systemUiVisibility = vis
        }

    }

    companion object {

        private const val KEY_PAGE_NAME = "pageName"
        private const val KEY_PAGE_DATA = "pageData"

        init {
            initKuiklyAdapter()
        }

        fun start(context: Context, pageName: String, pageData: JSONObject) {
            val starter = Intent(context, KuiklyRenderActivity::class.java)
            starter.putExtra(KEY_PAGE_NAME, pageName)
            starter.putExtra(KEY_PAGE_DATA, pageData.toString())
            context.startActivity(starter)
        }

        private fun initKuiklyAdapter() {
            with(KuiklyRenderAdapterManager) {
                krImageAdapter = KRImageAdapter(KRApplication.application)
                krLogAdapter = KRLogAdapter
                krUncaughtExceptionHandlerAdapter = KRUncaughtExceptionHandlerAdapter
                krFontAdapter = KRFontAdapter
                krColorParseAdapter = KRColorParserAdapter(KRApplication.application)
                krRouterAdapter = KRRouterAdapter
                krThreadAdapter = KRThreadAdapter()
            }
        }
    }
}