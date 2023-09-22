package edu.umbc.hhmi.atombrowser_plugin;

import com.google.auto.service.AutoService;
import javafx.event.Event;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.SpectrumStatusBar;

import java.util.Set;

@AutoService(NMRFxPlugin.class)
public class AtomBrowserPlugin implements NMRFxPlugin {
    //STARTUP, MENU_FILE, MENU_PLUGINS, STATUS_BAR_TOOLS
    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {
        return Set.of(EntryPoint.STATUS_BAR_TOOLS);
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        if (entryPoint == EntryPoint.MENU_PLUGINS) {
            System.out.println("Adding AtomBrowser to plugins menu");

            MenuItem atomBrowserMenuItem = new MenuItem("AtomBrowser");
            atomBrowserMenuItem.setOnAction(this::showAtomBrowser);

            ((Menu) object).getItems().addAll(atomBrowserMenuItem);
        }
        if (entryPoint == EntryPoint.STATUS_BAR_TOOLS) {
            MenuItem atomBrowserMenuItem = new MenuItem("AtomBrowser");
            atomBrowserMenuItem.setOnAction(this::showAtomBrowser);
            ((SpectrumStatusBar) object).addToToolMenu(atomBrowserMenuItem);
        }
    }

    public void showAtomBrowser(Event e) {
        FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        if (!controller.containsTool(AtomBrowser.class)) {
            AtomBrowser atomBrowser = new AtomBrowser(controller, this::removeAtomBrowser);
            atomBrowser.initialize();
            controller.addTool(atomBrowser);
        }
    }

    public void removeAtomBrowser(AtomBrowser atomBrowser) {
        FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        controller.removeTool(AtomBrowser.class);
        controller.getBottomBox().getChildren().remove(atomBrowser.getToolBar());
    }

}