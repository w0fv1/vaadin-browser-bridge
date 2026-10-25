package dev.w0fv1.browserbridge;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@Tag("browser-stream")
@JsModule("./browser-stream.js")
public final class BrowserStreamComponent extends Component {
    public BrowserStreamComponent() {
        getElement().getStyle().set("width", "100%").set("display", "block");
    }

    public void setSessionId(String sessionId) {
        getElement().setProperty("sessionId", sessionId);
    }
}
