package com.lin0721.linmusic

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.ui.theme.MelodiaTheme

/** Independent passenger lifecycle, back dispatch, navigation and ViewModels.
 * The application-scoped Koin PlayerManager is shared, never a second player. */
class SecondaryPlayerPresentation(context: Context, display: Display, openPlayer: Boolean) :
    Presentation(context, display, R.style.Theme_Melodia),
    SavedStateRegistryOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    // File/crop permission launchers are registered with the one real Activity;
    // their callbacks belong to the passenger composition, not its main navigation.
    private val resultOwner = context as ActivityResultRegistryOwner
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    override val onBackPressedDispatcher = OnBackPressedDispatcher { /* At root keep the window. */ }
    private var openPlayer by mutableStateOf(openPlayer)
    private var refreshToken by mutableIntStateOf(0)
    private var composeView: ComposeView? = null
    private var released = false
    val contentDisplayId: Int? get() = composeView?.display?.displayId

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        setCanceledOnTouchOutside(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@SecondaryPlayerPresentation)
            setViewTreeViewModelStoreOwner(this@SecondaryPlayerPresentation)
            setViewTreeSavedStateRegistryOwner(this@SecondaryPlayerPresentation)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(
                    LocalOnBackPressedDispatcherOwner provides this@SecondaryPlayerPresentation,
                    LocalActivityResultRegistryOwner provides resultOwner
                ) {
                    MelodiaTheme {
                        key(refreshToken) {
                            MelodiaApp(isSecondaryDisplay = true, openPlayerOnSecondaryStart = openPlayer)
                        }
                    }
                }
            }
        }
        composeView = view
        setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        window?.let { configureMelodiaEdgeToEdge(it) }
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        registry.currentState = Lifecycle.State.RESUMED
        composeView?.post {
            AppLogger.performance("event=presentation_attached targetDisplay=${display.displayId} viewDisplay=$contentDisplayId")
        }
    }

    override fun onStop() { releaseOwners(); super.onStop() }
    override fun onBackPressed() { onBackPressedDispatcher.onBackPressed() }
    fun refreshContent(open: Boolean) { openPlayer = open; refreshToken++ }
    fun releaseOwners() {
        if (released) return
        released = true
        registry.currentState = Lifecycle.State.DESTROYED
        composeView?.disposeComposition()
        viewModelStore.clear()
        composeView = null
    }
}
