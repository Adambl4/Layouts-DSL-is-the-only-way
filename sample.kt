package com.example.theonlyway

import android.app.*
import android.graphics.*
import android.os.*
import android.view.*
import org.jetbrains.anko.*
import rx.*
import java.util.concurrent.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(main_view.bind(MainViewModel::Impl))
    }
}

interface MainViewModel : ViewModelMarker {
    val anObservable get() = Observable.interval(1, TimeUnit.SECONDS)

    class Impl : MainViewModel
}


val main_view = viewWithModelFactory<MainViewModel> {
    frameLayout {
        lparams(matchParent, matchParent)

        textView(viewModel.anObservable.map(Long::toString)) {
            textSize = 50f
            textColor = Color.RED
        }.lparams {
            gravity = Gravity.CENTER
        }
    }
}
