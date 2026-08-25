package io.github.majiajustar.codex.example.sse.controller;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;

@Controller
public final class HomeController {
    @Get
    @Mapping("/")
    public void home(Context context) {
        context.redirect("/index.html");
    }
}
