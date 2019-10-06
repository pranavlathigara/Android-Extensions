package com.tunjid.androidx.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import com.tunjid.androidx.core.components.args
import com.tunjid.androidx.savedstate.LifecycleSavedStateContainer
import com.tunjid.androidx.savedstate.savedStateFor
import java.util.*

const val MULTI_STACK_NAVIGATOR = "com.tunjid.androidx.navigation.MultiStackNavigator"

fun Fragment.childMultiStackNavigationController(
        stackCount: Int,
        @IdRes containerId: Int,
        rootFunction: (Int) -> Pair<Fragment, String>
): Lazy<MultiStackNavigator> = lazy {
    MultiStackNavigator(
            stackCount,
            savedStateFor(this@childMultiStackNavigationController, "$MULTI_STACK_NAVIGATOR-$containerId"),
            childFragmentManager,
            containerId, rootFunction
    )
}

fun FragmentActivity.multiStackNavigationController(
        stackCount: Int,
        @IdRes containerId: Int,
        rootFunction: (Int) -> Pair<Fragment, String>
): Lazy<MultiStackNavigator> = lazy {
    MultiStackNavigator(
            stackCount,
            savedStateFor(this@multiStackNavigationController, "$MULTI_STACK_NAVIGATOR-$containerId"),
            supportFragmentManager,
            containerId,
            rootFunction
    )
}

/**
 * Manages navigation for independent stacks of [Fragment]s, where each stack is managed by a
 * [StackNavigator].
 */
