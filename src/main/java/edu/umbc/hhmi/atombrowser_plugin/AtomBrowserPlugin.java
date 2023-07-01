package edu.umbc.hhmi.atombrowser_plugin;

import com.google.auto.service.AutoService;
import javafx.event.Event;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;

import java.util.Set;

@AutoService(NMRFxPlugin.class)
public class AtomBrowserPlugin implements NMRFxPlugin {
    //STARTUP, MENU_FILE, MENU_PLUGINS, STATUS_BAR_TOOLS
    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {
        return Set.of(EntryPoint.MENU_PLUGINS);
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        if (entryPoint == EntryPoint.MENU_PLUGINS) {
            System.out.println("Adding AtomBrowser to plugins menu");

            MenuItem atomBrowserMenuItem = new MenuItem("AtomBrowser");
            atomBrowserMenuItem.setOnAction(e -> showAtomBrowser(e));

            ((Menu) object).getItems().addAll(atomBrowserMenuItem);
        }
    }

    public void showAtomBrowser(Event e) {
        System.out.println("Not yet implemented");
    }

}