package com.vpnlab.controlplane.api

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Vue SPA(history mode) 라우트를 index.html로 포워딩 (개선 4).
 *
 * /blocklist, /events, /policy 같은 프런트 라우트로 새로고침/직접 진입 시
 * 서버엔 해당 경로 리소스가 없으므로 404가 난다. 이를 index.html로 포워딩하면
 * Vue Router가 클라이언트에서 라우팅을 이어받는다.
 *
 * API 경로(/api), /healthz, /actuator, 정적 리소스(/assets)는 여기서 제외 —
 * 각자 REST 컨트롤러 / 정적 핸들러가 처리한다. 프런트 라우트만 명시 매핑한다.
 */
@Controller
class SpaController {

    // 프런트가 사용하는 클라이언트 라우트만 명시적으로 포워딩 (원본 Vue Router 4개)
    @GetMapping("/", "/blocklist", "/events", "/policy")
    fun forwardToIndex(): String = "forward:/index.html"
}
