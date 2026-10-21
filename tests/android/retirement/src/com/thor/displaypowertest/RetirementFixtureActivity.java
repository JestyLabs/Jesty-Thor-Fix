package com.thor.displaypowertest;

import android.app.Activity;
import android.os.Bundle;
import java.util.Map;

/** Real renderer/layout/settings, no updater, network, media or automatic service start. */
public final class RetirementFixtureActivity extends Activity {
    private DashboardLayout layout;
    private DashboardRenderer renderer;
    private DashboardSettingsController settings;
    private boolean expectReplacement;
    private int replacementRequests;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!RetirementFixtureHardware.DISPOSABLE.equals(getPackageName())) {
            throw new IllegalStateException("Not the disposable fixture package");
        }
        android.content.SharedPreferences preferences = getSharedPreferences("state", MODE_PRIVATE);
        layout = new DashboardLayout(new DashboardViews(this), new DashboardLayout.TopActions() {
            public void onUpdate() {}
            public void onSupport() {}
            public void onGithub() {}
            public void onGithubLongPress() {}
            public void onDiagnostics() {}
        });
        renderer = new DashboardRenderer(layout, new DashboardRenderer.Host() {
            public void onDisplayVisual(DashboardStateModel.Visual visual) {}
            public void requestDaemonReplacement() {
                if (!expectReplacement) throw new AssertionError("Unexpected fixture dashboard replacement");
                replacementRequests++;
            }
        }, RebootActivation.ticket(this, preferences));
        settings = new DashboardSettingsController(this, preferences, layout, renderer);
        setContentView(layout.root);
        settings.bind();
    }

    String render(Map<String, String> sample, boolean expectedReplacement) {
        int before = replacementRequests;
        expectReplacement = expectedReplacement;
        try {
            renderer.render(sample, settings);
            if (replacementRequests - before != (expectedReplacement ? 1 : 0)) {
                throw new AssertionError("Unexpected dashboard replacement request count");
            }
            return layout.stateText.getText().toString();
        } finally { expectReplacement = false; }
    }

    @Override protected void onDestroy() {
        if (settings != null) settings.shutdown();
        super.onDestroy();
    }
}