class MultiStackNavigator(
        stackCount: Int,
        private val stateContainer: LifecycleSavedStateContainer,
        private val fragmentManager: FragmentManager,
        @IdRes override val containerId: Int,
        private val rootFunction: (Int) -> Pair<Fragment, String>) : Navigator {

    /**
     * A callback that will be invoked when a stack is selected, either by the user selecting it,
     * or from popping another stack off.
     */
    var stackSelectedListener: ((Int) -> Unit)? = null

    /**
     * Allows for the customization or augmentation of the [FragmentTransaction] that switches
     * from one active stack to another
     */
    var stackTransactionModifier: (FragmentTransaction.(Int) -> Unit)? = null

    /**
     * Allows for the customization or augmentation of the [FragmentTransaction] that will show
     * a [Fragment] inside the stack in focus
     */
    var transactionModifier: (FragmentTransaction.(Fragment) -> Unit)? = null
        set(value) {
            field = value
            stackFragments
                    .filter { it.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
                    .forEach { it.navigator.transactionModifier = value }
        }

    private val indices = 0 until stackCount
    private val backStack: Stack<Int> = Stack()
    private val stackFragments: List<StackFragment>
    private val tabVisitedLookUp = BooleanArray(stackCount) { false }

    private val activeFragment: StackFragment
        get() = stackFragments.run { firstOrNull(Fragment::isAttached) ?: first() }

    val activeIndex
        get() = activeFragment.index

    val activeNavigator
        get() = activeFragment.navigator

    override val currentFragment: Fragment?
        get() = activeNavigator.currentFragment

    init {
        fragmentManager.registerFragmentLifecycleCallbacks(StackLifecycleCallback(), false)
        fragmentManager.addOnBackStackChangedListener { throw IllegalStateException("Fragments may not be added to the back stack of a FragmentManager managed by a MultiStackNavigator") }

        val freshState = stateContainer.isFreshState

        if (freshState) fragmentManager.commitNow {
            indices.forEach { index -> add(containerId, StackFragment.newInstance(index), index.toString()) }
        }
        else fragmentManager.addedStackFragments(indices).forEach { stackFragment ->
            backStack.push(stackFragment.index)
        }

        stateContainer.savedState.getIntArray(NAV_STACK_ORDER)?.apply { backStack.sortBy { indexOf(it) } }
        stateContainer.savedState.getBooleanArray(TAB_VISITED_LOOKUP)?.apply { copyInto(tabVisitedLookUp) }

        stackFragments = fragmentManager.addedStackFragments(indices)

        if (freshState) show(0)
    }

    fun show(index: Int) = showInternal(index, true)

    fun navigatorAt(index: Int) = stackFragments[index].navigator

    /**
     * Pops the current fragment off the stack in focus. If The current
     * Fragment is the only Fragment on it's stack, the stack that was active before the current
     * stack is switched to.
     *
     * @see [StackNavigator.pop]
     */
    override fun pop(): Boolean = when {
        activeFragment.navigator.pop() -> true
        backStack.run { remove(activeFragment.index); isEmpty() } -> false
        else -> showInternal(backStack.peek(), false).let { true }
    }

    override fun clear(upToTag: String?, includeMatch: Boolean) = activeNavigator.clear(upToTag, includeMatch)

    override fun show(fragment: Fragment, tag: String): Boolean = activeNavigator.show(fragment, tag)

    private fun showInternal(index: Int, addTap: Boolean) = fragmentManager.commit {
        val toShow = stackFragments[index]

        if (!tabVisitedLookUp[index]) toShow.showRoot()
        if (addTap) track(toShow)

        stackTransactionModifier?.invoke(this, index)

        transactions@ for (fragment in stackFragments) when {
            fragment.index == index && !fragment.isDetached -> continue@transactions
            fragment.index == index && fragment.isDetached -> attach(fragment)
            else -> if (!fragment.isDetached) detach(fragment)
        }

        runOnCommit { stackSelectedListener?.invoke(index) }
    }

    private fun track(tab: StackFragment) = tab.run {
        if (backStack.contains(index)) backStack.remove(index)
        backStack.push(index)
        stateContainer.savedState.putIntArray(NAV_STACK_ORDER, backStack.toIntArray())
    }

    private fun StackFragment.showRoot() = index.let {
        if (!tabVisitedLookUp[it]) rootFunction(index).apply { navigator.show(first, second) }
        tabVisitedLookUp[it] = true
        stateContainer.savedState.putBooleanArray(TAB_VISITED_LOOKUP, tabVisitedLookUp)
    }

    private inner class StackLifecycleCallback : FragmentManager.FragmentLifecycleCallbacks() {

        override fun onFragmentCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
            if (fragment.id != containerId) return
            check(fragment is StackFragment) { "Only Stack Fragments may be added to a container View managed by a MultiStackNavigator" }

            if (!stateContainer.isFreshState) return

            if (fragment.index != 0) fm.beginTransaction().detach(fragment).commit()
        }

        override fun onFragmentViewCreated(fm: FragmentManager, fragment: Fragment, view: View, savedInstanceState: Bundle?) {
            if (fragment.id != containerId) return
            check(fragment is StackFragment) { "Only Stack Fragments may be added to a container View managed by a MultiStackNavigator" }
        }

        override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
            if (fragment.id != containerId) return
            check(fragment is StackFragment) { "Only Stack Fragments may be added to a container View managed by a MultiStackNavigator" }

            fragment.navigator.transactionModifier = this@MultiStackNavigator.transactionModifier
        }
    }
}

const val NAV_STACK_ORDER = "navState"
const val TAB_VISITED_LOOKUP = "tabVisitedLookup"

class StackFragment : Fragment() {

    internal lateinit var navigator: StackNavigator

    internal var index: Int by args()
    private var containerId: Int by args()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deferred: StackNavigator by childStackNavigationController(containerId)
        navigator = deferred
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            FragmentContainerView(inflater.context).apply { id = containerId }

    companion object {
        internal fun newInstance(index: Int) = StackFragment().apply { this.index = index; containerId = View.generateViewId() }
    }
}

private val Fragment.isAttached get() = !isDetached

private fun FragmentManager.addedStackFragments(indices: IntRange) = indices
        .map(Int::toString)
        .map(::findFragmentByTag)
        .filterIsInstance(StackFragment::class.java)