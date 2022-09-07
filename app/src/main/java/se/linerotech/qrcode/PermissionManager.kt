package se.linerotech.qrcode

import android.app.Activity
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.reactivex.Observable

class PermissionManager(private val activity: Activity) {
    fun isPermissionGranted(permissionName: String): Observable<Boolean> {
        val result =
            ContextCompat.checkSelfPermission(
                activity,
                permissionName
            ) == PackageManager.PERMISSION_GRANTED
        return Observable.just(result)
    }

    fun requestPermissionFor(permissionName: String, permissionRequestKey: Int) {
        ActivityCompat.requestPermissions(activity, arrayOf(permissionName), permissionRequestKey)
    }

    fun hasPermissionBeenAsked(): Boolean {
        val getPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
        return getPrefs.getBoolean(ASKING_PERMISSION_KEY, false)
    }

    fun permissionHasBeenAsked() {
        val getPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val e = getPrefs.edit()
        e.putBoolean(ASKING_PERMISSION_KEY, true)
        e.apply()
    }

    companion object {
        private const val ASKING_PERMISSION_KEY = "askingPermissionKey"
    }
}
