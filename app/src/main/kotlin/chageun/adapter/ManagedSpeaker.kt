/*******************************
 목   적 : Android 음성 엔진의 상태와 종료 계약
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-05·08 음성 엔진 상태 계약 추가
*******************************/
package chageun.adapter

import kotlinx.coroutines.flow.StateFlow

/** FR-05·08: UI 상태와 자원 종료를 Android 계층에 한정합니다. */
interface ManagedSpeaker : Speaker {
    val available: StateFlow<Boolean?>
    val limitation: StateFlow<String?>
    /** 엔진과 진행 중인 재생을 정리합니다. */
    fun close()
}
