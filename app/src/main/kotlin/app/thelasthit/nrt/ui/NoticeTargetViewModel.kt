package app.thelasthit.nrt.ui

import androidx.lifecycle.ViewModel
import app.thelasthit.nrt.reminders.NoticeTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The page a tapped notice asked for, until the app's shell has opened it. It belongs to the
 * activity, as the links' view model does, so a notice that starts the app waits for the shell.
 */
class NoticeTargetViewModel : ViewModel() {

    private val _target = MutableStateFlow<NoticeTarget?>(null)
    val target: StateFlow<NoticeTarget?> = _target.asStateFlow()

    fun open(target: NoticeTarget) {
        _target.value = target
    }

    /** The shell has opened [target]. */
    fun opened(target: NoticeTarget) {
        _target.compareAndSet(target, null)
    }
}
