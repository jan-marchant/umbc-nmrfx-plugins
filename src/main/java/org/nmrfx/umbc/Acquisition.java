package org.nmrfx.umbc;

import javafx.event.Event;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import java.util.Set;

public class Acquisition implements NMRFxPlugin {
//STARTUP, MENU_FILE, MENU_PLUGINS, STATUS_BAR_TOOLS
    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {
        return Set.of(EntryPoint.MENU_PLUGINS);
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        if (entryPoint == EntryPoint.MENU_PLUGINS) {
            System.out.println("Adding Acquisition setup to plugins menu");
            MenuItem acqMenuItem = new MenuItem("Acquisitions");
            acqMenuItem.setOnAction(e -> showAcqSetup(e));
            ((Menu) object).getItems().addAll(acqMenuItem);
        }
    }

    public void showAcqSetup(Event e) {
        System.out.println("Not yet implemented");
    }
}
