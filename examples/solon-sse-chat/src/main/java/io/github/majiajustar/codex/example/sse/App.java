package io.github.majiajustar.codex.example.sse;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;

@SolonMain
public final class App {
    private App() {}

    public static void main(String[] args) {
        Solon.start(App.class, args);
    }
}
