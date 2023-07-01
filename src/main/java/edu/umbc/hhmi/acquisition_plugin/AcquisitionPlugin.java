package edu.umbc.hhmi.acquisition_plugin;

import com.google.auto.service.AutoService;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import java.util.Set;

@AutoService(NMRFxPlugin.class)
public class AcquisitionPlugin implements NMRFxPlugin {
//STARTUP, MENU_FILE, MENU_PLUGINS, STATUS_BAR_TOOLS
    public static Menu acqMenu;

    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {
        return Set.of(EntryPoint.MENU_PLUGINS, EntryPoint.STARTUP);
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        if (entryPoint == EntryPoint.STARTUP) {
            Condition.doStartup();
            Sample.doStartup();
            Experiment.doStartup();
            Acquisition.doStartup();
            NoeSetup.doStartup();
            ManagedList.doStartup();
        }

        if (entryPoint == EntryPoint.MENU_PLUGINS) {
            System.out.println("Adding Acquisition setup to plugins menu");
            acqMenu = new Menu("Acquisition Setup");

            MenuItem acqMenuItem = new MenuItem("Associations");
            acqMenuItem.setOnAction(e -> Acquisition.showAcquisitionList(e));
            MenuItem sampleMenuItem = new MenuItem("Samples");
            sampleMenuItem.setOnAction(e -> Sample.showSampleList(e));
            MenuItem conditionMenuItem = new MenuItem("Conditions");
            conditionMenuItem.setOnAction(e -> Condition.showConditionList(e));
            MenuItem experimentMenuItem = new MenuItem("Experiments");
            experimentMenuItem.setOnAction(e -> Experiment.showExperimentList(e));
            MenuItem noeSetupMenuItem = new MenuItem("NOE Sets");
            noeSetupMenuItem.setOnAction(e -> NoeSetup.showNoeSetup(e));

            acqMenu.getItems().addAll(acqMenuItem,sampleMenuItem,conditionMenuItem,experimentMenuItem,noeSetupMenuItem);
            ((Menu) object).getItems().addAll(acqMenu);
        }
    }
}
