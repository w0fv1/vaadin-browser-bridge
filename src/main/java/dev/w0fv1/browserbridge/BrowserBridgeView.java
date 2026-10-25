package dev.w0fv1.browserbridge;

import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;

import java.util.Optional;

@Route("")
public final class BrowserBridgeView extends VerticalLayout implements BeforeEnterObserver {
    private static final String DEFAULT_URL = "https://example.com/";

    private final BrowserSessionManager sessions;
    private final Select<BrowserProfileId> profile = new Select<>("浏览器配置");
    private final BrowserStreamComponent stream = new BrowserStreamComponent();
    private final Div status = new Div();
    private String targetUrl = DEFAULT_URL;
    private BrowserSessionLease lease;

    public BrowserBridgeView(BrowserSessionManager sessions, BrowserEnvironmentStore environments) {
        this.sessions = sessions;
        profile.setItems(environments.profileIds());
        profile.setItemLabelGenerator(BrowserProfileId::value);
        profile.setValue(BrowserProfileId.DEFAULT);
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        add(new H2("服务端浏览器桥接"),
                profile,
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
        if (lease != null) {
            lease.close();
            lease = null;
        }
        BrowserProfileId profileId = profile.getValue();
        if (profileId == null) {
            status.setText("请选择浏览器配置");
            return;
        }
        try {
            lease = sessions.open(profileId.value(), targetUrl);
            BrowserSession session = lease.session();
            stream.setSessionId(session.id());
            status.setText("会话 " + session.id() + " 已就绪，配置 " + profileId.value()
                    + "，环境 " + session.browserInfo().environmentMode());
        } catch (RuntimeException failure) {
            status.setText("创建会话失败：" + failure.getMessage());
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (lease != null) {
            lease.close();
            lease = null;
        }
    }
}
