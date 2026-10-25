package dev.w0fv1.vaadmin.remotebrowser.sample;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import dev.w0fv1.vaadmin.remotebrowser.RemoteBrowser;
import dev.w0fv1.vaadmin.remotebrowser.RemoteBrowserViewer;

import java.util.Optional;

@Route("")
public final class RemoteBrowserSampleView extends VerticalLayout implements BeforeEnterObserver {
    private final RemoteBrowserViewer viewer;

    public RemoteBrowserSampleView(RemoteBrowser browser) {
        viewer = new RemoteBrowserViewer(browser);
        viewer.setSizeFull();
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        add(new H2("vaadmin-remote-browser sample"), viewer);
        expand(viewer);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<String> url = event.getLocation().getQueryParameters().getSingleParameter("url");
        url.ifPresent(viewer::setInitialUrl);
    }
}
