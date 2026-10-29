package com.kewei.parts.infrastructure.desktop;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Windows运行包使用同一端口提供页面与API，页面刷新由Vue路由处理。 */
@Controller
@ConditionalOnProperty(name = "parts.trace.desktop.enabled", havingValue = "true")
public class DesktopPageController {
    @GetMapping({"/", "/login", "/dashboard", "/profile", "/query", "/statistics",
            "/user", "/user/list", "/user/role", "/user/permission",
            "/parts", "/parts/category", "/parts/list", "/parts/did",
            "/trace", "/trace/workbench", "/trace/event", "/trace/certificate",
            "/anomaly", "/anomaly/rule", "/anomaly/alert"})
    public String page() {
        return "forward:/index.html";
    }
}
