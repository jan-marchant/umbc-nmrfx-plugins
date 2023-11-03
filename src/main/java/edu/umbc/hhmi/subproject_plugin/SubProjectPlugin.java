package edu.umbc.hhmi.subproject_plugin;

import com.google.auto.service.AutoService;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;

import java.util.Set;

@AutoService(NMRFxPlugin.class)
public class SubProjectPlugin implements NMRFxPlugin {

    public static Menu subProjMenu;

    @Override
    public String getName() {
        return NMRFxPlugin.super.getName();
    }

    @Override
    public String getVersion() {
        return NMRFxPlugin.super.getVersion();
    }

    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {
        return Set.of(EntryPoint.MENU_PLUGINS, EntryPoint.STARTUP);
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        if (entryPoint == EntryPoint.STARTUP) {
            ProjectRelations.doStartup();
        }

        if (entryPoint == EntryPoint.MENU_PLUGINS) {
            System.out.println("Adding SubProject setup to plugins menu");
            subProjMenu = new Menu("SubProjects");

            MenuItem subProjSetupMenuItem = new MenuItem("Setup");
            subProjSetupMenuItem.setOnAction(ProjectRelations::showProjectRelations);

            MenuItem subProjAssignments = new MenuItem("Get Assignments");
            subProjAssignments.setOnAction(ProjectRelations::showSubProjAssignmentTransfer);

            MenuItem subProjNoes = new MenuItem("Get NOEs");
            subProjNoes.setOnAction(ProjectRelations::showSubProjNoeTransfer);

            MenuItem subProjPeaks = new MenuItem("Get From Peaks");
            subProjPeaks.setOnAction(ProjectRelations::showSubProjPeakTransfer);

            subProjMenu.getItems().addAll(subProjSetupMenuItem, subProjAssignments, subProjNoes, subProjPeaks);
            ((Menu) object).getItems().addAll(subProjMenu);

            //KeyBindings.registerGlobalKeyAction("ds", this::tweakPeaks);
        }
    }
}
