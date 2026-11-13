/*******************************
 목   적 : 재부팅 및 앱 갱신 후 기존 감시 재개
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-14 부팅 수신기 추가
*******************************/
package chageun.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** FR-14: 한 번 시작된 감시만 복원하며 사용자 강제 종료 제한을 우회하지 않습니다. */
class MonitorBootReceiver : BroadcastReceiver() {
    /** 잠금 해제 후 부팅 완료 또는 패키지 갱신 시 감시 서비스를 재개합니다. */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if (WarningMonitorService.preferences(context).getBoolean(WarningMonitorService.ENABLED, false)) {
            WarningMonitorService.start(context)
        }
    }
}
