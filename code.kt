package com.example.theonlyway

import android.app.*
import android.content.*
import android.support.v4.view.*
import android.view.*
import com.jakewharton.rxbinding.view.*
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.*
import rx.*
import rx.android.schedulers.*
import rx.subjects.*
import java.lang.reflect.*

typealias ViewFactory = (Context) -> View

fun <T : View> viewFactory(factory: Context.() -> T): ViewFactory = { context -> context.factory() }

inline fun Activity.setContentView(viewFactory: ViewFactory) = setContentView(viewFactory(this))

typealias ViewWithModelFactory<VM> = (ContextWithViewModel<VM>) -> View

interface ViewModelMarker

fun <VM : ViewModelMarker> viewWithModelFactory(factory: ContextWithViewModel<VM>.() -> View)
        : ViewWithModelFactory<VM> = { context -> context.factory() }

inline fun <reified VM : ViewModelMarker> ((ContextWithViewModel<VM>) -> View).bind(crossinline vmFactory: () -> VM): ViewFactory = { context ->
    val viewModelInterface = if (VM::class.java.isInterface && ViewModelMarker::class.java.isAssignableFrom(VM::class.java)) {
        VM::class.java
    } else {
        VM::class.java.interfaces.first(ViewModelMarker::class.java::isAssignableFrom)
    }

    val viewBus = BehaviorSubject.create<View>()

    val lifecycleProxy = lifecycleProxy(vmFactory(), viewModelInterface, viewBus)
    val view = invoke(ContextWithViewModel(lifecycleProxy, context))

    viewBus.onNext(view)

    view
}

class ContextWithViewModel<out VM : ViewModelMarker>(
        val viewModel: VM,
        context: Context
) : ContextWrapper(context)

@Suppress("UNCHECKED_CAST")
fun <VM : ViewModelMarker> lifecycleProxy(viewModel: VM,
                                          vmInterface: Class<*>,
                                          view: Observable<View>): VM {
    val handler = InvocationHandler { proxy, method, args ->
       if (method.returnType == Observable::class.java) {
            (method.invokeWithArgs(viewModel, args) as Observable<*>)
                    .observeOn(AndroidSchedulers.mainThread())
                    .delaySubscription(view.flatMap { view ->
                        if (!ViewCompat.isAttachedToWindow(view))
                            view.attaches()
                        else
                            Observable.just(Unit)
                    })
                    .takeUntil(view.flatMap(View::detaches))
                    .repeatWhen { it.flatMap { view.flatMap(View::attaches) } }
        } else {
            method.invokeWithArgs(viewModel, args)
        }
    }

    return Proxy.newProxyInstance(vmInterface.classLoader, arrayOf(vmInterface), handler) as VM
}

//adds nullability to args parameter
fun Method.invokeWithArgs(receiver: Any, args: Array<Any>?) =
        if (args != null) {
            invoke(receiver, *args)
        } else {
            invoke(receiver)
        }


inline fun <T : CharSequence?> ViewManager.textView(observableText: Observable<T>, init: android.widget.TextView.() -> Unit): android.widget.TextView
        = textView { init(); observableText.subscribe(::setText) }

inline fun ViewManager.addView(viewFactory: ViewFactory) = ankoView(viewFactory, 0, {})

inline fun <VM : ViewModelMarker>  ViewManager.addViewWithModel(viewFactory: ViewWithModelFactory<VM>, vm: VM)
        = ankoView({ ctx: Context -> viewFactory(ContextWithViewModel(vm, ctx)) }, 0, {})

