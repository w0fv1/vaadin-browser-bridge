package dev.w0fv1.vaadmin.remotebrowser;

import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;

public final class RemoteBrowserViewer extends VerticalLayout {
    public static final String DEFAULT_URL = "https://example.com/";

    private final RemoteBrowser browser;
    private final Select<BrowserProfileId> profile = new Select<>("浏览器配置");
    private final Button start = new Button("启动浏览器", event -> open(false));
    private final Button privateWindow = new Button("隐私窗口", event -> open(true));
    private final Button stop = new Button("关闭会话", event -> close());
    private final Div status = new Div();
    private final BrowserStreamComponent stream = new BrowserStreamComponent();
    private String initialUrl = DEFAULT_URL;
    private RemoteBrowserSession session;
    private BrowserProfileId activeProfile;
    private boolean privateMode;

    public RemoteBrowserViewer(RemoteBrowser browser) {
        this.browser = browser;
        profile.setItems(browser.profiles());
        profile.setItemLabelGenerator(BrowserProfileId::value);
        profile.setValue(BrowserProfileId.DEFAULT);
        stream.setStreamPath(browser.streamPath());
        setSizeFull();
        setSpacing(true);
        HorizontalLayout actions = new HorizontalLayout(start, privateWindow, stop);
        actions.setPadding(false);
        add(profile, actions, status, stream);
        expand(stream);
        renderStatus();
    }

    public void setInitialUrl(String url) {
        initialUrl = url == null || url.isBlank() ? DEFAULT_URL : url.trim();
        renderStatus();
    }

    public void setProfile(BrowserProfileId profileId) {
        profile.setValue(profileId);
    }

    public void open() {
        open(false);
    }

    public void openPrivate() {
        open(true);
    }

    public void close() {
        if (session != null) {
            session.close();
            session = null;
        }
        stream.setSessionId(null);
        start.setText("启动浏览器");
        renderStatus();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        close();
    }

    private void open(boolean requestedPrivate) {
        close();
        BrowserProfileId profileId = profile.getValue();
        if (profileId == null) {
            status.setText("请选择浏览器配置");
            return;
        }
        try {
            session = requestedPrivate
                    ? browser.openPrivate(profileId, initialUrl)
                    : browser.open(profileId, initialUrl);
            activeProfile = profileId;
            privateMode = requestedPrivate;
            stream.setSessionId(session.sessionId());
            start.setText("重启浏览器");
            renderStatus();
        } catch (RuntimeException failure) {
            status.setText("创建会话失败：" + failure.getMessage());
        }
    }

    private void renderStatus() {
        if (session == null) {
            status.setText("尚未创建会话，首个标签页地址 " + initialUrl);
            return;
        }
        String source = privateMode
                ? "隐私窗口，模板配置 " + activeProfile.value() + "，关闭后清空全部状态"
                : "配置 " + activeProfile.value();
        status.setText("会话 " + session.sessionId() + " 已就绪，" + source
                + "，环境 " + session.environmentMode());
    }
}
