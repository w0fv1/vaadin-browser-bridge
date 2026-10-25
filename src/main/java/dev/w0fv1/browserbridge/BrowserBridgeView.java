package dev.w0fv1.browserbridge;

import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;

import java.util.Optional;

@Route("")
public final class BrowserBridgeView extends VerticalLayout implements BeforeEnterObserver {
    private static final String DEFAULT_URL = "https://example.com/";

    private final BrowserSessionManager sessions;
    private final BrowserStreamComponent stream = new BrowserStreamComponent();
    private final Div status = new Div();
    private String targetUrl = DEFAULT_URL;
    private BrowserSession session;

    public BrowserBridgeView(BrowserSessionManager sessions) {
        this.sessions = sessions;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        add(new H2("服务端浏览器桥接"),
                new Button("启动浏览器", event -> start()),
                status,
                stream);
        status.setText("尚未创建会话，首个标签页地址 " + targetUrl);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<String> requested = event.getLocation().getQueryParameters().getSingleParameter("url");
        requested.ifPresent(url -> {
            targetUrl = url;
            status.setText("尚未创建会话，首个标签页地址 " + targetUrl);
        });
    }

    private void start() {
        if (session != null) {
            sessions.close(session.id());
            session = null;
        }
        try {
            session = sessions.create(targetUrl);
            stream.setSessionId(session.id());
            status.setText("会话 " + session.id() + " 已创建");
        } catch (RuntimeException failure) {
            status.setText("创建会话失败：" + failure.getMessage());
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (session != null) {
            sessions.close(session.id());
            session = null;
        }
    }
}
